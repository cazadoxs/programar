import { addMinutes } from "./time";

export type CalendarProvider = "foco" | "google" | "microsoft" | "apple" | "device" | "caldav" | "ics";

export interface CalendarSource {
  id: string;
  provider: CalendarProvider;
  /** e.g. "Trabajo (Outlook)", "Samsung Calendar". */
  name: string;
  color: string;
  visible: boolean;
  readOnly: boolean;
}

export interface CalendarEvent {
  id: string;
  sourceId: string;
  /** Id in the provider (Google event id, Graph id, device row id…). */
  externalId?: string;
  /** RFC 5545 UID: the same invitation has the same UID in every provider. */
  iCalUID?: string;
  title: string;
  start: string;
  end: string;
  allDay: boolean;
  location?: string;
  notes?: string;
  /** Focus blocks start a blocking session when they begin. */
  focus?: boolean;
  updatedAt: string;
}

export interface Task {
  id: string;
  title: string;
  notes?: string;
  /** ISO date (YYYY-MM-DD) or datetime; undefined = inbox. */
  due?: string;
  estimateMinutes?: number;
  done: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Events from every source, one per real-world meeting, sorted by start. */
export function mergeEvents(events: CalendarEvent[], sources: CalendarSource[] = []): CalendarEvent[] {
  const visible = new Set(sources.filter((s) => s.visible).map((s) => s.id));
  const filtered = sources.length ? events.filter((e) => visible.has(e.sourceId)) : events;
  const priority = (e: CalendarEvent) => {
    // Prefer the copy the user can edit and that came from a cloud provider.
    const src = sources.find((s) => s.id === e.sourceId);
    return (src?.readOnly ? 0 : 2) + (src && src.provider !== "device" && src.provider !== "ics" ? 1 : 0);
  };
  const keyed = new Map<string, CalendarEvent>();
  for (const e of filtered) {
    const key = dedupeKey(e);
    const existing = keyed.get(key);
    if (!existing || priority(e) > priority(existing)) keyed.set(key, e);
  }
  return [...keyed.values()].sort((a, b) => a.start.localeCompare(b.start) || a.end.localeCompare(b.end));
}

function dedupeKey(e: CalendarEvent): string {
  // Same invitation synced to several providers: same UID and start (recurring
  // instances share the UID, so the start disambiguates them).
  const start = new Date(e.start).getTime();
  if (e.iCalUID) return `uid:${e.iCalUID}:${start}`;
  const title = e.title.trim().toLowerCase().replace(/\s+/g, " ");
  return `t:${title}:${start}:${new Date(e.end).getTime()}`;
}

export interface Slot {
  start: Date;
  end: Date;
}

/** Free gaps of at least `minMinutes` between `from` and `to`, ignoring all-day events. */
export function freeSlots(events: CalendarEvent[], from: Date, to: Date, minMinutes = 15): Slot[] {
  const busy = events
    .filter((e) => !e.allDay)
    .map((e) => ({ start: new Date(e.start), end: new Date(e.end) }))
    .filter((b) => b.end > from && b.start < to)
    .sort((a, b) => a.start.getTime() - b.start.getTime());

  const slots: Slot[] = [];
  let cursor = from;
  for (const b of busy) {
    if (b.start > cursor && b.start.getTime() - cursor.getTime() >= minMinutes * 60_000) {
      slots.push({ start: cursor, end: b.start < to ? b.start : to });
    }
    if (b.end > cursor) cursor = b.end;
  }
  if (to.getTime() - cursor.getTime() >= minMinutes * 60_000) slots.push({ start: cursor, end: to });
  return slots;
}

/** First free slot that fits `minutes`, or null. */
export function findSlot(events: CalendarEvent[], from: Date, to: Date, minutes: number): Slot | null {
  const slot = freeSlots(events, from, to, minutes)[0];
  return slot ? { start: slot.start, end: addMinutes(slot.start, minutes) } : null;
}

export function eventsBetween(events: CalendarEvent[], from: Date, to: Date): CalendarEvent[] {
  return events.filter((e) => new Date(e.end) > from && new Date(e.start) < to);
}
