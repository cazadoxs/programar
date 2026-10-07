/** "HH:MM" → minutes since midnight. Throws on malformed input. */
export function parseHHMM(value: string): number {
  const m = /^(\d{1,2}):(\d{2})$/.exec(value);
  if (!m) throw new Error(`Hora inválida: ${value}`);
  const h = Number(m[1]);
  const min = Number(m[2]);
  if (h > 24 || min > 59 || (h === 24 && min !== 0)) throw new Error(`Hora inválida: ${value}`);
  return h * 60 + min;
}

export function formatHHMM(minutes: number): string {
  const h = Math.floor(minutes / 60) % 24;
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

export function minutesOfDay(d: Date): number {
  return d.getHours() * 60 + d.getMinutes();
}

export function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

export function addDays(d: Date, days: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + days);
  return r;
}

export function addMinutes(d: Date, minutes: number): Date {
  return new Date(d.getTime() + minutes * 60_000);
}

/** Local date at `minutes` after midnight of `day`. */
export function atMinutes(day: Date, minutes: number): Date {
  const r = startOfDay(day);
  r.setMinutes(minutes);
  return r;
}

export function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
