import React from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useNavigation } from "@react-navigation/native";

import { notifications } from "@/api";
import { useApi } from "@/hooks/useApi";
import { useApp } from "@/store/AppContext";
import type { GuardianNav } from "@/navigation/types";
import { colors, guardian } from "@/theme";

export default function GuardianNotificationButton() {
  const navigation = useNavigation<GuardianNav>();
  const { userId, role } = useApp();
  const result = useApi(
    () => notifications.list(userId as string, role ?? "guardian", { unreadOnly: true, limit: 1 }),
    [userId, role],
    { enabled: !!userId },
  );
  const unread = result.data?.unread_count ?? 0;

  return (
    <Pressable
      onPress={() => navigation.navigate("GuardianNotifications")}
      accessibilityRole="button"
      accessibilityLabel={unread > 0 ? `알림 ${unread}개` : "알림"}
      hitSlop={10}
      style={styles.button}
    >
      <Ionicons name="notifications-outline" size={20} color={colors.white} />
      {unread > 0 ? <View style={styles.dot} /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { width: 28, height: 28, alignItems: "center", justifyContent: "center" },
  dot: {
    position: "absolute",
    top: 1,
    right: 2,
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.destructive,
    borderWidth: 1,
    borderColor: guardian.blue,
  },
});
