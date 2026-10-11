import AsyncStorage from "@react-native-async-storage/async-storage";
import { useSyncExternalStore } from "react";
import * as Crypto from "expo-crypto";
import { applyRemote, recordKey, type Collection, type PullResponse, type PushResponse, type SyncRecord } from "@foco/core";
import { api, tokenStorage } from "./api";

const STORAGE_KEY = "foco.db.v1";

interface Persisted {
  deviceId: string;
  cursor: number;
  records: SyncRecord[];
  pending: string[];
  /** Account the synced data belongs to; null for data created before logging in. */
  owner?: string | null;
}

interface PushResult extends PushResponse {
  /** Records the server refused as invalid: dropped so they can't block sync forever. */
  errors?: Array<{ collection: string; id: string; error: string }>;
  /** Server copies that won over our rejected writes. */
  conflicts?: SyncRecord[];
}

/**
 * Local-first store. Everything works offline and without an account; with
 * an account, local changes are pushed and remote ones pulled (see the sync
 * protocol in packages/core/src/sync.ts).
 */
class LocalStore {
  private records = new Map<string, SyncRecord>();
  private pending = new Set<string>();
  private cursor = 0;
  owner: string | null = null;
  deviceId = "";
  private listeners = new Set<() => void>();
  private cache = new Map<Collection, unknown[]>();
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  private syncTimer: ReturnType<typeof setTimeout> | null = null;
  private syncing: Promise<void> | null = null;
  ready = false;
  lastSyncError: string | null = null;

  async load() {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (raw) {
      const p = JSON.parse(raw) as Persisted;
      this.deviceId = p.deviceId;
      this.cursor = p.cursor;
      p.records.forEach((r) => this.records.set(recordKey(r), r));
      this.pending = new Set(p.pending);
      this.owner = p.owner ?? null;
    }
    if (!this.deviceId) this.deviceId = `device:${Crypto.randomUUID()}`;
    this.ready = true;
    this.changed();
  }

  subscribe = (l: () => void) => {
    this.listeners.add(l);
    return () => {
      this.listeners.delete(l);
    };
  };

  list<T>(collection: Collection): T[] {
    let cached = this.cache.get(collection) as T[] | undefined;
    if (!cached) {
      cached = [...this.records.values()].filter((r) => r.collection === collection && !r.deletedAt).map((r) => r.data as T);
      this.cache.set(collection, cached);
    }
    return cached;
  }

  get<T>(collection: Collection, id: string): T | undefined {
    const r = this.records.get(`${collection}:${id}`);
    return r && !r.deletedAt ? (r.data as T) : undefined;
  }

  put<T>(collection: Collection, id: string, data: T) {
    this.write({ collection, id, data, updatedAt: new Date().toISOString(), deletedAt: null, deviceId: this.deviceId });
  }

  remove(collection: Collection, id: string) {
    const prev = this.records.get(`${collection}:${id}`);
    if (!prev || prev.deletedAt) return;
    const now = new Date().toISOString();
    this.write({ collection, id, data: prev.data ?? {}, updatedAt: now, deletedAt: now, deviceId: this.deviceId });
  }

  private write(r: SyncRecord) {
    const key = recordKey(r);
    this.records.set(key, r);
    this.pending.add(key);
    this.changed();
    this.scheduleSync();
  }

  private changed() {
    this.cache.clear();
    this.listeners.forEach((l) => l());
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => void this.save(), 300);
  }

  private async save() {
    const p: Persisted = { deviceId: this.deviceId, cursor: this.cursor, records: [...this.records.values()], pending: [...this.pending], owner: this.owner };
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(p));
  }

  scheduleSync(delay = 1500) {
    if (!tokenStorage.get()) return;
    if (this.syncTimer) clearTimeout(this.syncTimer);
    this.syncTimer = setTimeout(() => void this.sync(), delay);
  }

  /** Push local changes, then pull until up to date. Safe to call often. */
  sync(): Promise<void> {
    if (!tokenStorage.get()) return Promise.resolve();
    this.syncing ??= this.doSync().finally(() => {
      this.syncing = null;
    });
    return this.syncing;
  }

  private async doSync() {
    try {
      const keys = [...this.pending];
      if (keys.length) {
        const changes = keys.map((k) => this.records.get(k)!).filter(Boolean);
        const res = await api<PushResult>("/sync/push", { body: { deviceId: this.deviceId, changes } });
        // Rejected writes lost to a newer server copy: apply it so this device converges.
        keys.forEach((k) => {
          if (this.records.get(k) === changes.find((c) => recordKey(c) === k)) this.pending.delete(k);
        });
        if (res.conflicts?.length) applyRemote(this.records, res.conflicts);
        res.errors?.forEach((e) => console.warn(`[foco] el servidor rechazó ${e.collection}:${e.id}: ${e.error}`));
      }
      let more = true;
      while (more) {
        const page = await api<PullResponse>(`/sync/pull?cursor=${this.cursor}`);
        const changedKeys = applyRemote(this.records, page.changes);
        changedKeys.forEach((k) => this.pending.delete(k));
        this.cursor = page.cursor;
        more = page.hasMore;
      }
      this.lastSyncError = null;
    } catch (e) {
      this.lastSyncError = e instanceof Error ? e.message : String(e);
    }
    this.changed();
  }

  /**
   * After logging into `userId`: data created before having an account (or
   * already this account's) is uploaded; another account's leftovers are
   * wiped so they never leak into this one.
   */
  adopt(userId: string) {
    if (this.owner && this.owner !== userId) this.clear();
    else this.records.forEach((_, k) => this.pending.add(k));
    this.owner = userId;
    this.cursor = 0;
    this.changed();
  }

  /** Forget everything on this device (logout). The account keeps its copy. */
  clear() {
    this.records.clear();
    this.pending.clear();
    this.cursor = 0;
    this.owner = null;
    this.lastSyncError = null;
    this.changed();
  }
}

export const store = new LocalStore();

export function useCollection<T>(collection: Collection): T[] {
  return useSyncExternalStore(store.subscribe, () => store.list<T>(collection), () => store.list<T>(collection));
}

export function useRecord<T>(collection: Collection, id: string): T | undefined {
  return useSyncExternalStore(store.subscribe, () => store.get<T>(collection, id), () => store.get<T>(collection, id));
}

export const newId = () => Crypto.randomUUID();
