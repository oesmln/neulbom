/**
 * MemoCare design tokens.
 *
 * Ported from the Figma Make project「치매노노」(BkFVZ148AZd7XvC89w8llK):
 * `src/styles/theme.css` for the palette, and the per-screen constant blocks at
 * the top of `elder-screens.tsx` / `onboarding-screens.tsx` for everything the
 * CSS file does not carry (character stage, success, tint surfaces).
 *
 * The design is flat — no gradients anywhere. Sage green leads, tan marks
 * rewards, and the one dark surface (`characterStage`) exists so the character
 * reads against it on Splash.
 */

export const colors = {
  background: "#FFFFFF",
  foreground: "#2D3132", // T  — charcoal, primary text
  card: "#FFFFFF",
  cardForeground: "#1A1F2E",

  primary: "#5A8F68", // P  — sage green: headers, buttons, active state
  primaryForeground: "#FFFFFF",
  primaryDark: "#3D6B4A", // PD — dark sage: text on light sage surfaces

  secondary: "#EAF3EC", // PL — light sage: selected rows, tags, character stages
  secondaryForeground: "#3D6B4A",

  muted: "#F0EDE6", // MU — warm muted surface: inputs, disabled, keypad
  mutedForeground: "#697268", // TS — warm gray, secondary text

  accent: "#D4A373", // TAN — reward / attention accent
  accentForeground: "#FFFFFF",
  accentLight: "#FAF0E4", // TANL

  destructive: "#C0392B", // D
  destructiveForeground: "#FFFFFF",
  destructiveLight: "#FDEAEA", // DL

  success: "#4E9566", // OK
  successLight: "#D4F0DE",

  /**
   * CHR — solid dark stage the character sits on. Splash only.
   * Everywhere else the character sits on `primary` (Home) or `secondary`.
   */
  characterStage: "#2E5238",

  border: "rgba(45, 49, 50, 0.11)", // BR
  inputBackground: "#F5F2EC",
  switchBackground: "#C4BFB2",
  ring: "#5A8F68",

  white: "#FFFFFF",
  warning: "#C08A2A",
  warningLight: "#FEF3DC",
  screenBackground: "#F5F7FA",
} as const;

// Chart palette (chart-1 ... chart-5 from theme.css)
export const chartColors = [
  "#5A8F68",
  "#D4A373",
  "#6B9CB8",
  "#C0392B",
  "#8A7FAA",
] as const;

/**
 * Cognitive activity stages shown to the elder.
 *
 * These are presentation only. The server decides which stage applies via
 * `dashboard.cognitive_activity.status` and sends the wording with it — the app
 * must never derive a stage from a score (api-spec 5.1).
 */
export const cognitiveStages = [
  { key: "stable", step: "안정적", color: "#4E9566", bg: "#E8F5EC" },
  { key: "observe", step: "꾸준한 관찰", color: "#C08A2A", bg: "#FEF3DC" },
  { key: "attention_required", step: "확인 필요", color: "#C0392B", bg: "#FDEAEA" },
] as const;

export type CognitiveStageKey = (typeof cognitiveStages)[number]["key"];

export const radius = {
  sm: 8,
  md: 12,
  lg: 16, // cards, inputs
  xl: 20, // buttons, large cards
  pill: 999,
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20, // screen gutter — Figma uses px-5
  xxl: 28,
} as const;

/**
 * Type scale, taken from the Figma screens rather than from theme.css.
 *
 * Note this is smaller than the "고령자 폰트 18px+" line in the Figma spec panel
 * — the screens themselves set 13-20px, and matching the screens is what keeps
 * the layouts from overflowing. Revisit with the team before shipping to users.
 */
export const fontSize = {
  badge: 11,
  micro: 12,
  caption: 13,
  body: 14,
  bodyLg: 16,
  cardTitle: 17,
  subtitle: 18,
  title: 20,
  display: 26,
} as const;

export const fontWeight = {
  normal: "400",
  medium: "500",
  semibold: "600",
  bold: "700",
} as const;

export const sizes = {
  buttonHeight: 52,
  buttonHeightSm: 44,
  buttonHeightLg: 56,
  inputHeight: 52,
  tabBarHeight: 68,
  /** Center home button on the elder tab bar, raised above the bar. */
  tabBarHomeButton: 50,
  canvasWidth: 375,
} as const;

export const shadow = {
  /** Figma: `0 1px 4px rgba(45,49,50,0.06)` on white cards. */
  card: {
    shadowColor: "#2D3132",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  /** Figma: `0 4px 20px rgba(0,0,0,0.10)` on speech bubbles and the tab home button. */
  floating: {
    shadowColor: "#000000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 20,
    elevation: 6,
  },
} as const;

export const theme = {
  colors,
  chartColors,
  cognitiveStages,
  radius,
  spacing,
  fontSize,
  fontWeight,
  sizes,
  shadow,
};

export type Theme = typeof theme;
export default theme;
