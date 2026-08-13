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

/**
 * Guardian palette.
 *
 * The guardian area is blue where the elder area is sage. The Figma file
 * defines it in the header comment of `guardian-screens.tsx` and notes the blue
 * was deliberately desaturated to sit at the same weight as the sage green
 * rather than shout over it — so this is `#4A7BC4`, not a stock blue.
 *
 * Only the blues are new. Accent, danger, text and surface colours are shared
 * with `colors` above and were verified to match the design one for one, which
 * is why they are not repeated here.
 */
export const guardian = {
  blue: "#4A7BC4", // GB  — headers, active tab, chart line
  blueLight: "#EBF2FB", // GBL — selected rows, unread/success tint
  blueDark: "#3465A8", // GBD — text on light blue surfaces
  /** Body copy inside a danger card — darker than `destructive` so it stays readable on `destructiveLight`. */
  dangerText: "#7A2020",
  /** `destructive` at the alphas the design uses for danger-card borders (`${D}40` / `${D}30`). */
  dangerBorder: "rgba(192,57,43,0.25)",
  dangerBorderSoft: "rgba(192,57,43,0.19)",
  /** Score band fills behind the trend chart: at or above 24, and 18-24. */
  bandNormal: "#F0FDF4",
  bandCaution: "#FFFBEB",
} as const;

/**
 * Facility type badges in the counselling centre list.
 *
 * Keyed by the `facility_type` enum api-spec 12.1 defines — `hospital`,
 * `dementia_center`, `public_health_center` — not by the Korean labels the
 * Figma prototype hardcoded. The colours are the design's: hospitals blue,
 * dementia centres green, health centres muted.
 */
export const facilityTypes: Record<
  string,
  { label: string; color: string; background: string }
> = {
  hospital: { label: "병원", color: "#3465A8", background: "#EBF2FB" },
  dementia_center: { label: "치매안심센터", color: "#2D7A40", background: "#E8F5EC" },
  public_health_center: { label: "보건소", color: "#697268", background: "#F0EDE6" },
};

/** An unrecognised `facility_type` still gets a readable badge rather than none. */
export const facilityTypeFallback = {
  label: "기관",
  color: colors.mutedForeground,
  background: colors.muted,
};

/**
 * White at the alphas used on top of a coloured header band.
 *
 * Full white is the header title; everything secondary steps down from it. Kept
 * as tokens because both `ScreenHeader` and the screens that add their own
 * header controls need the same values.
 */
export const onHeader = {
  /** Back links and header actions. */
  action: "rgba(255,255,255,0.65)",
  /** Subtitles and eyebrows. */
  muted: "rgba(255,255,255,0.6)",
  /** Fill behind a round header button, e.g. the settings gear. */
  surface: "rgba(255,255,255,0.18)",
} as const;

/**
 * Colours owned by third parties.
 *
 * Kept apart from the palette because they are not ours to restyle — they are
 * only correct on a control that hands off to that service.
 */
export const brandColors = {
  /** Naver's green, on the "네이버 지도" button in the counselling centre list. */
  naverGreen: "#03C75A",
} as const;

/**
 * 시·도 codes for the centre search.
 *
 * `GET /counseling/centers` requires `province_code` and the spec has no
 * endpoint that lists regions, so the 시·도 level is this fixed table of
 * standard 2-digit administrative codes. 시·군·구 is *not* listed here — it is
 * derived from the `district_code`/`district_name` on the centres that come
 * back, so it can never drift from what the server actually holds.
 */
export const provinces = [
  { code: "11", name: "서울특별시" },
  { code: "26", name: "부산광역시" },
  { code: "27", name: "대구광역시" },
  { code: "28", name: "인천광역시" },
  { code: "29", name: "광주광역시" },
  { code: "30", name: "대전광역시" },
  { code: "31", name: "울산광역시" },
  { code: "36", name: "세종특별자치시" },
  { code: "41", name: "경기도" },
  { code: "43", name: "충청북도" },
  { code: "44", name: "충청남도" },
  { code: "45", name: "전북특별자치도" },
  { code: "46", name: "전라남도" },
  { code: "47", name: "경상북도" },
  { code: "48", name: "경상남도" },
  { code: "50", name: "제주특별자치도" },
  { code: "51", name: "강원특별자치도" },
] as const;

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

/* ------------------------------------------------- display settings hook-in */

/**
 * Dark surfaces, applied over `colors` in place.
 *
 * Only surfaces and text invert; the sage/blue/tan accents stay, because the
 * headers and buttons built on them already carry white text. Every override
 * keeps the role of the token it replaces (e.g. `primaryDark` is "text on a
 * light-sage surface", so in dark mode it becomes a light sage).
 */
const darkColors = {
  background: "#191C1A",
  foreground: "#E8ECE9",
  card: "#232725",
  cardForeground: "#E8ECE9",
  secondary: "#253B2C",
  secondaryForeground: "#A9CFB4",
  primaryDark: "#A9CFB4",
  muted: "#2A2E2C",
  mutedForeground: "#9BA69E",
  accentLight: "#3B3223",
  destructiveLight: "#3B2523",
  successLight: "#22382A",
  warningLight: "#3A3222",
  border: "rgba(255,255,255,0.14)",
  inputBackground: "#262A28",
  switchBackground: "#4A504C",
  screenBackground: "#141715",
} as const;

const darkGuardian = {
  blueLight: "#20304A",
  blueDark: "#9FC2EC",
  dangerText: "#EDAFA6",
  bandNormal: "#1C2B21",
  bandCaution: "#332E1D",
} as const;

/** Multipliers behind the 보통/크게/매우 크게 choice on the settings screen. */
const FONT_SCALES = { normal: 1, large: 1.15, xlarge: 1.3 } as const;

let darkApplied = false;

/**
 * Mutates the exported tokens to match the stored display settings.
 *
 * MUST run before any screen module is imported: screens call
 * `StyleSheet.create` at import time and capture token values then, which is
 * why `src/Boot.tsx` requires `App` only after this has run — and why a change
 * from the settings screen applies on the next launch.
 */
export function applyDisplaySettings(settings: {
  darkMode: boolean;
  fontScale: keyof typeof FONT_SCALES;
}): void {
  if (settings.darkMode) {
    Object.assign(colors, darkColors);
    Object.assign(guardian, darkGuardian);
    darkApplied = true;
  }
  const scale = FONT_SCALES[settings.fontScale] ?? 1;
  if (scale !== 1) {
    for (const key of Object.keys(fontSize) as (keyof typeof fontSize)[]) {
      (fontSize as Record<string, number>)[key] = Math.round(fontSize[key] * scale);
    }
  }
}

/** Whether the dark palette is active this launch — drives the status bar. */
export function isDarkApplied(): boolean {
  return darkApplied;
}
