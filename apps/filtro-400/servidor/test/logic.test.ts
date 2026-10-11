import assert from "node:assert/strict";
import { test } from "node:test";
import { buildList, parseReport, sha256Hex, subscriptionValidUntil } from "../src/logic.ts";

const reporter = "abcdefghijklmnop1234";

test("acepta denuncias válidas", () => {
  assert.deepEqual(parseReport({ number: "+34400123456", category: "comercial", reporter }), {
    number: "+34400123456", category: "comercial", reporter,
  });
});

test("rechaza denuncias mal formadas", () => {
  assert.equal(parseReport(null), null);
  assert.equal(parseReport({ number: "400123456", category: "comercial", reporter }), null);
  assert.equal(parseReport({ number: "+3440012345", category: "comercial", reporter }), null);
  assert.equal(parseReport({ number: "+34400123456", category: "hackeo", reporter }), null);
  assert.equal(parseReport({ number: "+34400123456", category: "comercial", reporter: "x" }), null);
  assert.equal(parseReport({ number: "+34400123456'; DROP TABLE", category: "comercial", reporter }), null);
});

test("la lista exige varias denuncias y respeta exclusiones", () => {
  const rows = [
    { number: "+34612345678", category: "energia", reporters: 2 },
    { number: "+34612345678", category: "comercial", reporters: 1 },
    { number: "+34911111111", category: "robot", reporters: 2 },
    { number: "+34922222222", category: "estafa", reporters: 9 },
  ];
  const list = buildList(rows, 3, new Set(["+34922222222"]));
  assert.deepEqual(list, [{ n: "+34612345678", r: 3, c: "energia" }]);
});

test("suscripción activa con caducidad", () => {
  const now = Date.parse("2026-10-11T00:00:00Z");
  const sub = {
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    lineItems: [{ productId: "comunidad", expiryTime: "2026-10-11T02:00:00Z" }],
  };
  assert.equal(subscriptionValidUntil(sub, "comunidad", now), Date.parse("2026-10-11T02:00:00Z"));
  assert.equal(subscriptionValidUntil(sub, "otro", now), null);
  assert.equal(subscriptionValidUntil({ ...sub, subscriptionState: "SUBSCRIPTION_STATE_EXPIRED" }, "comunidad", now), null);
  const far = { ...sub, lineItems: [{ productId: "comunidad", expiryTime: "2027-01-01T00:00:00Z" }] };
  assert.equal(subscriptionValidUntil(far, "comunidad", now), now + 12 * 60 * 60 * 1000);
});

test("hash estable", async () => {
  assert.equal(
    await sha256Hex("abc"),
    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
  );
});
