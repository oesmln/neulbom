import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

/**
 * Display settings — dark mode and text scale.
 *
 * These are device-local: `PATCH /users/{id}/preferences` has no field for
 * either, so they live next to the auth session in SecureStore rather than on
 * the server. They are read once at boot (see `src/Boot.tsx`) because every
 * registered styles are refreshed by `DisplaySettingsContext` whenever these
 * settings change, and the same values are loaded before the first screen on
 * the next launch.
 */
export type FontScaleKey = "normal" | "large" | "xlarge";

export interface DisplaySettings {
  darkMode: boolean;
  fontScale: FontScaleKey;
}

export const DEFAULT_DISPLAY_SETTINGS: DisplaySettings = {
  darkMode: false,
  fontScale: "normal",
};

const KEY = "neulbom.display.settings";

let cached: DisplaySettings = DEFAULT_DISPLAY_SETTINGS;

const secureStoreAvailable = Platform.OS !== "web";

function normalizeDisplaySettings(value: Partial<DisplaySettings>): DisplaySettings {
  return {
    darkMode: typeof value.darkMode === "boolean" ? value.darkMode : DEFAULT_DISPLAY_SETTINGS.darkMode,
    fontScale: ["normal", "large", "xlarge"].includes(value.fontScale ?? "")
      ? (value.fontScale as FontScaleKey)
      : DEFAULT_DISPLAY_SETTINGS.fontScale,
  };
}

function loadWebSettings(): DisplaySettings {
  try {
    const raw = globalThis.localStorage?.getItem(KEY);
    if (raw) return normalizeDisplaySettings(JSON.parse(raw) as Partial<DisplaySettings>);
  } catch {
    // Private browsing or a corrupt value falls back to the defaults.
  }
  return cached;
}

export async function loadDisplaySettings(): Promise<DisplaySettings> {
  if (!secureStoreAvailable) {
    cached = loadWebSettings();
    return cached;
  }
  try {
    const raw = await SecureStore.getItemAsync(KEY);
    if (raw) {
      // Merge over the defaults so a settings file written by an older build
      // (missing a newer field) still produces a complete object.
      cached = normalizeDisplaySettings(JSON.parse(raw) as Partial<DisplaySettings>);
    }
  } catch {
    // A corrupt entry falls back to the defaults — not worth crashing over.
  }
  return cached;
}

export async function saveDisplaySettings(next: DisplaySettings): Promise<void> {
  cached = normalizeDisplaySettings(next);
  if (!secureStoreAvailable) {
    try {
      globalThis.localStorage?.setItem(KEY, JSON.stringify(cached));
    } catch {
      // Keep the in-memory copy when browser storage is unavailable.
    }
    return;
  }
  try {
    await SecureStore.setItemAsync(KEY, JSON.stringify(cached));
  } catch {
    // Keep the in-memory copy so the settings screen still reflects the choice.
  }
}

/** Synchronous read — valid after `loadDisplaySettings()` ran at boot. */
export function currentDisplaySettings(): DisplaySettings {
  return cached;
}
