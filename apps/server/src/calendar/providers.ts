import type { CalendarEvent, CalendarSource } from "@foco/core";
import type { Env } from "../env";

export interface OAuthTokens {
  accessToken: string;
  refreshToken?: string;
  expiresAt: number;
}

export interface RemoteCalendar {
  externalId: string;
  name: string;
  color: string;
  readOnly: boolean;
}

export type RemoteEvent = Omit<CalendarEvent, "id" | "sourceId" | "updatedAt">;

export interface OAuthCalendarProvider {
  id: "google" | "microsoft";
  authUrl(state: string): string;
  exchangeCode(code: string): Promise<OAuthTokens>;
  refresh(tokens: OAuthTokens): Promise<OAuthTokens>;
  listCalendars(accessToken: string): Promise<RemoteCalendar[]>;
  listEvents(accessToken: string, calendarId: string, from: Date, to: Date): Promise<RemoteEvent[]>;
}

async function postForm(fetchImpl: typeof fetch, url: string, form: Record<string, string>) {
  const res = await fetchImpl(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(form).toString(),
  });
  if (!res.ok) throw new Error(`OAuth ${res.status}: ${(await res.text()).slice(0, 300)}`);
  return (await res.json()) as { access_token: string; refresh_token?: string; expires_in: number };
}

async function getJson<T>(fetchImpl: typeof fetch, url: string, accessToken: string, headers: Record<string, string> = {}): Promise<T> {
  const res = await fetchImpl(url, { headers: { authorization: `Bearer ${accessToken}`, ...headers } });
  if (!res.ok) throw new Error(`${new URL(url).host} ${res.status}: ${(await res.text()).slice(0, 300)}`);
  return (await res.json()) as T;
}

const toTokens = (t: { access_token: string; refresh_token?: string; expires_in: number }, previous?: OAuthTokens): OAuthTokens => ({
  accessToken: t.access_token,
  refreshToken: t.refresh_token ?? previous?.refreshToken,
  expiresAt: Date.now() + t.expires_in * 1000,
});

export function redirectUri(env: Env, provider: string) {
  return `${env.publicUrl}/calendar/oauth/${provider}/callback`;
}

export function googleCalendar(env: Env, fetchImpl: typeof fetch = fetch): OAuthCalendarProvider | undefined {
  const cfg = env.google;
  if (!cfg) return undefined;
  const redirect = redirectUri(env, "google");
  const API = "https://www.googleapis.com/calendar/v3";
  return {
    id: "google",
    authUrl: (state) =>
      `https://accounts.google.com/o/oauth2/v2/auth?${new URLSearchParams({
        client_id: cfg.clientId,
        redirect_uri: redirect,
        response_type: "code",
        scope: "https://www.googleapis.com/auth/calendar openid email",
        access_type: "offline",
        prompt: "consent",
        state,
      })}`,
    exchangeCode: async (code) =>
      toTokens(await postForm(fetchImpl, "https://oauth2.googleapis.com/token", {
        code, client_id: cfg.clientId, client_secret: cfg.clientSecret, redirect_uri: redirect, grant_type: "authorization_code",
      })),
    refresh: async (tokens) =>
      toTokens(
        await postForm(fetchImpl, "https://oauth2.googleapis.com/token", {
          refresh_token: tokens.refreshToken ?? "", client_id: cfg.clientId, client_secret: cfg.clientSecret, grant_type: "refresh_token",
        }),
        tokens,
      ),
    async listCalendars(token) {
      const body = await getJson<{ items: Array<{ id: string; summary: string; backgroundColor?: string; accessRole: string }> }>(fetchImpl, `${API}/users/me/calendarList`, token);
      return body.items.map((c) => ({ externalId: c.id, name: c.summary, color: c.backgroundColor ?? "#4285f4", readOnly: !["owner", "writer"].includes(c.accessRole) }));
    },
    async listEvents(token, calendarId, from, to) {
      const out: RemoteEvent[] = [];
      let pageToken: string | undefined;
      do {
        const qs = new URLSearchParams({ timeMin: from.toISOString(), timeMax: to.toISOString(), singleEvents: "true", maxResults: "2500" });
        if (pageToken) qs.set("pageToken", pageToken);
        const body = await getJson<{
          items: Array<{ id: string; iCalUID?: string; status?: string; summary?: string; location?: string; description?: string; start: { dateTime?: string; date?: string }; end: { dateTime?: string; date?: string } }>;
          nextPageToken?: string;
        }>(fetchImpl, `${API}/calendars/${encodeURIComponent(calendarId)}/events?${qs}`, token);
        for (const e of body.items) {
          if (e.status === "cancelled") continue;
          const allDay = !e.start.dateTime;
          out.push({
            externalId: e.id,
            iCalUID: e.iCalUID,
            title: e.summary ?? "(sin título)",
            start: allDay ? `${e.start.date}T00:00:00.000Z` : new Date(e.start.dateTime!).toISOString(),
            end: allDay ? `${e.end.date}T00:00:00.000Z` : new Date(e.end.dateTime!).toISOString(),
            allDay,
            location: e.location,
            notes: e.description,
          });
        }
        pageToken = body.nextPageToken;
      } while (pageToken);
      return out;
    },
  };
}

