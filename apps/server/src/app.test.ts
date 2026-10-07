import { randomBytes } from "node:crypto";
import { beforeEach, describe, expect, it } from "vitest";
import type { CalendarEvent, FocusSession, PullResponse, Task } from "@foco/core";
import { createApp } from "./app";
import { decrypt } from "./crypto";
import { createDb, type Db } from "./db";
import { aiKeys } from "./db/schema";
import type { Env } from "./env";
import type { AgentRequest, AiProvider, ProviderFactory } from "./ai";
import { googleProvider } from "./ai/google";
import { openaiProvider } from "./ai/openai";

const env: Env = { databaseUrl: "memory://", jwtSecret: "test-secret", encryptionKey: randomBytes(32), publicUrl: "http://test" };

const ICS = [
  "BEGIN:VCALENDAR",
  "BEGIN:VEVENT",
  "UID:clase@uni",
  "SUMMARY:Clase",
  `DTSTART:${new Date(Date.now() + 86_400_000).toISOString().replace(/[-:]/g, "").slice(0, 15)}Z`,
  `DTEND:${new Date(Date.now() + 90_000_000).toISOString().replace(/[-:]/g, "").slice(0, 15)}Z`,
  "END:VEVENT",
  "END:VCALENDAR",
].join("\r\n");

const fakeFetch: typeof fetch = async (input) => {
  const url = String(input);
  if (url.includes("calendar.example")) return new Response(ICS);
  return new Response("not found", { status: 404 });
};

/** Scripted provider: runs the given tool calls, then answers. */
function scriptedFactory(calls: Array<{ name: string; input: unknown }>, seen: AgentRequest[] = []): ProviderFactory {
  return (id, _key, model) => ({
    id,
    model: model ?? "test",
    async run(req) {
      seen.push(req);
      const actions = [];
      for (const call of calls) {
        try {
          actions.push({ tool: call.name, input: call.input, result: await req.execute(call.name, call.input) });
        } catch (e) {
          actions.push({ tool: call.name, input: call.input, result: null, error: (e as Error).message });
        }
      }
      return { text: "Hecho", actions };
    },
  }) satisfies AiProvider;
}

let db: Db;
beforeEach(async () => {
  db = await createDb("memory://");
});

async function setup(providerFactory?: ProviderFactory) {
  const app = createApp({ db, env, providerFactory, fetchImpl: fakeFetch, calendarProviders: {} });
  const res = await app.request("/auth/register", { method: "POST", body: JSON.stringify({ email: "Alvaro@Example.com", password: "supersecreta", name: "Álvaro" }) });
  expect(res.status).toBe(201);
  const { token } = (await res.json()) as { token: string };
  const call = (path: string, init: RequestInit = {}) =>
    app.request(path, { ...init, headers: { authorization: `Bearer ${token}`, "content-type": "application/json", ...(init.headers ?? {}) } });
  return { app, call };
}

describe("account", () => {
  it("registers, logs in case-insensitively and rejects bad passwords", async () => {
    const { app, call } = await setup();
    expect((await app.request("/auth/login", { method: "POST", body: JSON.stringify({ email: "alvaro@example.com", password: "supersecreta" }) })).status).toBe(200);
    expect((await app.request("/auth/login", { method: "POST", body: JSON.stringify({ email: "alvaro@example.com", password: "incorrecta1" }) })).status).toBe(401);
    expect((await app.request("/auth/register", { method: "POST", body: JSON.stringify({ email: "alvaro@example.com", password: "otraotra1" }) })).status).toBe(409);
    expect(await (await call("/me")).json()).toMatchObject({ email: "alvaro@example.com", name: "Álvaro" });
    expect((await app.request("/me")).status).toBe(401);
  });
});

