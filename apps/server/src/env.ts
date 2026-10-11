import { randomBytes } from "node:crypto";
import { parseServiceAccount, type PlayConfig } from "./billing";

export interface Env {
  databaseUrl: string;
  jwtSecret: string;
  /** 32-byte key (base64) used to encrypt AI keys and calendar tokens at rest. */
  encryptionKey: Buffer;
  publicUrl: string;
  /** Where the web app lives; the only browser origin allowed by CORS and an allowed OAuth return target. */
  webUrl: string;
  /** Trust the first hop of X-Forwarded-For for rate limiting (only behind your own proxy). */
  trustProxy?: boolean;
  /** Development mode: allows the local Expo web origin and temporary secrets. */
  dev?: boolean;
  google?: { clientId: string; clientSecret: string };
  microsoft?: { clientId: string; clientSecret: string; tenant: string };
  play: PlayConfig;
}

export const JWT_SECRET_PLACEHOLDER = "cambia-esto-por-algo-largo-y-aleatorio";

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  // Production, or anything served over https, must have real secrets and a real database.
  const dev = source.NODE_ENV !== "production" && !source.PUBLIC_URL?.startsWith("https");
  const required = (name: string, devFallback: () => string) => {
    const v = source[name];
    if (v) return v;
    if (!dev) throw new Error(`Falta la variable de entorno ${name}`);
    console.warn(`[foco] ${name} no está definida; usando un valor temporal de desarrollo.`);
    return devFallback();
  };
  const encryptionKey = Buffer.from(required("ENCRYPTION_KEY", () => randomBytes(32).toString("base64")), "base64");
  if (encryptionKey.length !== 32) throw new Error("ENCRYPTION_KEY debe ser 32 bytes en base64");
  const jwtSecret = required("JWT_SECRET", () => randomBytes(32).toString("hex"));
  if (!dev && (jwtSecret.length < 32 || jwtSecret === JWT_SECRET_PLACEHOLDER)) {
    throw new Error("JWT_SECRET debe tener al menos 32 caracteres aleatorios (openssl rand -hex 32)");
  }
  if (!dev && !source.DATABASE_URL) throw new Error("Falta la variable de entorno DATABASE_URL");
  return {
    databaseUrl: source.DATABASE_URL ?? "file:.data/foco",
    jwtSecret,
    encryptionKey,
    publicUrl: source.PUBLIC_URL ?? `http://localhost:${source.PORT ?? 8787}`,
    webUrl: (source.WEB_URL ?? "http://localhost:8081").replace(/\/+$/, ""),
    trustProxy: source.TRUST_PROXY === "1",
    dev,
    google: source.GOOGLE_CLIENT_ID && source.GOOGLE_CLIENT_SECRET
      ? { clientId: source.GOOGLE_CLIENT_ID, clientSecret: source.GOOGLE_CLIENT_SECRET }
      : undefined,
    microsoft: source.MICROSOFT_CLIENT_ID && source.MICROSOFT_CLIENT_SECRET
      ? { clientId: source.MICROSOFT_CLIENT_ID, clientSecret: source.MICROSOFT_CLIENT_SECRET, tenant: source.MICROSOFT_TENANT ?? "common" }
      : undefined,
    play: {
      packageName: source.GOOGLE_PLAY_PACKAGE ?? "app.foco.mobile",
      serviceAccount: parseServiceAccount(source.GOOGLE_PLAY_SERVICE_ACCOUNT),
      // Never in production: lets you test the purchase flow locally.
      acceptAll: dev && source.BILLING_DEV_ACCEPT_ALL === "1",
    },
  };
}
