import React from "react";
import { Pressable, View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useNavigation } from "@react-navigation/native";

import { notifications } from "@/api";
import { useApi } from "@/hooks/useApi";
import type { GuardianNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { colors, guardian } from "@/theme";

/** Shared bell used in guardian headers after alerts leave the bottom bar. */
export default function GuardianNotificationButton() {
  const navigation = useNavigation<GuardianNav>();
  const { userId } = useApp();
  const request = useApi(
    () => notifications.list(userId as string, "guardian"),
    [userId],
    { enabled: !!userId },
  );
  const unread = request.data?.notifications.some((item) => !item.is_read) ?? false;

  return (
    <Pressable
      onPress={() => navigation.navigate("GuardianNotifications")}
      accessibilityRole="button"
      accessibilityLabel={unread ? "읽지 않은 알림" : "알림"}
      hitSlop={10}
      style={styles.button}
    >
      <Ionicons name="notifications-outline" size={20} color={colors.white} />
      {unread ? <View style={styles.dot} /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { width: 28, height: 28, alignItems: "center", justifyContent: "center" },
  dot: {
    position: "absolute",
    top: 2,
    right: 2,
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: colors.destructive,
    borderWidth: 1,
    borderColor: guardian.blue,
  },
});
