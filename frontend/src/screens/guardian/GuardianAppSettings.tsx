import React from "react";
import { useNavigation } from "@react-navigation/native";
import { guardian } from "@/theme";
import AppSettingsView from "@/components/AppSettingsView";
import GuardianNotificationButton from "@/components/GuardianNotificationButton";
import GuardianAccountActions from "@/screens/guardian/GuardianAccountActions";
import type { GuardianNav } from "@/navigation/types";

/** 앱 설정 (보호자) — the shared view in the guardian blue palette. */
export default function GuardianAppSettingsScreen() {
  const navigation = useNavigation<GuardianNav>();

  return (
    <AppSettingsView
      palette={() => ({
        accent: guardian.blue,
        accentLight: guardian.blueLight,
        accentDark: guardian.blueDark,
      })}
      onBack={() => navigation.goBack()}
      backLabel="홈"
      headerRight={<GuardianNotificationButton />}
      footer={<GuardianAccountActions />}
    />
  );
}
