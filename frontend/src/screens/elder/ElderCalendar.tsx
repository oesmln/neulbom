import React from "react";
import { View, StyleSheet, Pressable, useWindowDimensions } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useIsFocused } from "@react-navigation/native";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi } from "@/api";
import DiaryReactionList from "@/components/DiaryReactionList";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { isoDateOf, moodEmoji, parseIso } from "@/utils/format";
import type { DiaryListItem } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import {
  Screen,
  ScreenHeader,
  Card,
  Caption,
  Body,
  EmptyState,
  ErrorState,
  LoadingState,
  SentenceText as Text,
} from "@/components/ui";

/** 날짜를 고르고 일기 본문과 보호자 반응을 한 번씩 읽는 화면. */
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
  const isFocused = useIsFocused();
  const wide = useWindowDimensions().width >= 960;
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
  const [selectedDiaryId, setSelectedDiaryId] = React.useState<string | null>(null);

  const calendar = useApi(
    async () => ({ owner: userId, month: fromDate, ...await diariesApi.calendar(userId as string, fromDate, toDate) }),
    [userId, fromDate, toDate, isFocused],
    { enabled: !!userId && isFocused },
  );

  const diaryList = useApi(
    async () => ({ owner: userId, month: fromDate, ...await diariesApi.listForUser(userId as string, { fromDate, toDate, limit: 100 }) }),
    [userId, fromDate, toDate, isFocused],
    { enabled: !!userId && isFocused, intervalMs: isFocused ? 5000 : undefined },
  );

  const calendarData = calendar.data?.owner === userId && calendar.data?.month === fromDate ? calendar.data : null;
  const diaryData = diaryList.data?.owner === userId && diaryList.data?.month === fromDate ? diaryList.data : null;

  /** date → mood, taken from the calendar activities. */
  const moodByDate = React.useMemo(() => {
    const map = new Map<string, string>();
    for (const activity of calendarData?.activities ?? []) {
      if (activity.activity_type !== "diary") continue;
      const { mood, level } = metadataMood(activity.metadata);
      map.set(activity.activity_date, mood || level != null ? moodEmoji(mood, level) : "📖");
    }
    return map;
  }, [calendarData]);

  /** date → diaries, preserving every conversation on the selected day. */
  const diaryByDate = React.useMemo(() => {
    const map = new Map<string, DiaryListItem[]>();
    const seen = new Set<string>();
    for (const diary of diaryData?.diaries ?? []) {
      if (seen.has(diary.diary_id)) continue;
      seen.add(diary.diary_id);
      const writtenAt = Date.parse(diary.written_at);
      if (!Number.isFinite(writtenAt)) continue;
      const date = new Date(writtenAt + 9 * 60 * 60 * 1000).toISOString().slice(0, 10);
      if (date < fromDate || date > toDate) continue;
      map.set(date, [...(map.get(date) ?? []), diary]);
    }
    return map;
  }, [diaryData, fromDate, toDate]);

  const firstDayOffset = first.getDay();
  const daysInMonth = last.getDate();
  const cells: (number | null)[] = [
    ...Array(firstDayOffset).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  const dateOf = (day: number) =>
    isoDateOf(new Date(monthBase.getFullYear(), monthBase.getMonth(), day));

  const selectedDiaries = selected ? diaryByDate.get(selected) ?? [] : [];
  const selectedDiary = selectedDiaries.find((diary) => diary.diary_id === selectedDiaryId) ?? selectedDiaries[0];
  const selectedDay = selected ? Number(selected.slice(8, 10)) : null;
  const showGenerationStatus =
    selected === todayDate && Boolean(diaryData) && !selectedDiary;
  const generation = useApi(
    () => diariesApi.generationStatus(userId as string, selected as string),
    [userId, selected],
    { enabled: !!userId && showGenerationStatus },
  );
  // The list only carries an 80-char `preview`; the card shows the whole entry,
  // so fetch the detail for whichever day is selected (same pattern as GuardianDiary).
  const detail = useApi(
    () => diariesApi.detail(selectedDiary?.diary_id as string),
    [selectedDiary?.diary_id, isFocused],
    { enabled: !!selectedDiary && isFocused },
  );
  const selectedDetail = detail.data?.diary_id === selectedDiary?.diary_id && detail.data?.user_id === userId ? detail.data : null;
  const content = selectedDetail?.content ?? "";

  const loading = calendar.loading || diaryList.loading;
  const error = calendar.error ?? diaryList.error;

  const moveMonth = (delta: number) => {
    setMonthOffset((offset) => offset + delta);
    setSelected(null);
    setSelectedDiaryId(null);
  };

  const selectDay = (date: string) => {
    setSelected(date);
    setSelectedDiaryId(null);
  };

  return (
    <Screen header={<ScreenHeader title="나의 일기" subtitle="하루의 이야기를 다시 읽어 보세요." />} background={colors.screenBackground}>
      <View style={styles.container}>
      {error ? (
        <ErrorState
          message={apiErrorMessage(error)}
          onRetry={() => {
            calendar.reload();
            diaryList.reload();
          }}
        />
      ) : null}

      {loading && !calendarData ? <LoadingState /> : null}

      {calendarData ? (
        <View style={[styles.layout, wide && styles.wideLayout]}>
          <View style={[styles.calendarColumn, wide && styles.wideCalendarColumn]}>
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
          </View>
          <View style={[styles.entryColumn, wide && styles.wideEntryColumn]}>
          {!diaryData ? (
            diaryList.error ? null : <LoadingState label="일기를 불러오는 중이에요" />
          ) : selected && selectedDiary ? (
            <View style={styles.entryCard}>
              <Caption>{monthBase.getMonth() + 1}월 {selectedDay}일 · 일기 {selectedDiaries.length}편</Caption>
              {selectedDiaries.length > 1 ? selectedDiaries.map((diary) => (
                <Pressable
                  key={diary.diary_id}
                  onPress={() => setSelectedDiaryId(diary.diary_id)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: diary.diary_id === selectedDiary.diary_id }}
                  style={[styles.diaryChoice, diary.diary_id === selectedDiary.diary_id && styles.diaryChoiceSelected]}
                >
                  <Text style={styles.diaryChoiceTitle}>
                    {parseIso(diary.written_at).toLocaleTimeString("ko-KR", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit" })}
                  </Text>
                </Pressable>
              )) : null}
              <View style={styles.entryHead}>
                <Text style={styles.entryTitle}>
                  {selectedDiary.title && !["오늘의 이야기", "일기"].includes(selectedDiary.title.trim())
                    ? selectedDiary.title
                    : `${monthBase.getMonth() + 1}월 ${selectedDay}일의 일기`}
                </Text>
                <Text style={styles.entryMood}>
                  {moodEmoji(selectedDiary.mood, selectedDiary.mood_level)}
                </Text>
              </View>
              {detail.error ? (
                <ErrorState message={apiErrorMessage(detail.error)} onRetry={detail.reload} />
              ) : detail.loading || !selectedDetail ? (
                <LoadingState label="일기를 불러오는 중이에요" />
              ) : (
                <>
                  <Text style={styles.entryBody}>
                    {content}
                  </Text>
                  {selectedDetail.reactions.length > 0 ? (
                    <View style={styles.reactionSection}>
                      <DiaryReactionList reactions={selectedDetail.reactions} grouped />
                    </View>
                  ) : null}
                </>
              )}
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
          ) : (
            <Card><EmptyState message="달력에서 읽고 싶은 날짜를 선택해 주세요." icon="calendar-outline" /></Card>
          )}
          </View>
        </View>
      ) : null}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { width: "100%", maxWidth: 1160, alignSelf: "center" },
  layout: { gap: spacing.xl },
  wideLayout: { flexDirection: "row", alignItems: "flex-start", gap: spacing.xxl },
  calendarColumn: { width: "100%" },
  wideCalendarColumn: { width: 350, flexShrink: 0 },
  entryColumn: { width: "100%", minWidth: 0 },
  wideEntryColumn: { flex: 1, width: undefined },
  reactionSection: { borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.lg, marginTop: spacing.lg },
  monthRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.lg,
  },
  monthButton: {
    width: 48,
    height: 48,
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
  cell: { width: "14.2857%", minHeight: 52 },
  dayInner: {
    alignItems: "center",
    gap: 2,
    paddingVertical: 6,
    borderRadius: radius.md,
  },
  dayToday: { backgroundColor: colors.secondary },
  daySelected: { backgroundColor: colors.primary },
  dayNum: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold, color: colors.foreground },
  mood: { fontSize: 12 },
  moodSpacer: { height: 14 },

  entryCard: {
    padding: spacing.xl,
    gap: spacing.md,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.card,
  },
  entryHead: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  diaryChoice: { padding: spacing.md, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, backgroundColor: colors.card },
  diaryChoiceSelected: { borderColor: colors.primary },
  diaryChoiceTitle: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  entryTitle: { fontSize: 16, fontWeight: fontWeight.bold, color: colors.foreground },
  entryMood: { fontSize: 22 },
  entryBody: { fontSize: fontSize.bodyLg, lineHeight: 28, color: colors.foreground },

  emptyCard: { marginTop: spacing.lg, alignItems: "center", gap: spacing.sm },
  emptyText: { textAlign: "center", color: colors.mutedForeground },
  generationLabel: { fontSize: fontSize.body, fontWeight: fontWeight.bold, color: colors.foreground },

  legend: {
    marginTop: spacing.lg,
    flexDirection: "row",
    justifyContent: "center",
    gap: spacing.md,
    flexWrap: "wrap",
  },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 6 },
});