export function microsoftCalendar(env: Env, fetchImpl: typeof fetch = fetch): OAuthCalendarProvider | undefined {
  const cfg = env.microsoft;
  if (!cfg) return undefined;
  const redirect = redirectUri(env, "microsoft");
  const base = `https://login.microsoftonline.com/${cfg.tenant}/oauth2/v2.0`;
  const scope = "offline_access Calendars.ReadWrite User.Read";
  const GRAPH = "https://graph.microsoft.com/v1.0";
  return {
    id: "microsoft",
    authUrl: (state) =>
      `${base}/authorize?${new URLSearchParams({ client_id: cfg.clientId, response_type: "code", redirect_uri: redirect, response_mode: "query", scope, state })}`,
    exchangeCode: async (code) =>
      toTokens(await postForm(fetchImpl, `${base}/token`, {
        code, client_id: cfg.clientId, client_secret: cfg.clientSecret, redirect_uri: redirect, grant_type: "authorization_code", scope,
      })),
    refresh: async (tokens) =>
      toTokens(
        await postForm(fetchImpl, `${base}/token`, {
          refresh_token: tokens.refreshToken ?? "", client_id: cfg.clientId, client_secret: cfg.clientSecret, grant_type: "refresh_token", scope,
        }),
        tokens,
      ),
    async listCalendars(token) {
      const body = await getJson<{ value: Array<{ id: string; name: string; hexColor?: string; canEdit: boolean }> }>(fetchImpl, `${GRAPH}/me/calendars`, token);
      return body.value.map((c) => ({ externalId: c.id, name: c.name, color: c.hexColor || "#0078d4", readOnly: !c.canEdit }));
    },
    async listEvents(token, calendarId, from, to) {
      const out: RemoteEvent[] = [];
      let url: string | undefined =
        `${GRAPH}/me/calendars/${encodeURIComponent(calendarId)}/calendarView?${new URLSearchParams({ startDateTime: from.toISOString(), endDateTime: to.toISOString(), $top: "500" })}`;
      while (url) {
        const body: {
          value: Array<{ id: string; iCalUId?: string; subject?: string; isAllDay: boolean; isCancelled?: boolean; bodyPreview?: string; location?: { displayName?: string }; start: { dateTime: string }; end: { dateTime: string } }>;
          "@odata.nextLink"?: string;
        } = await getJson(fetchImpl, url, token, { prefer: 'outlook.timezone="UTC"' });
        for (const e of body.value) {
          if (e.isCancelled) continue;
          out.push({
            externalId: e.id,
            iCalUID: e.iCalUId,
            title: e.subject || "(sin título)",
            // With the UTC preference Graph returns UTC wall-clock times without an offset.
            start: new Date(`${e.start.dateTime.replace(/Z?$/, "Z")}`).toISOString(),
            end: new Date(`${e.end.dateTime.replace(/Z?$/, "Z")}`).toISOString(),
            allDay: e.isAllDay,
            location: e.location?.displayName || undefined,
            notes: e.bodyPreview || undefined,
          });
        }
        url = body["@odata.nextLink"];
      }
      return out;
    },
  };
}

export function sourceRecord(id: string, provider: CalendarSource["provider"], name: string, color: string, readOnly: boolean): CalendarSource {
  return { id, provider, name, color, visible: true, readOnly };
}
