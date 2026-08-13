import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

/**
 * Display settings — dark mode and text scale.
 *
 * These are device-local: `PATCH /users/{id}/preferences` has no field for
 * either, so they live next to the auth session in SecureStore rather than on
 * the server. They are read once at boot (see `src/Boot.tsx`) because every
 * screen builds its `StyleSheet` from the theme tokens at import time — which
 * is also why a change applies on the next launch, exactly what the settings
 * screen tells the user.
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

export async function loadDisplaySettings(): Promise<DisplaySettings> {
  if (!secureStoreAvailable) return cached;
  try {
    const raw = await SecureStore.getItemAsync(KEY);
    if (raw) {
      // Merge over the defaults so a settings file written by an older build
      // (missing a newer field) still produces a complete object.
      cached = { ...DEFAULT_DISPLAY_SETTINGS, ...(JSON.parse(raw) as Partial<DisplaySettings>) };
    }
  } catch {
    // A corrupt entry falls back to the defaults — not worth crashing over.
  }
  return cached;
}

export async function saveDisplaySettings(next: DisplaySettings): Promise<void> {
  cached = next;
  if (!secureStoreAvailable) return;
  try {
    await SecureStore.setItemAsync(KEY, JSON.stringify(next));
  } catch {
    // Keep the in-memory copy so the settings screen still reflects the choice.
  }
}

/** Synchronous read — valid after `loadDisplaySettings()` ran at boot. */
export function currentDisplaySettings(): DisplaySettings {
  return cached;
}
