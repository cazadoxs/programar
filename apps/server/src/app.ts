import { randomUUID } from "node:crypto";
import { and, eq } from "drizzle-orm";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { sign, verify } from "hono/jwt";
import { z } from "zod";
import { ASSISTANT_TOOLS, COLLECTIONS, assistantSystemPrompt } from "@foco/core";
import { requireAuth, issueToken, type AuthVars } from "./auth";
import { createExecutor } from "./ai/executor";
import { createProvider, DEFAULT_MODELS, PROVIDERS, ProviderError, type ProviderFactory, type ProviderId } from "./ai";
import { googleTranscribe } from "./ai/google";
import { openaiTranscribe } from "./ai/openai";
import { googleCalendar, microsoftCalendar, type OAuthCalendarProvider } from "./calendar/providers";
import { deleteAccount, syncAccount, syncAllForUser, type CalendarDeps } from "./calendar/sync";
import { decrypt, encrypt, hashPassword, verifyPassword } from "./crypto";
import type { Db } from "./db";
import { aiKeys, calendarAccounts, users } from "./db/schema";
import type { Env } from "./env";
import { pullRecords, pushRecords } from "./records";

export interface AppDeps {
  db: Db;
  env: Env;
  providerFactory?: ProviderFactory;
  fetchImpl?: typeof fetch;
  calendarProviders?: Partial<Record<"google" | "microsoft", OAuthCalendarProvider>>;
}

const credentials = z.object({ email: z.string().email().transform((e) => e.toLowerCase()), password: z.string().min(8, "La contraseña debe tener al menos 8 caracteres") });
const syncRecord = z.object({
  collection: z.enum(COLLECTIONS),
  id: z.string().min(1).max(200),
  data: z.unknown(),
  updatedAt: z.string().refine((s) => !Number.isNaN(Date.parse(s))),
  deletedAt: z.string().nullable().optional(),
  deviceId: z.string().min(1).max(100),
});
const chatBody = z.object({
  messages: z.array(z.object({ role: z.enum(["user", "assistant"]), content: z.string().min(1) })).min(1).max(50),
  mode: z.enum(["chat", "work"]).default("chat"),
  timeZone: z.string().default("Europe/Madrid"),
});

