import { mkdirSync } from "node:fs";
import { PGlite } from "@electric-sql/pglite";
import { drizzle as drizzlePglite } from "drizzle-orm/pglite";
import { drizzle as drizzlePostgres } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";

export type Db = ReturnType<typeof drizzlePglite<typeof schema>>;

/**
 * `postgres://…` → real Postgres (production).
 * `memory://` → in-memory PGlite (tests). `file:<dir>` → PGlite on disk (local dev, nothing to install).
 */
export async function createDb(url: string): Promise<Db> {
  if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
    const client = postgres(url, { max: 10 });
    await client.unsafe(schema.BOOTSTRAP_SQL);
    // Both drivers expose the same query builder; the cast keeps one Db type.
    return drizzlePostgres(client, { schema }) as unknown as Db;
  }
  let pg: PGlite;
  if (url === "memory://") pg = new PGlite();
  else {
    const dir = url.replace(/^file:/, "");
    mkdirSync(dir, { recursive: true });
    pg = new PGlite(dir);
  }
  await pg.exec(schema.BOOTSTRAP_SQL);
  return drizzlePglite(pg, { schema });
}

export { schema };
