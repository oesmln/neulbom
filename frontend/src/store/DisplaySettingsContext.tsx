import React from "react";

import {
  currentDisplaySettings,
  saveDisplaySettings,
  type DisplaySettings,
} from "@/store/settings";
import { applyDisplaySettings } from "@/theme";
import { refreshRuntimeStyles } from "@/theme/runtimeStyles";

type DisplaySettingsState = {
  settings: DisplaySettings;
  updateSettings: (next: DisplaySettings) => Promise<void>;
};

const DisplaySettingsContext = React.createContext<DisplaySettingsState | null>(null);

export function DisplaySettingsProvider({ children }: { children: React.ReactNode }) {
  const [settings, setSettings] = React.useState(currentDisplaySettings);

  const updateSettings = React.useCallback(async (next: DisplaySettings) => {
    applyDisplaySettings(next);
    refreshRuntimeStyles(next);
    setSettings(next);
    await saveDisplaySettings(next);
  }, []);

  const value = React.useMemo(() => ({ settings, updateSettings }), [settings, updateSettings]);
  return <DisplaySettingsContext.Provider value={value}>{children}</DisplaySettingsContext.Provider>;
}

export function useDisplaySettings(): DisplaySettingsState {
  const context = React.useContext(DisplaySettingsContext);
  if (!context) throw new Error("useDisplaySettings must be used within DisplaySettingsProvider");
  return context;
}
