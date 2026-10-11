/**
 * Foco design tokens. The brand idea is a warm spotlight cutting through a
 * dark, noisy night: almost-black ink surfaces, warm paper text and a single
 * amber accent that marks whatever deserves your attention.
 */
export const colors = {
  bg: "#0B0B12", // Noche
  card: "#15151F", // Superficie
  cardAlt: "#1E1E2B", // Superficie 2
  border: "#2A2A3A", // Línea
  text: "#F5F2EA", // Papel
  muted: "#A09DB0", // Niebla
  faint: "#6E6B80",
  primary: "#FFB23F", // Foco
  primaryText: "#1A1206",
  primarySoft: "rgba(255, 178, 63, 0.14)",
  danger: "#FF5B49", // Brasa
  dangerSoft: "rgba(255, 91, 73, 0.14)",
  success: "#43D9A3", // Brote
  successSoft: "rgba(67, 217, 163, 0.14)",
  info: "#7AA7FF", // Cielo
  infoSoft: "rgba(122, 167, 255, 0.14)",
  warning: "#FFB23F",
  overlay: "rgba(5, 5, 10, 0.72)",
};

export const fonts = {
  display: "SpaceGrotesk_700Bold",
  displayMedium: "SpaceGrotesk_500Medium",
  body: "Inter_400Regular",
  bodyMedium: "Inter_500Medium",
  bodySemi: "Inter_600SemiBold",
  bodyBold: "Inter_700Bold",
};

export const radius = 20;
export const radiusSm = 14;
export const space = (n: number) => n * 8;

/** Accent colour per catalog app, used for the small app tiles. */
export const APP_TINTS: Record<string, string> = {
  instagram: "#E1306C",
  tiktok: "#25F4EE",
  youtube: "#FF3B30",
  snapchat: "#FFFC00",
  facebook: "#4C8BF5",
  x: "#E7E7EA",
  reddit: "#FF5700",
  whatsapp: "#25D366",
};
