import React from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { isoDateOf, moodEmoji } from "@/utils/format";
import type { DiaryListItem } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import {
  Screen,
  ScreenHeader,
  Card,
  Button,
  Caption,
  Body,
  ErrorState,
  LoadingState,
  SentenceText as Text,
} from "@/components/ui";

/**
 * Month calendar backed by `GET /calendar/{user_id}/activities` for the day
 * markers and `GET /diaries/{user_id}` for the entry shown underneath.
 *
 * Layout follows the Figma prototype (ElderCalendarScreen): a ‹ month › row,
 * a bordered grid where the selected day turns sage with a 📖, and a light-sage
 * entry card with the day's mood and a "전체 보기" toggle. The grid is built
 * from the real month, and a day without an entry stays blank instead of
 * borrowing a neighbour's mood.
 */
const WEEK = ["일", "월", "화", "수", "목", "금", "토"];
/** Entries longer than this start collapsed behind "전체 보기". */
const COLLAPSE_AFTER = 90;

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
  const todayDate = isoDateOf(today);

  // 0 = this month; ‹ › move by whole months and the queries follow.
  const [monthOffset, setMonthOffset] = React.useState(0);
  const monthBase = React.useMemo(
    () => new Date(today.getFullYear(), today.getMonth() + monthOffset, 1),
    [today, monthOffset],
  );
  const { first, last } = React.useMemo(() => monthRange(monthBase), [monthBase]);
  const fromDate = isoDateOf(first);
  const toDate = isoDateOf(last);
  const monthLabel = `${monthBase.getFullYear()}년 ${monthBase.getMonth() + 1}월`;

  const [selected, setSelected] = React.useState<string | null>(todayDate);
  const [expanded, setExpanded] = React.useState(false);

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
    isoDateOf(new Date(monthBase.getFullYear(), monthBase.getMonth(), day));

  const selectedDiary = selected ? diaryByDate.get(selected) : undefined;
  const selectedDay = selected ? Number(selected.slice(8, 10)) : null;
  const showGenerationStatus =
    selected === todayDate && Boolean(diaryList.data) && !selectedDiary;
  const generation = useApi(
    () => diariesApi.generationStatus(userId as string, selected as string),
    [userId, selected],
    { enabled: !!userId && showGenerationStatus },
  );
  // The list only carries an 80-char `preview`; the card shows the whole entry,
  // so fetch the detail for whichever day is selected (same pattern as GuardianDiary).
  const detail = useApi(
    () => diariesApi.detail(selectedDiary?.diary_id as string),
    [selectedDiary?.diary_id],
    { enabled: !!selectedDiary },
  );
  const content = detail.data?.content ?? selectedDiary?.preview ?? selectedDiary?.title ?? "";
  const collapsible = content.length > COLLAPSE_AFTER;

  const loading = calendar.loading || diaryList.loading;
  const error = calendar.error ?? diaryList.error;

  const moveMonth = (delta: number) => {
    setMonthOffset((offset) => offset + delta);
    setSelected(null);
    setExpanded(false);
  };

  const selectDay = (date: string) => {
    setSelected((current) => (current === date ? null : date));
    setExpanded(false);
  };

  return (
    <Screen header={<ScreenHeader title={monthLabel} subtitle={`${diaryByDate.size}일 일기 작성`} />}>
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
          {/* ‹ 2026년 9월 › — month navigation */}
          <View style={styles.monthRow}>
            <Pressable
              onPress={() => moveMonth(-1)}
              accessibilityRole="button"
              accessibilityLabel="이전 달"
              style={styles.monthButton}
            >
              <Ionicons name="chevron-back" size={16} color={colors.mutedForeground} />
            </Pressable>
            <Text style={styles.monthLabel}>{monthLabel}</Text>
            <Pressable
              onPress={() => moveMonth(1)}
              accessibilityRole="button"
              accessibilityLabel="다음 달"
              style={styles.monthButton}
            >
              <Ionicons name="chevron-forward" size={16} color={colors.mutedForeground} />
            </Pressable>
          </View>

          <Card style={styles.calendarCard}>
            <View style={styles.weekRow}>
              {WEEK.map((w, i) => (
                <Text
                  key={w}
                  style={[
                    styles.weekLabel,
                    i === 0 && { color: colors.destructive },
                    i === 6 && { color: colors.primary },
                  ]}
                >
                  {w}
                </Text>
              ))}
            </View>

            <View style={styles.grid}>
              {cells.map((day, i) => {
                if (!day) return <View key={`pad-${i}`} style={styles.cell} />;
                const date = dateOf(day);
                const isSelected = date === selected;
                const isToday = date === todayDate;
                const mood = moodByDate.get(date);
                return (
                  <Pressable
                    key={date}
                    style={styles.cell}
                    onPress={() => selectDay(date)}
                    accessibilityRole="button"
                    accessibilityLabel={`${monthBase.getMonth() + 1}월 ${day}일${mood ? " 일기 있음" : ""}`}
                    accessibilityState={{ selected: isSelected }}
                  >
                    <View
                      style={[
                        styles.dayInner,
                        isToday && styles.dayToday,
                        isSelected && styles.daySelected,
                      ]}
                    >
                      <Text
                        style={[
                          styles.dayNum,
                          isToday && { color: colors.primaryDark },
                          isSelected && { color: colors.white },
                        ]}
                      >
                        {day}
                      </Text>
                      {mood ? (
                        <Text style={styles.mood}>{isSelected ? "📖" : mood}</Text>
                      ) : (
                        <View style={styles.moodSpacer} />
                      )}
                    </View>
                  </Pressable>
                );
              })}
            </View>
          </Card>

          {selected && selectedDiary ? (
            // Light-sage entry card: "9월 8일의 일기" + mood, body, 전체 보기 toggle.
            <View style={styles.entryCard}>
              <View style={styles.entryHead}>
                <Text style={styles.entryTitle}>
                  {monthBase.getMonth() + 1}월 {selectedDay}일의 일기
                </Text>
                <Text style={styles.entryMood}>
                  {moodEmoji(selectedDiary.mood, selectedDiary.mood_level)}
                </Text>
              </View>
              <Text style={styles.entryBody} numberOfLines={collapsible && !expanded ? 3 : undefined}>
                {content}
              </Text>
              {collapsible ? (
                <Button
                  label={expanded ? "접기" : "전체 보기"}
                  onPress={() => setExpanded((value) => !value)}
                  style={styles.entryAction}
                />
              ) : null}
            </View>
          ) : selected ? (
            <Card style={styles.emptyCard}>
              {showGenerationStatus && generation.error ? (
                <ErrorState message={apiErrorMessage(generation.error)} onRetry={generation.reload} />
              ) : showGenerationStatus && !generation.data ? (
                <LoadingState label="일기 준비 상태를 확인하고 있어요" />
              ) : showGenerationStatus && generation.data ? (
                <>
                  <Text style={styles.generationLabel}>
                    {generation.data.display_label ?? "일기를 준비하고 있어요"}
                  </Text>
                  <Body style={styles.emptyText}>
                    {generation.data.message ?? "잠시 후 다시 확인해 주세요."}
                  </Body>
                </>
              ) : (
                <Body style={styles.emptyText}>
                  {monthBase.getMonth() + 1}월 {selectedDay}일에는 아직 일기가 없어요.
                </Body>
              )}
            </Card>
          ) : null}

          <View style={styles.legend}>
            {[
              ["😄", "아주 좋음"],
              ["😊", "좋음"],
              ["😐", "보통"],
              ["😔", "힘듦"],
            ].map(([emoji, label]) => (
              <View key={label} style={styles.legendItem}>
                <Text style={{ fontSize: 14 }}>{emoji}</Text>
                <Caption>{label}</Caption>
              </View>
            ))}
          </View>
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  monthRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.lg,
  },
  monthButton: {
    width: 36,
    height: 36,
    borderRadius: radius.md,
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
  },
  monthLabel: { fontSize: 15, fontWeight: fontWeight.semibold, color: colors.foreground },

  calendarCard: { padding: spacing.lg },
  weekRow: { flexDirection: "row", marginBottom: spacing.sm },
  weekLabel: {
    flex: 1,
    textAlign: "center",
    paddingVertical: spacing.xs,
    fontSize: fontSize.caption,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
  },
  grid: { flexDirection: "row", flexWrap: "wrap", rowGap: spacing.xs },
  cell: { width: "14.2857%" },
  dayInner: {
    alignItems: "center",
    gap: 2,
    paddingVertical: 6,
    borderRadius: radius.md,
  },
  dayToday: { backgroundColor: colors.secondary },
  daySelected: { backgroundColor: colors.primary },
  dayNum: { fontSize: 13, fontWeight: fontWeight.semibold, color: colors.foreground },
  mood: { fontSize: 12 },
  moodSpacer: { height: 14 },

  entryCard: {
    marginTop: spacing.lg,
    padding: spacing.xl,
    gap: spacing.md,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: colors.primary,
    backgroundColor: colors.secondary,
  },
  entryHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  entryTitle: { fontSize: 16, fontWeight: fontWeight.bold, color: colors.foreground },
  entryMood: { fontSize: 22 },
  entryBody: { fontSize: 14, lineHeight: 24, color: colors.mutedForeground },
  entryAction: { marginTop: spacing.xs },

  emptyCard: { marginTop: spacing.lg, alignItems: "center", gap: spacing.sm },
  emptyText: { textAlign: "center", color: colors.mutedForeground },
  generationLabel: { fontSize: fontSize.body, fontWeight: fontWeight.bold, color: colors.foreground },

  legend: {
    marginTop: spacing.lg,
    flexDirection: "row",
    justifyContent: "center",
    gap: spacing.lg,
  },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 6 },
});
