import * as Calendar from "expo-calendar";
import { Platform } from "react-native";
import type { CalendarEvent, CalendarSource } from "@foco/core";
import { store } from "./store";

const PAST_DAYS = 30;
const FUTURE_DAYS = 120;

/**
 * Copies the phone's calendars into Foco: this is how Samsung, Xiaomi and any
 * other calendar without a public cloud API reaches the account (and the PC).
 */
export async function importDeviceCalendars(): Promise<{ calendars: number; events: number }> {
  if (Platform.OS === "web") throw new Error("Solo disponible en el móvil");
  const perm = await Calendar.requestCalendarPermissions();
  if (!perm.granted) throw new Error("Sin permiso para leer el calendario");

  const now = Date.now();
  const from = new Date(now - PAST_DAYS * 86_400_000);
  const to = new Date(now + FUTURE_DAYS * 86_400_000);
  const calendars = await Calendar.getCalendars(Calendar.EntityTypes.EVENT);
  let events = 0;

  for (const cal of calendars) {
    const sourceId = `device:${store.deviceId}:${cal.id}`;
    const account = cal.source?.name ? ` · ${cal.source.name}` : "";
    const source: CalendarSource = {
      id: sourceId,
      provider: "device",
      name: `${cal.title}${account}`,
      color: cal.color ?? "#8e8e93",
      visible: store.get<CalendarSource>("sources", sourceId)?.visible ?? true,
      readOnly: true,
    };
    store.put("sources", sourceId, source);

    const seen = new Set<string>();
    for (const e of await cal.listEvents(from, to)) {
      const start = new Date(e.startDate);
      const id = `${sourceId}:${e.id}:${start.getTime()}`;
      seen.add(id);
      const next: CalendarEvent = {
        id,
        sourceId,
        externalId: e.id,
        title: e.title || "(sin título)",
        start: start.toISOString(),
        end: new Date(e.endDate).toISOString(),
        allDay: e.allDay,
        location: e.location ?? undefined,
        notes: e.notes || undefined,
        updatedAt: new Date().toISOString(),
      };
      const prev = store.get<CalendarEvent>("events", id);
      if (!prev || prev.title !== next.title || prev.start !== next.start || prev.end !== next.end || prev.location !== next.location) {
        store.put("events", id, next);
      }
      events++;
    }
    for (const e of store.list<CalendarEvent>("events")) {
      if (e.sourceId === sourceId && !seen.has(e.id) && new Date(e.start) >= from && new Date(e.start) <= to) store.remove("events", e.id);
    }
  }
  return { calendars: calendars.length, events };
}
