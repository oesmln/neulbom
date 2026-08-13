import React from "react";
import { useNavigation } from "@react-navigation/native";

import { colors } from "@/theme";
import AppSettingsView from "@/components/AppSettingsView";

/** 앱 설정 (고령자) — the shared view in the sage palette. */
export default function ElderAppSettingsScreen() {
  const navigation = useNavigation();

  return (
    <AppSettingsView
      palette={{
        accent: colors.primary,
        accentLight: colors.secondary,
        accentDark: colors.primaryDark,
      }}
      onBack={() => navigation.goBack()}
      backLabel="마이페이지"
    />
  );
}
