import { randomUUID } from "node:crypto";
import { lt } from "drizzle-orm";
import { z } from "zod";
import {
  CATALOG,
  eventsBetween,
  freeSlots,
  isSessionBlocking,
  mergeEvents,
  sessionLabel,
  type CalendarEvent,
  type CalendarSource,
  type FocusSession,
  type Task,
} from "@foco/core";
import type { Db } from "../db";
import { aiPending } from "../db/schema";
import { getRecord, listRecords, putRecord } from "../records";

export const ASSISTANT_DEVICE = "server:assistant";
const FOCO_SOURCE = "foco";

/** Tools that change or remove the user's things: from the chat they only propose, the user confirms in the app. */
export const CONFIRM_TOOLS = ["delete_event", "complete_task", "stop_focus_session"] as const;
export const PENDING_TTL_MS = 30 * 60_000;
/** Tool calls allowed in one chat turn, whatever the provider loop does. */
const MAX_TOOL_CALLS = 20;

export interface PendingAction {
  id: string;
  tool: string;
  summary: string;
}

export interface ExecutorOptions {
  now?: () => Date;
  /** Used to word pending-action summaries. */
  timeZone?: string;
  /** Record CONFIRM_TOOLS as pending actions (pushed here) instead of running them. */
  pending?: PendingAction[];
}

const isoDate = z.string().refine((s) => !Number.isNaN(Date.parse(s)), "Fecha inválida");
const surfaces = z.enum(["app", "reels", "stories", "explore", "messages", "feed"]);

const schemas = {
  list_events: z.object({ from: isoDate, to: isoDate }),
  create_event: z.object({
    title: z.string().min(1).max(300),
    start: isoDate,
    end: isoDate,
    location: z.string().max(500).optional(),
    notes: z.string().max(5000).optional(),
    focus: z.boolean().optional(),
  }),
  move_event: z.object({ id: z.string().max(300), start: isoDate, end: isoDate }),
  delete_event: z.object({ id: z.string().max(300) }),
  list_tasks: z.object({ includeDone: z.boolean().optional() }),
  create_task: z.object({ title: z.string().min(1).max(300), due: z.string().max(40).optional(), estimateMinutes: z.number().int().positive().max(100_000).optional(), notes: z.string().max(5000).optional() }),
  complete_task: z.object({ id: z.string().max(300) }),
  find_free_slots: z.object({ from: isoDate, to: isoDate, minMinutes: z.number().int().positive().optional() }),
  start_focus_session: z.object({
    kind: z.enum(["pomodoro", "study", "manual"]),
    minutes: z.number().int().min(1).max(12 * 60),
    title: z.string().optional(),
    appIds: z.array(z.string()).min(1),
    surfaces: z.array(surfaces).optional(),
  }),
  stop_focus_session: z.object({}),
} as const;

type ToolName = keyof typeof schemas;

