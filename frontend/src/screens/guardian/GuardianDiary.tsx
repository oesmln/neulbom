import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { monthDayLabel, moodEmoji } from "@/utils/format";
import type { Uuid } from "@/api/types";
import { colors, spacing, fontSize, fontWeight } from "@/theme";
import { Screen, Card, Body, Caption, EmptyState, ErrorState, LoadingState } from "@/components/ui";

/**
 * The elder's diaries with the guardian's reactions.
 *
 * `GET /diaries/{user_id}` lists them and `POST /diaries/{diary_id}/reactions`
 * records a heart. The count moves as soon as it is tapped and is corrected by
 * a reload if the server refuses.
 */
const HEART = "heart";

export default function GuardianDiaryScreen() {
  const { selectedElderId } = useApp();
  const [liked, setLiked] = React.useState<Record<Uuid, boolean>>({});
  const [bumped, setBumped] = React.useState<Record<Uuid, number>>({});

  const list = useApi(
    () => diariesApi.listForUser(selectedElderId as string, { limit: 20 }),
    [selectedElderId],
    { enabled: !!selectedElderId },
  );

  const react = (diaryId: Uuid) => {
    if (liked[diaryId]) return;
    setLiked((prev) => ({ ...prev, [diaryId]: true }));
    setBumped((prev) => ({ ...prev, [diaryId]: (prev[diaryId] ?? 0) + 1 }));
    void diariesApi.react(diaryId, HEART).catch(() => {
      setLiked((prev) => ({ ...prev, [diaryId]: false }));
      setBumped((prev) => ({ ...prev, [diaryId]: (prev[diaryId] ?? 1) - 1 }));
    });
  };

  if (!selectedElderId) {
    return (
      <Screen>
        <EmptyState message="먼저 대시보드에서 어르신을 선택해 주세요." icon="people-outline" />
      </Screen>
    );
  }

  return (
    <Screen>
      <Text style={styles.h1}>일기 · 반응</Text>
      <Caption style={{ marginTop: 4 }}>어르신의 하루에 따뜻한 반응을 남겨보세요.</Caption>

      {list.error ? (
        <ErrorState
          message={
            list.error.isForbidden
              ? "어르신이 아직 일기 열람에 동의하지 않았어요."
              : apiErrorMessage(list.error)
          }
          onRetry={list.error.isForbidden ? undefined : list.reload}
        />
      ) : null}

      {list.loading && !list.data ? <LoadingState /> : null}

      {list.data && list.data.diaries.length === 0 ? (
        <EmptyState message="아직 등록된 일기가 없어요." />
      ) : null}

      <View style={{ gap: spacing.md, marginTop: spacing.lg }}>
        {(list.data?.diaries ?? []).map((entry) => {
          const count = entry.reaction_count + (bumped[entry.diary_id] ?? 0);
          return (
            <Card key={entry.diary_id}>
              <View style={styles.rowBetween}>
                <View style={styles.dateRow}>
                  <Ionicons name="calendar-outline" size={18} color={colors.mutedForeground} />
                  <Caption style={{ marginLeft: 6 }}>{monthDayLabel(entry.written_at)}</Caption>
                </View>
                <Text style={{ fontSize: 22 }}>{moodEmoji(entry.mood, entry.mood_level)}</Text>
              </View>
              <Body style={{ marginTop: spacing.sm }}>{entry.preview ?? entry.title ?? ""}</Body>

              <View style={styles.actions}>
                <Pressable
                  style={styles.reactBtn}
                  onPress={() => react(entry.diary_id)}
                  accessibilityRole="button"
                  accessibilityLabel="응원하기"
                >
                  <Ionicons
                    name={liked[entry.diary_id] ? "heart" : "heart-outline"}
                    size={22}
                    color={liked[entry.diary_id] ? colors.destructive : colors.mutedForeground}
                  />
                  <Text style={styles.reactText}>{count}</Text>
                </Pressable>
                <Pressable
                  style={styles.reactBtn}
                  onPress={() => {}}
                  accessibilityRole="button"
                  accessibilityLabel="댓글"
                >
                  <Ionicons name="chatbubble-outline" size={20} color={colors.mutedForeground} />
                  <Text style={styles.reactText}>댓글</Text>
                </Pressable>
              </View>
            </Card>
          );
        })}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.foreground },
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  dateRow: { flexDirection: "row", alignItems: "center" },
  actions: { flexDirection: "row", gap: spacing.xl, marginTop: spacing.md, paddingTop: spacing.md, borderTopWidth: 1, borderTopColor: colors.border },
  // minHeight keeps the heart and comment taps at 44dp without moving the row.
  reactBtn: { flexDirection: "row", alignItems: "center", gap: 6, minHeight: 44 },
  reactText: { fontSize: fontSize.caption, color: colors.mutedForeground, fontWeight: fontWeight.semibold },
});
