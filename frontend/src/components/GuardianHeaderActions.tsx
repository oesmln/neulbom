import React from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useNavigation } from "@react-navigation/native";

import GuardianNotificationButton from "@/components/GuardianNotificationButton";
import type { GuardianNav } from "@/navigation/types";
import { colors, onHeader, spacing } from "@/theme";

/** Shared actions for every top-level guardian screen. */
export default function GuardianHeaderActions() {
  const navigation = useNavigation<GuardianNav>();

  return (
    <View style={styles.actions}>
      <Pressable
        onPress={() => navigation.navigate("GuardianConnections")}
        accessibilityRole="button"
        accessibilityLabel="사용자 관리"
        hitSlop={8}
        style={styles.button}
      >
        <Ionicons name="people-outline" size={20} color={colors.white} />
      </Pressable>
      <GuardianNotificationButton />
    </View>
  );
}

const styles = StyleSheet.create({
  actions: {
    alignSelf: "flex-start",
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  button: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: onHeader.surface,
    alignItems: "center",
    justifyContent: "center",
  },
});
