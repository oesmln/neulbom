import React from "react";

import AppSettingsView from "@/components/AppSettingsView";
import GuardianNotificationButton from "@/components/GuardianNotificationButton";
import { guardian } from "@/theme";

/** Settings tab has no stack back action; the stack route keeps the back link. */
export default function GuardianSettingsScreen() {
  return (
    <AppSettingsView
      palette={{
        accent: guardian.blue,
        accentLight: guardian.blueLight,
        accentDark: guardian.blueDark,
      }}
      headerRight={<GuardianNotificationButton />}
    />
  );
}
