import type { BlockRule, FocusSession } from "./blocking";
import type { Surface } from "./catalog";

/**
 * Foco is free with an optional one-time "Foco Pro" purchase (Google Play
 * in-app product `foco_pro_lifetime`). These limits are the single source of
 * truth for the app and the server.
 */
export type Plan = "free" | "pro";

export const PRO_PRODUCT_ID = "foco_pro_lifetime";

export const FREE_LIMITS = {
  /** Apps that can be blocked by part (Reels, Stories…) at the same time. Whole-app blocking is unlimited. */
  granularApps: 2,
  /** Enabled schedule rules. */
  rules: 3,
  /** Cloud calendars (Google, Outlook, .ics). The phone's own calendars are always free. */
  cloudCalendars: 1,
  /** Days of focus history shown in stats. */
  statsDays: 7,
} as const;

export type ProFeature = "strict" | "granular" | "rules" | "calendars" | "stats" | "workMode";

export const PRO_FEATURES: Array<{ id: ProFeature; title: string; description: string }> = [
  { id: "granular", title: "Bloqueo por partes sin límite", description: "Reels, Historias, Explorar o Mensajes en todas las apps que quieras." },
  { id: "strict", title: "Modo estricto", description: "Sesiones y horarios que ni tú puedes saltarte." },
  { id: "rules", title: "Horarios ilimitados", description: "Clase, estudio, noche, fin de semana… todos a la vez." },
  { id: "calendars", title: "Todos tus calendarios", description: "Google, Outlook y enlaces .ics sin límite." },
  { id: "stats", title: "Historial completo", description: "Tus rachas y horas de foco de siempre, no solo 7 días." },
  { id: "workMode", title: "Modo Trabajo de la IA", description: "El asistente planifica y ejecuta cambios en tu agenda." },
];

/** Distinct apps that use any surface other than the whole app across rules and sessions. */
export function granularAppIds(rules: Pick<BlockRule, "enabled" | "targets">[], sessions: Pick<FocusSession, "targets">[] = []): string[] {
  const ids = new Set<string>();
  const add = (targets: Array<{ appId: string; surfaces: Surface[] }>) => {
    for (const t of targets) if (t.surfaces.some((s) => s !== "app")) ids.add(t.appId);
  };
  for (const r of rules) if (r.enabled) add(r.targets);
  for (const s of sessions) add(s.targets);
  return [...ids];
}

/**
 * Whether a free user may save a rule/session with these targets, given the
 * apps already blocked by part elsewhere. Returns the feature that needs Pro.
 */
export function checkFreeTargets(
  targets: Array<{ appId: string; surfaces: Surface[] }>,
  otherGranularApps: string[],
  opts: { strict?: boolean } = {},
): ProFeature | null {
  if (opts.strict) return "strict";
  const mine = targets.filter((t) => t.surfaces.some((s) => s !== "app")).map((t) => t.appId);
  const all = new Set([...otherGranularApps, ...mine]);
  return all.size > FREE_LIMITS.granularApps ? "granular" : null;
}
