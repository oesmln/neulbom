import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { notifications as notificationsApi } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { notificationTime } from "@/utils/format";
import type { NotificationResponse, Uuid } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, Card, Body, Caption, EmptyState, ErrorState, LoadingState } from "@/components/ui";

/**
 * Guardian alerts from `GET /notifications/{user_id}`.
 *
 * The icon and colour come from the notification `type` and `severity` the
 * server sends — the app does not decide what counts as a warning.
 */
const TYPE_ICONS: Record<string, { icon: keyof typeof Ionicons.glyphMap; color: string }> = {
  score_drop: { icon: "warning", color: colors.destructive },
  screening_completed: { icon: "checkmark-circle", color: colors.primary },
  diary_created: { icon: "book", color: "#6B9CB8" },
  diary_generated: { icon: "book", color: "#6B9CB8" },
  guardian_reaction: { icon: "heart", color: colors.destructive },
  appointment_reminder: { icon: "calendar", color: colors.accent },
  weekly_report: { icon: "document-text", color: colors.primary },
};

function iconFor(item: NotificationResponse) {
  return TYPE_ICONS[item.type] ?? { icon: "notifications" as const, color: colors.primary };
}

export default function GuardianNotificationsScreen() {
  const { userId, role } = useApp();
  const [items, setItems] = React.useState<NotificationResponse[]>([]);

  const { data, error, loading, reload } = useApi(
    () => notificationsApi.list(userId as string, role ?? "guardian"),
    [userId, role],
    { enabled: !!userId },
  );

  React.useEffect(() => {
    if (data) setItems(data.notifications);
  }, [data]);

  const markRead = (id: Uuid) => {
    setItems((prev) => prev.map((n) => (n.notification_id === id ? { ...n, is_read: true } : n)));
    void notificationsApi.markRead(id).catch(reload);
  };

  return (
    <Screen>
      <Text style={styles.h1}>알림</Text>

      {loading && items.length === 0 ? <LoadingState /> : null}

      {error && items.length === 0 ? (
        <ErrorState message={apiErrorMessage(error)} onRetry={reload} />
      ) : null}

      {!loading && !error && items.length === 0 ? (
        <EmptyState message="새로운 알림이 없어요" icon="notifications-outline" />
      ) : null}

      <View style={{ gap: spacing.md, marginTop: spacing.lg }}>
        {items.map((n) => {
          const visual = iconFor(n);
          const warn = n.severity === "warning" || n.severity === "critical";
          return (
            <Pressable
              key={n.notification_id}
              onPress={() => markRead(n.notification_id)}
              accessibilityRole="button"
              accessibilityLabel={`${n.title}. ${n.body}`}
            >
              <Card style={warn && !n.is_read ? styles.warn : undefined}>
                <View style={styles.row}>
                  <View style={[styles.iconWrap, { backgroundColor: visual.color }]}>
                    <Ionicons name={visual.icon} size={22} color={colors.white} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.title}>{n.title}</Text>
                    <Body style={{ color: colors.mutedForeground, marginTop: 2 }}>{n.body}</Body>
                    <Caption style={{ marginTop: spacing.sm }}>
                      {notificationTime(n.created_at)}
                    </Caption>
                  </View>
                </View>
              </Card>
            </Pressable>
          );
        })}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.foreground },
  warn: { borderColor: colors.destructive, backgroundColor: "#FBEBE9" },
  row: { flexDirection: "row", alignItems: "flex-start", gap: spacing.md },
  iconWrap: { width: 44, height: 44, borderRadius: radius.md, alignItems: "center", justifyContent: "center" },
  title: { fontSize: fontSize.body, fontWeight: fontWeight.bold, color: colors.foreground },
});
