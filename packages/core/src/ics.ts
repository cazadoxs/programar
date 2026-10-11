/**
 * Minimal iCalendar (RFC 5545) reader for `.ics` subscriptions: VEVENTs with
 * UID, SUMMARY, LOCATION, DESCRIPTION, DTSTART/DTEND/DURATION (UTC, floating,
 * TZID or all-day), EXDATE and RECURRENCE-ID overrides. RRULEs with FREQ
 * DAILY / WEEKLY (BYDAY) / MONTHLY (BYMONTHDAY or BYDAY) / YEARLY (BYMONTH,
 * BYMONTHDAY), INTERVAL, COUNT and UNTIL are expanded inside the requested
 * window on wall-clock time in the event's zone, so DST changes keep the local
 * hour. Anything fancier is shown as its first occurrence only.
 *
 * All-day events come back as UTC midnights (`2026-10-09T00:00:00.000Z`), the
 * same convention the Google sync uses; consumers compare them by date.
 *
 * Feeds are untrusted: the number of VEVENTs and the total recurrence
 * iterations are capped, and a malformed VEVENT is skipped, not fatal.
 */

export interface IcsEvent {
  uid?: string;
  title: string;
  start: Date;
  end: Date;
  allDay: boolean;
  location?: string;
  description?: string;
}

export interface IcsLimits {
  maxEvents?: number;
  maxIterations?: number;
}

export const ICS_LIMITS = { maxEvents: 5000, maxIterations: 200_000 } as const;

const DAY = 86_400_000;

interface Prop {
  value: string;
  params: Record<string, string>;
}
type Props = Record<string, Prop[]>;

/** Wall-clock fields stored as if they were UTC (`wall`), plus how to read them. */
interface DT {
  wall: number;
  kind: "utc" | "floating" | "date" | "zoned";
  tz?: string;
}

export function parseIcs(text: string, window?: { from: Date; to: Date }, limits: IcsLimits = {}): IcsEvent[] {
  const maxEvents = limits.maxEvents ?? ICS_LIMITS.maxEvents;
  const budget = { left: limits.maxIterations ?? ICS_LIMITS.maxIterations };
  const vevents: Props[] = [];
  let cur: Props | null = null;
  let nested = 0;

  for (const line of unfold(text)) {
    const upper = line.toUpperCase();
    if (upper === "BEGIN:VEVENT") {
      if (vevents.length >= maxEvents) break;
      cur = {};
      nested = 0;
      continue;
    }
    if (!cur) continue;
    if (upper === "END:VEVENT") {
      vevents.push(cur);
      cur = null;
      continue;
    }
    // Skip nested components (VALARM…): their DESCRIPTION etc. are not the event's.
    if (upper.startsWith("BEGIN:")) nested++;
    else if (upper.startsWith("END:")) nested = Math.max(0, nested - 1);
    else if (nested === 0) {
      const colon = line.indexOf(":");
      if (colon < 0) continue;
      const [name, ...paramParts] = line.slice(0, colon).split(";");
      const params: Record<string, string> = {};
      for (const p of paramParts) {
        const eq = p.indexOf("=");
        if (eq > 0) params[p.slice(0, eq).toUpperCase()] = p.slice(eq + 1).replace(/^"|"$/g, "");
      }
      (cur[name!.toUpperCase()] ??= []).push({ value: line.slice(colon + 1), params });
    }
  }

  // RECURRENCE-ID overrides replace one occurrence of their series.
  const overridden = new Map<string, Set<number>>();
  for (const ev of vevents) {
    const rid = first(ev, "RECURRENCE-ID");
    if (!rid) continue;
    try {
      const uid = first(ev, "UID")?.value ?? "";
      const set = overridden.get(uid) ?? new Set<number>();
      set.add(toInstant(parseDT(rid)));
      overridden.set(uid, set);
    } catch {
      // Unreadable override: the series keeps its original occurrence.
    }
  }

  const out: IcsEvent[] = [];
  for (const ev of vevents) {
    if (budget.left <= 0) break;
    try {
      if (first(ev, "STATUS")?.value.toUpperCase() === "CANCELLED") continue;
      const isOverride = !!first(ev, "RECURRENCE-ID");
      const exclude = new Set<number>(isOverride ? [] : overridden.get(first(ev, "UID")?.value ?? ""));
      out.push(...toEvents(ev, window, exclude, budget, isOverride));
    } catch {
      // One bad VEVENT must not break the whole feed.
    }
  }
  return out;
}

