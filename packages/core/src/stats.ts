import type { FocusSession } from "./blocking";
import { addDays, startOfDay } from "./time";

/** Minutes of focus a session contributed between `from` and `to` (stopped sessions count until they stopped). */
export function sessionMinutesBetween(session: FocusSession, from: Date, to: Date, now: Date): number {
  const start = new Date(session.startedAt).getTime();
  const plannedEnd = new Date(session.endsAt).getTime();
  const stopped = session.endedAt ? new Date(session.endedAt).getTime() : Infinity;
  const end = Math.min(plannedEnd, stopped, now.getTime());
  const a = Math.max(start, from.getTime());
  const b = Math.min(end, to.getTime());
  return b > a ? (b - a) / 60_000 : 0;
}

/** Focus minutes per day for the `days` days ending today (oldest first). Calendar-derived sessions are skipped to avoid double counting. */
export function focusMinutesByDay(sessions: FocusSession[], now: Date, days: number): number[] {
  const today = startOfDay(now);
  const out: number[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const from = addDays(today, -i);
    const to = addDays(from, 1);
    let sum = 0;
    for (const s of sessions) if (s.kind !== "calendar") sum += sessionMinutesBetween(s, from, to, now);
    out.push(Math.round(sum));
  }
  return out;
}

/**
 * Consecutive days (ending today, or yesterday if today has no focus yet)
 * with at least `minMinutes` of focus.
 */
export function focusStreak(sessions: FocusSession[], now: Date, minMinutes = 25, maxDays = 365): number {
  const perDay = focusMinutesByDay(sessions, now, maxDays);
  let i = perDay.length - 1;
  if ((perDay[i] ?? 0) < minMinutes) i--;
  let streak = 0;
  for (; i >= 0 && (perDay[i] ?? 0) >= minMinutes; i--) streak++;
  return streak;
}

export function formatMinutes(min: number): string {
  const m = Math.round(min);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const r = m % 60;
  return r ? `${h} h ${r} min` : `${h} h`;
}
