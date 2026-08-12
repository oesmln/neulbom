import React from "react";
import { useNavigation } from "@react-navigation/native";

import { guardian } from "@/theme";
import AppSettingsView from "@/components/AppSettingsView";

/** 앱 설정 (보호자) — the shared view in the guardian blue palette. */
export default function GuardianAppSettingsScreen() {
  const navigation = useNavigation();

  return (
    <AppSettingsView
      palette={{
        accent: guardian.blue,
        accentLight: guardian.blueLight,
        accentDark: guardian.blueDark,
      }}
      onBack={() => navigation.goBack()}
      backLabel="홈"
    />
  );
}
