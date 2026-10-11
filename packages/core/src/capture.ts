/**
 * On-device parser for quick captures ("mañana a las 5 dentista",
 * "recuérdame llamar a mamá el viernes", "reunión de 10 a 11:30 el lunes").
 * Spanish first, common English forms too. It never calls the network: when
 * `confidence` is low the app may ask the AI to interpret the text instead.
 */
import { addDays, addMinutes, startOfDay } from "./time";

export type CaptureKind = "event" | "task" | "note";

export interface CaptureResult {
  kind: CaptureKind;
  title: string;
  /** Events: start/end. Tasks: start = due datetime when a time was given. */
  start?: Date;
  end?: Date;
  /** Tasks with a day but no time. */
  dueDate?: Date;
  durationMinutes?: number;
  /** 0–1. Below ~0.7 the app shows the result for confirmation (or asks the AI). */
  confidence: number;
}

const WEEKDAYS: Record<string, number> = {
  domingo: 0, lunes: 1, martes: 2, miercoles: 3, jueves: 4, viernes: 5, sabado: 6,
  sunday: 0, monday: 1, tuesday: 2, wednesday: 3, thursday: 4, friday: 5, saturday: 6,
};
const MONTHS: Record<string, number> = {
  enero: 0, febrero: 1, marzo: 2, abril: 3, mayo: 4, junio: 5, julio: 6, agosto: 7,
  septiembre: 8, setiembre: 8, octubre: 9, noviembre: 10, diciembre: 11,
  january: 0, february: 1, march: 2, april: 3, may: 4, june: 5, july: 6, august: 7,
  september: 8, october: 9, november: 10, december: 11,
};

const DEFAULT_EVENT_MINUTES = 60;

/** Lowercase + strip accents keeping string length, so indexes map back to the original. */
function normalize(text: string): string {
  let out = "";
  for (const ch of text) {
    const base = ch.normalize("NFD")[0] ?? ch;
    // Characters outside the BMP would break the 1:1 index mapping; replace them.
    out += ch.length === 1 ? base.toLowerCase() : " ".repeat(ch.length);
  }
  return out;
}