function first(props: Props, name: string): Prop | undefined {
  return props[name]?.[0];
}

function unfold(text: string): string[] {
  return text.replace(/\r\n?/g, "\n").replace(/\n[ \t]/g, "").split("\n").map((l) => l.trimEnd());
}

function unescape(v: string): string {
  return v.replace(/\\([\\;,nN])/g, (_, c: string) => (c === "n" || c === "N" ? "\n" : c));
}

// ---- Dates ---------------------------------------------------------------

/** Common Windows zone names that Outlook writes in TZID. */
const WINDOWS_ZONES: Record<string, string> = {
  "Romance Standard Time": "Europe/Paris",
  "W. Europe Standard Time": "Europe/Berlin",
  "Central Europe Standard Time": "Europe/Budapest",
  "Central European Standard Time": "Europe/Warsaw",
  "GMT Standard Time": "Europe/London",
  "Greenwich Standard Time": "Atlantic/Reykjavik",
  "E. Europe Standard Time": "Europe/Chisinau",
  "FLE Standard Time": "Europe/Kiev",
  "Eastern Standard Time": "America/New_York",
  "Central Standard Time": "America/Chicago",
  "Mountain Standard Time": "America/Denver",
  "Pacific Standard Time": "America/Los_Angeles",
  "Central Standard Time (Mexico)": "America/Mexico_City",
  "SA Pacific Standard Time": "America/Bogota",
  "Argentina Standard Time": "America/Argentina/Buenos_Aires",
  "Pacific SA Standard Time": "America/Santiago",
  "Canary Standard Time": "Atlantic/Canary",
  UTC: "UTC",
};

const formatters = new Map<string, Intl.DateTimeFormat | null>();

function formatter(tz: string): Intl.DateTimeFormat | null {
  if (formatters.has(tz)) return formatters.get(tz)!;
  let f: Intl.DateTimeFormat | null = null;
  try {
    f = new Intl.DateTimeFormat("en-US", {
      timeZone: tz, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
    });
  } catch {
    f = null;
  }
  if (formatters.size > 200) formatters.clear();
  formatters.set(tz, f);
  return f;
}

function resolveZone(tzid: string): string | undefined {
  const name = tzid.trim();
  if (formatter(name)) return name;
  if (WINDOWS_ZONES[name]) return WINDOWS_ZONES[name];
  // e.g. "/mozilla.org/20050126_1/Europe/Madrid"
  const m = /([A-Za-z_]+\/[A-Za-z_+-]+(?:\/[A-Za-z_+-]+)?)$/.exec(name);
  return m && formatter(m[1]!) ? m[1] : undefined;
}

function offsetAt(ms: number, f: Intl.DateTimeFormat): number {
  const t = Math.floor(ms / 1000) * 1000;
  const p: Record<string, string> = {};
  for (const part of f.formatToParts(new Date(t))) p[part.type] = part.value;
  return Date.UTC(+p.year!, +p.month! - 1, +p.day!, +p.hour!, +p.minute!, +p.second!) - t;
}

/**
 * Wall-clock time in an IANA zone (fields given as a UTC timestamp) → instant,
 * using Intl (no tz database needed). Unknown zones are read as UTC.
 */
export function zonedToUtc(wall: number, tz: string): number {
  const f = formatter(tz);
  if (!f) return wall;
  const o1 = offsetAt(wall, f);
  let t = wall - o1;
  const o2 = offsetAt(t, f);
  if (o2 !== o1) t = wall - o2;
  return t;
}

