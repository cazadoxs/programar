import { createHash, createSign } from "node:crypto";

/**
 * Google Play purchase verification for the one-time "Foco Pro" product.
 * Uses a Google Cloud service account linked to the Play Console (Users and
 * permissions → invite the service account with "View financial data" and
 * "Manage orders"), authenticated with a self-signed JWT (no SDK needed).
 */
export interface PlayConfig {
  packageName: string;
  serviceAccount?: { client_email: string; private_key: string; token_uri?: string };
  /** Development only: accept any token without calling Google. */
  acceptAll?: boolean;
}

export interface VerifiedPurchase {
  orderId: string | null;
  /** sha256(userId) set by the app at purchase time, if any. */
  accountId: string | null;
  acknowledged: boolean;
}

export class BillingError extends Error {
  constructor(message: string, readonly status: 400 | 402 | 409 | 502 | 503) {
    super(message);
  }
}

/** The opaque account id the app passes to Play as `obfuscatedAccountId`. */
export const playAccountId = (userId: string) => createHash("sha256").update(`foco:${userId}`).digest("hex");

const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

export function createPlayVerifier(config: PlayConfig, fetchImpl: typeof fetch = fetch) {
  let cached: { token: string; exp: number } | null = null;

  async function accessToken(): Promise<string> {
    const sa = config.serviceAccount;
    if (!sa) throw new BillingError("La verificación de compras no está configurada en el servidor", 503);
    if (cached && cached.exp > Date.now() + 60_000) return cached.token;
    const now = Math.floor(Date.now() / 1000);
    const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
    const b64 = (v: object) => Buffer.from(JSON.stringify(v)).toString("base64url");
    const unsigned = `${b64({ alg: "RS256", typ: "JWT" })}.${b64({ iss: sa.client_email, scope: SCOPE, aud: tokenUri, iat: now, exp: now + 3600 })}`;
    const signature = createSign("RSA-SHA256").update(unsigned).sign(sa.private_key).toString("base64url");
    const res = await fetchImpl(tokenUri, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${signature}` }),
    });
    if (!res.ok) throw new BillingError("No se pudo autenticar con Google Play", 502);
    const json = (await res.json()) as { access_token: string; expires_in: number };
    cached = { token: json.access_token, exp: Date.now() + json.expires_in * 1000 };
    return json.access_token;
  }

  return {
    async verify(productId: string, purchaseToken: string): Promise<VerifiedPurchase> {
      if (config.acceptAll) return { orderId: `dev-${purchaseToken.slice(0, 12)}`, accountId: null, acknowledged: true };
      const url = `${API}/${encodeURIComponent(config.packageName)}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;
      const headers = { authorization: `Bearer ${await accessToken()}` };
      const res = await fetchImpl(url, { headers });
      if (res.status === 400 || res.status === 404 || res.status === 410) throw new BillingError("Compra no válida", 402);
      if (!res.ok) throw new BillingError("Google Play no respondió; inténtalo más tarde", 502);
      const p = (await res.json()) as {
        purchaseState?: number;
        acknowledgementState?: number;
        orderId?: string;
        obfuscatedExternalAccountId?: string;
      };
      // 0 = purchased, 1 = cancelled/refunded, 2 = pending.
      if (p.purchaseState !== 0) throw new BillingError(p.purchaseState === 2 ? "La compra está pendiente de pago" : "La compra se canceló o se reembolsó", 402);
      let acknowledged = p.acknowledgementState === 1;
      if (!acknowledged) {
        const ack = await fetchImpl(`${url}:acknowledge`, { method: "POST", headers: { ...headers, "content-type": "application/json" }, body: "{}" });
        acknowledged = ack.ok;
      }
      return { orderId: p.orderId ?? null, accountId: p.obfuscatedExternalAccountId ?? null, acknowledged };
    },
  };
}

export type PlayVerifier = ReturnType<typeof createPlayVerifier>;

export function parseServiceAccount(raw: string | undefined): PlayConfig["serviceAccount"] {
  if (!raw) return undefined;
  const text = raw.trim().startsWith("{") ? raw : Buffer.from(raw, "base64").toString("utf8");
  const json = JSON.parse(text) as { client_email?: string; private_key?: string; token_uri?: string };
  if (!json.client_email || !json.private_key) throw new Error("GOOGLE_PLAY_SERVICE_ACCOUNT no es una clave de cuenta de servicio válida");
  return { client_email: json.client_email, private_key: json.private_key, token_uri: json.token_uri };
}
