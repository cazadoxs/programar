import { randomUUID } from "node:crypto";
import { z } from "zod";
import {
  CATALOG,
  eventsBetween,
  freeSlots,
  isSessionBlocking,
  mergeEvents,
  type CalendarEvent,
  type CalendarSource,
  type FocusSession,
  type Task,
} from "@foco/core";
import type { Db } from "../db";
import { getRecord, listRecords, putRecord } from "../records";

export const ASSISTANT_DEVICE = "server:assistant";
const FOCO_SOURCE = "foco";

const isoDate = z.string().refine((s) => !Number.isNaN(Date.parse(s)), "Fecha inválida");
const surfaces = z.enum(["app", "reels", "stories", "explore", "messages", "feed"]);

const schemas = {
  list_events: z.object({ from: isoDate, to: isoDate }),
  create_event: z.object({
    title: z.string().min(1),
    start: isoDate,
    end: isoDate,
    location: z.string().optional(),
    notes: z.string().optional(),
    focus: z.boolean().optional(),
  }),
  move_event: z.object({ id: z.string(), start: isoDate, end: isoDate }),
  delete_event: z.object({ id: z.string() }),
  list_tasks: z.object({ includeDone: z.boolean().optional() }),
  create_task: z.object({ title: z.string().min(1), due: z.string().optional(), estimateMinutes: z.number().int().positive().optional(), notes: z.string().optional() }),
  complete_task: z.object({ id: z.string() }),
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
export function createExecutor(db: Db, userId: string, now: () => Date = () => new Date()) {
  const handlers: { [K in ToolName]: (input: z.infer<(typeof schemas)[K]>) => Promise<unknown> } = {
    async list_events({ from, to }) {
      const [events, sources] = await Promise.all([
        listRecords<CalendarEvent>(db, userId, "events"),
        listRecords<CalendarSource>(db, userId, "sources"),
      ]);
      const merged = mergeEvents(events.map((r) => r.data), sources.map((r) => r.data));
      return eventsBetween(merged, new Date(from), new Date(to)).map(({ id, title, start, end, allDay, location, sourceId, focus }) => ({
        id, title, start, end, allDay, location, focus, editable: sourceId === FOCO_SOURCE,
      }));
    },
    async create_event(input) {
      if (new Date(input.end) <= new Date(input.start)) throw new Error("El final debe ser posterior al inicio");
      const event: CalendarEvent = { id: randomUUID(), sourceId: FOCO_SOURCE, allDay: false, updatedAt: now().toISOString(), ...input };
      await putRecord(db, userId, "events", event.id, event, ASSISTANT_DEVICE);
      return { id: event.id, created: true };
    },
    async move_event({ id, start, end }) {
      const rec = await getRecord<CalendarEvent>(db, userId, "events", id);
      if (!rec) throw new Error("No existe ese evento");
      if (rec.data.sourceId !== FOCO_SOURCE) throw new Error("Solo puedo mover eventos del calendario de Foco");
      await putRecord(db, userId, "events", id, { ...rec.data, start, end, updatedAt: now().toISOString() }, ASSISTANT_DEVICE);
      return { id, moved: true };
    },
    async delete_event({ id }) {
      const rec = await getRecord<CalendarEvent>(db, userId, "events", id);
      if (!rec) throw new Error("No existe ese evento");
      if (rec.data.sourceId !== FOCO_SOURCE) throw new Error("Solo puedo borrar eventos del calendario de Foco");
      await putRecord(db, userId, "events", id, rec.data, ASSISTANT_DEVICE, true);
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
      const sessions = await listRecords<FocusSession>(db, userId, "sessions");
      const active = sessions.map((r) => r.data).filter((s) => !s.endedAt && new Date(s.endsAt) > now() && new Date(s.startedAt) <= now());
      if (active.some((s) => s.strict)) throw new Error("Hay una sesión estricta: no se puede parar antes de tiempo");
      for (const s of active) {
        await putRecord(db, userId, "sessions", s.id, { ...s, endedAt: now().toISOString() }, ASSISTANT_DEVICE);
      }
      return { stopped: active.length, wasBlocking: active.some((s) => isSessionBlocking(s, now())) };
    },
  };

  return async function execute(name: string, rawInput: unknown): Promise<unknown> {
    if (!(name in schemas)) throw new Error(`Herramienta desconocida: ${name}`);
    const tool = name as ToolName;
    const parsed = schemas[tool].safeParse(rawInput ?? {});
    if (!parsed.success) throw new Error(`Parámetros inválidos: ${parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ")}`);
    return (handlers[tool] as (i: unknown) => Promise<unknown>)(parsed.data);
  };
}