function parseDT(p: Prop): DT {
  const value = p.value.trim();
  if (p.params.VALUE === "DATE" || /^\d{8}$/.test(value)) {
    const m = /^(\d{4})(\d{2})(\d{2})$/.exec(value);
    if (!m) throw new Error(`Fecha ICS inválida: ${value}`);
    return { wall: Date.UTC(+m[1]!, +m[2]! - 1, +m[3]!), kind: "date" };
  }
  const m = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z)?$/.exec(value);
  if (!m) throw new Error(`Fecha ICS inválida: ${value}`);
  const [, y, mo, d, h, mi, s, z] = m;
  const wall = Date.UTC(+y!, +mo! - 1, +d!, +h!, +mi!, +s!);
  if (Number.isNaN(wall)) throw new Error(`Fecha ICS inválida: ${value}`);
  if (z) return { wall, kind: "utc" };
  const tz = p.params.TZID ? resolveZone(p.params.TZID) : undefined;
  if (tz) return { wall, kind: "zoned", tz };
  if (p.params.TZID) return { wall, kind: "utc" }; // unknown zone: best effort
  return { wall, kind: "floating" };
}

function toInstant(dt: DT, wall = dt.wall): number {
  switch (dt.kind) {
    case "utc":
    case "date":
      return wall;
    case "zoned":
      return zonedToUtc(wall, dt.tz!);
    case "floating": {
      const w = new Date(wall);
      return new Date(w.getUTCFullYear(), w.getUTCMonth(), w.getUTCDate(), w.getUTCHours(), w.getUTCMinutes(), w.getUTCSeconds()).getTime();
    }
  }
}

function parseDuration(v: string): number {
  const m = /^[+]?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/.exec(v.trim());
  if (!m) return 0;
  const [, w, d, h, mi, s] = m.map((x) => Number(x ?? 0));
  return ((((w! * 7 + d!) * 24 + h!) * 60 + mi!) * 60 + s!) * 1000;
}

// ---- Events --------------------------------------------------------------

function toEvents(props: Props, window: { from: Date; to: Date } | undefined, exclude: Set<number>, budget: { left: number }, isOverride: boolean): IcsEvent[] {
  const dtstartProp = first(props, "DTSTART");
  if (!dtstartProp) return [];
  const start = parseDT(dtstartProp);
  const allDay = start.kind === "date";
  const startInstant = toInstant(start);

  // Duration on the wall clock when DTEND shares DTSTART's zone (keeps local hours across DST).
  let wallDur: number;
  const dtendProp = first(props, "DTEND");
  const durationProp = first(props, "DURATION");
  if (dtendProp) {
    const end = parseDT(dtendProp);
    wallDur = end.kind === start.kind && end.tz === start.tz ? end.wall - start.wall : toInstant(end) - startInstant;
  } else if (durationProp) wallDur = parseDuration(durationProp.value);
  else wallDur = allDay ? DAY : 0;
  if (!(wallDur >= 0)) wallDur = 0;
  const endOf = (wall: number) => toInstant(start, wall + wallDur);

  const base = {
    uid: first(props, "UID")?.value,
    title: unescape(first(props, "SUMMARY")?.value ?? "(sin título)") || "(sin título)",
    allDay,
    location: props.LOCATION ? unescape(first(props, "LOCATION")!.value) : undefined,
    description: props.DESCRIPTION ? unescape(first(props, "DESCRIPTION")!.value) : undefined,
  };
  const make = (s: number, e: number): IcsEvent => ({ ...base, start: new Date(s), end: new Date(e) });
  const overlaps = (s: number, e: number) => !window || (Math.max(e, s + 1) > window.from.getTime() && s < window.to.getTime());

  for (const p of props.EXDATE ?? []) {
    for (const v of p.value.split(",")) {
      try {
        exclude.add(toInstant(parseDT({ value: v, params: p.params })));
      } catch {
        // ignore unreadable EXDATE values
      }
    }
  }

  const rrule = isOverride ? undefined : first(props, "RRULE")?.value;
  if (!rrule || !window) {
    budget.left--;
    const s = startInstant, e = endOf(start.wall);
    return !exclude.has(s) && overlaps(s, e) ? [make(s, e)] : [];
  }

  const rule: Record<string, string> = {};
  for (const kv of rrule.split(";")) {
    const eq = kv.indexOf("=");
    if (eq > 0) rule[kv.slice(0, eq).toUpperCase()] = kv.slice(eq + 1).toUpperCase();
  }
  const out: IcsEvent[] = [];
  expand(start, rule, window, wallDur, budget, (wall) => {
    const s = toInstant(start, wall);
    const e = endOf(wall);
    if (!exclude.has(s) && overlaps(s, e)) out.push(make(s, e));
  }, () => {
    // Unsupported rule: first occurrence only.
    const s = startInstant, e = endOf(start.wall);
    if (!exclude.has(s) && overlaps(s, e)) out.push(make(s, e));
  });
  return out;
}

