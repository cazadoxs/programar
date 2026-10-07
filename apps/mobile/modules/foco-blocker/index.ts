import { Platform } from "react-native";
import { requireOptionalNativeModule } from "expo";

export interface AndroidStatus {
  platform: "android";
  accessibilityEnabled: boolean;
  serviceRunning: boolean;
  overlayPermission: boolean;
  usageAccess: boolean;
}

export interface IosStatus {
  platform: "ios";
  screenTimeAuthorized: boolean;
  selectedApps: number;
  selectedCategories: number;
}

export type BlockerStatus = AndroidStatus | IosStatus | { platform: "unsupported" };

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
}

interface NativeModuleShape {
  getStatus(): AndroidStatus | IosStatus;
  setState(json: string): void;
  getBlockStats(): Record<string, Record<string, number>>;
  getUsageToday(): Record<string, number>;
  openAccessibilitySettings?(): void;
  openOverlaySettings?(): void;
  openUsageAccessSettings?(): void;
  requestAuthorization?(): Promise<boolean>;
  presentAppPicker?(): Promise<{ apps: number; categories: number }>;
}

// Absent on web and in Expo Go: every call below degrades to a no-op.
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
export const requestScreenTimeAuthorization = async () => (await native?.requestAuthorization?.()) ?? false;
export const presentAppPicker = async () => (await native?.presentAppPicker?.()) ?? null;
