import { useSyncExternalStore } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

/** Set EXPO_PUBLIC_API_URL to the deployed server; defaults to a local dev server. */
export const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8787").replace(/\/$/, "");

const TOKEN_KEY = "foco.token";

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

let token: string | null = null;
const listeners = new Set<() => void>();

export const tokenStorage = {
  async load() {
    token = Platform.OS === "web" ? await AsyncStorage.getItem(TOKEN_KEY) : await SecureStore.getItemAsync(TOKEN_KEY);
    listeners.forEach((l) => l());
    return token;
  },
  async set(value: string | null) {
    token = value;
    if (Platform.OS === "web") {
      if (value) await AsyncStorage.setItem(TOKEN_KEY, value);
      else await AsyncStorage.removeItem(TOKEN_KEY);
    } else if (value) await SecureStore.setItemAsync(TOKEN_KEY, value);
    else await SecureStore.deleteItemAsync(TOKEN_KEY);
    listeners.forEach((l) => l());
  },
  get: () => token,
  subscribe(l: () => void) {
    listeners.add(l);
    return () => {
      listeners.delete(l);
    };
  },
};

export async function api<T>(path: string, init: { method?: string; body?: unknown; form?: FormData } = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.authorization = `Bearer ${token}`;
  if (init.body !== undefined) headers["content-type"] = "application/json";
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      method: init.method ?? (init.body !== undefined || init.form ? "POST" : "GET"),
      headers,
      body: init.form ?? (init.body !== undefined ? JSON.stringify(init.body) : undefined),
    });
  } catch {
    throw new ApiError("Sin conexión con el servidor", 0);
  }
  const json = (await res.json().catch(() => ({}))) as { error?: string };
  if (!res.ok) {
    if (res.status === 401 && token) await tokenStorage.set(null);
    throw new ApiError(json.error ?? `Error ${res.status}`, res.status);
  }
  return json as T;
}

export function useToken(): string | null {
  return useSyncExternalStore(tokenStorage.subscribe, tokenStorage.get, tokenStorage.get);
}
