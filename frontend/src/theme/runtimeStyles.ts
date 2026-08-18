import { StyleSheet } from "react-native";

import type { DisplaySettings } from "@/store/settings";
import { displayTokenSnapshot } from "@/theme";

type StyleMap = Record<string, Record<string, unknown>>;
type RegisteredStyleMap = Record<string, unknown>;

const defaultSettings: DisplaySettings = { darkMode: false, fontScale: "normal" };
const originalCreate = StyleSheet.create.bind(StyleSheet) as (styles: StyleMap) => RegisteredStyleMap;
const registered: { base: StyleMap; live: RegisteredStyleMap }[] = [];

let installed = false;
let currentSettings = defaultSettings;

function tokenKeyFor(value: string, property: string, source: Record<string, string>): string | null {
  const candidates = Object.keys(source).filter((key) => source[key] === value);
  if (candidates.length === 0) return null;
  if (candidates.length === 1) return candidates[0];
  if (property === "color") {
    return candidates.find((key) => key === "foreground" || key.endsWith("Foreground") || key === "white")
      ?? candidates[0];
  }
  if (property === "backgroundColor") {
    return candidates.find((key) => key === "card")
      ?? candidates.find((key) => key === "background")
      ?? candidates[0];
  }
  if (property === "borderColor") return candidates.find((key) => key === "border") ?? candidates[0];
  return candidates[0];
}

function transformSheet(sheet: StyleMap, from: DisplaySettings, to: DisplaySettings): StyleMap {
  const source = displayTokenSnapshot(from);
  const target = displayTokenSnapshot(to);
  const sourceTokens = { ...source.colors, ...source.guardian } as Record<string, string>;
  const targetTokens = { ...target.colors, ...target.guardian } as Record<string, string>;

  return Object.fromEntries(
    Object.entries(sheet).map(([styleName, style]) => [
      styleName,
      Object.fromEntries(
        Object.entries(style).map(([property, value]) => {
          if ((property === "fontSize" || property === "lineHeight") && typeof value === "number") {
            return [property, Math.round((value / source.fontScale) * target.fontScale)];
          }
          if (typeof value === "string") {
            const tokenKey = tokenKeyFor(value, property, sourceTokens);
            if (tokenKey && targetTokens[tokenKey]) return [property, targetTokens[tokenKey]];
          }
          return [property, value];
        }),
      ),
    ]),
  );
}

export function installRuntimeStyles(initialSettings: DisplaySettings): void {
  if (installed) return;
  installed = true;
  currentSettings = initialSettings;

  (StyleSheet as unknown as { create: (styles: StyleMap) => RegisteredStyleMap }).create = (
    styles: StyleMap,
  ) => {
    const base = transformSheet(styles, initialSettings, defaultSettings);
    const live = originalCreate(transformSheet(base, defaultSettings, currentSettings));
    registered.push({ base, live });
    return live;
  };
}

export function refreshRuntimeStyles(nextSettings: DisplaySettings): void {
  currentSettings = nextSettings;
  for (const entry of registered) {
    const refreshed = originalCreate(transformSheet(entry.base, defaultSettings, nextSettings));
    for (const key of Object.keys(entry.live)) delete entry.live[key];
    Object.assign(entry.live, refreshed);
  }
}
