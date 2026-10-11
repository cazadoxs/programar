import * as WebBrowser from "expo-web-browser";
import { Platform } from "react-native";
import { api } from "./api";
import { store } from "./store";

/**
 * Calendar OAuth (Google / Outlook). The server never links an account from
 * the browser alone: the provider redirects back to the app, and the app
 * finishes the link with its own session, so a link sent by someone else
 * can't attach your calendar to their account.
 */
export const oauthReturnTo = () => (Platform.OS === "web" ? `${window.location.origin}/oauth` : "foco://oauth");

const finished = new Map<string, Promise<string>>();

/** Completes a redirect `…/oauth?provider&code&state` once, however many times it arrives. */
export function finishOAuth(params: { provider?: string; code?: string; state?: string; error?: string }): Promise<string> {
  if (params.error) return Promise.reject(new Error(params.error === "access_denied" ? "Has cancelado el permiso." : "No se pudo conectar el calendario."));
  if (!params.provider || !params.code || !params.state) return Promise.reject(new Error("Respuesta incompleta del proveedor."));
  const key = params.state;
  if (!finished.has(key)) {
    finished.set(
      key,
      api<{ events?: number; error?: string }>(`/calendar/oauth/${params.provider}/finish`, { body: { code: params.code, state: params.state } }).then(async (r) => {
        await store.sync();
        return r.error ? `Calendario conectado, pero la primera sincronización falló: ${r.error}` : "¡Calendario conectado!";
      }),
    );
  }
  return finished.get(key)!;
}

export async function connectCalendar(provider: "google" | "microsoft"): Promise<string | undefined> {
  const returnTo = oauthReturnTo();
  const { url } = await api<{ url: string }>(`/calendar/oauth/${provider}/start?returnTo=${encodeURIComponent(returnTo)}`);
  const result = await WebBrowser.openAuthSessionAsync(url, returnTo);
  if (result.type !== "success") return undefined;
  const q = new URL(result.url).searchParams;
  return finishOAuth({ provider: q.get("provider") ?? undefined, code: q.get("code") ?? undefined, state: q.get("state") ?? undefined, error: q.get("error") ?? undefined });
}
