import { describe, expect, it } from "vitest";
import {
  applyRemote,
  evaluateBlocks,
  findSlot,
  freeSlots,
  isBlocked,
  isScheduleActive,
  mergeEvents,
  nextChangeAt,
  parseCapture,
  parseIcs,
  pomodoroStateAt,
  recordKey,
  unlockAvailableAt,
  DEFAULT_FRICTION,
  type BlockRule,
  type CalendarEvent,
  type FocusSession,
  type SyncRecord,
} from "./index";

// Wednesday 7 Oct 2026, 10:00 local time.
const NOW = new Date(2026, 9, 7, 10, 0);
const at = (d: number, h: number, m = 0) => new Date(2026, 9, d, h, m);

describe("schedules", () => {
  const weekdays = { kind: "weekly" as const, days: [1, 2, 3, 4, 5], start: "09:00", end: "14:00" };
  it("activates inside the range on listed days", () => {
    expect(isScheduleActive(weekdays, NOW)).toBe(true);
    expect(isScheduleActive(weekdays, at(7, 14))).toBe(false);
    expect(isScheduleActive(weekdays, at(10, 10))).toBe(false); // Saturday
  });
  it("handles ranges that cross midnight", () => {
    const night = { kind: "weekly" as const, days: [3], start: "22:00", end: "07:00" };
    expect(isScheduleActive(night, at(7, 23))).toBe(true); // Wed 23:00
    expect(isScheduleActive(night, at(8, 6, 59))).toBe(true); // Thu 06:59 (from Wed)
    expect(isScheduleActive(night, at(8, 7))).toBe(false);
    expect(isScheduleActive(night, at(7, 6))).toBe(false); // Wed 06:00 belongs to Tuesday
  });
});

describe("evaluateBlocks", () => {
  const rules: BlockRule[] = [
    { id: "r1", name: "Clase", enabled: true, schedule: { kind: "weekly", days: [3], start: "09:00", end: "12:00" }, targets: [{ appId: "instagram", surfaces: ["reels", "explore"] }] },
    { id: "r2", name: "Siempre", enabled: true, schedule: { kind: "always" }, targets: [{ appId: "tiktok", surfaces: ["app"] }] },
    { id: "r3", name: "Off", enabled: false, schedule: { kind: "always" }, targets: [{ appId: "youtube", surfaces: ["app"] }] },
  ];

  it("merges rules, keeps granular surfaces and ignores disabled rules", () => {
    const blocks = evaluateBlocks({ now: NOW, rules });
    expect(isBlocked(blocks, "instagram", "reels")).toBe(true);
    expect(isBlocked(blocks, "instagram", "messages")).toBe(false);
    expect(isBlocked(blocks, "tiktok", "messages")).toBe(true);
    expect(isBlocked(blocks, "youtube", "app")).toBe(false);
  });

  it("a whole-app block swallows surfaces", () => {
    const session: FocusSession = {
      id: "s", kind: "study", startedAt: at(7, 9).toISOString(), endsAt: at(7, 11).toISOString(),
      targets: [{ appId: "instagram", surfaces: ["app"] }], strict: true,
    };
    const blocks = evaluateBlocks({ now: NOW, rules, sessions: [session] });
    const ig = blocks.find((b) => b.appId === "instagram")!;
    expect(ig.surfaces).toEqual(["app"]);
    expect(ig.reasons).toEqual(["Clase", "Estudio"]);
    expect(unlockAvailableAt(blocks, NOW, DEFAULT_FRICTION)).toBeNull();
  });

  it("pomodoro only blocks during focus", () => {
    const pomo: FocusSession = {
      id: "p", kind: "pomodoro", startedAt: at(7, 9, 30).toISOString(), endsAt: at(7, 12).toISOString(),
      targets: [{ appId: "youtube", surfaces: ["app"] }], strict: false,
    };
    expect(isBlocked(evaluateBlocks({ now: at(7, 9, 40), rules: [], sessions: [pomo] }), "youtube", "app")).toBe(true);
    expect(isBlocked(evaluateBlocks({ now: at(7, 9, 57), rules: [], sessions: [pomo] }), "youtube", "app")).toBe(false);
  });

  it("daily limits block the app once used up", () => {
    const blocks = evaluateBlocks({ now: NOW, rules: [], limits: [{ appId: "x", minutesPerDay: 30 }], usageToday: { x: 31 } });
    expect(isBlocked(blocks, "x", "feed")).toBe(true);
  });

  it("nextChangeAt finds the next schedule edge", () => {
    expect(nextChangeAt({ now: NOW, rules })).toEqual(at(7, 12));
    expect(nextChangeAt({ now: at(7, 12, 30), rules })).toEqual(at(14, 9));
  });
});

