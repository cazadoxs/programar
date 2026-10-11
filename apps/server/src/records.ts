import { and, asc, eq, gt, isNull, or, lt, sql } from "drizzle-orm";
import { z } from "zod";
import type { Collection, PullResponse, PushError, PushResponse, SyncRecord } from "@foco/core";
import { COLLECTIONS, recordKey } from "@foco/core";
import type { Db } from "./db";
import { records } from "./db/schema";

/** A transaction handle; every helper below accepts either. */
export type Tx = Parameters<Parameters<Db["transaction"]>[0]>[0];
type Conn = Db | Tx;

const MAX_DATA_BYTES = 64 * 1024;
/** Clients may run slightly fast; anything further in the future is clamped so it cannot win every conflict forever. */
const MAX_CLOCK_SKEW_MS = 5 * 60_000;
const MIN_TIME = Date.parse("2000-01-01T00:00:00Z");

const toIso = (v: string | null | undefined) => (v ? new Date(v).toISOString() : null);

function rowToRecord(r: typeof records.$inferSelect): SyncRecord {
  return {
    collection: r.collection as Collection,
    id: r.id,
    data: r.data,
    updatedAt: toIso(r.updatedAt)!,
    deletedAt: toIso(r.deletedAt),
    deviceId: r.deviceId,
  };
}

const timestamp = z.string().max(40).refine((s) => {
  const t = Date.parse(s);
  return !Number.isNaN(t) && t >= MIN_TIME && t < Date.parse("9999-01-01T00:00:00Z");
}, "Fecha inválida");

const syncRecord = z.object({
  collection: z.enum(COLLECTIONS),
  id: z.string().min(1).max(200),
  data: z.unknown(),
  updatedAt: timestamp,
  deletedAt: timestamp.nullable().optional(),
  deviceId: z.string().min(1).max(100),
});

/** Validates one pushed record; returns the normalized record or a human-readable error. */
export function parseSyncRecord(raw: unknown, now = Date.now()): { record: SyncRecord } | { error: string } {
  const parsed = syncRecord.safeParse(raw);
  if (!parsed.success) return { error: parsed.error.issues.map((i) => `${i.path.join(".") || "registro"}: ${i.message}`).join("; ") };
  const r = parsed.data;
  const deleted = !!r.deletedAt;
  if (!deleted && (r.data === null || r.data === undefined)) return { error: "data: falta el contenido" };
  const json = JSON.stringify(r.data ?? {});
  if (Buffer.byteLength(json) > MAX_DATA_BYTES) return { error: "data: demasiado grande (máx. 64 KB)" };
  if (json.includes("\\u0000")) return { error: "data: contiene caracteres no válidos" };
  const clamp = (s: string) => new Date(Math.min(Date.parse(s), now + MAX_CLOCK_SKEW_MS)).toISOString();
  return {
    record: {
      collection: r.collection,
      id: r.id,
      data: r.data ?? {},
      updatedAt: clamp(r.updatedAt),
      deletedAt: r.deletedAt ? clamp(r.deletedAt) : null,
      deviceId: r.deviceId,
    },
  };
}

/**
 * Serializes record writes (and pulls) per user with a transaction-scoped
 * advisory lock, so seq order equals commit order for each user and a pull
 * can never skip a write that committed after it. PGlite supports it too.
 */
