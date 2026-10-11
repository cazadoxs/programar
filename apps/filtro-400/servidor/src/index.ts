// API de la lista de la comunidad de Filtro 400 (Cloudflare Worker + D1).
//
//   POST /v1/report  Denuncia anónima. Gratis para todos.
//   GET  /v1/list    Lista de números denunciados. Requiere suscripción Premium
//                    verificada con Google Play en el servidor.
//
// Nunca se recibe el número del usuario ni su agenda: solo el número que le llamó.

import { fetchSubscription } from "./google.ts";
import {
  LIMITS, buildList, dayKey, parseReport, sha256Hex, subscriptionValidUntil, type Row,
} from "./logic.ts";

export interface Env {
  DB: D1Database;
  PACKAGE_NAME: string;
  PRODUCT_ID: string;
  MIN_REPORTS: string;
  PEPPER: string;
  GOOGLE_SERVICE_ACCOUNT: string;
}

const DAY = 24 * 60 * 60 * 1000;
const MAX_BODY = 1024;

function json(status: number, body: unknown, extra: HeadersInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...extra,
    },
  });
}

async function handleReport(req: Request, env: Env): Promise<Response> {
  if (!env.PEPPER || env.PEPPER.length < 16) return json(500, { error: "config" });
  const len = Number(req.headers.get("Content-Length") ?? "0");
  if (len > MAX_BODY) return json(413, { error: "too_large" });
  const text = await req.text();
  if (text.length > MAX_BODY) return json(413, { error: "too_large" });
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return json(400, { error: "bad_json" });
  }
  const report = parseReport(body);
  if (!report) return json(400, { error: "invalid" });

  const now = Date.now();
  const day = dayKey(now);
  const reporterHash = await sha256Hex(`${env.PEPPER}|r|${report.reporter}`);

  // Límite por IP (hash del día; la IP nunca se guarda en claro).
  const ip = req.headers.get("CF-Connecting-IP") ?? "unknown";
  const ipHash = await sha256Hex(`${env.PEPPER}|ip|${day}|${ip}`);
  const ipRow = await env.DB.prepare(
    "INSERT INTO ip_usage (ip_day_hash, count, day) VALUES (?1, 1, ?2) " +
      "ON CONFLICT(ip_day_hash) DO UPDATE SET count = count + 1 RETURNING count",
  ).bind(ipHash, day).first<{ count: number }>();
  if ((ipRow?.count ?? 0) > LIMITS.reportsPerIpPerDay) return json(429, { error: "rate_limited" });

  // Límite por instalación.
  const recent = await env.DB.prepare(
    "SELECT COUNT(*) AS c FROM reports WHERE reporter_hash = ?1 AND created_at > ?2",
  ).bind(reporterHash, now - DAY).first<{ c: number }>();
  if ((recent?.c ?? 0) >= LIMITS.reportsPerInstallPerDay) return json(429, { error: "rate_limited" });

  // Una denuncia por número y persona: si repite, se actualiza la categoría y la fecha.
  await env.DB.prepare(
    "INSERT INTO reports (number, reporter_hash, category, created_at) VALUES (?1, ?2, ?3, ?4) " +
      "ON CONFLICT(number, reporter_hash) DO UPDATE SET category = excluded.category, created_at = excluded.created_at",
  ).bind(report.number, reporterHash, report.category, now).run();

  return json(202, { ok: true });
}

async function isSubscribed(req: Request, env: Env): Promise<boolean> {
  const auth = req.headers.get("Authorization") ?? "";
  const m = /^Bearer ([A-Za-z0-9._\-]{20,1024})$/.exec(auth);
  if (!m) return false;
  const token = m[1];
  const tokenHash = await sha256Hex(`${env.PEPPER}|t|${token}`);
  const now = Date.now();

  const cached = await env.DB.prepare("SELECT valid_until FROM subscriptions WHERE token_hash = ?1")
    .bind(tokenHash).first<{ valid_until: number }>();
  if (cached && cached.valid_until > now) return true;

  if (!env.GOOGLE_SERVICE_ACCOUNT) return false;
  const sub = await fetchSubscription(env.GOOGLE_SERVICE_ACCOUNT, env.PACKAGE_NAME, token);
  const until = sub ? subscriptionValidUntil(sub, env.PRODUCT_ID, now) : null;
  if (until === null) {
    await env.DB.prepare("DELETE FROM subscriptions WHERE token_hash = ?1").bind(tokenHash).run();
    return false;
  }
  await env.DB.prepare(
    "INSERT INTO subscriptions (token_hash, valid_until) VALUES (?1, ?2) " +
      "ON CONFLICT(token_hash) DO UPDATE SET valid_until = excluded.valid_until",
  ).bind(tokenHash, until).run();
  return true;
}

async function handleList(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  let ok: boolean;
  try {
    ok = await isSubscribed(req, env);
  } catch {
    return json(503, { error: "verification_unavailable" });
  }
  if (!ok) return json(402, { error: "subscription_required" });

  // La lista es la misma para todos: se cachea una hora en el borde.
  const cache = caches.default;
  const cacheKey = new Request("https://cache.filtro400.internal/v1/list");
  const hit = await cache.match(cacheKey);
  if (hit) return new Response(hit.body, { status: 200, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });

  const now = Date.now();
  const minReports = Math.max(2, Number(env.MIN_REPORTS) || 3);
  const { results } = await env.DB.prepare(
    "SELECT number, category, COUNT(*) AS reporters FROM reports WHERE created_at > ?1 GROUP BY number, category",
  ).bind(now - LIMITS.reportWindowDays * DAY).all<Row>();
  const excludedRows = await env.DB.prepare("SELECT number FROM excluded").all<{ number: string }>();
  const excluded = new Set(excludedRows.results.map((r) => r.number));
  const entries = buildList(results, minReports, excluded).slice(0, LIMITS.maxEntries);

  const body = JSON.stringify({ version: now, generatedAt: now, minReports, entries });
  ctx.waitUntil(cache.put(cacheKey, new Response(body, { headers: { "Cache-Control": "max-age=3600" } })));
  return new Response(body, { status: 200, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    try {
      if (url.pathname === "/v1/report" && req.method === "POST") return await handleReport(req, env);
      if (url.pathname === "/v1/list" && req.method === "GET") return await handleList(req, env, ctx);
      if (url.pathname === "/health") return json(200, { ok: true });
      return json(404, { error: "not_found" });
    } catch (e) {
      console.error(e);
      return json(500, { error: "internal" });
    }
  },

  // Limpieza diaria: denuncias viejas, contadores de IP y caché de suscripciones.
  async scheduled(_event: ScheduledController, env: Env): Promise<void> {
    const now = Date.now();
    await env.DB.batch([
      env.DB.prepare("DELETE FROM reports WHERE created_at < ?1").bind(now - LIMITS.retentionDays * DAY),
      env.DB.prepare("DELETE FROM ip_usage WHERE day < ?1").bind(dayKey(now - DAY)),
      env.DB.prepare("DELETE FROM subscriptions WHERE valid_until < ?1").bind(now),
    ]);
  },
} satisfies ExportedHandler<Env>;
