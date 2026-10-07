export interface PomodoroConfig {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  /** Focus blocks before a long break. */
  cyclesBeforeLongBreak: number;
}

export const DEFAULT_POMODORO: PomodoroConfig = {
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  cyclesBeforeLongBreak: 4,
};

export type PomodoroPhase = "focus" | "short_break" | "long_break";

export interface PomodoroState {
  phase: PomodoroPhase;
  /** 1-based number of the current focus block (breaks keep the number of the block they follow). */
  cycle: number;
  phaseStartedAt: Date;
  phaseEndsAt: Date;
}

/**
 * Stateless: the phase is derived from when the session started, so every
 * device (and the native blocker with the app closed) agrees on it without
 * exchanging timer state.
 */
export function pomodoroStateAt(startedAt: Date, now: Date, config: PomodoroConfig = DEFAULT_POMODORO): PomodoroState {
  const { focusMinutes: f, shortBreakMinutes: s, longBreakMinutes: l, cyclesBeforeLongBreak: n } = config;
  if (f <= 0 || n <= 0) throw new Error("Configuración de pomodoro inválida");
  // One "round" = n focus blocks, n-1 short breaks and one long break.
  const roundMinutes = n * f + (n - 1) * s + l;
  const elapsed = Math.max(0, (now.getTime() - startedAt.getTime()) / 60_000);
  const round = Math.floor(elapsed / roundMinutes);
  let t = elapsed - round * roundMinutes;
  let offset = round * roundMinutes;

  for (let i = 1; i <= n; i++) {
    const cycle = round * n + i;
    if (t < f) return state("focus", cycle, offset, f);
    t -= f;
    offset += f;
    const isLast = i === n;
    const breakLen = isLast ? l : s;
    if (t < breakLen) return state(isLast ? "long_break" : "short_break", cycle, offset, breakLen);
    t -= breakLen;
    offset += breakLen;
  }
  // Unreachable: t < roundMinutes by construction.
  throw new Error("pomodoroStateAt: estado inalcanzable");

  function state(phase: PomodoroPhase, cycle: number, startMin: number, len: number): PomodoroState {
    const phaseStartedAt = new Date(startedAt.getTime() + startMin * 60_000);
    return { phase, cycle, phaseStartedAt, phaseEndsAt: new Date(phaseStartedAt.getTime() + len * 60_000) };
  }
}
