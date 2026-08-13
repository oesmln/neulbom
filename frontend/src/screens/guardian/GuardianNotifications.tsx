import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";

import { useApp } from "@/store/AppContext";
import { notifications as notificationsApi } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { notificationTime } from "@/utils/format";
import type { NotificationResponse, Uuid } from "@/api/types";
import { colors, guardian, onHeader, spacing, fontSize, fontWeight } from "@/theme";
import {
  Screen,
  ScreenHeader,
  Card,
  Badge,
  Body,
  Caption,
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/components/ui";

/**
 * Guardian alerts from `GET /notifications/{user_id}`.
 *
 * The badge comes from the notification `severity` the server sends — the app
 * does not decide what counts as a warning.
 */
const SEVERITY_BADGE: Record<string, { label: string; color: string; background: string }> = {
  critical: { label: "위험", color: colors.destructive, background: colors.destructiveLight },
  warning: { label: "주의", color: colors.warning, background: colors.accentLight },
  info: { label: "완료", color: guardian.blue, background: guardian.blueLight },
};

function badgeFor(item: NotificationResponse) {
  return SEVERITY_BADGE[item.severity ?? "info"] ?? SEVERITY_BADGE.info;
}

export default function GuardianNotificationsScreen() {
  const { userId, role } = useApp();
  const [items, setItems] = React.useState<NotificationResponse[]>([]);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const { data, error, loading, reload } = useApi(
    () => notificationsApi.list(userId as string, role ?? "guardian"),
    [userId, role],
    { enabled: !!userId },
  );

  React.useEffect(() => {
    if (data) setItems(data.notifications);
  }, [data]);

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

  const markAllRead = async () => {
    if (!items.some((item) => !item.is_read)) return;
    const previous = items;
    setActionError(null);
    setItems((prev) => prev.map((n) => ({ ...n, is_read: true })));
    try {
      await notificationsApi.markAllRead(role ?? "guardian");
    } catch (cause) {
      setItems(previous);
      setActionError(apiErrorMessage(cause));
    }
  };

  const hasUnread = items.some((n) => !n.is_read);

  const header = (
    <ScreenHeader
      color={guardian.blue}
      title="알림"
      right={
        hasUnread ? (
          <Pressable
            onPress={() => void markAllRead()}
            accessibilityRole="button"
            accessibilityLabel="모든 알림 읽음 처리"
            hitSlop={10}
          >
            <Text style={styles.headerAction}>모두 읽음</Text>
          </Pressable>
        ) : undefined
      }
    />
  );

  return (
    <Screen header={header}>
      {loading && items.length === 0 ? <LoadingState /> : null}

      {error && items.length === 0 ? (
        <ErrorState message={apiErrorMessage(error)} onRetry={reload} />
      ) : null}

      {actionError ? <Text style={styles.actionError}>{actionError}</Text> : null}

      {!loading && !error && items.length === 0 ? (
        <EmptyState message="새로운 알림이 없어요" icon="notifications-outline" />
      ) : null}

      <View style={{ gap: spacing.md }}>
        {items.map((n) => {
          const badge = badgeFor(n);
          const unread = !n.is_read;
          return (
            <Pressable
              key={n.notification_id}
              onPress={() => void markRead(n.notification_id)}
              accessibilityRole="button"
              accessibilityLabel={`${n.title}. ${n.body}`}
            >
              <Card style={unread ? styles.unreadCard : undefined}>
                <View style={styles.titleRow}>
                  <View style={styles.titleGroup}>
                    <Text style={[styles.title, unread && { fontWeight: fontWeight.bold }]}>
                      {n.title}
                    </Text>
                    <Badge
                      label={badge.label}
                      color={badge.color}
                      background={badge.background}
                    />
                  </View>
                  {unread ? <View style={styles.unreadDot} /> : null}
                </View>
                <Body style={{ color: colors.mutedForeground, marginTop: 2 }}>{n.body}</Body>
                <Caption style={styles.time}>{notificationTime(n.created_at)}</Caption>
              </Card>
            </Pressable>
          );
        })}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerAction: { fontSize: fontSize.caption, color: onHeader.action },
  actionError: {
    marginBottom: spacing.md,
    borderRadius: 10,
    backgroundColor: colors.destructiveLight,
    padding: spacing.md,
    fontSize: fontSize.caption,
    color: colors.destructive,
    textAlign: "center",
  },
  unreadCard: { borderColor: guardian.blue },
  titleRow: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: spacing.sm },
  titleGroup: { flexDirection: "row", alignItems: "center", flexWrap: "wrap", gap: spacing.sm, flex: 1 },
  title: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold, color: colors.foreground },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.destructive,
    marginTop: 6,
  },
  time: { textAlign: "right", marginTop: spacing.sm },
});
