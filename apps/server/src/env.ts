import { randomBytes } from "node:crypto";

export interface Env {
  databaseUrl: string;
  jwtSecret: string;
  /** 32-byte key (base64) used to encrypt AI keys and calendar tokens at rest. */
  encryptionKey: Buffer;
  publicUrl: string;
  google?: { clientId: string; clientSecret: string };
  microsoft?: { clientId: string; clientSecret: string; tenant: string };
}

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  const dev = source.NODE_ENV !== "production";
  const required = (name: string, devFallback: () => string) => {
    const v = source[name];
    if (v) return v;
    if (!dev) throw new Error(`Falta la variable de entorno ${name}`);
    console.warn(`[foco] ${name} no está definida; usando un valor temporal de desarrollo.`);
    return devFallback();
  };
  const encryptionKey = Buffer.from(required("ENCRYPTION_KEY", () => randomBytes(32).toString("base64")), "base64");
  if (encryptionKey.length !== 32) throw new Error("ENCRYPTION_KEY debe ser 32 bytes en base64");
  return {
    databaseUrl: source.DATABASE_URL ?? "file:.data/foco",
    jwtSecret: required("JWT_SECRET", () => randomBytes(32).toString("hex")),
    encryptionKey,
    publicUrl: source.PUBLIC_URL ?? `http://localhost:${source.PORT ?? 8787}`,
    google: source.GOOGLE_CLIENT_ID && source.GOOGLE_CLIENT_SECRET
      ? { clientId: source.GOOGLE_CLIENT_ID, clientSecret: source.GOOGLE_CLIENT_SECRET }
      : undefined,
    microsoft: source.MICROSOFT_CLIENT_ID && source.MICROSOFT_CLIENT_SECRET
      ? { clientId: source.MICROSOFT_CLIENT_ID, clientSecret: source.MICROSOFT_CLIENT_SECRET, tenant: source.MICROSOFT_TENANT ?? "common" }
      : undefined,
  };
}
