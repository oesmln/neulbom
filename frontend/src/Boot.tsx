import React from "react";

import { loadDisplaySettings } from "@/store/settings";
import { applyDisplaySettings } from "@/theme";
import { installRuntimeStyles } from "@/theme/runtimeStyles";

/**
 * Loads the stored display settings and only then requires the app.
 *
 * The order matters: every screen calls `StyleSheet.create` with theme tokens
 * at import time, so dark mode and the text scale can only take effect if the
 * tokens are mutated before `App` (and the screens behind it) are evaluated.
 * Runtime changes refresh the registered styles in place; this boot path only
 * guarantees that a persisted choice is already active on the first frame.
 * The inline `require` below is what delays screen evaluation.
 */
export default function Boot() {
  const [App, setApp] = React.useState<React.ComponentType | null>(null);

  React.useEffect(() => {
    let mounted = true;
    void loadDisplaySettings().then((settings) => {
      applyDisplaySettings(settings);
      installRuntimeStyles(settings);
      const mod = require("./App") as { default: React.ComponentType };
      if (mounted) setApp(() => mod.default);
    });
    return () => {
      mounted = false;
    };
  }, []);

  // One frame of nothing while SecureStore answers — faster than any splash.
  if (!App) return null;
  return <App />;
}
