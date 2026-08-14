import React from "react";
import { guardian } from "@/theme";
import AppSettingsView from "@/components/AppSettingsView";
import GuardianNotificationButton from "@/components/GuardianNotificationButton";

/** 앱 설정 (보호자) — the shared view in the guardian blue palette. */
export default function GuardianAppSettingsScreen() {
  return (
    <AppSettingsView
      palette={{
        accent: guardian.blue,
        accentLight: guardian.blueLight,
        accentDark: guardian.blueDark,
      }}
      onBack={() => navigation.goBack()}
      backLabel="홈"
      headerRight={<GuardianNotificationButton />}
    />
  );
}
