import { createHmac } from "node:crypto";
import type { Context, MiddlewareHandler } from "hono";
import { sign, verify } from "hono/jwt";

export interface AuthVars {
  Variables: { userId: string };
}

const THIRTY_DAYS = 60 * 60 * 24 * 30;

/**
 * Session token. `typ` keeps other JWTs signed by the server from working as
 * logins; `ver` is the user's token version, bumped by "log out everywhere",
 * password changes and account deletion.
 */
export async function issueToken(userId: string, secret: string, version = 0): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  return sign({ sub: userId, typ: "access", ver: version, iat: now, exp: now + THIRTY_DAYS }, secret, "HS256");
}

/** Separate key for short-lived non-session tokens (OAuth state), derived from the JWT secret. */
export function derivedSecret(secret: string, purpose: string): string {
  return createHmac("sha256", secret).update(purpose).digest("hex");
}

/** `versionOf` returns the user's current token version, or undefined if the account is gone. */
export function requireAuth(secret: string, versionOf: (userId: string) => Promise<number | undefined>): MiddlewareHandler<AuthVars> {
  return async (c, next) => {
    const header = c.req.header("authorization") ?? "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : undefined;
    if (!token) return c.json({ error: "No autenticado" }, 401);
    let userId: string;
    let ver: unknown;
    try {
      const payload = await verify(token, secret, "HS256");
      if (payload.typ !== "access" || typeof payload.sub !== "string") throw new Error("typ");
      userId = payload.sub;
      ver = payload.ver;
    } catch {
      return c.json({ error: "Sesión caducada o inválida" }, 401);
    }
    const current = await versionOf(userId);
    if (current === undefined || ver !== current) return c.json({ error: "Sesión caducada o inválida" }, 401);
    c.set("userId", userId);
    await next();
  };
}

export const userId = (c: Context<AuthVars>) => c.get("userId");
