import { DEFAULT_FRICTION, DEFAULT_POMODORO, type DailyLimit, type FrictionSettings, type PomodoroConfig } from "@foco/core";
import { store, useRecord } from "./store";

export interface Prefs {
  captureGesture: "volume_down_double" | "volume_up_double" | "off";
  friction: FrictionSettings;
  pomodoro: PomodoroConfig;
  limits: DailyLimit[];
  /** Apps blocked by default when starting a focus session. */
  focusApps: string[];
  voice: "device" | "server";
}

export const DEFAULT_PREFS: Prefs = {
  captureGesture: "volume_down_double",
  friction: DEFAULT_FRICTION,
  pomodoro: DEFAULT_POMODORO,
  limits: [],
  focusApps: ["instagram", "tiktok", "youtube", "snapchat", "facebook", "x"],
  voice: "device",
};

export function usePrefs(): Prefs {
  const saved = useRecord<Partial<Prefs>>("settings", "prefs");
  return { ...DEFAULT_PREFS, ...saved };
}

export function getPrefs(): Prefs {
  return { ...DEFAULT_PREFS, ...store.get<Partial<Prefs>>("settings", "prefs") };
}

export function updatePrefs(patch: Partial<Prefs>) {
  store.put("settings", "prefs", { ...getPrefs(), ...patch });
}
