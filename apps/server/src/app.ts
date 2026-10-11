import { randomUUID } from "node:crypto";
import { and, count, eq, lt, sql } from "drizzle-orm";
import { Hono, type Context } from "hono";
import { bodyLimit } from "hono/body-limit";
import { cors } from "hono/cors";
import { sign, verify } from "hono/jwt";
import { z } from "zod";
import { ASSISTANT_TOOLS, FREE_LIMITS, PRO_PRODUCT_ID, assistantSystemPrompt, type Plan } from "@foco/core";
import { derivedSecret, issueToken, requireAuth, type AuthVars } from "./auth";
import { createExecutor, PENDING_TTL_MS, type PendingAction } from "./ai/executor";
import { BillingError, createPlayVerifier, playAccountId, type PlayVerifier } from "./billing";
import { createProvider, DEFAULT_MODELS, PROVIDERS, ProviderError, type ProviderFactory, type ProviderId } from "./ai";
import { googleTranscribe } from "./ai/google";
import { openaiTranscribe } from "./ai/openai";
import { googleCalendar, microsoftCalendar, type OAuthCalendarProvider, type OAuthTokens } from "./calendar/providers";
import { assertPublicUrl, UnsafeUrlError, type Resolver } from "./calendar/safeFetch";
import { deleteAccount, publicSyncError, syncAccount, syncAllForUser, type CalendarDeps } from "./calendar/sync";
import { decrypt, encrypt, hashPassword, verifyPassword } from "./crypto";
import type { Db } from "./db";
import { aiKeys, aiPending, calendarAccounts, oauthStates, purchases, users } from "./db/schema";
import type { Env } from "./env";
import { clientIp, createRateLimiter, rateLimit, tooMany, type RateLimitConfig } from "./ratelimit";
import { pullRecords, pushRecords } from "./records";

export interface AppDeps {
  db: Db;
  env: Env;
  providerFactory?: ProviderFactory;
  fetchImpl?: typeof fetch;
  calendarProviders?: Partial<Record<"google" | "microsoft", OAuthCalendarProvider>>;
  playVerifier?: PlayVerifier;
  /** Override or disable (`false`) the rate limits. */
  rateLimits?: RateLimitConfig;
  /** DNS resolver used to vet ICS hosts (tests inject one). */
  resolve?: Resolver;
}

/** Rejected on register and password change (existing accounts can still log in with them). */
const COMMON_PASSWORDS = new Set([
  "12345678", "123456789", "1234567890", "password", "password1", "contraseña", "qwertyuiop", "11111111", "iloveyou", "00000000",
  "abcd1234", "87654321", "12341234", "qwerty123", "123123123", "contrasena",
]);
const email = z.string().max(254).email("Email no válido").transform((e) => e.toLowerCase());
const newPassword = z
  .string()
  .min(8, "La contraseña debe tener al menos 8 caracteres")
  .max(256, "La contraseña es demasiado larga")
  .refine((p) => !COMMON_PASSWORDS.has(p.toLowerCase()), "Esa contraseña es demasiado común; elige otra");
const anyPassword = z.string().min(1, "Falta la contraseña").max(256, "La contraseña es demasiado larga");

/** Same cost as a real check, so a login for an unknown email takes as long as a wrong password. */
const DUMMY_HASH = `scrypt:${Buffer.alloc(16).toString("base64")}:${Buffer.alloc(64).toString("base64")}`;

const isTimeZone = (tz: string) => {
  try {
    new Intl.DateTimeFormat("en", { timeZone: tz });
    return true;
  } catch {
    return false;
  }
};
const timeZone = z.string().max(64).refine(isTimeZone, "Zona horaria no válida").default("Europe/Madrid");
const chatBody = z.object({
  messages: z
    .array(z.object({ role: z.enum(["user", "assistant"]), content: z.string().min(1).max(20_000, "Mensaje demasiado largo") }))
    .min(1)
    .max(50, "Conversación demasiado larga"),
  mode: z.enum(["chat", "work"]).default("chat"),
  timeZone,
});
const language = z.string().max(10).regex(/^[a-z]{2}(-[A-Z]{2})?$/, "Idioma no válido").optional();