async function withUserLock<T>(db: Db, userId: string, fn: (tx: Tx) => Promise<T>): Promise<T> {
  return db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${userId}))`);
    return fn(tx);
  });
}

/** Last-writer-wins upsert of one record; false when the server already had a newer version. */
async function upsert(tx: Conn, userId: string, c: SyncRecord): Promise<boolean> {
  const res = await tx
    .insert(records)
    .values({
      userId,
      collection: c.collection,
      id: c.id,
      data: c.data as object,
      updatedAt: c.updatedAt,
      deletedAt: c.deletedAt ?? null,
      deviceId: c.deviceId,
    })
    .onConflictDoUpdate({
      target: [records.userId, records.collection, records.id],
      set: {
        data: sql`excluded.data`,
        updatedAt: sql`excluded.updated_at`,
        deletedAt: sql`excluded.deleted_at`,
        deviceId: sql`excluded.device_id`,
        seq: sql`nextval('records_seq')`,
      },
      // Same rule as core's `wins()`.
      setWhere: or(
        lt(records.updatedAt, sql`excluded.updated_at`),
        and(eq(records.updatedAt, sql`excluded.updated_at`), lt(records.deviceId, sql`excluded.device_id`)),
      ),
    })
    .returning({ id: records.id });
  return res.length > 0;
}

async function getRow(tx: Conn, userId: string, collection: string, id: string) {
  const [row] = await tx
    .select()
    .from(records)
    .where(and(eq(records.userId, userId), eq(records.collection, collection), eq(records.id, id)));
  return row;
}

/**
 * Validates and applies a batch from a device. Invalid records are reported
 * in `errors` without failing the rest; records that lost the last-writer-wins
 * check are listed in `rejected`, with the server's copy in `conflicts`.
 */
export async function pushRecords(db: Db, userId: string, rawChanges: unknown[], now = Date.now()): Promise<Required<PushResponse>> {
  const errors: PushError[] = [];
  const valid: SyncRecord[] = [];
  for (const raw of rawChanges) {
    const r = parseSyncRecord(raw, now);
    if ("record" in r) valid.push(r.record);
    else {
      const o = (raw && typeof raw === "object" ? raw : {}) as { collection?: unknown; id?: unknown };
      errors.push({ collection: String(o.collection ?? "").slice(0, 50), id: String(o.id ?? "").slice(0, 200), error: r.error });
    }
  }
  return withUserLock(db, userId, async (tx) => {
    const rejected: string[] = [];
    const conflicts: SyncRecord[] = [];
    for (const c of valid) {
      if (await upsert(tx, userId, c)) continue;
      const key = recordKey(c);
      if (rejected.includes(key)) continue;
      rejected.push(key);
      const row = await getRow(tx, userId, c.collection, c.id);
      if (row) conflicts.push(rowToRecord(row));
    }
    return { rejected, conflicts, errors, cursor: await currentCursor(tx, userId) };
  });
}

export async function pullRecords(db: Db, userId: string, cursor: number, limit = 500): Promise<PullResponse> {
  return withUserLock(db, userId, async (tx) => {
    const rows = await tx
      .select()
      .from(records)
      .where(and(eq(records.userId, userId), gt(records.seq, cursor)))
      .orderBy(asc(records.seq))
      .limit(limit + 1);
    const page = rows.slice(0, limit);
    return {
      changes: page.map(rowToRecord),
      cursor: page.length ? page[page.length - 1]!.seq : cursor,
      hasMore: rows.length > limit,
    };
  });
}

async function currentCursor(tx: Conn, userId: string): Promise<number> {
  const [row] = await tx
    .select({ max: sql<number | null>`max(${records.seq})` })
    .from(records)
    .where(eq(records.userId, userId));
  return Number(row?.max ?? 0);
}

export async function listRecords<T>(db: Conn, userId: string, collection: Collection): Promise<Array<SyncRecord<T>>> {
  const rows = await db
    .select()
    .from(records)
    .where(and(eq(records.userId, userId), eq(records.collection, collection), isNull(records.deletedAt)));
  return rows.map(rowToRecord) as Array<SyncRecord<T>>;
}

export async function getRecord<T>(db: Conn, userId: string, collection: Collection, id: string): Promise<SyncRecord<T> | undefined> {
  const row = await getRow(db, userId, collection, id);
  return row && !row.deletedAt ? (rowToRecord(row) as SyncRecord<T>) : undefined;
}

/**
 * Server-side write (assistant, calendar import). Its timestamp is the later
 * of now and just after the stored version, so a device clock running fast
 * cannot make the server's write lose silently.
 */
export async function putRecord(db: Db, userId: string, collection: Collection, id: string, data: unknown, deviceId: string, deleted = false) {
  await withUserLock(db, userId, async (tx) => {
    const existing = await getRow(tx, userId, collection, id);
    const after = existing ? new Date(existing.updatedAt).getTime() + 1 : 0;
    const ts = new Date(Math.max(Date.now(), after)).toISOString();
    const ok = await upsert(tx, userId, { collection, id, data: data ?? {}, updatedAt: ts, deletedAt: deleted ? ts : null, deviceId });
    if (!ok) throw new Error(`No se pudo guardar ${collection}:${id}`);
  });
}