describe("pomodoro", () => {
  it("cycles focus, short break and long break", () => {
    const start = at(7, 9);
    expect(pomodoroStateAt(start, at(7, 9, 10)).phase).toBe("focus");
    expect(pomodoroStateAt(start, at(7, 9, 26)).phase).toBe("short_break");
    const s = pomodoroStateAt(start, at(7, 9, 31));
    expect(s.phase).toBe("focus");
    expect(s.cycle).toBe(2);
    // 4×25 + 3×5 = 115 min → long break at 10:55.
    expect(pomodoroStateAt(start, at(7, 10, 56)).phase).toBe("long_break");
    expect(pomodoroStateAt(start, at(7, 11, 10)).cycle).toBe(5);
  });
});

describe("parseCapture", () => {
  it("event tomorrow with hour read as afternoon", () => {
    const r = parseCapture("mañana a las 5 dentista", NOW);
    expect(r.kind).toBe("event");
    expect(r.title).toBe("Dentista");
    expect(r.start).toEqual(at(8, 17));
    expect(r.end).toEqual(at(8, 18));
    expect(r.confidence).toBeLessThan(1);
  });
  it("does not confuse 'de la mañana' with tomorrow", () => {
    const r = parseCapture("gimnasio a las 9 de la mañana el viernes", NOW);
    expect(r.start).toEqual(at(9, 9));
    expect(r.title).toBe("Gimnasio");
  });
  it("time ranges and weekdays", () => {
    const r = parseCapture("Reunión con Ana de 10 a 11:30 el lunes", NOW);
    expect(r.kind).toBe("event");
    expect(r.title).toBe("Reunión con Ana");
    expect(r.start).toEqual(at(12, 10));
    expect(r.end).toEqual(at(12, 11, 30));
  });
  it("reminders become tasks", () => {
    const r = parseCapture("recuérdame llamar a mamá el sábado", NOW);
    expect(r.kind).toBe("task");
    expect(r.title).toBe("Llamar a mamá");
    expect(r.dueDate).toEqual(at(10, 0));
  });
  it("explicit dates and durations", () => {
    const r = parseCapture("examen de física el 20 de octubre a las 9:30 durante 2 horas", NOW);
    expect(r.title).toBe("Examen de física");
    expect(r.start).toEqual(at(20, 9, 30));
    expect(r.durationMinutes).toBe(120);
  });
  it("numeric dates roll to next year when past", () => {
    const r = parseCapture("renovar DNI 3/2", NOW);
    expect(r.kind).toBe("task");
    expect(r.dueDate).toEqual(new Date(2027, 1, 3));
  });
  it("notes and plain inbox tasks", () => {
    expect(parseCapture("nota: idea para la app de recetas", NOW)).toMatchObject({ kind: "note", title: "Idea para la app de recetas" });
    expect(parseCapture("comprar pan", NOW)).toMatchObject({ kind: "task", title: "Comprar pan" });
  });
  it("english", () => {
    const r = parseCapture("call John tomorrow at 3pm", NOW);
    expect(r.kind).toBe("task");
    expect(r.start).toEqual(at(8, 15));
    expect(r.title).toBe("Call John");
  });
  it("time without a day that already passed goes to tomorrow", () => {
    expect(parseCapture("a las 9:00 llamar al banco", NOW).start).toEqual(at(8, 9));
  });
});

