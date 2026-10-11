import { lookup } from "node:dns/promises";
import { BlockList, isIP } from "node:net";

/**
 * Fetching user-supplied URLs (ICS subscriptions) without opening an SSRF
 * hole: https only, every hop's host resolved and checked against private,
 * loopback, link-local, CGNAT, ULA, multicast and metadata ranges, redirects
 * followed by hand (max 3), a timeout and a size cap.
 *
 * Residual risk: the address checked here and the one `fetch` connects to
 * come from two separate DNS lookups, so a hostile DNS server could answer
 * differently the second time (DNS rebinding). Pinning the address needs
 * undici's Agent `connect.lookup`, which is not a dependency of this server.
 * The window is small and the response is only parsed as iCalendar, never
 * returned verbatim, which limits what an attacker could read.
 */

export class UnsafeUrlError extends Error {}
export type Resolver = (hostname: string) => Promise<string[]>;

export const defaultResolver: Resolver = async (hostname) => (await lookup(hostname, { all: true, verbatim: true })).map((a) => a.address);

const blocked = new BlockList();
for (const [net, prefix] of [
  ["0.0.0.0", 8], ["10.0.0.0", 8], ["100.64.0.0", 10], ["127.0.0.0", 8], ["169.254.0.0", 16], ["172.16.0.0", 12],
  ["192.0.0.0", 24], ["192.0.2.0", 24], ["192.88.99.0", 24], ["192.168.0.0", 16], ["198.18.0.0", 15], ["198.51.100.0", 24],
  ["203.0.113.0", 24], ["224.0.0.0", 4], ["240.0.0.0", 4],
] as const) blocked.addSubnet(net, prefix, "ipv4");
for (const [net, prefix] of [
  ["::", 128], ["::1", 128], ["100::", 64], ["2001:db8::", 32], ["fc00::", 7], ["fe80::", 10], ["fec0::", 10], ["ff00::", 8],
] as const) blocked.addSubnet(net, prefix, "ipv6");

/** IPv6 address → 8 groups (handles "::" and a dotted IPv4 tail). */
function v6Groups(ip: string): number[] | null {
  let addr = ip.toLowerCase().replace(/%.*$/, "");
  const dotted = /(\d+\.\d+\.\d+\.\d+)$/.exec(addr);
  if (dotted) {
    const [a, b, c, d] = dotted[1]!.split(".").map(Number);
    addr = addr.slice(0, -dotted[1]!.length) + `${((a! << 8) | b!).toString(16)}:${((c! << 8) | d!).toString(16)}`;
  }
  const [head, tail] = addr.split("::");
  const h = head ? head.split(":") : [];
  const t = tail ? tail.split(":") : [];
  const groups = addr.includes("::") ? [...h, ...Array(8 - h.length - t.length).fill("0"), ...t] : h;
  if (groups.length !== 8) return null;
  return groups.map((g) => parseInt(g, 16));
}

const v4FromGroups = (hi: number, lo: number) => `${hi >> 8}.${hi & 255}.${lo >> 8}.${lo & 255}`;

export function isPrivateAddress(ip: string): boolean {
  const family = isIP(ip.replace(/%.*$/, ""));
  if (family === 4) return blocked.check(ip, "ipv4");
  if (family !== 6) return true;
  const g = v6Groups(ip);
  if (!g || g.some((x) => Number.isNaN(x))) return true;
  // IPv4 embedded in IPv6: mapped (::ffff:a.b.c.d), compatible (::a.b.c.d), NAT64 (64:ff9b::/96) and 6to4 (2002::/16).
  const zeros = (n: number) => g.slice(0, n).every((x) => x === 0);
  if (zeros(5) && (g[5] === 0xffff || g[5] === 0)) return g[5] === 0 && g[6] === 0 ? true : isPrivateAddress(v4FromGroups(g[6]!, g[7]!));
  if (g[0] === 0x64 && g[1] === 0xff9b && g.slice(2, 6).every((x) => x === 0)) return isPrivateAddress(v4FromGroups(g[6]!, g[7]!));
  if (g[0] === 0x2002) return isPrivateAddress(v4FromGroups(g[1]!, g[2]!));
  return blocked.check(ip, "ipv6");
}

/** Throws UnsafeUrlError unless `raw` is an https URL whose host resolves only to public addresses. */
export async function assertPublicUrl(raw: string, resolve: Resolver = defaultResolver): Promise<URL> {
  let url: URL;
  try {
    url = new URL(raw.trim().replace(/^webcal:\/\//i, "https://"));
  } catch {
    throw new UnsafeUrlError("Enlace no válido");
  }
  if (url.protocol !== "https:") throw new UnsafeUrlError("El enlace tiene que empezar por https:// o webcal://");
  if (url.username || url.password) throw new UnsafeUrlError("El enlace no puede llevar usuario ni contraseña");
  const host = url.hostname.replace(/^\[|\]$/g, "");
  if (!host || host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
    throw new UnsafeUrlError("Ese servidor no está permitido");
  }
  let addresses: string[];
  if (isIP(host)) addresses = [host];
  else {
    try {
      addresses = await resolve(host);
    } catch {
      throw new UnsafeUrlError("No se encontró el servidor del calendario");
    }
  }
  if (!addresses.length || addresses.some(isPrivateAddress)) throw new UnsafeUrlError("Ese servidor no está permitido");
  return url;
}

export interface SafeFetchOptions {
  resolve?: Resolver;
  maxBytes?: number;
  timeoutMs?: number;
  maxRedirects?: number;
}

/** GET a public https URL and return its body as text, enforcing every limit above. */
export async function safeFetchText(raw: string, fetchImpl: typeof fetch, opts: SafeFetchOptions = {}): Promise<string> {
  const { resolve = defaultResolver, maxBytes = 2 * 1024 * 1024, timeoutMs = 10_000, maxRedirects = 3 } = opts;
  const signal = AbortSignal.timeout(timeoutMs);
  let url = await assertPublicUrl(raw, resolve);
  for (let hop = 0; ; hop++) {
    const res = await fetchImpl(url, { redirect: "manual", signal, headers: { accept: "text/calendar, text/plain;q=0.9, */*;q=0.1" } });
    if (res.status >= 300 && res.status < 400 && res.headers.get("location")) {
      await res.body?.cancel().catch(() => {});
      if (hop >= maxRedirects) throw new UnsafeUrlError("Demasiadas redirecciones");
      url = await assertPublicUrl(new URL(res.headers.get("location")!, url).toString(), resolve);
      continue;
    }
    if (!res.ok) {
      await res.body?.cancel().catch(() => {});
      throw new UnsafeUrlError(`El servidor del calendario respondió ${res.status}`);
    }
    const declared = Number(res.headers.get("content-length") ?? 0);
    if (declared > maxBytes) {
      await res.body?.cancel().catch(() => {});
      throw new UnsafeUrlError("El calendario es demasiado grande");
    }
    if (!res.body) return "";
    const reader = res.body.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) {
        await reader.cancel().catch(() => {});
        throw new UnsafeUrlError("El calendario es demasiado grande");
      }
      chunks.push(value);
    }
    return Buffer.concat(chunks).toString("utf8");
  }
}
