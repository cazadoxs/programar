import { sql } from "drizzle-orm";
import { bigint, integer, jsonb, pgTable, primaryKey, text, timestamp } from "drizzle-orm/pg-core";

export const users = pgTable("users", {
  id: text("id").primaryKey(),
  email: text("email").notNull().unique(),
  name: text("name"),
  passwordHash: text("password_hash").notNull(),
  aiProvider: text("ai_provider"),
  /** "free" | "pro" (one-time Google Play purchase). */
  plan: text("plan").notNull().default("free"),
  /** Must match the `ver` claim of a session token; bumping it logs out every device. */
  tokenVersion: integer("token_version").notNull().default(0),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

/** Every synced object (tasks, events, rules, sessions, captures, settings…). */
export const records = pgTable(
  "records",
  {
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    collection: text("collection").notNull(),
    id: text("id").notNull(),
    data: jsonb("data").notNull(),
    updatedAt: timestamp("updated_at", { withTimezone: true, mode: "string" }).notNull(),
    deletedAt: timestamp("deleted_at", { withTimezone: true, mode: "string" }),
    deviceId: text("device_id").notNull(),
    /** Server-assigned, increases on every write: the pull cursor. */
    seq: bigint("seq", { mode: "number" }).notNull().default(sql`nextval('records_seq')`),
  },
  (t) => [primaryKey({ columns: [t.userId, t.collection, t.id] })],
);

export const aiKeys = pgTable(
  "ai_keys",
  {
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    provider: text("provider").notNull(),
    encryptedKey: text("encrypted_key").notNull(),
    model: text("model"),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [primaryKey({ columns: [t.userId, t.provider] })],
);

export const calendarAccounts = pgTable("calendar_accounts", {
  id: text("id").primaryKey(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  provider: text("provider").notNull(),
  label: text("label").notNull(),
  /** Encrypted JSON: OAuth tokens or the ICS URL (it can contain a secret token). */
  encryptedSecret: text("encrypted_secret").notNull(),
  lastSyncedAt: timestamp("last_synced_at", { withTimezone: true }),
  lastError: text("last_error"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

/** Google Play purchases; a purchase token can unlock exactly one account. */
export const purchases = pgTable("purchases", {
  purchaseToken: text("purchase_token").primaryKey(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  productId: text("product_id").notNull(),
  orderId: text("order_id"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

/** Destructive assistant actions waiting for the user's confirmation in the app (expire after 30 min). */
export const aiPending = pgTable("ai_pending", {
  id: text("id").primaryKey(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  tool: text("tool").notNull(),
  input: jsonb("input").notNull(),
  summary: text("summary").notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

/** OAuth `state` ids already redeemed, kept until the state would have expired anyway. */
export const oauthStates = pgTable("oauth_states", {
  jti: text("jti").primaryKey(),
  expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
});

export const BOOTSTRAP_SQL = `
CREATE SEQUENCE IF NOT EXISTS records_seq;
CREATE TABLE IF NOT EXISTS users (
  id text PRIMARY KEY,
  email text NOT NULL UNIQUE,
  name text,
  password_hash text NOT NULL,
  ai_provider text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS records (
  user_id text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  collection text NOT NULL,
  id text NOT NULL,
  data jsonb NOT NULL,
  updated_at timestamptz NOT NULL,
  deleted_at timestamptz,
  device_id text NOT NULL,
  seq bigint NOT NULL DEFAULT nextval('records_seq'),
  PRIMARY KEY (user_id, collection, id)
);
CREATE INDEX IF NOT EXISTS records_user_seq ON records (user_id, seq);
CREATE TABLE IF NOT EXISTS ai_keys (
  user_id text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider text NOT NULL,
  encrypted_key text NOT NULL,
  model text,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, provider)
);
CREATE TABLE IF NOT EXISTS calendar_accounts (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider text NOT NULL,
  label text NOT NULL,
  encrypted_secret text NOT NULL,
  last_synced_at timestamptz,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS plan text NOT NULL DEFAULT 'free';
CREATE TABLE IF NOT EXISTS purchases (
  purchase_token text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id text NOT NULL,
  order_id text,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS ai_pending (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tool text NOT NULL,
  input jsonb NOT NULL,
  summary text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ai_pending_user ON ai_pending (user_id);
CREATE TABLE IF NOT EXISTS oauth_states (
  jti text PRIMARY KEY,
  expires_at timestamptz NOT NULL
);
`;
