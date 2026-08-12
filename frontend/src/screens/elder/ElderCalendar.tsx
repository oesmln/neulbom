import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { diaryDateLabel, isoDateOf, moodEmoji } from "@/utils/format";
import type { DiaryListItem } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, ScreenHeader, Card, Caption, Body, ErrorState, LoadingState } from "@/components/ui";

/**
 * Month calendar backed by `GET /calendar/{user_id}/activities` for the day
 * markers and `GET /diaries/{user_id}` for the entry shown underneath.
 *
 * The grid is built from the real month rather than a fixed offset, and a day
 * without an entry stays blank instead of borrowing a neighbour's mood.
 */
const WEEK = ["일", "월", "화", "수", "목", "금", "토"];

function monthRange(base: Date) {
  const first = new Date(base.getFullYear(), base.getMonth(), 1);
  const last = new Date(base.getFullYear(), base.getMonth() + 1, 0);
  return { first, last };
}

/** `metadata` is a free-form JsonNode on the backend; read it defensively. */
function metadataMood(metadata: unknown): { mood: string | null; level: number | null } {
  if (!metadata || typeof metadata !== "object") return { mood: null, level: null };
  const record = metadata as Record<string, unknown>;
  return {
    mood: typeof record.mood === "string" ? record.mood : null,
    level: typeof record.mood_level === "number" ? record.mood_level : null,
  };
}

export default function ElderCalendarScreen() {
  const { userId } = useApp();
  const today = React.useMemo(() => new Date(), []);
  const { first, last } = React.useMemo(() => monthRange(today), [today]);
  const fromDate = isoDateOf(first);
  const toDate = isoDateOf(last);

  const [selected, setSelected] = React.useState<string>(isoDateOf(today));

  const calendar = useApi(
    () => diariesApi.calendar(userId as string, fromDate, toDate),
    [userId, fromDate, toDate],
    { enabled: !!userId },
  );

  const diaryList = useApi(
    () => diariesApi.listForUser(userId as string, { fromDate, toDate, limit: 31 }),
    [userId, fromDate, toDate],
    { enabled: !!userId },
  );

  /** date → mood, taken from the calendar activities. */
  const moodByDate = React.useMemo(() => {
    const map = new Map<string, string>();
    for (const activity of calendar.data?.activities ?? []) {
      if (activity.activity_type !== "diary") continue;
      const { mood, level } = metadataMood(activity.metadata);
      map.set(activity.activity_date, moodEmoji(mood, level));
    }
    return map;
  }, [calendar.data]);

  /** date → diary, so tapping a day can show what was written. */
  const diaryByDate = React.useMemo(() => {
    const map = new Map<string, DiaryListItem>();
    for (const diary of diaryList.data?.diaries ?? []) {
      map.set(isoDateOf(new Date(diary.written_at)), diary);
    }
    return map;
  }, [diaryList.data]);

  const firstDayOffset = first.getDay();
  const daysInMonth = last.getDate();
  const cells: (number | null)[] = [
    ...Array(firstDayOffset).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  const dateOf = (day: number) =>
    isoDateOf(new Date(today.getFullYear(), today.getMonth(), day));

  const selectedDiary = diaryByDate.get(selected);
  const loading = calendar.loading || diaryList.loading;
  const error = calendar.error ?? diaryList.error;

  return (
    <Screen
      header={
        <ScreenHeader
          title={`${today.getFullYear()}년 ${today.getMonth() + 1}월`}
          subtitle={`${diaryByDate.size}일 일기 작성`}
        />
      }
    >
      {error && !calendar.data ? (
        <ErrorState
          message={apiErrorMessage(error)}
          onRetry={() => {
            calendar.reload();
            diaryList.reload();
          }}
        />
      ) : null}

      {loading && !calendar.data ? <LoadingState /> : null}

      {calendar.data ? (
        <>
          <Card>
            <View style={styles.weekRow}>
              {WEEK.map((w, i) => (
                <Text key={w} style={[styles.weekLabel, i === 0 && { color: colors.destructive }]}>
                  {w}
                </Text>
              ))}
            </View>

            <View style={styles.grid}>
              {cells.map((day, i) => {
                if (!day) return <View key={`pad-${i}`} style={styles.cell} />;
                const date = dateOf(day);
                const isSelected = date === selected;
                const mood = moodByDate.get(date);
                return (
                  <Pressable
                    key={date}
                    style={styles.cell}
                    onPress={() => setSelected(date)}
                    accessibilityRole="button"
                    accessibilityLabel={`${today.getMonth() + 1}월 ${day}일${mood ? " 일기 있음" : ""}`}
                    accessibilityState={{ selected: isSelected }}
                  >
                    <View style={[styles.dayInner, isSelected && styles.today]}>
                      <Text style={[styles.dayNum, isSelected && { color: colors.white }]}>{day}</Text>
                      {mood ? <Text style={styles.mood}>{mood}</Text> : null}
                    </View>
                  </Pressable>
                );
              })}
            </View>
          </Card>

          <Card style={{ marginTop: spacing.lg }}>
            <Caption>{diaryDateLabel(selectedDiary?.written_at ?? `${selected}T00:00:00`)}</Caption>
            {selectedDiary ? (
              <>
                <Text style={{ fontSize: 30, marginVertical: spacing.xs }}>
                  {moodEmoji(selectedDiary.mood, selectedDiary.mood_level)}
                </Text>
                <Body>{selectedDiary.preview ?? selectedDiary.title ?? ""}</Body>
              </>
            ) : (
              <Body>아직 이 날의 일기가 없어요.</Body>
            )}
          </Card>

          <View style={styles.legend}>
            <Caption>😊 좋음 · 😐 보통 · 빈 칸은 기록 없음</Caption>
          </View>
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  weekRow: { flexDirection: "row", marginBottom: spacing.sm },
  weekLabel: { flex: 1, textAlign: "center", fontSize: fontSize.caption, fontWeight: fontWeight.bold, color: colors.mutedForeground },
  grid: { flexDirection: "row", flexWrap: "wrap" },
  cell: { width: "14.2857%", aspectRatio: 1, padding: 3 },
  dayInner: { flex: 1, alignItems: "center", justifyContent: "center", borderRadius: radius.sm },
  today: { backgroundColor: colors.primary },
  dayNum: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.foreground },
  mood: { fontSize: 14, marginTop: 1 },
  legend: { marginTop: spacing.lg, alignItems: "center" },
});