/** Executes assistant tool calls against the user's synced data. Every write syncs to their devices. */
export function createExecutor(db: Db, userId: string, opts: ExecutorOptions = {}) {
  const now = opts.now ?? (() => new Date());

  async function editableEvent(id: string, verb: string) {
    const rec = await getRecord<CalendarEvent>(db, userId, "events", id);
    if (!rec) throw new Error("No existe ese evento");
    if (rec.data.sourceId !== FOCO_SOURCE) throw new Error(`Solo puedo ${verb} eventos del calendario de Foco`);
    return rec.data;
  }
  async function activeSessions() {
    const sessions = await listRecords<FocusSession>(db, userId, "sessions");
    const active = sessions.map((r) => r.data).filter((s) => !s.endedAt && new Date(s.endsAt) > now() && new Date(s.startedAt) <= now());
    if (active.some((s) => s.strict)) throw new Error("Hay una sesión estricta: no se puede parar antes de tiempo");
    return active;
  }

  /** Checks a destructive call now (so the model hears about errors) and words it for the user. */
  const describe: { [K in (typeof CONFIRM_TOOLS)[number]]: (input: z.infer<(typeof schemas)[K]>) => Promise<string> } = {
    async delete_event({ id }) {
      const e = await editableEvent(id, "borrar");
      return `Borrar «${short(e.title)}» (${when(e, opts.timeZone)})`;
    },
    async complete_task({ id }) {
      const rec = await getRecord<Task>(db, userId, "tasks", id);
      if (!rec) throw new Error("No existe esa tarea");
      return `Marcar como hecha «${short(rec.data.title)}»`;
    },
    async stop_focus_session() {
      const active = await activeSessions();
      if (!active.length) throw new Error("No hay ninguna sesión de foco activa");
      return active.length === 1 ? `Parar la sesión «${short(active[0]!.title ?? sessionLabel(active[0]!.kind))}»` : `Parar ${active.length} sesiones de foco`;
    },
  };

  const handlers: { [K in ToolName]: (input: z.infer<(typeof schemas)[K]>) => Promise<unknown> } = {
    async list_events({ from, to }) {
      const [events, sources] = await Promise.all([
        listRecords<CalendarEvent>(db, userId, "events"),
        listRecords<CalendarSource>(db, userId, "sources"),
      ]);
      const merged = mergeEvents(events.map((r) => r.data), sources.map((r) => r.data));
      // Titles and locations can come from anyone who invites the user: data, never instructions.
      return {
        untrusted: true,
        events: eventsBetween(merged, new Date(from), new Date(to)).slice(0, 200).map(({ id, title, start, end, allDay, location, sourceId, focus }) => ({
          id, title, start, end, allDay, location, focus, editable: sourceId === FOCO_SOURCE,
        })),
      };
    },
    async create_event(input) {
      if (new Date(input.end) <= new Date(input.start)) throw new Error("El final debe ser posterior al inicio");
      const event: CalendarEvent = { id: randomUUID(), sourceId: FOCO_SOURCE, allDay: false, updatedAt: now().toISOString(), ...input };
      await putRecord(db, userId, "events", event.id, event, ASSISTANT_DEVICE);
      return { id: event.id, created: true };
    },
    async move_event({ id, start, end }) {
      if (new Date(end) <= new Date(start)) throw new Error("El final debe ser posterior al inicio");
      const event = await editableEvent(id, "mover");
      await putRecord(db, userId, "events", id, { ...event, start, end, updatedAt: now().toISOString() }, ASSISTANT_DEVICE);
      return { id, moved: true };
    },
    async delete_event({ id }) {
      const event = await editableEvent(id, "borrar");
      await putRecord(db, userId, "events", id, event, ASSISTANT_DEVICE, true);
      return { id, deleted: true };
    },
    async list_tasks({ includeDone }) {
      const tasks = (await listRecords<Task>(db, userId, "tasks")).map((r) => r.data);
      return tasks.filter((t) => includeDone || !t.done);
    },
    async create_task(input) {
      const ts = now().toISOString();
      const task: Task = { id: randomUUID(), done: false, createdAt: ts, updatedAt: ts, ...input };
      await putRecord(db, userId, "tasks", task.id, task, ASSISTANT_DEVICE);
      return { id: task.id, created: true };
    },
    async complete_task({ id }) {
      const rec = await getRecord<Task>(db, userId, "tasks", id);
      if (!rec) throw new Error("No existe esa tarea");
      await putRecord(db, userId, "tasks", id, { ...rec.data, done: true, updatedAt: now().toISOString() }, ASSISTANT_DEVICE);
      return { id, done: true };
    },
    async find_free_slots({ from, to, minMinutes }) {
      const events = (await listRecords<CalendarEvent>(db, userId, "events")).map((r) => r.data);
      return freeSlots(events, new Date(from), new Date(to), minMinutes ?? 30).map((s) => ({ start: s.start.toISOString(), end: s.end.toISOString() }));
    },
    async start_focus_session({ kind, minutes, title, appIds, surfaces }) {
      const unknown = appIds.filter((id) => !CATALOG.some((a) => a.id === id));
      if (unknown.length) throw new Error(`Apps desconocidas: ${unknown.join(", ")}`);
      const start = now();
      const session: FocusSession = {
        id: randomUUID(),
        kind,
        title,
        startedAt: start.toISOString(),
        endsAt: new Date(start.getTime() + minutes * 60_000).toISOString(),
        targets: appIds.map((appId) => ({ appId, surfaces: surfaces?.length ? surfaces : ["app"] })),
        strict: false,
      };
      await putRecord(db, userId, "sessions", session.id, session, ASSISTANT_DEVICE);
      return { id: session.id, endsAt: session.endsAt };
    },
    async stop_focus_session() {
      const active = await activeSessions();
      for (const s of active) {
        await putRecord(db, userId, "sessions", s.id, { ...s, endedAt: now().toISOString() }, ASSISTANT_DEVICE);
      }
      return { stopped: active.length, wasBlocking: active.some((s) => isSessionBlocking(s, now())) };
    },
  };

  let calls = 0;
  return async function execute(name: string, rawInput: unknown): Promise<unknown> {
    if (++calls > MAX_TOOL_CALLS) throw new Error("Demasiadas acciones en un solo mensaje; para y resume al usuario lo que llevas.");
    if (!Object.hasOwn(schemas, name)) throw new Error(`Herramienta desconocida: ${name}`);
    const tool = name as ToolName;
    const parsed = schemas[tool].safeParse(rawInput ?? {});
    if (!parsed.success) throw new Error(`Parámetros inválidos: ${parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ")}`);
    if (opts.pending && (CONFIRM_TOOLS as readonly string[]).includes(tool)) {
      const summary = await (describe[tool as (typeof CONFIRM_TOOLS)[number]] as (i: unknown) => Promise<string>)(parsed.data);
      const action: PendingAction = { id: randomUUID(), tool, summary };
      await db.delete(aiPending).where(lt(aiPending.createdAt, new Date(now().getTime() - PENDING_TTL_MS)));
      await db.insert(aiPending).values({ ...action, userId, input: parsed.data as object });
      opts.pending.push(action);
      return { pending: true, id: action.id, summary, message: "Pendiente de confirmación del usuario: todavía no se ha hecho. Dile que lo confirme en la app." };
    }
    return (handlers[tool] as (i: unknown) => Promise<unknown>)(parsed.data);
  };
}

const short = (s: string) => (s.length > 80 ? `${s.slice(0, 79)}…` : s);

/** "jue 15, 17:00" in the user's zone; all-day events by date only. */
function when(e: CalendarEvent, timeZone = "UTC"): string {
  const d = new Date(e.start);
  if (Number.isNaN(d.getTime())) return e.start;
  try {
    return e.allDay
      ? new Intl.DateTimeFormat("es-ES", { weekday: "short", day: "numeric", timeZone: "UTC" }).format(d)
      : new Intl.DateTimeFormat("es-ES", { weekday: "short", day: "numeric", hour: "2-digit", minute: "2-digit", timeZone }).format(d);
  } catch {
    return e.start;
  }
}
