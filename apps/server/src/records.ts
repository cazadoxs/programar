import { and, asc, eq, gt, isNull, or, lt, sql } from "drizzle-orm";
import type { Collection, PullResponse, PushResponse, SyncRecord } from "@foco/core";
import { recordKey } from "@foco/core";
import type { Db } from "./db";
import { records } from "./db/schema";

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

/** Last-writer-wins upsert. Returns the keys that were rejected because the server had a newer version. */
export async function pushRecords(db: Db, userId: string, changes: SyncRecord[]): Promise<PushResponse> {
  const rejected: string[] = [];
  for (const c of changes) {
    const updatedAt = new Date(c.updatedAt).toISOString();
    const res = await db
      .insert(records)
      .values({
        userId,
        collection: c.collection,
        id: c.id,
        data: c.data as object,
        updatedAt,
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
    if (res.length === 0) rejected.push(recordKey(c));
  }
  return { rejected, cursor: await currentCursor(db, userId) };
}

export async function pullRecords(db: Db, userId: string, cursor: number, limit = 500): Promise<PullResponse> {
  const rows = await db
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
}

async function currentCursor(db: Db, userId: string): Promise<number> {
  const [row] = await db
    .select({ max: sql<number | null>`max(${records.seq})` })
    .from(records)
    .where(eq(records.userId, userId));
  return Number(row?.max ?? 0);
}

export async function listRecords<T>(db: Db, userId: string, collection: Collection): Promise<Array<SyncRecord<T>>> {
  const rows = await db
    .select()
    .from(records)
    .where(and(eq(records.userId, userId), eq(records.collection, collection), isNull(records.deletedAt)));
  return rows.map(rowToRecord) as Array<SyncRecord<T>>;
}

export async function getRecord<T>(db: Db, userId: string, collection: Collection, id: string): Promise<SyncRecord<T> | undefined> {
  const [row] = await db
    .select()
    .from(records)
    .where(and(eq(records.userId, userId), eq(records.collection, collection), eq(records.id, id)));
  return row && !row.deletedAt ? (rowToRecord(row) as SyncRecord<T>) : undefined;
}

/** Server-side write (assistant, calendar import). */
export async function putRecord(db: Db, userId: string, collection: Collection, id: string, data: unknown, deviceId: string, deleted = false) {
  const now = new Date().toISOString();
  await pushRecords(db, userId, [{ collection, id, data, updatedAt: now, deletedAt: deleted ? now : null, deviceId }]);
}
