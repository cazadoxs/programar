// Lógica pura del servidor: validación, agregación y límites. Sin E/S, para poder probarla.

export const CATEGORIES = [
  "comercial", "energia", "telefonia", "finanzas", "encuesta", "robot", "estafa", "otro",
] as const;
export type Category = (typeof CATEGORIES)[number];

export interface ReportInput {
  number: string;
  category: Category;
  reporter: string;
}

const E164 = /^\+[1-9]\d{6,14}$/;
const REPORTER = /^[A-Za-z0-9_-]{16,64}$/;

/** Valida el cuerpo de una denuncia. Devuelve null si algo no cuadra. */
export function parseReport(body: unknown): ReportInput | null {
  if (typeof body !== "object" || body === null) return null;
  const b = body as Record<string, unknown>;
  const { number, category, reporter } = b;
  if (typeof number !== "string" || !E164.test(number)) return null;
  if (typeof category !== "string" || !(CATEGORIES as readonly string[]).includes(category)) return null;
  if (typeof reporter !== "string" || !REPORTER.test(reporter)) return null;
  // Números españoles: deben tener 9 cifras tras el +34.
  if (number.startsWith("+34") && number.length !== 12) return null;
  return { number, category: category as Category, reporter };
}

export interface Row {
  number: string;
  category: string;
  reporters: number;
}

export interface ListEntry {
  n: string;
  r: number;
  c: Category;
}

/**
 * Agrupa filas (número, categoría, nº de denunciantes) en una entrada por número.
 * La categoría publicada es la más votada. Solo entran números con al menos
 * `minReports` denunciantes distintos y que no estén excluidos a mano.
 */
export function buildList(rows: Row[], minReports: number, excluded: Set<string>): ListEntry[] {
  const byNumber = new Map<string, { total: number; cats: Map<string, number> }>();
  for (const row of rows) {
    if (excluded.has(row.number)) continue;
    const e = byNumber.get(row.number) ?? { total: 0, cats: new Map() };
    e.total += row.reporters;
    e.cats.set(row.category, (e.cats.get(row.category) ?? 0) + row.reporters);
    byNumber.set(row.number, e);
  }
  const out: ListEntry[] = [];
  for (const [n, e] of byNumber) {
    if (e.total < minReports) continue;
    let best = "otro";
    let bestCount = -1;
    for (const [c, count] of e.cats) {
      if (count > bestCount || (count === bestCount && c < best)) {
        best = c;
        bestCount = count;
      }
    }
    const c = (CATEGORIES as readonly string[]).includes(best) ? (best as Category) : "otro";
    out.push({ n, r: e.total, c });
  }
  out.sort((a, b) => b.r - a.r || a.n.localeCompare(b.n));
  return out;
}

export const LIMITS = {
  reportsPerInstallPerDay: 30,
  reportsPerIpPerDay: 120,
  reportWindowDays: 90,
  retentionDays: 180,
  subscriptionCacheMs: 12 * 60 * 60 * 1000,
  maxEntries: 250_000,
} as const;

export function dayKey(nowMs: number): string {
  return new Date(nowMs).toISOString().slice(0, 10);
}

export async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Estado de una suscripción según la API de Google Play (purchases.subscriptionsv2). */
export interface SubscriptionV2 {
  subscriptionState?: string;
  lineItems?: { productId?: string; expiryTime?: string }[];
}

/**
 * ¿Da acceso esta suscripción? Devuelve hasta cuándo (ms) o null.
 * Solo cuentan los estados activos o en periodo de gracia, y el producto correcto.
 */
export function subscriptionValidUntil(sub: SubscriptionV2, productId: string, nowMs: number): number | null {
  const okState = sub.subscriptionState === "SUBSCRIPTION_STATE_ACTIVE" ||
    sub.subscriptionState === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";
  if (!okState) return null;
  const item = (sub.lineItems ?? []).find((li) => li.productId === productId);
  if (!item) return null;
  const expiry = item.expiryTime ? Date.parse(item.expiryTime) : NaN;
  const cap = nowMs + LIMITS.subscriptionCacheMs;
  if (Number.isNaN(expiry)) return cap;
  if (expiry <= nowMs) return null;
  return Math.min(expiry, cap);
}
