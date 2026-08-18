import React from "react";

import AppSettingsView from "@/components/AppSettingsView";
import GuardianNotificationButton from "@/components/GuardianNotificationButton";
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
      headerRight={<GuardianNotificationButton />}
      footer={<GuardianAccountActions />}
    />
  );
}
