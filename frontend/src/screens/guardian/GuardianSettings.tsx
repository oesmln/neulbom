import React from "react";

import AppSettingsView from "@/components/AppSettingsView";
import GuardianHeaderActions from "@/components/GuardianHeaderActions";
import GuardianAccountActions from "@/screens/guardian/GuardianAccountActions";
import { guardian } from "@/theme";

export default function GuardianSettingsScreen() {
  return (
    <AppSettingsView
      palette={() => ({
        accent: guardian.blue,
        accentLight: guardian.blueLight,
        accentDark: guardian.blueDark,
      })}
      headerRight={<GuardianHeaderActions />}
      footer={<GuardianAccountActions />}
    />
  );
}
