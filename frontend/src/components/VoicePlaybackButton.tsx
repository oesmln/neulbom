import React from "react";
import { ActivityIndicator, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";
import { SentenceText as Text } from "@/components/ui";

export default function VoicePlaybackButton({
  enabled,
  loading,
  speaking,
  onPress,
  onReplay,
  compact = false,
}: {
  enabled: boolean;
  loading: boolean;
  speaking: boolean;
  onPress: () => void;
  onReplay?: () => void;
  compact?: boolean;
}) {
  const label = enabled ? "음성 켜짐" : "음성 꺼짐";
  return (
    <Pressable
      onPress={onPress}
      onLongPress={enabled ? onReplay : undefined}
      accessibilityRole="switch"
      accessibilityLabel={`${label}. 눌러서 ${enabled ? "끄기" : "켜기"}`}
      accessibilityState={{ checked: enabled }}
      style={({ pressed }) => [
        styles.button,
        compact && styles.compact,
        enabled ? styles.enabled : styles.disabled,
        pressed && { opacity: 0.82 },
      ]}
    >
      {loading ? (
        <ActivityIndicator size="small" color={enabled ? colors.white : colors.mutedForeground} />
      ) : (
        <Ionicons
          name={enabled ? (speaking ? "volume-high" : "volume-medium") : "volume-mute"}
          size={compact ? 17 : 20}
          color={enabled ? colors.white : colors.mutedForeground}
        />
      )}
      <Text style={[styles.label, enabled ? styles.enabledLabel : styles.disabledLabel]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 44,
    paddingHorizontal: spacing.md,
    borderRadius: radius.pill,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.xs,
    borderWidth: 1,
  },
  compact: { minHeight: 38, paddingHorizontal: spacing.sm },
  enabled: { backgroundColor: colors.primary, borderColor: colors.primary },
  disabled: { backgroundColor: colors.muted, borderColor: colors.border },
  label: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold },
  enabledLabel: { color: colors.white },
  disabledLabel: { color: colors.mutedForeground },
});
