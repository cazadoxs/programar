const WEEKDAYS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
const MONTHS = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];

export const hhmm = (d: Date | string) => {
  const x = typeof d === "string" ? new Date(d) : d;
  return `${String(x.getHours()).padStart(2, "0")}:${String(x.getMinutes()).padStart(2, "0")}`;
};

export const longDate = (d: Date) => `${WEEKDAYS[d.getDay()]} ${d.getDate()} de ${MONTHS[d.getMonth()]}`;
export const shortWeekday = (d: Date) => WEEKDAYS[d.getDay()]!.slice(0, 3);

export function remaining(until: Date | string, now = new Date()): string {
  const ms = new Date(until).getTime() - now.getTime();
  if (ms <= 0) return "0 min";
  const min = Math.ceil(ms / 60_000);
  return min >= 60 ? `${Math.floor(min / 60)} h ${min % 60} min` : `${min} min`;
}

export const dayKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

export function greeting(now = new Date()) {
  const h = now.getHours();
  return h < 6 ? "Buenas noches" : h < 14 ? "Buenos días" : h < 21 ? "Buenas tardes" : "Buenas noches";
}
