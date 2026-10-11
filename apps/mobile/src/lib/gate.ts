import { router } from "expo-router";
import type { ProFeature } from "@foco/core";
import { isPro } from "./pro";

/** Returns true if the user can use a Pro feature; otherwise opens the Foco Pro screen. */
export function requirePro(feature: ProFeature): boolean {
  if (isPro()) return true;
  router.push({ pathname: "/pro", params: { feature } });
  return false;
}
