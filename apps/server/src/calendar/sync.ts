import { and, eq } from "drizzle-orm";
import { parseIcs, type CalendarEvent, type CalendarSource } from "@foco/core";
import { decrypt, encrypt } from "../crypto";
import type { Db } from "../db";
import { calendarAccounts } from "../db/schema";
import { listRecords, putRecord } from "../records";
import { sourceRecord, type OAuthCalendarProvider, type OAuthTokens, type RemoteEvent } from "./providers";

export const CALENDAR_DEVICE = "server:calendar";
const WINDOW_PAST_DAYS = 30;
const WINDOW_FUTURE_DAYS = 180;

export interface CalendarDeps {
  db: Db;
  key: Buffer;
  providers: Partial<Record<"google" | "microsoft", OAuthCalendarProvider>>;
  fetchImpl: typeof fetch;
}

type Account = typeof calendarAccounts.$inferSelect;

/** Pulls every calendar of one account into the user's synced `sources` and `events`. */
export async function syncAccount(deps: CalendarDeps, account: Account, now = new Date()): Promise<{ events: number }> {
  const from = new Date(now.getTime() - WINDOW_PAST_DAYS * 86_400_000);
  const to = new Date(now.getTime() + WINDOW_FUTURE_DAYS * 86_400_000);
  const secret = JSON.parse(decrypt(account.encryptedSecret, deps.key));
  const calendars: Array<{ source: CalendarSource; events: RemoteEvent[] }> = [];

  try {
    if (account.provider === "ics") {
      const res = await deps.fetchImpl(String(secret.url).replace(/^webcal:/, "https:"));
      if (!res.ok) throw new Error(`No se pudo descargar el calendario (${res.status})`);
      const events = parseIcs(await res.text(), { from, to }).map((e, i) => ({
        externalId: `${e.uid ?? i}:${e.start.getTime()}`,
        iCalUID: e.uid,
        title: e.title,
        start: e.start.toISOString(),
        end: e.end.toISOString(),
        allDay: e.allDay,
        location: e.location,
        notes: e.description,
      }));
      calendars.push({ source: sourceRecord(account.id, "ics", account.label, "#8e8e93", true), events });
    } else {
      const provider = deps.providers[account.provider as "google" | "microsoft"];
      if (!provider) throw new Error(`Proveedor ${account.provider} no configurado en el servidor`);
      let tokens = secret as OAuthTokens;
      if (tokens.expiresAt < Date.now() + 60_000) {
        tokens = await provider.refresh(tokens);
        await deps.db
          .update(calendarAccounts)
          .set({ encryptedSecret: encrypt(JSON.stringify(tokens), deps.key) })
          .where(eq(calendarAccounts.id, account.id));
      }
      for (const cal of await provider.listCalendars(tokens.accessToken)) {
        const id = `${account.id}:${cal.externalId}`;
        calendars.push({
          source: sourceRecord(id, account.provider as "google" | "microsoft", `${cal.name} (${account.label})`, cal.color, cal.readOnly),
          events: await provider.listEvents(tokens.accessToken, cal.externalId, from, to),
        });
      }
    }
  } catch (e) {
    await deps.db.update(calendarAccounts).set({ lastError: e instanceof Error ? e.message : String(e) }).where(eq(calendarAccounts.id, account.id));
    throw e;
  }

  const existing = await listRecords<CalendarEvent>(deps.db, account.userId, "events");
  let count = 0;
  for (const { source, events } of calendars) {
    await putRecord(deps.db, account.userId, "sources", source.id, source, CALENDAR_DEVICE);
    const seen = new Set<string>();
    for (const e of events) {
      const id = `${source.id}:${e.externalId}`;
      seen.add(id);
      const prev = existing.find((r) => r.id === id)?.data;
      const next: CalendarEvent = { ...e, id, sourceId: source.id, updatedAt: now.toISOString() };
      // Only write what changed so devices do not re-download the whole calendar.
      if (!prev || !sameEvent(prev, next)) {
        await putRecord(deps.db, account.userId, "events", id, next, CALENDAR_DEVICE);
      }
      count++;
    }
    for (const r of existing) {
      if (r.data.sourceId === source.id && !seen.has(r.id) && new Date(r.data.start) >= from && new Date(r.data.start) <= to) {
        await putRecord(deps.db, account.userId, "events", r.id, r.data, CALENDAR_DEVICE, true);
      }
    }
  }
  await deps.db.update(calendarAccounts).set({ lastSyncedAt: now, lastError: null }).where(eq(calendarAccounts.id, account.id));
  return { events: count };
}

export async function syncAllForUser(deps: CalendarDeps, userId: string) {
  const accounts = await deps.db.select().from(calendarAccounts).where(eq(calendarAccounts.userId, userId));
  const results: Array<{ id: string; label: string; events?: number; error?: string }> = [];
  for (const a of accounts) {
    try {
      results.push({ id: a.id, label: a.label, ...(await syncAccount(deps, a)) });
    } catch (e) {
      results.push({ id: a.id, label: a.label, error: e instanceof Error ? e.message : String(e) });
    }
  }
  return results;
}

export async function deleteAccount(deps: CalendarDeps, userId: string, accountId: string) {
  const [account] = await deps.db.select().from(calendarAccounts).where(and(eq(calendarAccounts.id, accountId), eq(calendarAccounts.userId, userId)));
  if (!account) return false;
  for (const kind of ["events", "sources"] as const) {
    const recs = await listRecords<{ sourceId?: string }>(deps.db, userId, kind);
    for (const r of recs) {
      const owner = kind === "events" ? r.data.sourceId ?? "" : r.id;
      if (owner === accountId || owner.startsWith(`${accountId}:`)) await putRecord(deps.db, userId, kind, r.id, r.data, CALENDAR_DEVICE, true);
    }
  }
  await deps.db.delete(calendarAccounts).where(eq(calendarAccounts.id, accountId));
  return true;
}

const COMPARED: Array<keyof CalendarEvent> = ["title", "start", "end", "allDay", "location", "notes", "iCalUID", "sourceId"];
function sameEvent(a: CalendarEvent, b: CalendarEvent) {
  return COMPARED.every((k) => (a[k] ?? null) === (b[k] ?? null));
}