const OAUTH_PROVIDERS = ["google", "microsoft"] as const;
const OAUTH_STATE_SECONDS = 600;
const MAX_ICS_ACCOUNTS = 10;

export function createApp(deps: AppDeps) {
  const { db, env } = deps;
  const fetchImpl = deps.fetchImpl ?? fetch;
  const providerFactory = deps.providerFactory ?? createProvider;
  const calendarProviders = deps.calendarProviders ?? { google: googleCalendar(env, fetchImpl), microsoft: microsoftCalendar(env, fetchImpl) };
  const calDeps: CalendarDeps = { db, key: env.encryptionKey, providers: calendarProviders, fetchImpl, resolve: deps.resolve };
  const auth = requireAuth(env.jwtSecret, async (uid) => {
    const [row] = await db.select({ v: users.tokenVersion }).from(users).where(eq(users.id, uid));
    return row?.v;
  });
  const play = deps.playVerifier ?? createPlayVerifier(env.play ?? { packageName: "app.foco.mobile" }, fetchImpl);
  const stateSecret = derivedSecret(env.jwtSecret, "oauth-state");
  const returnTargets = ["foco://oauth", `${env.webUrl}/oauth`];

  const limiter = createRateLimiter(deps.rateLimits);
  const byIp = (c: Context) => clientIp(c, !!env.trustProxy);
  const byUser = (c: Context) => (c as Context<AuthVars>).get("userId");
  const loginIpLimit = rateLimit(limiter, "loginIp", byIp);
  const calendarLimit = rateLimit(limiter, "calendar", byUser);

  async function planOf(uid: string): Promise<Plan> {
    const [user] = await db.select({ plan: users.plan }).from(users).where(eq(users.id, uid));
    return user?.plan === "pro" ? "pro" : "free";
  }

  /** Invalidates every session of the user; returns the new version. */
  async function bumpTokenVersion(uid: string): Promise<number> {
    const [row] = await db.update(users).set({ tokenVersion: sql`${users.tokenVersion} + 1` }).where(eq(users.id, uid)).returning({ v: users.tokenVersion });
    return row?.v ?? 0;
  }

  const app = new Hono<AuthVars>();
  // Native apps send no Origin; browsers only from the web app.
  app.use("*", cors({ origin: env.dev ? [env.webUrl, "http://localhost:8081"] : [env.webUrl] }));
  const jsonLimit = bodyLimit({ maxSize: 1024 * 1024, onError: (c) => c.json({ error: "La petición es demasiado grande" }, 413) });
  const audioLimit = bodyLimit({ maxSize: 26 * 1024 * 1024, onError: (c) => c.json({ error: "Audio demasiado largo" }, 413) });
  app.use("*", (c, next) => (c.req.path === "/ai/transcribe" ? audioLimit : jsonLimit)(c, next));
  app.onError((err, c) => {
    if (err instanceof ProviderError) return c.json({ error: err.message }, err.status as 400);
    if (err instanceof BillingError) return c.json({ error: err.message }, err.status);
    if (err instanceof z.ZodError) return c.json({ error: err.issues.map((i) => i.message).join("; ") }, 400);
    if (err instanceof SyntaxError) return c.json({ error: "JSON no válido" }, 400);
    // Only the message: driver errors append the query parameters (user data) after "params:".
    const msg = (e: unknown) => (e instanceof Error ? e.message.split("\nparams:")[0]!.slice(0, 500) : "error");
    const cause = err instanceof Error && err.cause ? ` (${msg(err.cause)})` : "";
    console.error(`[foco] ${c.req.method} ${c.req.path}: ${msg(err)}${cause}`);
    return c.json({ error: "Error interno" }, 500);
  });

  app.get("/health", (c) => c.json({ ok: true }));

  // ---- Account -------------------------------------------------------
  app.post("/auth/register", rateLimit(limiter, "register", byIp), async (c) => {
    const body = z.object({ email, password: newPassword, name: z.string().max(100).optional() }).parse(await c.req.json());
    const [existing] = await db.select({ id: users.id }).from(users).where(eq(users.email, body.email));
    // Trade-off: this 409 tells whether an email has an account (the app needs it to suggest logging in).
    // Registration is rate-limited per IP, which keeps enumeration slow.
    if (existing) return c.json({ error: "Ya existe una cuenta con ese email" }, 409);
    const user = { id: randomUUID(), email: body.email, name: body.name ?? null, passwordHash: await hashPassword(body.password) };
    await db.insert(users).values(user);
    return c.json({ token: await issueToken(user.id, env.jwtSecret, 0), user: { id: user.id, email: user.email, name: user.name } }, 201);
  });

  app.post("/auth/login", loginIpLimit, async (c) => {
    const body = z.object({ email, password: anyPassword }).parse(await c.req.json());
    const hit = limiter.hit("loginEmail", body.email);
    if (!hit.ok) return tooMany(c, hit.retryAfter);
    const [user] = await db.select().from(users).where(eq(users.email, body.email));
    const ok = await verifyPassword(body.password, user?.passwordHash ?? DUMMY_HASH);
    if (!user || !ok) return c.json({ error: "Email o contraseña incorrectos" }, 401);
    limiter.reset("loginEmail", body.email);
    return c.json({ token: await issueToken(user.id, env.jwtSecret, user.tokenVersion), user: { id: user.id, email: user.email, name: user.name } });
  });

  /** Logs out every device (including this one). */
  app.post("/auth/logout-all", auth, async (c) => {
    await bumpTokenVersion(c.get("userId"));
    return c.json({ ok: true });
  });

  app.get("/me", auth, async (c) => {
    const [user] = await db.select().from(users).where(eq(users.id, c.get("userId")));
    if (!user) return c.json({ error: "Cuenta no encontrada" }, 404);
    return c.json({ id: user.id, email: user.email, name: user.name, plan: user.plan === "pro" ? "pro" : "free" });
  });

  /** Changes the password and logs out every other device; returns a fresh token for this one. */
  app.put("/me/password", loginIpLimit, auth, async (c) => {
    const body = z.object({ currentPassword: anyPassword, newPassword }).parse(await c.req.json());
    const [user] = await db.select().from(users).where(eq(users.id, c.get("userId")));
    if (!user) return c.json({ error: "Cuenta no encontrada" }, 404);
    if (!(await verifyPassword(body.currentPassword, user.passwordHash))) return c.json({ error: "Contraseña incorrecta" }, 401);
    const [row] = await db
      .update(users)
      .set({ passwordHash: await hashPassword(body.newPassword), tokenVersion: sql`${users.tokenVersion} + 1` })
      .where(eq(users.id, user.id))
      .returning({ v: users.tokenVersion });
    return c.json({ token: await issueToken(user.id, env.jwtSecret, row?.v ?? 0) });
  });

  /** Deletes the account and everything in it (Google Play requires in-app account deletion). */
  app.delete("/me", loginIpLimit, auth, async (c) => {
    const body = z.object({ password: anyPassword }).parse(await c.req.json());
    const [user] = await db.select().from(users).where(eq(users.id, c.get("userId")));
    if (!user) return c.json({ error: "Cuenta no encontrada" }, 404);
    if (!(await verifyPassword(body.password, user.passwordHash))) return c.json({ error: "Contraseña incorrecta" }, 401);
    // Best effort: tell Google to forget the calendar grant. Never blocks the deletion.
    const google = calendarProviders.google;
    if (google?.revoke) {
      const accounts = await db.select().from(calendarAccounts).where(and(eq(calendarAccounts.userId, user.id), eq(calendarAccounts.provider, "google")));
      await Promise.allSettled(accounts.map(async (a) => google.revoke!(JSON.parse(decrypt(a.encryptedSecret, env.encryptionKey)) as OAuthTokens)));
    }
    await db.delete(users).where(eq(users.id, user.id)); // records, keys, calendars, purchases and pending actions cascade
    return c.json({ ok: true });
  });

  // ---- Foco Pro --------------------------------------------------------
  app.get("/billing", auth, async (c) => {
    const uid = c.get("userId");
    return c.json({ plan: await planOf(uid), productId: PRO_PRODUCT_ID, accountId: playAccountId(uid), limits: FREE_LIMITS });
  });

  app.post("/billing/google/verify", auth, async (c) => {
    const body = z.object({ productId: z.literal(PRO_PRODUCT_ID), purchaseToken: z.string().min(10).max(4096) }).parse(await c.req.json());
    const uid = c.get("userId");
    const [claimed] = await db.select().from(purchases).where(eq(purchases.purchaseToken, body.purchaseToken));
    if (claimed && claimed.userId !== uid) throw new BillingError("Esta compra ya está asociada a otra cuenta de Foco", 409);
    const verified = await play.verify(body.productId, body.purchaseToken);
    if (verified.accountId && verified.accountId !== playAccountId(uid)) throw new BillingError("Esta compra se hizo con otra cuenta de Foco", 409);
    if (!claimed) {
      await db.insert(purchases).values({ purchaseToken: body.purchaseToken, userId: uid, productId: body.productId, orderId: verified.orderId }).onConflictDoNothing();
      const [owner] = await db.select({ userId: purchases.userId }).from(purchases).where(eq(purchases.purchaseToken, body.purchaseToken));
      if (owner?.userId !== uid) throw new BillingError("Esta compra ya está asociada a otra cuenta de Foco", 409);
    }
    await db.update(users).set({ plan: "pro" }).where(eq(users.id, uid));
    return c.json({ plan: "pro" as Plan, acknowledged: verified.acknowledged });
  });

  // ---- Sync ----------------------------------------------------------
  app.post("/sync/push", auth, rateLimit(limiter, "syncPush", byUser), async (c) => {
    // Records are validated one by one inside pushRecords: a bad one is reported, not fatal.
    const body = z.object({ deviceId: z.string().max(100), changes: z.array(z.unknown()).max(1000) }).parse(await c.req.json());
    return c.json(await pushRecords(db, c.get("userId"), body.changes));
  });

  app.get("/sync/pull", auth, async (c) => {
    const cursor = Math.max(0, Number(c.req.query("cursor") ?? 0) || 0);
    const limit = Math.max(1, Math.min(Number(c.req.query("limit") ?? 500) || 500, 1000));
    return c.json(await pullRecords(db, c.get("userId"), cursor, limit));
  });

  // ---- AI ------------------------------------------------------------
  app.use("/ai/*", auth, rateLimit(limiter, "ai", byUser));

  app.get("/ai/settings", async (c) => {
    const uid = c.get("userId");
    const [user] = await db.select({ aiProvider: users.aiProvider }).from(users).where(eq(users.id, uid));
    const keys = await db.select({ provider: aiKeys.provider, model: aiKeys.model }).from(aiKeys).where(eq(aiKeys.userId, uid));
    return c.json({
      provider: user?.aiProvider ?? keys[0]?.provider ?? null,
      providers: PROVIDERS.map((p) => ({ provider: p, hasKey: keys.some((k) => k.provider === p), model: keys.find((k) => k.provider === p)?.model ?? DEFAULT_MODELS[p] })),
    });
  });

  app.put("/ai/keys/:provider", async (c) => {
    const provider = z.enum(PROVIDERS).parse(c.req.param("provider"));
    const body = z.object({ apiKey: z.string().min(10).max(500), model: z.string().max(100).optional() }).parse(await c.req.json());
    const uid = c.get("userId");
    const values = { userId: uid, provider, encryptedKey: encrypt(body.apiKey.trim(), env.encryptionKey), model: body.model ?? null, updatedAt: new Date() };
    await db.insert(aiKeys).values(values).onConflictDoUpdate({ target: [aiKeys.userId, aiKeys.provider], set: values });
    const [user] = await db.select({ aiProvider: users.aiProvider }).from(users).where(eq(users.id, uid));
    if (!user?.aiProvider) await db.update(users).set({ aiProvider: provider }).where(eq(users.id, uid));
    return c.json({ ok: true });
  });

  app.delete("/ai/keys/:provider", async (c) => {
    const provider = z.enum(PROVIDERS).parse(c.req.param("provider"));
    const uid = c.get("userId");
    await db.delete(aiKeys).where(and(eq(aiKeys.userId, uid), eq(aiKeys.provider, provider)));
    await db.update(users).set({ aiProvider: null }).where(and(eq(users.id, uid), eq(users.aiProvider, provider)));
    return c.json({ ok: true });
  });

  app.put("/ai/settings", async (c) => {
    const body = z.object({ provider: z.enum(PROVIDERS) }).parse(await c.req.json());
    await db.update(users).set({ aiProvider: body.provider }).where(eq(users.id, c.get("userId")));
    return c.json({ ok: true });
  });

  /** The chosen provider's key, else any key; with `exact`, only that provider's key. */
  async function keyFor(uid: string, preferred?: ProviderId, exact = false) {
    const keys = await db.select().from(aiKeys).where(eq(aiKeys.userId, uid));
    let row: (typeof keys)[number] | undefined;
    if (exact) row = keys.find((k) => k.provider === preferred);
    else {
      const [user] = await db.select({ aiProvider: users.aiProvider }).from(users).where(eq(users.id, uid));
      const wanted = preferred ?? (user?.aiProvider as ProviderId | null) ?? undefined;
      row = keys.find((k) => k.provider === wanted) ?? keys[0];
    }
    if (!row) return undefined;
    return { provider: row.provider as ProviderId, apiKey: decrypt(row.encryptedKey, env.encryptionKey), model: row.model };
  }

  app.post("/ai/chat", async (c) => {
    const body = chatBody.parse(await c.req.json());
    const uid = c.get("userId");
    if (body.mode === "work" && (await planOf(uid)) !== "pro") return c.json({ error: "El Modo Trabajo es de Foco Pro.", pro: "workMode" }, 402);
    const key = await keyFor(uid);
    if (!key) return c.json({ error: "Conecta primero una IA en Ajustes (Anthropic, OpenAI o Google)." }, 400);
    const [user] = await db.select({ name: users.name }).from(users).where(eq(users.id, uid));
    const provider = providerFactory(key.provider, key.apiKey, key.model);
    // Destructive tools only propose: the app shows `pending` and the user confirms with /ai/confirm.
    const pending: PendingAction[] = [];
    const result = await provider.run({
      system: assistantSystemPrompt({ now: new Date(), timeZone: body.timeZone, userName: user?.name ?? undefined, mode: body.mode }),
      history: body.messages,
      tools: ASSISTANT_TOOLS,
      execute: createExecutor(db, uid, { timeZone: body.timeZone, pending }),
      maxSteps: 6,
    });
    return c.json({ ...result, pending, provider: provider.id, model: provider.model });
  });

  /** Runs an action the assistant proposed, once, for its owner. */
  app.post("/ai/confirm", async (c) => {
    const body = z.object({ id: z.string().min(1).max(100) }).parse(await c.req.json());
    const uid = c.get("userId");
    const [row] = await db.delete(aiPending).where(and(eq(aiPending.id, body.id), eq(aiPending.userId, uid))).returning();
    if (!row) return c.json({ error: "Esa acción ya no está pendiente" }, 404);
    if (Date.now() - new Date(row.createdAt).getTime() > PENDING_TTL_MS) return c.json({ error: "La acción ha caducado; pídesela otra vez al asistente" }, 410);
    try {
      const result = await createExecutor(db, uid)(row.tool, row.input);
      return c.json({ ok: true, result });
    } catch (e) {
      return c.json({ error: e instanceof Error ? e.message : "No se pudo hacer" }, 409);
    }
  });

  app.post("/ai/reject", async (c) => {
    const body = z.object({ id: z.string().min(1).max(100) }).parse(await c.req.json());
    await db.delete(aiPending).where(and(eq(aiPending.id, body.id), eq(aiPending.userId, c.get("userId"))));
    return c.json({ ok: true });
  });

  /** Lets the AI interpret a quick capture the on-device parser was unsure about, and saves it. */
  app.post("/ai/capture", async (c) => {
    const body = z.object({ text: z.string().min(1).max(2000), timeZone }).parse(await c.req.json());
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
      execute: createExecutor(db, uid, { timeZone: body.timeZone, pending: [] }),
      maxSteps: 3,
    });
    return c.json(result);
  });

  app.post("/ai/transcribe", async (c) => {
    const form = await c.req.formData().catch(() => undefined);
    if (!form) return c.json({ error: "Falta el audio (campo 'file')" }, 400);
    const file = form.get("file");
    const lang = language.parse((form.get("language") as string | null) || undefined);
    if (!(file instanceof Blob)) return c.json({ error: "Falta el audio (campo 'file')" }, 400);
    if (file.size > 25 * 1024 * 1024) return c.json({ error: "Audio demasiado largo" }, 413);
    const uid = c.get("userId");
    const key = (await keyFor(uid, "openai", true)) ?? (await keyFor(uid, "google", true));
    if (!key) {
      return c.json({ error: "Para transcribir en el servidor hace falta una clave de OpenAI o Google. Sin ella, la app usa el reconocimiento de voz del móvil." }, 400);
    }
    const text =
      key.provider === "openai"
        ? await openaiTranscribe(key.apiKey, file, (file as File).name || "audio.m4a", lang, fetchImpl)
        : await googleTranscribe(key.apiKey, key.model || DEFAULT_MODELS.google, file, lang, fetchImpl);
    return c.json({ text });
  });

  // ---- Calendars -----------------------------------------------------
  app.get("/calendar/accounts", auth, calendarLimit, async (c) => {
    const rows = await db.select().from(calendarAccounts).where(eq(calendarAccounts.userId, c.get("userId")));
    return c.json({
      accounts: rows.map(({ id, provider, label, lastSyncedAt, lastError }) => ({ id, provider, label, lastSyncedAt, lastError })),
      available: { google: !!calendarProviders.google, microsoft: !!calendarProviders.microsoft, ics: true },
    });
  });

  async function canAddCalendar(uid: string) {
    if ((await planOf(uid)) === "pro") return true;
    const rows = await db.select({ id: calendarAccounts.id }).from(calendarAccounts).where(eq(calendarAccounts.userId, uid));
    return rows.length < FREE_LIMITS.cloudCalendars;
  }
  const proLimit = { error: `Con Foco gratis puedes conectar ${FREE_LIMITS.cloudCalendars} calendario en la nube. Hazte Pro para conectar todos.`, pro: "calendars" };

  async function addAccount(uid: string, provider: string, label: string, secret: object) {
    const id = randomUUID();
    await db.insert(calendarAccounts).values({ id, userId: uid, provider, label, encryptedSecret: encrypt(JSON.stringify(secret), env.encryptionKey) });
    const [row] = await db.select().from(calendarAccounts).where(eq(calendarAccounts.id, id));
    try {
      return { id, ...(await syncAccount(calDeps, row!)) };
    } catch (e) {
      // The account is saved; the app shows the error and can retry.
      return { id, error: publicSyncError(e, provider) };
    }
  }

  app.post("/calendar/ics", auth, calendarLimit, async (c) => {
    const body = z.object({ url: z.string().min(1).max(2000), label: z.string().min(1).max(100) }).parse(await c.req.json());
    const uid = c.get("userId");
    if (!(await canAddCalendar(uid))) return c.json(proLimit, 402);
    const [{ n } = { n: 0 }] = await db
      .select({ n: count() })
      .from(calendarAccounts)
      .where(and(eq(calendarAccounts.userId, uid), eq(calendarAccounts.provider, "ics")));
    if (n >= MAX_ICS_ACCOUNTS) return c.json({ error: `Puedes suscribirte a ${MAX_ICS_ACCOUNTS} calendarios .ics como máximo` }, 400);
    let url: URL;
    try {
      url = await assertPublicUrl(body.url, deps.resolve);
    } catch (e) {
      return c.json({ error: e instanceof UnsafeUrlError ? e.message : "Enlace no válido" }, 400);
    }
    return c.json(await addAccount(uid, "ics", body.label, { url: url.toString() }), 201);
  });

  /** Starts linking Google/Microsoft. `returnTo` is where the callback hands the code back to the app. */
  app.get("/calendar/oauth/:provider/start", auth, calendarLimit, async (c) => {
    const id = z.enum(OAUTH_PROVIDERS).parse(c.req.param("provider"));
    const provider = calendarProviders[id];
    if (!provider) return c.json({ error: `${id} no está configurado en el servidor` }, 501);
    const returnTo = c.req.query("returnTo");
    if (!returnTo || !returnTargets.includes(returnTo)) return c.json({ error: "returnTo no válido" }, 400);
    if (!(await canAddCalendar(c.get("userId")))) return c.json(proLimit, 402);
    const now = Math.floor(Date.now() / 1000);
    const state = await sign(
      { sub: c.get("userId"), provider: id, purpose: "calendar-oauth", returnTo, jti: randomUUID(), iat: now, exp: now + OAUTH_STATE_SECONDS },
      stateSecret,
      "HS256",
    );
    return c.json({ url: provider.authUrl(state) });
  });

  async function readState(state: string | undefined, id: string) {
    if (!state || state.length > 4096) return undefined;
    try {
      const p = await verify(state, stateSecret, "HS256");
      if (p.purpose !== "calendar-oauth" || p.provider !== id || typeof p.returnTo !== "string" || !returnTargets.includes(p.returnTo)) return undefined;
      return p as { sub: string; returnTo: string; jti: string; exp: number };
    } catch {
      return undefined;
    }
  }

  /**
   * The provider redirects the browser here. Nothing is linked yet: the code
   * goes back to the app, which finishes with its own session (/finish), so
   * a link someone else started can't attach their calendar to this account.
   */
  app.get("/calendar/oauth/:provider/callback", async (c) => {
    const id = c.req.param("provider");
    const state = (OAUTH_PROVIDERS as readonly string[]).includes(id) ? await readState(c.req.query("state"), id) : undefined;
    if (!state) return page(c, "El enlace ha caducado o no es válido. Vuelve a intentarlo desde la app.", 400);
    const code = c.req.query("code");
    const error = c.req.query("error") ?? (code ? undefined : "invalid_request");
    const params: Record<string, string> = error ? { provider: id, error: error.slice(0, 100) } : { provider: id, code: code!, state: c.req.query("state")! };
    return c.redirect(`${state.returnTo}?${new URLSearchParams(params)}`, 302);
  });

  app.post("/calendar/oauth/:provider/finish", auth, calendarLimit, async (c) => {
    const id = z.enum(OAUTH_PROVIDERS).parse(c.req.param("provider"));
    const body = z.object({ code: z.string().min(1).max(4096), state: z.string().min(1).max(4096) }).parse(await c.req.json());
    const provider = calendarProviders[id];
    if (!provider) return c.json({ error: `${id} no está configurado en el servidor` }, 501);
    const uid = c.get("userId");
    const state = await readState(body.state, id);
    if (!state) return c.json({ error: "El enlace ha caducado. Vuelve a intentarlo desde la app." }, 400);
    if (state.sub !== uid) return c.json({ error: "Este enlace se abrió desde otra cuenta de Foco" }, 403);
    await db.delete(oauthStates).where(lt(oauthStates.expiresAt, new Date()));
    const fresh = await db.insert(oauthStates).values({ jti: String(state.jti), expiresAt: new Date(state.exp * 1000) }).onConflictDoNothing().returning();
    if (!fresh.length) return c.json({ error: "Este enlace ya se ha usado. Vuelve a intentarlo desde la app." }, 409);
    if (!(await canAddCalendar(uid))) return c.json(proLimit, 402);
    let tokens: OAuthTokens;
    try {
      tokens = await provider.exchangeCode(body.code);
    } catch (e) {
      console.warn(`[foco] OAuth ${id}: ${e instanceof Error ? e.message.slice(0, 200) : "error"}`);
      return c.json({ error: "No se pudo conectar el calendario. Vuelve a intentarlo." }, 400);
    }
    return c.json(await addAccount(uid, id, id === "google" ? "Google" : "Microsoft", tokens), 201);
  });

  app.post("/calendar/sync", auth, calendarLimit, async (c) => c.json({ results: await syncAllForUser(calDeps, c.get("userId")) }));

  app.delete("/calendar/accounts/:id", auth, calendarLimit, async (c) => {
    const ok = await deleteAccount(calDeps, c.get("userId"), c.req.param("id"));
    return ok ? c.json({ ok: true }) : c.json({ error: "No encontrado" }, 404);
  });

  return app;
}

const escapeHtml = (s: string) => s.replace(/[&<>"']/g, (ch) => `&#${ch.charCodeAt(0)};`);

function page(c: Context, message: string, status: 200 | 400 = 200) {
  c.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
  c.header("X-Content-Type-Options", "nosniff");
  c.header("Referrer-Policy", "no-referrer");
  return c.html(
    `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Foco</title><body style="font-family:system-ui;display:grid;place-items:center;height:100vh;margin:0"><p>${escapeHtml(message)}</p></body>`,
    status,
  );
}
