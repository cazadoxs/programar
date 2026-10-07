import type { Surface } from "./catalog";
import { pomodoroStateAt, type PomodoroConfig } from "./pomodoro";
import { addDays, atMinutes, minutesOfDay, parseHHMM, startOfDay } from "./time";

export interface BlockTarget {
  appId: string;
  /** Include "app" to block the whole app; otherwise only these surfaces. */
  surfaces: Surface[];
}

export type Schedule =
  | { kind: "always" }
  /** days: 0 = domingo … 6 = sábado. If start > end the range crosses midnight; start == end means the whole day. */
  | { kind: "weekly"; days: number[]; start: string; end: string };

export interface BlockRule {
  id: string;
  name: string;
  enabled: boolean;
  targets: BlockTarget[];
  schedule: Schedule;
  /** Strict rules cannot be paused while active. */
  strict?: boolean;
}

export type SessionKind = "pomodoro" | "study" | "manual" | "calendar";

export interface FocusSession {
  id: string;
  kind: SessionKind;
  title?: string;
  startedAt: string;
  endsAt: string;
  /** Set when the user stops the session early. */
  endedAt?: string;
  targets: BlockTarget[];
  strict: boolean;
  pomodoro?: PomodoroConfig;
}

export interface DailyLimit {
  appId: string;
  minutesPerDay: number;
}

export interface ActiveBlock {
  appId: string;
  surfaces: Surface[];
  /** Human-readable reasons, e.g. rule or session names. */
  reasons: string[];
  strict: boolean;
}

export interface BlockInput {
  now: Date;
  rules: BlockRule[];
  sessions?: FocusSession[];
  limits?: DailyLimit[];
  /** Minutes used today per appId. */
  usageToday?: Record<string, number>;
}

export function isScheduleActive(schedule: Schedule, now: Date): boolean {
  if (schedule.kind === "always") return true;
  const start = parseHHMM(schedule.start);
  const end = parseHHMM(schedule.end);
  const m = minutesOfDay(now);
  const day = now.getDay();
  const prevDay = (day + 6) % 7;
  if (start === end) return schedule.days.includes(day);
  if (start < end) return schedule.days.includes(day) && m >= start && m < end;
  // Overnight: the part after `start` belongs to today, the part before `end` to yesterday's range.
  return (schedule.days.includes(day) && m >= start) || (schedule.days.includes(prevDay) && m < end);
}

export function isSessionBlocking(session: FocusSession, now: Date): boolean {
  if (session.endedAt) return false;
  const start = new Date(session.startedAt);
  const end = new Date(session.endsAt);
  if (now < start || now >= end) return false;
  if (session.kind === "pomodoro") return pomodoroStateAt(start, now, session.pomodoro).phase === "focus";
  return true;
}

/** Everything that must be blocked right now, merged per app. */
export function evaluateBlocks(input: BlockInput): ActiveBlock[] {
  const byApp = new Map<string, { surfaces: Set<Surface>; reasons: string[]; strict: boolean }>();
  const add = (targets: BlockTarget[], reason: string, strict: boolean) => {
    for (const t of targets) {
      const entry = byApp.get(t.appId) ?? { surfaces: new Set<Surface>(), reasons: [], strict: false };
      t.surfaces.forEach((s) => entry.surfaces.add(s));
      if (!entry.reasons.includes(reason)) entry.reasons.push(reason);
      entry.strict ||= strict;
      byApp.set(t.appId, entry);
    }
  };

  for (const rule of input.rules) {
    if (rule.enabled && isScheduleActive(rule.schedule, input.now)) add(rule.targets, rule.name, !!rule.strict);
  }
  for (const s of input.sessions ?? []) {
    if (isSessionBlocking(s, input.now)) add(s.targets, s.title ?? sessionLabel(s.kind), s.strict);
  }
  for (const limit of input.limits ?? []) {
    const used = input.usageToday?.[limit.appId] ?? 0;
    if (used >= limit.minutesPerDay) add([{ appId: limit.appId, surfaces: ["app"] }], `Límite diario (${limit.minutesPerDay} min)`, false);
  }

  return [...byApp.entries()].map(([appId, e]) => ({
    appId,
    // "app" swallows every other surface.
    surfaces: e.surfaces.has("app") ? ["app"] : [...e.surfaces],
    reasons: e.reasons,
    strict: e.strict,
  }));
}

export function isBlocked(blocks: ActiveBlock[], appId: string, surface: Surface): boolean {
  const b = blocks.find((x) => x.appId === appId);
  if (!b) return false;
  return b.surfaces.includes("app") || b.surfaces.includes(surface);
}

/**
 * Earliest instant after `now` at which `evaluateBlocks` may return something
 * different (a schedule edge, a session ending, a pomodoro phase change).
 * The native blocker sets an alarm for it so it stays correct with the app closed.
 */
export function nextChangeAt(input: Omit<BlockInput, "limits" | "usageToday">): Date | null {
  const { now } = input;
  let best: Date | null = null;
  const consider = (d: Date) => {
    if (d > now && (!best || d < best)) best = d;
  };

  for (const rule of input.rules) {
    if (!rule.enabled || rule.schedule.kind !== "weekly") continue;
    const start = parseHHMM(rule.schedule.start);
    const end = parseHHMM(rule.schedule.end);
    const today = startOfDay(now);
    for (let i = -1; i <= 7; i++) {
      const day = addDays(today, i);
      if (!rule.schedule.days.includes(day.getDay())) continue;
      consider(atMinutes(day, start));
      const endDay = start === end ? addDays(day, 1) : start < end ? day : addDays(day, 1);
      consider(atMinutes(endDay, start === end ? 0 : end));
    }
  }
  for (const s of input.sessions ?? []) {
    if (s.endedAt) continue;
    const start = new Date(s.startedAt);
    consider(start);
    consider(new Date(s.endsAt));
    if (s.kind === "pomodoro" && now >= start) consider(pomodoroStateAt(start, now, s.pomodoro).phaseEndsAt);
  }
  return best;
}

export function sessionLabel(kind: SessionKind): string {
  return { pomodoro: "Pomodoro", study: "Estudio", manual: "Sesión de foco", calendar: "Bloque del calendario" }[kind];
}

export interface FrictionSettings {
  /** Minutes the user must wait between asking to pause and the pause taking effect. */
  unlockDelayMinutes: number;
  requirePin: boolean;
  /** If set, the user must type this phrase exactly to unlock. */
  unlockPhrase?: string;
}

export const DEFAULT_FRICTION: FrictionSettings = {
  unlockDelayMinutes: 15,
  requirePin: false,
  unlockPhrase: "Estoy eligiendo distraerme",
};

/** When a pause requested at `requestedAt` becomes effective, or null if strict blocks forbid it. */
export function unlockAvailableAt(blocks: ActiveBlock[], requestedAt: Date, friction: FrictionSettings): Date | null {
  if (blocks.some((b) => b.strict)) return null;
  return new Date(requestedAt.getTime() + friction.unlockDelayMinutes * 60_000);
}
