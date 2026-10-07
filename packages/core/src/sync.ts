/**
 * Sync protocol shared by every client and the server.
 *
 * Each record carries the time it was last changed on the device that
 * changed it. Clients push their changes and pull everything the server
 * accepted after their last cursor. Conflicts resolve per record by
 * last-writer-wins (ties broken by deviceId so every replica picks the same
 * winner). Deletions are tombstones (`deletedAt`) so they sync too.
 */

export const COLLECTIONS = ["tasks", "events", "sources", "rules", "sessions", "captures", "settings"] as const;
export type Collection = (typeof COLLECTIONS)[number];

export interface SyncRecord<T = unknown> {
  collection: Collection;
  id: string;
  data: T;
  updatedAt: string;
  deletedAt?: string | null;
  deviceId: string;
}

export interface PushRequest {
  deviceId: string;
  changes: SyncRecord[];
}

export interface PushResponse {
  /** Ids (collection:id) the server rejected because it held a newer version. */
  rejected: string[];
  cursor: number;
}

export interface PullResponse {
  changes: SyncRecord[];
  cursor: number;
  hasMore: boolean;
}

export function recordKey(r: Pick<SyncRecord, "collection" | "id">): string {
  return `${r.collection}:${r.id}`;
}

/** True when `incoming` should replace `current`. */
export function wins(incoming: SyncRecord, current: SyncRecord | undefined): boolean {
  if (!current) return true;
  const a = Date.parse(incoming.updatedAt);
  const b = Date.parse(current.updatedAt);
  if (a !== b) return a > b;
  return incoming.deviceId > current.deviceId;
}

/** Applies remote changes to a local map (keyed by recordKey), returning the keys that changed. */
export function applyRemote(local: Map<string, SyncRecord>, remote: SyncRecord[]): string[] {
  const changed: string[] = [];
  for (const r of remote) {
    const key = recordKey(r);
    if (wins(r, local.get(key))) {
      local.set(key, r);
      changed.push(key);
    }
  }
  return changed;
}

export function isCollection(value: string): value is Collection {
  return (COLLECTIONS as readonly string[]).includes(value);
}
