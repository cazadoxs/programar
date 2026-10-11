import { api, tokenStorage } from "./api";
import { refreshPro, resetPro } from "./pro";
import { store } from "./store";

export interface AuthResult {
  token: string;
  user: { id: string; email: string; name: string | null };
}

export async function signIn(mode: "login" | "register", body: { email: string; password: string; name?: string }) {
  const res = await api<AuthResult>(`/auth/${mode}`, { body });
  await tokenStorage.set(res.token);
  store.adopt(res.user.id);
  await store.sync();
  void refreshPro();
  return res.user;
}

/** Logs out and forgets this account's data on this device (it stays in the account). */
export async function signOut(everywhere = false) {
  if (everywhere) await api("/auth/logout-all", { method: "POST" }).catch(() => undefined);
  await tokenStorage.set(null);
  store.clear();
  resetPro();
}

export async function deleteAccount(password: string) {
  await api("/me", { method: "DELETE", body: { password } });
  await tokenStorage.set(null);
  store.clear();
  resetPro();
}

export async function changePassword(currentPassword: string, newPassword: string) {
  const { token } = await api<{ token: string }>("/me/password", { method: "PUT", body: { currentPassword, newPassword } });
  await tokenStorage.set(token);
}
