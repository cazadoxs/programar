import { serve } from "@hono/node-server";
import { createApp } from "./app";
import { createDb } from "./db";
import { loadEnv } from "./env";

const env = loadEnv();
const db = await createDb(env.databaseUrl);
const app = createApp({ db, env });
const port = Number(process.env.PORT ?? 8787);
serve({ fetch: app.fetch, port }, () => console.log(`[foco] servidor en ${env.publicUrl} (puerto ${port})`));