const DAY_CODES = ["SU", "MO", "TU", "WE", "TH", "FR", "SA"];

function daysInMonth(y: number, m: number): number {
  return new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
}

/** Days of a month matching BYDAY entries such as "MO", "2TU" or "-1FR". */
function byDayInMonth(y: number, m: number, byDay: string[]): number[] {
  const dim = daysInMonth(y, m);
  const days = new Set<number>();
  for (const entry of byDay) {
    const em = /^([+-]?\d{1,2})?(SU|MO|TU|WE|TH|FR|SA)$/.exec(entry);
    if (!em) continue;
    const wd = DAY_CODES.indexOf(em[2]!);
    const matching: number[] = [];
    for (let d = 1; d <= dim; d++) if (new Date(Date.UTC(y, m, d)).getUTCDay() === wd) matching.push(d);
    const n = em[1] ? Number(em[1]) : 0;
    if (n === 0) matching.forEach((d) => days.add(d));
    else {
      const d = n > 0 ? matching[n - 1] : matching[matching.length + n];
      if (d) days.add(d);
    }
  }
  return [...days];
}

function byMonthDay(y: number, m: number, list: number[]): number[] {
  const dim = daysInMonth(y, m);
  return list.map((d) => (d > 0 ? d : dim + d + 1)).filter((d) => d >= 1 && d <= dim);
}

/**
 * Calls `emit` with the wall-clock time of every occurrence that may touch
 * the window, in order. Without COUNT it jumps straight to the window, so
 * old series cost the same as new ones.
 */
