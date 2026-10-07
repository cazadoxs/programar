/**
 * Minimal iCalendar (RFC 5545) reader for `.ics` subscriptions: VEVENTs with
 * UID, SUMMARY, LOCATION, DESCRIPTION, DTSTART/DTEND (UTC, floating, TZID or
 * all-day). Simple DAILY/WEEKLY RRULEs are expanded inside the requested
 * window; anything fancier is shown as its first occurrence only.
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

export function parseIcs(text: string, window?: { from: Date; to: Date }): IcsEvent[] {
  const lines = unfold(text);
  const events: IcsEvent[] = [];
  let cur: Record<string, { value: string; params: Record<string, string> }> | null = null;

  for (const line of lines) {
    if (line === "BEGIN:VEVENT") {
      cur = {};
      continue;
    }
    if (line === "END:VEVENT") {
      if (cur) events.push(...toEvents(cur, window));
      cur = null;
      continue;
    }
    if (!cur) continue;
    const colon = line.indexOf(":");
    if (colon < 0) continue;
    const [name, ...paramParts] = line.slice(0, colon).split(";");
    const params: Record<string, string> = {};
    for (const p of paramParts) {
      const [k, v] = p.split("=");
      if (k && v) params[k.toUpperCase()] = v;
    }
    cur[name!.toUpperCase()] = { value: line.slice(colon + 1), params };
  }
  return events;
}

function unfold(text: string): string[] {
  return text.replace(/\r\n/g, "\n").replace(/\n[ \t]/g, "").split("\n").map((l) => l.trimEnd());
}

function unescape(v: string): string {
  return v.replace(/\\n/gi, "\n").replace(/\\([,;\\])/g, "$1");
}

function parseDate(value: string, params: Record<string, string>): { date: Date; allDay: boolean } {
  if (params.VALUE === "DATE" || /^\d{8}$/.test(value)) {
    const y = +value.slice(0, 4), m = +value.slice(4, 6) - 1, d = +value.slice(6, 8);
    return { date: new Date(y, m, d), allDay: true };
  }
  const m = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z)?$/.exec(value);
  if (!m) throw new Error(`Fecha ICS inválida: ${value}`);
  const [, y, mo, d, h, mi, s, z] = m;
  if (z) return { date: new Date(Date.UTC(+y!, +mo! - 1, +d!, +h!, +mi!, +s!)), allDay: false };
  if (params.TZID) return { date: zonedToUtc(+y!, +mo! - 1, +d!, +h!, +mi!, +s!, params.TZID), allDay: false };
  return { date: new Date(+y!, +mo! - 1, +d!, +h!, +mi!, +s!), allDay: false };
}

/** Wall-clock time in an IANA zone → instant, using Intl (no tz database needed). */
function zonedToUtc(y: number, mo: number, d: number, h: number, mi: number, s: number, tz: string): Date {
  const guess = Date.UTC(y, mo, d, h, mi, s);
  try {
    const fmt = new Intl.DateTimeFormat("en-US", {
      timeZone: tz, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
    });
    const parts = Object.fromEntries(fmt.formatToParts(new Date(guess)).map((p) => [p.type, p.value]));
    const asIfUtc = Date.UTC(+parts.year!, +parts.month! - 1, +parts.day!, +parts.hour!, +parts.minute!, +parts.second!);
    return new Date(guess - (asIfUtc - guess));
  } catch {
    return new Date(guess);
  }
}

function toEvents(
  props: Record<string, { value: string; params: Record<string, string> }>,
  window?: { from: Date; to: Date },
): IcsEvent[] {
  const dtstart = props.DTSTART;
  if (!dtstart) return [];
  const { date: start, allDay } = parseDate(dtstart.value, dtstart.params);
  let end: Date;
  if (props.DTEND) end = parseDate(props.DTEND.value, props.DTEND.params).date;
  else if (props.DURATION) end = new Date(start.getTime() + parseDuration(props.DURATION.value));
  else end = allDay ? new Date(start.getFullYear(), start.getMonth(), start.getDate() + 1) : start;

  const base: IcsEvent = {
    uid: props.UID?.value,
    title: unescape(props.SUMMARY?.value ?? "(sin título)"),
    start, end, allDay,
    location: props.LOCATION ? unescape(props.LOCATION.value) : undefined,
    description: props.DESCRIPTION ? unescape(props.DESCRIPTION.value) : undefined,
  };
  const rrule = props.RRULE?.value;
  if (!rrule || !window) return !window || overlaps(base, window) ? [base] : [];
  return expand(base, rrule, window);
}

function overlaps(e: IcsEvent, w: { from: Date; to: Date }): boolean {
  return e.end > w.from && e.start < w.to;
}

const DAY_CODES = ["SU", "MO", "TU", "WE", "TH", "FR", "SA"];

function expand(base: IcsEvent, rrule: string, w: { from: Date; to: Date }): IcsEvent[] {
  const r = Object.fromEntries(rrule.split(";").map((kv) => kv.split("=") as [string, string]));
  const freq = r.FREQ;
  if (freq !== "DAILY" && freq !== "WEEKLY") return overlaps(base, w) ? [base] : [];
  const interval = Number(r.INTERVAL ?? 1);
  const until = r.UNTIL ? parseDate(r.UNTIL, {}).date : undefined;
  const maxCount = r.COUNT ? Number(r.COUNT) : Infinity;
  const byDay = r.BYDAY ? r.BYDAY.split(",").map((c) => DAY_CODES.indexOf(c.slice(-2))) : [base.start.getDay()];
  const duration = base.end.getTime() - base.start.getTime();

  const out: IcsEvent[] = [];
  let count = 0;
  for (let i = 0; i < 3660; i++) {
    const day = new Date(base.start);
    day.setDate(day.getDate() + i);
    if (until && day > until) break;
    if (day > w.to) break;
    const daysFromStart = i;
    let matches: boolean;
    if (freq === "DAILY") matches = daysFromStart % interval === 0;
    else {
      const weekIndex = Math.floor((daysFromStart + base.start.getDay()) / 7);
      matches = byDay.includes(day.getDay()) && weekIndex % interval === 0;
    }
    if (!matches) continue;
    count++;
    if (count > maxCount) break;
    const occ = { ...base, start: day, end: new Date(day.getTime() + duration) };
    if (overlaps(occ, w)) out.push(occ);
  }
  return out;
}

function parseDuration(v: string): number {
  const m = /^P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/.exec(v);
  if (!m) return 0;
  const [, w, d, h, mi, s] = m.map((x) => Number(x ?? 0));
  return ((((w! * 7 + d!) * 24 + h!) * 60 + mi!) * 60 + s!) * 1000;
}
