import { DEFAULT_POMODORO, type FocusSession, type SessionKind, type Surface } from "@foco/core";
import { getPrefs } from "./settings";
import { newId, store } from "./store";

export function startSession(opts: { kind: SessionKind; minutes: number; title?: string; appIds?: string[]; surfaces?: Surface[]; strict?: boolean }) {
  const prefs = getPrefs();
  const start = new Date();
  const session: FocusSession = {
    id: newId(),
    kind: opts.kind,
    title: opts.title,
    startedAt: start.toISOString(),
    endsAt: new Date(start.getTime() + opts.minutes * 60_000).toISOString(),
    targets: (opts.appIds ?? prefs.focusApps).map((appId) => ({ appId, surfaces: opts.surfaces?.length ? opts.surfaces : ["app"] })),
    strict: opts.strict ?? false,
    pomodoro: opts.kind === "pomodoro" ? prefs.pomodoro ?? DEFAULT_POMODORO : undefined,
  };
  store.put("sessions", session.id, session);
  return session;
}

export function activeSessions(now = new Date()): FocusSession[] {
  return store
    .list<FocusSession>("sessions")
    .filter((s) => !s.endedAt && new Date(s.startedAt) <= now && new Date(s.endsAt) > now);
}
