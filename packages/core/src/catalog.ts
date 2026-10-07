/**
 * Apps that Foco knows how to block, with the identifiers each platform
 * needs: Android package names (Accessibility / UsageStats) and web domains
 * (browser extension on PC).
 */

/** A part of an app that can be blocked on its own. `app` means the whole app. */
export type Surface = "app" | "reels" | "stories" | "explore" | "messages" | "feed";

export const SURFACE_LABELS: Record<Surface, string> = {
  app: "App completa",
  reels: "Reels / Shorts / vídeos cortos",
  stories: "Historias",
  explore: "Explorar / Buscar",
  messages: "Mensajes",
  feed: "Feed infinito",
};

export interface CatalogApp {
  id: string;
  name: string;
  androidPackages: string[];
  webDomains: string[];
  /** Surfaces that can be blocked without blocking the whole app (Android + web). */
  surfaces: Surface[];
  /** URL path prefixes per surface, used by the browser extension. */
  webPaths?: Partial<Record<Surface, string[]>>;
}

export const CATALOG: CatalogApp[] = [
  {
    id: "instagram",
    name: "Instagram",
    androidPackages: ["com.instagram.android", "com.instagram.lite"],
    webDomains: ["instagram.com"],
    surfaces: ["reels", "stories", "explore", "messages", "feed"],
    webPaths: { reels: ["/reels", "/reel/"], explore: ["/explore"], messages: ["/direct"], stories: ["/stories"] },
  },
  {
    id: "tiktok",
    name: "TikTok",
    androidPackages: ["com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme"],
    webDomains: ["tiktok.com"],
    surfaces: ["feed", "messages", "explore"],
    webPaths: { feed: ["/foryou", "/"], explore: ["/explore"], messages: ["/messages"] },
  },
  {
    id: "youtube",
    name: "YouTube",
    androidPackages: ["com.google.android.youtube"],
    webDomains: ["youtube.com", "m.youtube.com"],
    surfaces: ["reels", "feed", "explore"],
    webPaths: { reels: ["/shorts"], feed: ["/feed"] },
  },
  {
    id: "snapchat",
    name: "Snapchat",
    androidPackages: ["com.snapchat.android"],
    webDomains: ["snapchat.com"],
    surfaces: ["stories", "reels", "messages"],
    webPaths: { reels: ["/spotlight"] },
  },
  {
    id: "facebook",
    name: "Facebook",
    androidPackages: ["com.facebook.katana", "com.facebook.lite"],
    webDomains: ["facebook.com", "m.facebook.com"],
    surfaces: ["reels", "stories", "feed", "messages"],
    webPaths: { reels: ["/reel", "/watch"], stories: ["/stories"], messages: ["/messages"] },
  },
  {
    id: "x",
    name: "X (Twitter)",
    androidPackages: ["com.twitter.android"],
    webDomains: ["x.com", "twitter.com"],
    surfaces: ["feed", "explore", "messages"],
    webPaths: { feed: ["/home"], explore: ["/explore"], messages: ["/messages"] },
  },
  {
    id: "reddit",
    name: "Reddit",
    androidPackages: ["com.reddit.frontpage"],
    webDomains: ["reddit.com"],
    surfaces: ["feed"],
  },
  {
    id: "whatsapp",
    name: "WhatsApp",
    androidPackages: ["com.whatsapp", "com.whatsapp.w4b"],
    webDomains: ["web.whatsapp.com"],
    surfaces: ["stories"],
  },
];

export function findApp(id: string): CatalogApp | undefined {
  return CATALOG.find((a) => a.id === id);
}

export function appForAndroidPackage(pkg: string): CatalogApp | undefined {
  return CATALOG.find((a) => a.androidPackages.includes(pkg));
}