function expand(
  start: DT,
  rule: Record<string, string>,
  w: { from: Date; to: Date },
  wallDur: number,
  budget: { left: number },
  emit: (wall: number) => void,
  unsupported: () => void,
): void {
  const freq = rule.FREQ;
  if (freq !== "DAILY" && freq !== "WEEKLY" && freq !== "MONTHLY" && freq !== "YEARLY") return unsupported();
  if (rule.BYSETPOS || rule.BYHOUR || rule.BYMINUTE || rule.BYWEEKNO || rule.BYYEARDAY) return unsupported();
  const interval = Math.max(1, Math.min(1000, Math.floor(Number(rule.INTERVAL ?? 1)) || 1));
  const count = rule.COUNT !== undefined ? Math.max(0, Math.floor(Number(rule.COUNT)) || 0) : undefined;
  let until = Infinity;
  if (rule.UNTIL) {
    const u = parseDT({ value: rule.UNTIL, params: {} });
    if (u.kind === "utc") until = u.wall;
    else if (u.kind === "date") until = start.kind === "date" ? u.wall : toInstant(start, u.wall + DAY - 1);
    else until = start.kind === "date" ? u.wall : toInstant(start, u.wall);
  }

  const sd = new Date(start.wall);
  const sy = sd.getUTCFullYear(), sm = sd.getUTCMonth(), sdom = sd.getUTCDate();
  const startDay = Date.UTC(sy, sm, sdom);
  const tod = start.wall - startDay;
  const byDayCodes = rule.BYDAY ? rule.BYDAY.split(",").map((c) => c.trim()).filter(Boolean) : [];
  const byDayPlain = byDayCodes.map((c) => DAY_CODES.indexOf(c.slice(-2))).filter((d) => d >= 0);
  const byMonthDays = rule.BYMONTHDAY ? rule.BYMONTHDAY.split(",").map(Number).filter((n) => Number.isInteger(n) && n !== 0 && Math.abs(n) <= 31) : [];
  const byMonths = rule.BYMONTH ? rule.BYMONTH.split(",").map((n) => Number(n) - 1).filter((n) => n >= 0 && n <= 11) : [sm];
  const wkst = rule.WKST ? Math.max(0, DAY_CODES.indexOf(rule.WKST)) : 1;
  const weekStart = startDay - (((sd.getUTCDay() - wkst + 7) % 7) * DAY);

  /** Wall-clock days of period `p` (sorted) and the period's first day. */
  const period = (p: number): { base: number; days: number[] } => {
    switch (freq) {
      case "DAILY": {
        const day = startDay + p * interval * DAY;
        const ok = !byDayPlain.length || byDayPlain.includes(new Date(day).getUTCDay());
        return { base: day, days: ok ? [day] : [] };
      }
      case "WEEKLY": {
        const ws = weekStart + p * interval * 7 * DAY;
        const wds = byDayPlain.length ? byDayPlain : [sd.getUTCDay()];
        return { base: ws, days: [...new Set(wds.map((wd) => ws + ((wd - wkst + 7) % 7) * DAY))].sort((a, b) => a - b) };
      }
      case "MONTHLY": {
        const total = sm + p * interval;
        const y = sy + Math.floor(total / 12), m = ((total % 12) + 12) % 12;
        let doms = byDayCodes.length ? byDayInMonth(y, m, byDayCodes) : byMonthDay(y, m, byMonthDays.length ? byMonthDays : [sdom]);
        if (byDayCodes.length && byMonthDays.length) {
          const allowed = new Set(byMonthDay(y, m, byMonthDays));
          doms = doms.filter((d) => allowed.has(d));
        }
        return { base: Date.UTC(y, m, 1), days: doms.sort((a, b) => a - b).map((d) => Date.UTC(y, m, d)) };
      }
      default: {
        const y = sy + p * interval;
        const days: number[] = [];
        for (const m of byMonths) {
          const doms = byDayCodes.length && rule.BYMONTH ? byDayInMonth(y, m, byDayCodes) : byMonthDay(y, m, byMonthDays.length ? byMonthDays : [sdom]);
          for (const d of doms) days.push(Date.UTC(y, m, d));
        }
        return { base: Date.UTC(y, 0, 1), days: days.sort((a, b) => a - b) };
      }
    }
  };

  // Jump close to the window (a day of margin covers any zone offset).
  let p = 0;
  if (count === undefined) {
    const target = w.from.getTime() - wallDur - 2 * DAY;
    if (target > startDay) {
      const td = new Date(target);
      if (freq === "DAILY") p = Math.floor((target - startDay) / (interval * DAY));
      else if (freq === "WEEKLY") p = Math.floor((target - weekStart) / (interval * 7 * DAY));
      else if (freq === "MONTHLY") p = Math.floor(((td.getUTCFullYear() - sy) * 12 + td.getUTCMonth() - sm) / interval);
      else p = Math.floor((td.getUTCFullYear() - sy) / interval);
      p = Math.max(0, p - 1);
    }
  }
  const stopWall = w.to.getTime() + 2 * DAY;
  let n = 0;
  for (; budget.left > 0; p++) {
    budget.left--;
    const { base, days } = period(p);
    if (base > stopWall) return;
    for (const day of days) {
      const wall = day + tod;
      if (wall < start.wall) continue;
      if (wall > stopWall) return;
      if (toInstant(start, wall) > until) return;
      n++;
      if (count !== undefined && n > count) return;
      emit(wall);
    }
  }
}
