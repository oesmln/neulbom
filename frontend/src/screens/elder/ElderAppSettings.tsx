import React from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import type { ElderMyPageNav } from "@/navigation/types";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";
import AppSettingsView from "@/components/AppSettingsView";
import { SentenceText as Text } from "@/components/ui";

/** 앱 설정 (고령자) — the shared view in the sage palette. */
export default function ElderAppSettingsScreen() {
  const navigation = useNavigation<ElderMyPageNav>();

  return (
    <AppSettingsView
      palette={() => ({
        accent: colors.primary,
        accentLight: colors.secondary,
        accentDark: colors.primaryDark,
      })}
      onBack={() => navigation.goBack()}
      backLabel="마이페이지"
      footer={
        <Pressable
          onPress={() => navigation.navigate("ElderGuardianInvite")}
          accessibilityRole="button"
          accessibilityLabel="초대코드로 보호자 연결"
          style={styles.connectionCard}
        >
          <Ionicons name="people-outline" size={26} color={colors.primary} />
          <View style={styles.connectionCopy}>
            <Text style={styles.connectionTitle}>보호자 연결</Text>
            <Text style={styles.connectionDescription}>
              초대코드를 입력해 보호자를 연결하세요.
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color={colors.mutedForeground} />
        </Pressable>
      }
    />
  );
}

const styles = StyleSheet.create({
  connectionCard: {
    minHeight: 88,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    padding: spacing.lg,
    marginBottom: spacing.lg,
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
  },
  connectionCopy: { flex: 1, gap: spacing.xs },
  connectionTitle: {
    color: colors.foreground,
    fontSize: fontSize.bodyLg,
    fontWeight: fontWeight.semibold,
  },
  connectionDescription: {
    color: colors.mutedForeground,
    fontSize: fontSize.body,
    lineHeight: 22,
  },
});