describe("sync", () => {
  it("pushes, pulls by cursor and resolves conflicts last-writer-wins", async () => {
    const { call } = await setup();
    const task = (title: string, updatedAt: string, deviceId: string) => ({ collection: "tasks", id: "t1", data: { title }, updatedAt, deviceId });

    const push1 = await (await call("/sync/push", { method: "POST", body: JSON.stringify({ deviceId: "phone", changes: [task("móvil", "2026-10-07T10:00:00.000Z", "phone")] }) })).json();
    expect(push1.rejected).toEqual([]);

    const stale = await (await call("/sync/push", { method: "POST", body: JSON.stringify({ deviceId: "pc", changes: [task("pc viejo", "2026-10-07T09:00:00.000Z", "pc")] }) })).json();
    expect(stale.rejected).toEqual(["tasks:t1"]);

    const pull = (await (await call("/sync/pull?cursor=0")).json()) as PullResponse;
    expect(pull.changes).toHaveLength(1);
    expect(pull.changes[0]).toMatchObject({ data: { title: "móvil" }, updatedAt: "2026-10-07T10:00:00.000Z" });

    await call("/sync/push", { method: "POST", body: JSON.stringify({ deviceId: "pc", changes: [task("pc nuevo", "2026-10-07T11:00:00.000Z", "pc")] }) });
    const next = (await (await call(`/sync/pull?cursor=${pull.cursor}`)).json()) as PullResponse;
    expect(next.changes.map((c) => c.data)).toEqual([{ title: "pc nuevo" }]);
    expect((await (await call(`/sync/pull?cursor=${next.cursor}`)).json()).changes).toEqual([]);
  });

  it("rejects unknown collections", async () => {
    const { call } = await setup();
    const res = await call("/sync/push", { method: "POST", body: JSON.stringify({ deviceId: "x", changes: [{ collection: "hack", id: "1", data: {}, updatedAt: new Date().toISOString(), deviceId: "x" }] }) });
    expect(res.status).toBe(400);
  });
});

describe("AI", () => {
  it("stores keys encrypted and requires one before chatting", async () => {
    const { call } = await setup(scriptedFactory([]));
    expect((await call("/ai/chat", { method: "POST", body: JSON.stringify({ messages: [{ role: "user", content: "hola" }] }) })).status).toBe(400);
    expect((await call("/ai/keys/anthropic", { method: "PUT", body: JSON.stringify({ apiKey: "sk-ant-1234567890" }) })).status).toBe(200);
    const [row] = await db.select().from(aiKeys);
    expect(row!.encryptedKey).not.toContain("sk-ant");
    expect(decrypt(row!.encryptedKey, env.encryptionKey)).toBe("sk-ant-1234567890");
    const settings = await (await call("/ai/settings")).json();
    expect(settings.provider).toBe("anthropic");
    expect(settings.providers[0]).toMatchObject({ provider: "anthropic", hasKey: true, model: "claude-opus-5-5" });
  });

  it("assistant tools create events, tasks and focus sessions that sync to devices", async () => {
    const seen: AgentRequest[] = [];
    const { call } = await setup(
      scriptedFactory(
        [
          { name: "create_event", input: { title: "Estudiar mates", start: "2026-10-08T10:00:00+02:00", end: "2026-10-08T11:00:00+02:00", focus: true } },
          { name: "create_task", input: { title: "Comprar pan", due: "2026-10-08" } },
          { name: "start_focus_session", input: { kind: "pomodoro", minutes: 50, appIds: ["instagram", "tiktok"], surfaces: ["reels"] } },
          { name: "start_focus_session", input: { kind: "study", minutes: 30, appIds: ["myspace"] } },
          { name: "create_event", input: { title: "Mal", start: "2026-10-08T11:00:00Z", end: "2026-10-08T10:00:00Z" } },
        ],
        seen,
      ),
    );
    await call("/ai/keys/openai", { method: "PUT", body: JSON.stringify({ apiKey: "sk-openai-123456" }) });
    const res = await (await call("/ai/chat", { method: "POST", body: JSON.stringify({ messages: [{ role: "user", content: "Planifica mi día" }], mode: "work" }) })).json();
    expect(res.provider).toBe("openai");
    expect(res.actions.map((a: { error?: string }) => a.error ?? "ok")).toEqual(["ok", "ok", "ok", "Apps desconocidas: myspace", "El final debe ser posterior al inicio"]);
    expect(seen[0]!.system).toContain("Modo Trabajo");
    expect(seen[0]!.system).toContain("Álvaro");

    const pull = (await (await call("/sync/pull?cursor=0")).json()) as PullResponse;
    const byCol = (c: string) => pull.changes.filter((x) => x.collection === c).map((x) => x.data);
    expect((byCol("events")[0] as CalendarEvent).focus).toBe(true);
    expect((byCol("tasks")[0] as Task).title).toBe("Comprar pan");
    const session = byCol("sessions")[0] as FocusSession;
    expect(session.targets).toEqual([{ appId: "instagram", surfaces: ["reels"] }, { appId: "tiktok", surfaces: ["reels"] }]);
  });
});

