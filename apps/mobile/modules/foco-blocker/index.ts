import { Platform } from "react-native";
import { requireOptionalNativeModule } from "expo";

/** Which engine is blocking: accessibility (primary, granular), usage access (fallback, whole app + pause screen) or none. */
export type BlockerEngine = "accessibility" | "usage" | "none";

export interface AndroidStatus {
  platform: "android";
  accessibilityEnabled: boolean;
  serviceRunning: boolean;
  overlayPermission: boolean;
  usageAccess: boolean;
  /** Engine in charge right now. "usage" means the fallback would run whenever there is something to block. */
  engine: BlockerEngine;
  /** Android 13+ notification permission (and notifications not turned off) for the fallback's ongoing notice. */
  notificationsPermission: boolean;
  /** setFallbackEnabled() switch (on by default). */
  fallbackEnabled: boolean;
  /** The fallback foreground service is running at this moment. */
  fallbackRunning: boolean;
}

export type BlockerStatus = AndroidStatus | { platform: "unsupported" };

/** Serialized by buildNativeState() in src/lib/blocker.ts. */
export interface NativeBlockState {
  timeline: Array<{
    start: number;
    end: number;
    blocks: Array<{ appId: string; packages: string[]; surfaces: string[]; reason: string; strict: boolean }>;
  }>;
  captureGesture: "volume_down_double" | "volume_up_double" | "off";
  watchedPackages: string[];
  patterns?: Record<string, unknown>;
  /**
   * Non-strict whole-app blocks show a 10 s pause ("Abrir igualmente" lets the
   * app through for 5 min) instead of a hard block. Default false.
   */
  wholeAppFriction?: boolean;
  /**
   * Daily limits, enforced natively by both engines: today's usage (since
   * local midnight) is read from UsageStatsManager at check time, so a limit
   * also trips between two setState() calls and while Foco is closed.
   * Needs usage access. Optional.
   */
  limits?: Array<{ appId: string; packages: string[]; minutesPerDay: number; reason?: string }>;
}

interface NativeModuleShape {
  getStatus(): AndroidStatus;
  setState(json: string): void;
  getBlockStats(): Record<string, Record<string, number>>;
  getUsageToday(): Record<string, number>;
  openAccessibilitySettings?(): void;
  openOverlaySettings?(): void;
  openUsageAccessSettings?(): void;
  openNotificationSettings?(): void;
  requestNotificationPermission?(): boolean;
  setFallbackEnabled?(enabled: boolean): void;
}

// Android only. Absent on web and in Expo Go: every call below degrades to a no-op.
const native = Platform.OS === "web" ? null : requireOptionalNativeModule<NativeModuleShape>("FocoBlocker");

export const isBlockerAvailable = native != null;

export function getStatus(): BlockerStatus {
  return native?.getStatus() ?? { platform: "unsupported" };
}

export function setNativeState(state: NativeBlockState): void {
  native?.setState(JSON.stringify(state));
}

export function getBlockStats(): Record<string, Record<string, number>> {
  return native?.getBlockStats() ?? {};
}

export function getUsageToday(): Record<string, number> {
  return native?.getUsageToday() ?? {};
}

export const openAccessibilitySettings = () => native?.openAccessibilitySettings?.();
export const openOverlaySettings = () => native?.openOverlaySettings?.();
export const openUsageAccessSettings = () => native?.openUsageAccessSettings?.();
export const openNotificationSettings = () => native?.openNotificationSettings?.();

/**
 * Asks for the notification permission (Android 13+), or opens the app's
 * notification settings when Android will not show the prompt again.
 * Returns whether notifications are already allowed; re-read getStatus()
 * after the user answers. Always false where unsupported.
 */
export function requestNotificationPermission(): boolean {
  return native?.requestNotificationPermission?.() ?? false;
}

/** Allows or forbids the usage-access fallback engine (used when accessibility is not running). */
export function setFallbackEnabled(enabled: boolean): void {
  native?.setFallbackEnabled?.(enabled);
}
