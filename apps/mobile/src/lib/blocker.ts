import { AppState, Platform } from "react-native";
import {
  CATALOG,
  evaluateBlocks,
  findApp,
  nextChangeAt,
  unlockAvailableAt,
  type ActiveBlock,
  type BlockRule,
  type CalendarEvent,
  type FocusSession,
} from "@foco/core";
import { getUsageToday, setNativeState, type NativeBlockState } from "../../modules/foco-blocker";
import { getPrefs } from "./settings";
import { store } from "./store";

const HORIZON_MS = 7 * 24 * 3600_000;
const MAX_SEGMENTS = 400;

/** Sessions from the user's own sessions plus calendar events marked as focus blocks. */
export function allSessions(): FocusSession[] {
  const prefs = getPrefs();
  const fromCalendar = store
    .list<CalendarEvent>("events")
    .filter((e) => e.focus && !e.allDay)
    .map<FocusSession>((e) => ({
      id: `event:${e.id}`,
      kind: "calendar",
      title: e.title,
      startedAt: e.start,
      endsAt: e.end,
      targets: prefs.focusApps.map((appId) => ({ appId, surfaces: ["app"] })),
      strict: false,
    }));
  return [...store.list<FocusSession>("sessions"), ...fromCalendar];
}

/** Minutes used today per catalog app, from Android usage stats. */
export function usageTodayByApp(): Record<string, number> {
  const byPackage = getUsageToday();
  const out: Record<string, number> = {};
  for (const app of CATALOG) {
    const minutes = app.androidPackages.reduce((sum, p) => sum + (byPackage[p] ?? 0), 0);
    if (minutes > 0) out[app.id] = Math.round(minutes);
  }
  return out;
}

export function currentBlocks(now = new Date()): ActiveBlock[] {
  const prefs = getPrefs();
  return evaluateBlocks({
    now,
    rules: store.list<BlockRule>("rules"),
    sessions: allSessions(),
    limits: prefs.limits,
    usageToday: Platform.OS === "android" ? usageTodayByApp() : {},
  });
}

/**
 * Precomputes what to block for the next days as time segments, so the
 * native service (which never evaluates rules itself) stays correct with the
 * app closed. Daily limits only apply to the current segment because usage
 * is only known now.
 */
export function buildNativeState(now = new Date()): NativeBlockState {
  const rules = store.list<BlockRule>("rules");
  const sessions = allSessions();
  const prefs = getPrefs();
  const timeline: NativeBlockState["timeline"] = [];
  let t = now;
  const horizon = new Date(now.getTime() + HORIZON_MS);
  while (t < horizon && timeline.length < MAX_SEGMENTS) {
    const next = nextChangeAt({ now: t, rules, sessions }) ?? horizon;
    const end = next < horizon ? next : horizon;
    const blocks = timeline.length === 0 ? currentBlocks(t) : evaluateBlocks({ now: t, rules, sessions });
    timeline.push({
      start: t.getTime(),
      end: end.getTime(),
      blocks: blocks.map((b) => ({
        appId: b.appId,
        packages: findApp(b.appId)?.androidPackages ?? [],
        surfaces: b.surfaces,
        reason: b.reasons.join(", "),
        strict: b.strict,
      })),
    });
    t = end;
  }
  return {
    timeline,
    captureGesture: prefs.captureGesture,
    watchedPackages: CATALOG.flatMap((a) => a.androidPackages),
  };
}

export function applyBlocking() {
  if (!store.ready) return;
  setNativeState(buildNativeState());
}

/** Keeps the native blocker in sync with rules/sessions/settings and refreshes it on foreground. */
export function startBlockingSync() {
  let timer: ReturnType<typeof setTimeout> | null = null;
  const schedule = () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(applyBlocking, 200);
  };
  const unsubStore = store.subscribe(schedule);
  const sub = AppState.addEventListener("change", (s) => s === "active" && schedule());
  schedule();
  return () => {
    unsubStore();
    sub.remove();
  };
}

export function stopSession(session: FocusSession): { ok: true } | { ok: false; reason: string; availableAt?: Date } {
  if (session.strict) return { ok: false, reason: "Es una sesión estricta: no se puede parar antes de tiempo." };
  store.put("sessions", session.id, { ...session, endedAt: new Date().toISOString() });
  return { ok: true };
}

export function canPauseNow(): { allowed: boolean; availableAt: Date | null } {
  const availableAt = unlockAvailableAt(currentBlocks(), new Date(), getPrefs().friction);
  return { allowed: availableAt !== null, availableAt };
}
