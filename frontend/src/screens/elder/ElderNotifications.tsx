import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { useNavigation } from "@react-navigation/native";

import { ElderNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { notifications as notificationsApi } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { notificationTime } from "@/utils/format";
import type { NotificationResponse, Uuid } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { EmptyState, ErrorState, LoadingState, Screen, ScreenHeader } from "@/components/ui";

/**
 * `GET /notifications/{user_id}`, with `PATCH /{id}/read` and
 * `PATCH /read-all` for the two actions.
 *
 * Read state is applied locally the moment it is tapped and then confirmed by
 * the server — an elder tapping a card should not wait on a round trip to see
 * it stop being highlighted.
 */
export default function ElderNotificationsScreen() {
  const navigation = useNavigation<ElderNav>();
  const { userId, role } = useApp();
  const [items, setItems] = React.useState<NotificationResponse[]>([]);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const { data, error, loading, reload } = useApi(
    () => notificationsApi.list(userId as string, role ?? "elder"),
    [userId, role],
    { enabled: !!userId },
  );

  React.useEffect(() => {
    if (data) setItems(data.notifications);
  }, [data]);

  const hasUnread = items.some((n) => !n.is_read);

  const markAllRead = async () => {
    const previous = items;
    setActionError(null);
    setItems((prev) => prev.map((n) => ({ ...n, is_read: true })));
    try {
      await notificationsApi.markAllRead(role ?? "elder");
    } catch (cause) {
      setItems(previous);
      setActionError(apiErrorMessage(cause));
    }
  };

  const markRead = async (id: Uuid) => {
    const previous = items;
    setActionError(null);
    setItems((prev) => prev.map((n) => (n.notification_id === id ? { ...n, is_read: true } : n)));
    try {
      await notificationsApi.markRead(id);
    } catch (cause) {
      setItems(previous);
      setActionError(apiErrorMessage(cause));
    }
  };

  return (
    <Screen
      contentStyle={{ gap: spacing.md }}
      header={
        <ScreenHeader
          title="알림"
          subtitle="새로운 소식을 확인하세요"
          onBack={() => navigation.goBack()}
          backLabel="홈"
          right={
            hasUnread ? (
              <Pressable
                onPress={() => void markAllRead()}
                accessibilityRole="button"
                accessibilityLabel="알림 모두 읽음 처리"
                style={styles.markAll}
              >
                <Text style={styles.markAllLabel}>모두 읽음</Text>
              </Pressable>
            ) : undefined
          }
        />
      }
    >
      {loading && items.length === 0 ? <LoadingState /> : null}

      {error && items.length === 0 ? (
        <ErrorState message={apiErrorMessage(error)} onRetry={reload} />
      ) : null}

      {actionError ? <Text style={styles.actionError}>{actionError}</Text> : null}

      {!loading && !error && items.length === 0 ? (
        <EmptyState message="새로운 알림이 없어요" icon="notifications-outline" />
      ) : null}

      {items.map((n) => (
        <Pressable
          key={n.notification_id}
          onPress={() => void markRead(n.notification_id)}
          accessibilityRole="button"
          accessibilityLabel={`${n.title}. ${n.body}`}
          style={({ pressed }) => [
            styles.item,
            {
              backgroundColor: n.is_read ? colors.white : colors.secondary,
              borderColor: n.is_read ? colors.border : `${colors.primary}55`,
              opacity: pressed ? 0.9 : 1,
            },
          ]}
        >
          <View style={styles.titleRow}>
            <Text
              style={[styles.title, { fontWeight: n.is_read ? fontWeight.semibold : fontWeight.bold }]}
            >
              {n.title}
            </Text>
            {n.is_read ? null : <View style={styles.dot} />}
          </View>
          <Text style={styles.body}>{n.body}</Text>
          <Text style={styles.time}>{notificationTime(n.created_at)}</Text>
        </Pressable>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  markAll: {
    // 44dp minimum touch target — the elder screens are the reason for the rule.
    height: 44,
    paddingHorizontal: 14,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.2)",
  },
  markAllLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.white },
  actionError: {
    borderRadius: radius.md,
    backgroundColor: colors.destructiveLight,
    padding: spacing.md,
    fontSize: fontSize.caption,
    color: colors.destructive,
    textAlign: "center",
  },

  item: { borderRadius: radius.xl, borderWidth: 1, padding: spacing.lg },
  titleRow: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, marginBottom: 4 },
  title: { flex: 1, fontSize: fontSize.bodyLg, color: colors.foreground, lineHeight: 22 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.destructive, marginTop: 6 },
  body: { fontSize: fontSize.caption, color: colors.mutedForeground, lineHeight: 21 },
  time: { fontSize: fontSize.badge, color: colors.mutedForeground, marginTop: spacing.sm, textAlign: "right" },
});
