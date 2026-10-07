import type { Context, MiddlewareHandler } from "hono";
import { sign, verify } from "hono/jwt";

export interface AuthVars {
  Variables: { userId: string };
}

const THIRTY_DAYS = 60 * 60 * 24 * 30;

export async function issueToken(userId: string, secret: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  return sign({ sub: userId, iat: now, exp: now + THIRTY_DAYS }, secret, "HS256");
}

export function requireAuth(secret: string): MiddlewareHandler<AuthVars> {
  return async (c, next) => {
    const header = c.req.header("authorization") ?? "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : undefined;
    if (!token) return c.json({ error: "No autenticado" }, 401);
    try {
      const payload = await verify(token, secret, "HS256");
      c.set("userId", String(payload.sub));
    } catch {
      return c.json({ error: "Sesión caducada o inválida" }, 401);
    }
    await next();
  };
}

export const userId = (c: Context<AuthVars>) => c.get("userId");