export function createApp(deps: AppDeps) {
  const { db, env } = deps;
  const fetchImpl = deps.fetchImpl ?? fetch;
  const providerFactory = deps.providerFactory ?? createProvider;
  const calendarProviders = deps.calendarProviders ?? { google: googleCalendar(env, fetchImpl), microsoft: microsoftCalendar(env, fetchImpl) };
  const calDeps: CalendarDeps = { db, key: env.encryptionKey, providers: calendarProviders, fetchImpl };
  const auth = requireAuth(env.jwtSecret);

  const app = new Hono<AuthVars>();
  app.use("*", cors());
  app.onError((err, c) => {
    if (err instanceof ProviderError) return c.json({ error: err.message }, err.status as 400);
    if (err instanceof z.ZodError) return c.json({ error: err.issues.map((i) => i.message).join("; ") }, 400);
    console.error(err);
    return c.json({ error: "Error interno" }, 500);
  });

  app.get("/health", (c) => c.json({ ok: true }));

  // ---- Account -------------------------------------------------------
  app.post("/auth/register", async (c) => {
    const body = credentials.extend({ name: z.string().max(100).optional() }).parse(await c.req.json());
    const [existing] = await db.select({ id: users.id }).from(users).where(eq(users.email, body.email));
    if (existing) return c.json({ error: "Ya existe una cuenta con ese email" }, 409);
    const user = { id: randomUUID(), email: body.email, name: body.name ?? null, passwordHash: await hashPassword(body.password) };
    await db.insert(users).values(user);
    return c.json({ token: await issueToken(user.id, env.jwtSecret), user: { id: user.id, email: user.email, name: user.name } }, 201);
  });

  app.post("/auth/login", async (c) => {
    const body = credentials.parse(await c.req.json());
    const [user] = await db.select().from(users).where(eq(users.email, body.email));
    if (!user || !(await verifyPassword(body.password, user.passwordHash))) return c.json({ error: "Email o contraseña incorrectos" }, 401);
    return c.json({ token: await issueToken(user.id, env.jwtSecret), user: { id: user.id, email: user.email, name: user.name } });
  });

  app.get("/me", auth, async (c) => {
    const [user] = await db.select().from(users).where(eq(users.id, c.get("userId")));
    if (!user) return c.json({ error: "Cuenta no encontrada" }, 404);
    return c.json({ id: user.id, email: user.email, name: user.name });
  });

  // ---- Sync ----------------------------------------------------------
  app.post("/sync/push", auth, async (c) => {
    const body = z.object({ deviceId: z.string(), changes: z.array(syncRecord).max(1000) }).parse(await c.req.json());
    return c.json(await pushRecords(db, c.get("userId"), body.changes));
  });

  app.get("/sync/pull", auth, async (c) => {
    const cursor = Number(c.req.query("cursor") ?? 0) || 0;
    const limit = Math.min(Number(c.req.query("limit") ?? 500) || 500, 1000);
    return c.json(await pullRecords(db, c.get("userId"), cursor, limit));
  });

  // ---- AI ------------------------------------------------------------
  app.get("/ai/settings", auth, async (c) => {
    const uid = c.get("userId");
    const [user] = await db.select({ aiProvider: users.aiProvider }).from(users).where(eq(users.id, uid));
    const keys = await db.select({ provider: aiKeys.provider, model: aiKeys.model }).from(aiKeys).where(eq(aiKeys.userId, uid));
    return c.json({
      provider: user?.aiProvider ?? keys[0]?.provider ?? null,
      providers: PROVIDERS.map((p) => ({ provider: p, hasKey: keys.some((k) => k.provider === p), model: keys.find((k) => k.provider === p)?.model ?? DEFAULT_MODELS[p] })),
    });
  });

  app.put("/ai/keys/:provider", auth, async (c) => {
    const provider = z.enum(PROVIDERS).parse(c.req.param("provider"));
    const body = z.object({ apiKey: z.string().min(10).max(500), model: z.string().max(100).optional() }).parse(await c.req.json());
    const uid = c.get("userId");
    const values = { userId: uid, provider, encryptedKey: encrypt(body.apiKey.trim(), env.encryptionKey), model: body.model ?? null, updatedAt: new Date() };
    await db.insert(aiKeys).values(values).onConflictDoUpdate({ target: [aiKeys.userId, aiKeys.provider], set: values });
    const [user] = await db.select({ aiProvider: users.aiProvider }).from(users).where(eq(users.id, uid));
    if (!user?.aiProvider) await db.update(users).set({ aiProvider: provider }).where(eq(users.id, uid));
    return c.json({ ok: true });
  });

  app.delete("/ai/keys/:provider", auth, async (c) => {
    const provider = z.enum(PROVIDERS).parse(c.req.param("provider"));
    const uid = c.get("userId");
    await db.delete(aiKeys).where(and(eq(aiKeys.userId, uid), eq(aiKeys.provider, provider)));
    await db.update(users).set({ aiProvider: null }).where(and(eq(users.id, uid), eq(users.aiProvider, provider)));
    return c.json({ ok: true });
  });

  app.put("/ai/settings", auth, async (c) => {
    const body = z.object({ provider: z.enum(PROVIDERS) }).parse(await c.req.json());
    await db.update(users).set({ aiProvider: body.provider }).where(eq(users.id, c.get("userId")));
    return c.json({ ok: true });
  });

  async function keyFor(uid: string, preferred?: ProviderId) {
    const [user] = await db.select({ aiProvider: users.aiProvider }).from(users).where(eq(users.id, uid));
    const keys = await db.select().from(aiKeys).where(eq(aiKeys.userId, uid));
    const wanted = preferred ?? (user?.aiProvider as ProviderId | null) ?? undefined;
    const row = keys.find((k) => k.provider === wanted) ?? keys[0];
    if (!row) return undefined;
    return { provider: row.provider as ProviderId, apiKey: decrypt(row.encryptedKey, env.encryptionKey), model: row.model };
  }

  app.post("/ai/chat", auth, async (c) => {
    const body = chatBody.parse(await c.req.json());
    const uid = c.get("userId");
    const key = await keyFor(uid);
    if (!key) return c.json({ error: "Conecta primero una IA en Ajustes (Anthropic, OpenAI o Google)." }, 400);
    const [user] = await db.select({ name: users.name }).from(users).where(eq(users.id, uid));
    const provider = providerFactory(key.provider, key.apiKey, key.model);
    const result = await provider.run({
      system: assistantSystemPrompt({ now: new Date(), timeZone: body.timeZone, userName: user?.name ?? undefined, mode: body.mode }),
      history: body.messages,
      tools: ASSISTANT_TOOLS,
      execute: createExecutor(db, uid),
    });
    return c.json({ ...result, provider: provider.id, model: provider.model });
  });

  /** Lets the AI interpret a quick capture the on-device parser was unsure about, and saves it. */
  app.post("/ai/capture", auth, async (c) => {
    const body = z.object({ text: z.string().min(1).max(2000), timeZone: z.string().default("Europe/Madrid") }).parse(await c.req.json());
    const uid = c.get("userId");
    const key = await keyFor(uid);
    if (!key) return c.json({ error: "No hay IA conectada" }, 400);
    const provider = providerFactory(key.provider, key.apiKey, key.model);
    const system =
      assistantSystemPrompt({ now: new Date(), timeZone: body.timeZone, mode: "chat" }) +
      "\nEl usuario ha dictado o escrito una nota rápida. Decide si es un evento (tiene hora) o una tarea y guárdala con create_event o create_task. Responde en una frase qué has guardado.";
    const result = await provider.run({
      system,
      history: [{ role: "user", content: body.text }],
      tools: ASSISTANT_TOOLS.filter((t) => t.name === "create_event" || t.name === "create_task"),
      execute: createExecutor(db, uid),
      maxSteps: 3,
    });
    return c.json(result);
  });

  app.post("/ai/transcribe", auth, async (c) => {
    const form = await c.req.formData();
    const file = form.get("file");
    const language = (form.get("language") as string | null) ?? undefined;
    if (!(file instanceof Blob)) return c.json({ error: "Falta el audio (campo 'file')" }, 400);
    if (file.size > 25 * 1024 * 1024) return c.json({ error: "Audio demasiado largo" }, 413);
    const uid = c.get("userId");
    const key = (await keyFor(uid, "openai")) ?? (await keyFor(uid, "google"));
    if (!key || key.provider === "anthropic") {
      return c.json({ error: "Para transcribir en el servidor hace falta una clave de OpenAI o Google. Sin ella, la app usa el reconocimiento de voz del móvil." }, 400);
    }
    const text =
      key.provider === "openai"
        ? await openaiTranscribe(key.apiKey, file, (file as File).name || "audio.m4a", language, fetchImpl)
        : await googleTranscribe(key.apiKey, key.model || DEFAULT_MODELS.google, file, language, fetchImpl);
    return c.json({ text });
  });

  // ---- Calendars -----------------------------------------------------
  app.get("/calendar/accounts", auth, async (c) => {
    const rows = await db.select().from(calendarAccounts).where(eq(calendarAccounts.userId, c.get("userId")));
    return c.json({
      accounts: rows.map(({ id, provider, label, lastSyncedAt, lastError }) => ({ id, provider, label, lastSyncedAt, lastError })),
      available: { google: !!calendarProviders.google, microsoft: !!calendarProviders.microsoft, ics: true },
    });
  });

  app.post("/calendar/ics", auth, async (c) => {
    const body = z.object({ url: z.string().url().or(z.string().startsWith("webcal://")), label: z.string().min(1).max(100) }).parse(await c.req.json());
    const account = {
      id: randomUUID(),
      userId: c.get("userId"),
      provider: "ics",
      label: body.label,
      encryptedSecret: encrypt(JSON.stringify({ url: body.url }), env.encryptionKey),
    };
    await db.insert(calendarAccounts).values(account);
    const [row] = await db.select().from(calendarAccounts).where(eq(calendarAccounts.id, account.id));
    try {
      const res = await syncAccount(calDeps, row!);
      return c.json({ id: account.id, ...res }, 201);
    } catch (e) {
      return c.json({ id: account.id, error: e instanceof Error ? e.message : String(e) }, 201);
    }
  });

  app.get("/calendar/oauth/:provider/start", auth, async (c) => {
    const id = z.enum(["google", "microsoft"]).parse(c.req.param("provider"));
    const provider = calendarProviders[id];
    if (!provider) return c.json({ error: `${id} no está configurado en el servidor` }, 501);
    const now = Math.floor(Date.now() / 1000);
    const state = await sign({ sub: c.get("userId"), provider: id, purpose: "calendar-oauth", exp: now + 600 }, env.jwtSecret, "HS256");
    return c.json({ url: provider.authUrl(state) });
  });

  app.get("/calendar/oauth/:provider/callback", async (c) => {
    const id = z.enum(["google", "microsoft"]).parse(c.req.param("provider"));
    const provider = calendarProviders[id];
    const code = c.req.query("code");
    const state = c.req.query("state");
    if (!provider || !code || !state) return c.html(page("No se pudo conectar el calendario."), 400);
    let userIdFromState: string;
    try {
      const payload = await verify(state, env.jwtSecret, "HS256");
      if (payload.purpose !== "calendar-oauth" || payload.provider !== id) throw new Error("state");
      userIdFromState = String(payload.sub);
    } catch {
      return c.html(page("El enlace ha caducado. Vuelve a intentarlo desde la app."), 400);
    }
    const tokens = await provider.exchangeCode(code);
    const accountId = randomUUID();
    await db.insert(calendarAccounts).values({
      id: accountId,
      userId: userIdFromState,
      provider: id,
      label: id === "google" ? "Google" : "Microsoft",
      encryptedSecret: encrypt(JSON.stringify(tokens), env.encryptionKey),
    });
    const [row] = await db.select().from(calendarAccounts).where(eq(calendarAccounts.id, accountId));
    try {
      await syncAccount(calDeps, row!);
    } catch {
      // The account is saved; the app shows the error and can retry.
    }
    return c.html(page("¡Calendario conectado! Ya puedes volver a Foco."));
  });

  app.post("/calendar/sync", auth, async (c) => c.json({ results: await syncAllForUser(calDeps, c.get("userId")) }));

  app.delete("/calendar/accounts/:id", auth, async (c) => {
    const ok = await deleteAccount(calDeps, c.get("userId"), c.req.param("id"));
    return ok ? c.json({ ok: true }) : c.json({ error: "No encontrado" }, 404);
  });

  return app;
}

function page(message: string) {
  return `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Foco</title><body style="font-family:system-ui;display:grid;place-items:center;height:100vh;margin:0"><p>${message}</p></body>`;
}
