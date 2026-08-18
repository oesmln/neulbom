import React from "react";
import { View, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors, spacing, fontSize, fontWeight } from "@/theme";
import { Card, SentenceText as Text } from "@/components/ui";

type AccountActionsViewProps = {
  onPasswordChange: () => void;
  onLogout: () => void;
  onWithdraw: () => void;
};

/** The account actions shared by the elder and guardian settings experiences. */
export default function AccountActionsView({
  onPasswordChange,
  onLogout,
  onWithdraw,
}: AccountActionsViewProps) {
  return (
    <Card style={styles.card}>
      <Text style={styles.title}>계정</Text>
      <AccountActionRow
        icon="lock-closed-outline"
        label="비밀번호 변경"
        onPress={onPasswordChange}
      />
      <AccountActionRow icon="log-out-outline" label="로그아웃" onPress={onLogout} />
      <AccountActionRow
        icon="trash-outline"
        label="계정 탈퇴"
        tone={colors.destructive}
        onPress={onWithdraw}
      />
    </Card>
  );
}

function AccountActionRow({
  icon,
  label,
  tone = colors.foreground,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  tone?: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => [styles.row, { opacity: pressed ? 0.6 : 1 }]}
    >
      <View style={styles.left}>
        <Ionicons
          name={icon}
          size={16}
          color={tone === colors.destructive ? tone : colors.mutedForeground}
        />
        <Text style={[styles.label, { color: tone }]}>{label}</Text>
      </View>
      <Ionicons name="chevron-forward" size={16} color={colors.mutedForeground} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { padding: 0, overflow: "hidden", marginBottom: spacing.lg },
  title: {
    fontSize: fontSize.badge,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
    letterSpacing: 0.6,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.lg,
    marginBottom: spacing.sm,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.lg,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  left: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  label: { fontSize: fontSize.bodyLg - 1 },
});
