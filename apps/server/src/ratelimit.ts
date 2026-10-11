import type { Context, MiddlewareHandler } from "hono";
import { getConnInfo } from "@hono/node-server/conninfo";

/**
 * Small in-memory fixed-window rate limiter (one process; enough for a single
 * container). Memory is bounded: expired windows are swept periodically and,
 * past `maxKeys`, the oldest windows are dropped first.
 */
export interface Limit {
  limit: number;
  windowMs: number;
}

export const LIMIT_NAMES = ["loginEmail", "loginIp", "register", "ai", "calendar", "syncPush"] as const;
export type LimitName = (typeof LIMIT_NAMES)[number];

export const DEFAULT_LIMITS: Record<LimitName, Limit> = {
  loginEmail: { limit: 5, windowMs: 15 * 60_000 },
  loginIp: { limit: 20, windowMs: 60_000 },
  register: { limit: 5, windowMs: 60 * 60_000 },
  ai: { limit: 30, windowMs: 60_000 },
  calendar: { limit: 20, windowMs: 60_000 },
  syncPush: { limit: 120, windowMs: 60_000 },
};

/** `false` disables every limit (tests); a partial object overrides some. */
export type RateLimitConfig = false | Partial<Record<LimitName, Limit>>;

export type HitResult = { ok: true } | { ok: false; retryAfter: number };

export function createRateLimiter(config: RateLimitConfig = {}, opts: { maxKeys?: number; now?: () => number } = {}) {
  const maxKeys = opts.maxKeys ?? 100_000;
  const now = opts.now ?? Date.now;
  const windows = new Map<string, { count: number; resetAt: number }>();
  let lastSweep = 0;

  function sweep(t: number) {
    lastSweep = t;
    for (const [k, w] of windows) if (w.resetAt <= t) windows.delete(k);
    // Still too many live keys: drop the oldest (Map keeps insertion order).
    for (const k of windows.keys()) {
      if (windows.size <= maxKeys) break;
      windows.delete(k);
    }
  }

  const limitOf = (name: LimitName): Limit | undefined => (config === false ? undefined : (config[name] ?? DEFAULT_LIMITS[name]));

  return {
    hit(name: LimitName, key: string): HitResult {
      const l = limitOf(name);
      if (!l) return { ok: true };
      const t = now();
      if (t - lastSweep > 60_000 || windows.size > maxKeys) sweep(t);
      const id = `${name}:${key}`;
      let w = windows.get(id);
      if (!w || w.resetAt <= t) {
        windows.delete(id);
        w = { count: 0, resetAt: t + l.windowMs };
        windows.set(id, w);
      }
      w.count++;
      if (w.count > l.limit) return { ok: false, retryAfter: Math.max(1, Math.ceil((w.resetAt - t) / 1000)) };
      return { ok: true };
    },
    reset(name: LimitName, key: string) {
      windows.delete(`${name}:${key}`);
    },
    get size() {
      return windows.size;
    },
  };
}

export type RateLimiter = ReturnType<typeof createRateLimiter>;

export function tooMany(c: Context, retryAfter: number) {
  c.header("Retry-After", String(retryAfter));
  return c.json({ error: `Demasiados intentos. Vuelve a probar en ${retryAfter} s.`, retryAfter }, 429);
}

/** Client IP: first X-Forwarded-For hop only behind a trusted proxy, else the socket address. */
export function clientIp(c: Context, trustProxy: boolean): string {
  if (trustProxy) {
    const first = c.req.header("x-forwarded-for")?.split(",")[0]?.trim();
    if (first) return first;
  }
  try {
    return getConnInfo(c).remote.address ?? "unknown";
  } catch {
    return "unknown"; // no Node socket (tests, other runtimes)
  }
}

/** Middleware limiting by a key taken from the request (IP, user id…). */
export function rateLimit(limiter: RateLimiter, name: LimitName, key: (c: Context) => string): MiddlewareHandler {
  return async (c, next) => {
    const r = limiter.hit(name, key(c));
    if (!r.ok) return tooMany(c, r.retryAfter);
    await next();
  };
}