describe("calendar", () => {
  const ev = (id: string, sourceId: string, start: Date, end: Date, extra: Partial<CalendarEvent> = {}): CalendarEvent => ({
    id, sourceId, title: "Reunión", start: start.toISOString(), end: end.toISOString(), allDay: false, updatedAt: NOW.toISOString(), ...extra,
  });

  it("deduplicates the same invitation across providers", () => {
    const sources = [
      { id: "g", provider: "google" as const, name: "Google", color: "#4285f4", visible: true, readOnly: false },
      { id: "d", provider: "device" as const, name: "Samsung", color: "#1428a0", visible: true, readOnly: true },
      { id: "h", provider: "ics" as const, name: "Oculto", color: "#000", visible: false, readOnly: true },
    ];
    const merged = mergeEvents(
      [
        ev("1", "d", at(7, 11), at(7, 12), { iCalUID: "abc" }),
        ev("2", "g", at(7, 11), at(7, 12), { iCalUID: "abc" }),
        ev("3", "d", at(7, 9), at(7, 10), { title: "  reunión " }),
        ev("4", "g", at(7, 9), at(7, 10)),
        ev("5", "h", at(7, 15), at(7, 16)),
      ],
      sources,
    );
    expect(merged.map((e) => e.id)).toEqual(["4", "2"]);
  });

  it("finds free slots", () => {
    const events = [ev("a", "g", at(7, 10), at(7, 11)), ev("b", "g", at(7, 10, 30), at(7, 12)), ev("c", "g", at(7, 13), at(7, 13, 10))];
    const slots = freeSlots(events, at(7, 9), at(7, 14), 30);
    expect(slots.map((s) => [s.start.getHours(), s.end.getHours()])).toEqual([[9, 10], [12, 13], [13, 14]]);
    expect(findSlot(events, at(7, 10), at(7, 18), 45)).toEqual({ start: at(7, 12), end: at(7, 12, 45) });
  });
});

describe("ics", () => {
  const ics = [
    "BEGIN:VCALENDAR",
    "BEGIN:VEVENT",
    "UID:1@x",
    "SUMMARY:Clase de inglés\\, nivel B2",
    "DTSTART;TZID=Europe/Madrid:20261005T180000",
    "DTEND;TZID=Europe/Madrid:20261005T193000",
    "RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
    "END:VEVENT",
    "BEGIN:VEVENT",
    "UID:2@x",
    "SUMMARY:Cumple",
    "DTSTART;VALUE=DATE:20261009",
    "END:VEVENT",
    "END:VCALENDAR",
  ].join("\r\n");

  it("parses, converts TZID and expands weekly rules", () => {
    const events = parseIcs(ics, { from: new Date("2026-10-05T00:00:00Z"), to: new Date("2026-10-13T00:00:00Z") });
    const classes = events.filter((e) => e.uid === "1@x");
    expect(classes.map((e) => e.start.toISOString())).toEqual([
      "2026-10-05T16:00:00.000Z",
      "2026-10-07T16:00:00.000Z",
      "2026-10-12T16:00:00.000Z",
    ]);
    expect(classes[0]!.title).toBe("Clase de inglés, nivel B2");
    const bday = events.find((e) => e.uid === "2@x")!;
    expect(bday.allDay).toBe(true);
  });
});

describe("sync", () => {
  const rec = (updatedAt: string, deviceId: string, data: unknown): SyncRecord => ({ collection: "tasks", id: "t1", data, updatedAt, deviceId });
  it("last writer wins with deterministic ties", () => {
    const local = new Map([[recordKey(rec("", "", null)), rec("2026-10-07T10:00:00Z", "phone", "a")]]);
    expect(applyRemote(local, [rec("2026-10-07T09:00:00Z", "pc", "old")])).toEqual([]);
    expect(applyRemote(local, [rec("2026-10-07T10:00:00Z", "tablet", "tie")])).toEqual(["tasks:t1"]);
    expect(local.get("tasks:t1")!.data).toBe("tie");
  });
});
