import AsyncStorage from "@react-native-async-storage/async-storage";
import { useSyncExternalStore } from "react";
import { Platform } from "react-native";
import { PRO_PRODUCT_ID, type Plan } from "@foco/core";
import { api, tokenStorage } from "./api";

/**
 * Foco Pro entitlement. Pro is a one-time Google Play purchase. The app trusts
 * the device's own Play purchases (so it works without an account) and the
 * account's plan from the server (so it follows you to the PC). Purchases
 * are verified and claimed by the server when you're logged in.
 */
const CACHE_KEY = "foco.pro.v1";

interface ProState {
  pro: boolean;
  /** Localised price from Play, e.g. "9,99 €". */
  price: string | null;
  busy: boolean;
}

let state: ProState = { pro: false, price: null, busy: false };
const listeners = new Set<() => void>();
const set = (patch: Partial<ProState>) => {
  state = { ...state, ...patch };
  listeners.forEach((l) => l());
  if (patch.pro !== undefined) void AsyncStorage.setItem(CACHE_KEY, patch.pro ? "1" : "0");
};

export const proStore = {
  get: () => state,
  subscribe(l: () => void) {
    listeners.add(l);
    return () => {
      listeners.delete(l);
    };
  },
};

export function usePro(): ProState {
  return useSyncExternalStore(proStore.subscribe, proStore.get, proStore.get);
}

export const isPro = () => state.pro;

type Iap = typeof import("expo-iap");
let iap: Iap | null = null;
let connected = false;

async function billing(): Promise<Iap | null> {
  if (Platform.OS !== "android") return null;
  try {
    iap ??= await import("expo-iap");
    if (!connected) {
      await iap.initConnection();
      connected = true;
    }
    return iap;
  } catch {
    return null; // Expo Go / no Play Store: Pro can still come from the account.
  }
}

/** Sends a Play purchase to the server so the account (and the PC) becomes Pro. */
async function claim(purchaseToken: string) {
  if (!tokenStorage.get()) return;
  try {
    await api("/billing/google/verify", { body: { productId: PRO_PRODUCT_ID, purchaseToken } });
  } catch (e) {
    console.warn("[foco] no se pudo asociar la compra a la cuenta", e);
  }
}

/** Loads the cached entitlement, the account plan and the device's Play purchases. */
export async function refreshPro() {
  if ((await AsyncStorage.getItem(CACHE_KEY)) === "1" && !state.pro) set({ pro: true });
  let pro = false;
  if (tokenStorage.get()) {
    try {
      const me = await api<{ plan: Plan }>("/me");
      pro = me.plan === "pro";
    } catch {
      pro = state.pro; // offline: keep what we knew
    }
  }
  const b = await billing();
  if (b) {
    try {
      const [products, owned] = await Promise.all([
        b.fetchProducts({ skus: [PRO_PRODUCT_ID], type: "in-app" }),
        b.getAvailablePurchases(),
      ]);
      const product = (products ?? []).find((p) => p.id === PRO_PRODUCT_ID);
      if (product) set({ price: product.displayPrice });
      const purchase = owned.find((p) => p.productId === PRO_PRODUCT_ID && p.purchaseState === "purchased");
      if (purchase) {
        pro = true;
        if (purchase.purchaseToken) void claim(purchase.purchaseToken);
      }
    } catch {
      // Play unavailable right now.
    }
  }
  set({ pro: pro || state.pro });
}

/**
 * Opens Google Play's purchase sheet. Resolves to true once Pro is unlocked.
 * `accountId` ties the purchase to the Foco account (Play's obfuscatedAccountId).
 */
export async function buyPro(): Promise<{ ok: boolean; message?: string }> {
  if (Platform.OS !== "android") return { ok: false, message: "Foco Pro se compra desde la app de Android con Google Play. Después se activa en el PC con la misma cuenta." };
  const b = await billing();
  if (!b) return { ok: false, message: "Google Play no está disponible en este dispositivo." };
  set({ busy: true });
  let accountId: string | undefined;
  if (tokenStorage.get()) accountId = await api<{ accountId: string }>("/billing").then((r) => r.accountId).catch(() => undefined);
  return new Promise((resolve) => {
    const done = (r: { ok: boolean; message?: string }) => {
      updated.remove();
      failed.remove();
      set({ busy: false });
      resolve(r);
    };
    const updated = b.purchaseUpdatedListener(async (purchase) => {
      if (purchase.productId !== PRO_PRODUCT_ID) return;
      if (purchase.purchaseState === "pending") return done({ ok: false, message: "El pago está pendiente. Foco Pro se activará en cuanto Google lo confirme." });
      if (purchase.purchaseState !== "purchased") return;
      if (purchase.purchaseToken) await claim(purchase.purchaseToken);
      try {
        await b.finishTransaction({ purchase, isConsumable: false });
      } catch {
        // Already acknowledged by the server.
      }
      set({ pro: true });
      done({ ok: true });
    });
    const failed = b.purchaseErrorListener((error) => {
      if (String(error.code) === "already-owned") {
        void refreshPro().then(() => done({ ok: state.pro }));
        return;
      }
      done({ ok: false, message: String(error.code) === "user-cancelled" ? undefined : error.message });
    });
    b.requestPurchase({ type: "in-app", request: { google: { skus: [PRO_PRODUCT_ID], obfuscatedAccountId: accountId } } }).catch((e: unknown) =>
      done({ ok: false, message: e instanceof Error ? e.message : String(e) }),
    );
  });
}

export async function restorePro(): Promise<boolean> {
  set({ busy: true });
  try {
    await refreshPro();
  } finally {
    set({ busy: false });
  }
  return state.pro;
}

/** Called on logout: the device keeps its own Play purchase, which refreshPro re-detects. */
export function resetPro() {
  set({ pro: false });
  void refreshPro();
}