export function parseCapture(text: string, now: Date = new Date()): CaptureResult {
  const original = text.trim();
  const norm = normalize(original);
  const removed = new Array<boolean>(original.length).fill(false);
  /** First match that does not overlap text already consumed; consumes it. */
  const take = (re: RegExp): RegExpExecArray | null => {
    const g = new RegExp(re.source, re.flags.includes("g") ? re.flags : re.flags + "g");
    let m: RegExpExecArray | null;
    while ((m = g.exec(norm))) {
      if (m[0].length === 0) {
        g.lastIndex++;
        continue;
      }
      let free = true;
      for (let i = m.index; i < m.index + m[0].length; i++) if (removed[i]) free = false;
      if (!free) continue;
      for (let i = m.index; i < m.index + m[0].length; i++) removed[i] = true;
      return m;
    }
    return null;
  };

  let confidence = 1;
  let kind: CaptureKind | undefined;

  if (take(/^\s*(nota|note|idea)\s*:\s*/)) kind = "note";
  if (!kind && take(/^\s*(recuerdame(?: que)?|recordar(?:me)?|recuerda(?: que)?|tarea\s*:|todo\s*:|pendiente\s*:|remind me(?: to)?|task\s*:)\s*/)) kind = "task";
  const taskVerb = /^\s*(comprar|llamar|enviar|mandar|pagar|hacer|terminar|estudiar|revisar|buy|call|send|pay|finish)\b/.test(norm);

  let day: Date | undefined;
  const today = startOfDay(now);
  let m: RegExpExecArray | null;

  // Time first, so "de la mañana" is not read as "tomorrow".
  // ---- Time ----------------------------------------------------------
  let startMin: number | undefined;
  let endMin: number | undefined;

  const range = take(/\b(?:de|desde|from|entre)\s+(?:las\s+)?(\d{1,2})(?:[:.h](\d{2}))?\s*(am|pm)?\s+(?:a|hasta|to|y)\s+(?:las\s+)?(\d{1,2})(?:[:.h](\d{2}))?\s*(am|pm)?\b/);
  if (range) {
    const s = toMinutes(Number(range[1]), Number(range[2] ?? 0), range[3]);
    const e = toMinutes(Number(range[4]), Number(range[5] ?? 0), range[6]);
    startMin = s.minutes;
    endMin = e.minutes;
    if (endMin <= startMin && endMin + 12 * 60 > startMin) endMin += 12 * 60;
    if (s.ambiguous || e.ambiguous) confidence = Math.min(confidence, 0.8);
  }
  if (startMin === undefined) {
    const t =
      take(/\b(?:a las|a la|sobre las|at|@)\s*(\d{1,2})(?:[:.h](\d{2}))?(?:\s*(am|pm|a\.m\.|p\.m\.))?(\s+y\s+(media|cuarto))?(?:\s+(de la manana|de la tarde|de la noche|de la madrugada))?\b/) ??
      take(/\b(\d{1,2}):(\d{2})\s*(am|pm)?\b/) ??
      take(/\b(\d{1,2})\s*(am|pm|h)\b/);
    if (t) {
      let hour = Number(t[1]);
      let minute = /^\d+$/.test(t[2] ?? "") ? Number(t[2]) : 0;
      const ampm = (t[3] ?? "").replace(/\./g, "");
      if (t[5] === "media") minute = 30;
      if (t[5] === "cuarto") minute = 15;
      const period = t[6];
      if (period === "de la tarde" || (period === "de la noche" && hour >= 6)) hour = hour < 12 ? hour + 12 : hour;
      if ((period === "de la manana" || period === "de la madrugada") && hour === 12) hour = 0;
      const r = toMinutes(hour, minute, period ? "explicit" : ampm === "h" ? "24h" : ampm);
      startMin = r.minutes;
      if (r.ambiguous) confidence = Math.min(confidence, 0.8);
    } else if (take(/\b(a mediodia|al mediodia|mediodia|noon)\b/)) {
      startMin = 12 * 60;
    } else {
      const part = take(/\b(por la manana|por la tarde|por la noche|in the morning|in the afternoon|in the evening)\b/);
      if (part) {
        startMin = /manana|morning/.test(part[0]) ? 9 * 60 : /tarde|afternoon/.test(part[0]) ? 17 * 60 : 21 * 60;
        confidence = Math.min(confidence, 0.6);
      }
    }
  }

  // ---- Duration ------------------------------------------------------
  let duration: number | undefined;
  const d =
    take(/\b(?:durante|por|for)?\s*(\d+)\s*h\s*(\d{1,2})\b/) ??
    take(/\b(?:durante|por|for)?\s*(hora y media|media hora|una hora|un cuarto de hora|an hour|half an hour)\b/) ??
    take(/\b(?:durante|por|for)\s*(\d+(?:[.,]\d+)?)\s*(h|horas?|hours?|min|minutos?|minutes?)\b/) ??
    take(/\b(\d+(?:[.,]\d+)?)\s*(horas?|hours?|minutos?|minutes?|min)\b/);
  if (d) {
    if (/^\d+$/.test(d[1] ?? "") && /^\d{1,2}$/.test(d[2] ?? "")) duration = Number(d[1]) * 60 + Number(d[2]);
    else if (d[1] === "hora y media") duration = 90;
    else if (d[1] === "media hora" || d[1] === "half an hour") duration = 30;
    else if (d[1] === "una hora" || d[1] === "an hour") duration = 60;
    else if (d[1] === "un cuarto de hora") duration = 15;
    else {
      const n = Number((d[1] ?? "0").replace(",", "."));
      duration = /^h|hora|hour/.test(d[2] ?? "") ? Math.round(n * 60) : Math.round(n);
    }
  }

  // ---- Day -----------------------------------------------------------

  if ((m = take(/\b(pasado manana|day after tomorrow)\b/))) day = addDays(today, 2);
  else if ((m = take(/\b(manana|tomorrow)\b(?! por la| de la)|(?<!(?:por|de) la )\bmanana\b/))) day = addDays(today, 1);
  else if ((m = take(/\b(hoy|today|esta noche|tonight|esta tarde)\b/))) {
    day = today;
    if (startMin === undefined && /noche|tonight|tarde/.test(m[0])) {
      startMin = /tarde/.test(m[0]) ? 17 * 60 : 21 * 60;
      confidence = Math.min(confidence, 0.6);
    }
  }
  if (!day && (m = take(/\ben (\d+) (dias?|semanas?|days?|weeks?)\b|\bin (\d+) (days?|weeks?)\b/))) {
    const n = Number(m[1] ?? m[3]);
    const unit = m[2] ?? m[4] ?? "";
    day = addDays(today, /seman|week/.test(unit) ? n * 7 : n);
  }
  if (!day && (m = take(/\b(?:el |este |esta |el proximo |el pr[oó]ximo |proximo |next |on |this )?(lunes|martes|miercoles|jueves|viernes|sabado|domingo|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b/))) {
    const target = WEEKDAYS[m[1]!]!;
    let delta = (target - today.getDay() + 7) % 7;
    const explicitThis = /\b(este|esta|this)\b/.test(m[0]);
    if (delta === 0 && !explicitThis) delta = 7;
    day = addDays(today, delta);
  }
  if (!day && (m = take(/\b(?:el )?(\d{1,2}) de (enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)(?: de (\d{4}))?\b/))) {
    day = dateWithYearRollover(today, Number(m[1]), MONTHS[m[2]!]!, m[3] ? Number(m[3]) : undefined);
  }
  if (!day && (m = take(/\b(?:el )?(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?\b/))) {
    const year = m[3] ? (m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3])) : undefined;
    day = dateWithYearRollover(today, Number(m[1]), Number(m[2]) - 1, year);
  }

  // ---- Assemble ------------------------------------------------------
  const title = cleanTitle(original, removed) || original;
  const hasTime = startMin !== undefined;
  const baseDay = day ?? (hasTime ? today : undefined);

  if (!kind) kind = hasTime ? (taskVerb && !range ? "task" : "event") : "task";
  if (kind === "note") return { kind, title, confidence };

  if (hasTime && baseDay) {
    let start = addMinutes(baseDay, startMin!);
    // "a las 9" said at 18:00 without a day means tomorrow.
    if (!day && start < now) start = addDays(start, 1);
    if (kind === "task") return { kind, title, start, confidence };
    const minutes = endMin !== undefined ? endMin - startMin! : duration ?? DEFAULT_EVENT_MINUTES;
    return { kind, title, start, end: addMinutes(start, minutes), durationMinutes: minutes, confidence };
  }
  if (baseDay) return { kind: "task", title, dueDate: baseDay, durationMinutes: duration, confidence: Math.min(confidence, 0.9) };
  return { kind: "task", title, durationMinutes: duration, confidence: Math.min(confidence, title.split(/\s+/).length > 12 ? 0.5 : 0.9) };
}