describe("calendars", () => {
  it("subscribes to an ICS feed and imports its events as a source", async () => {
    const { call } = await setup();
    const res = await call("/calendar/ics", { method: "POST", body: JSON.stringify({ url: "https://calendar.example/uni.ics", label: "Universidad" }) });
    expect(res.status).toBe(201);
    expect((await res.json()).events).toBe(1);
    const pull = (await (await call("/sync/pull?cursor=0")).json()) as PullResponse;
    expect(pull.changes.find((c) => c.collection === "sources")!.data).toMatchObject({ name: "Universidad", provider: "ics" });
    expect(pull.changes.find((c) => c.collection === "events")!.data).toMatchObject({ title: "Clase", iCalUID: "clase@uni" });

    // Re-sync writes nothing new; deleting the account tombstones its data.
    await call("/calendar/sync", { method: "POST" });
    const again = (await (await call(`/sync/pull?cursor=${pull.cursor}`)).json()) as PullResponse;
    expect(again.changes.filter((c) => c.collection === "events")).toEqual([]);
    const { accounts } = await (await call("/calendar/accounts")).json();
    await call(`/calendar/accounts/${accounts[0].id}`, { method: "DELETE" });
    const after = (await (await call(`/sync/pull?cursor=${again.cursor}`)).json()) as PullResponse;
    expect(after.changes.every((c) => c.deletedAt)).toBe(true);
    expect(after.changes).toHaveLength(2);
  });

  it("reports unconfigured OAuth providers", async () => {
    const { call } = await setup();
    expect((await call("/calendar/oauth/google/start")).status).toBe(501);
  });
});

describe("provider adapters (HTTP shape)", () => {
  const tools = [{ name: "list_tasks", description: "x", input_schema: { type: "object" as const, properties: {}, required: [], additionalProperties: false as const } }];

  it("OpenAI loop sends tool results back and returns the final text", async () => {
    const bodies: any[] = [];
    const replies = [
      { choices: [{ message: { role: "assistant", content: null, tool_calls: [{ id: "c1", type: "function", function: { name: "list_tasks", arguments: "{}" } }] } }] },
      { choices: [{ message: { role: "assistant", content: "Tienes 1 tarea" } }] },
    ];
    const f: typeof fetch = async (_url, init) => {
      bodies.push(JSON.parse(String(init!.body)));
      return Response.json(replies.shift());
    };
    const out = await openaiProvider("k", "m", f).run({ system: "s", history: [{ role: "user", content: "hola" }], tools, execute: async () => [{ title: "a" }] });
    expect(out.text).toBe("Tienes 1 tarea");
    expect(bodies[1].messages.at(-1)).toEqual({ role: "tool", tool_call_id: "c1", content: '[{"title":"a"}]' });
  });

  it("Gemini loop omits empty parameter schemas and returns function responses", async () => {
    const bodies: any[] = [];
    const replies = [
      { candidates: [{ content: { role: "model", parts: [{ functionCall: { name: "list_tasks", args: {} } }] } }] },
      { candidates: [{ content: { role: "model", parts: [{ text: "Listo" }] } }] },
    ];
    const f: typeof fetch = async (_url, init) => {
      bodies.push(JSON.parse(String(init!.body)));
      return Response.json(replies.shift());
    };
    const out = await googleProvider("k", "m", f).run({ system: "s", history: [{ role: "user", content: "hola" }], tools, execute: async () => 3 });
    expect(out.text).toBe("Listo");
    expect(bodies[0].tools[0].functionDeclarations[0].parameters).toBeUndefined();
    expect(bodies[1].contents.at(-1).parts[0]).toEqual({ functionResponse: { name: "list_tasks", response: { result: 3 } } });
  });
});