function dateWithYearRollover(today: Date, dayOfMonth: number, month: number, year?: number): Date {
  const d = new Date(year ?? today.getFullYear(), month, dayOfMonth);
  if (year === undefined && d < today) d.setFullYear(d.getFullYear() + 1);
  return d;
}

/**
 * Hours 1–7 without am/pm are read as afternoon ("a las 5" = 17:00), which is
 * how they are used in Spanish; it is flagged as ambiguous.
 */
function toMinutes(hour: number, minute: number, marker?: string): { minutes: number; ambiguous: boolean } {
  let h = hour;
  let ambiguous = false;
  if (marker === "pm" && h < 12) h += 12;
  else if (marker === "am" && h === 12) h = 0;
  else if (!marker && h >= 1 && h <= 7) {
    h += 12;
    ambiguous = true;
  }
  return { minutes: Math.min(h, 23) * 60 + Math.min(minute, 59), ambiguous };
}

const DANGLING = /^(?:el|la|los|las|de|del|a|al|en|para|y|que|con|on|at|the|to|por)$/i;

function cleanTitle(original: string, removed: boolean[]): string {
  let kept = "";
  for (let i = 0; i < original.length; i++) kept += removed[i] ? " " : original[i];
  const words = kept.split(/\s+/).filter(Boolean);
  while (words.length && DANGLING.test(words[0]!)) words.shift();
  while (words.length && (DANGLING.test(words[words.length - 1]!) || /^[,.;:-]+$/.test(words[words.length - 1]!))) words.pop();
  const t = words.join(" ").replace(/\s+([,.;:])/g, "$1").replace(/^[,.;:\s-]+|[,;:\s-]+$/g, "");
  return t ? t[0]!.toUpperCase() + t.slice(1) : "";
}
