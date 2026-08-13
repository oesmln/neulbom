import React from "react";
import { View, Text, StyleSheet, Pressable, TextInput } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi, reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { isoDateOf, moodEmoji, parseIso } from "@/utils/format";
import type { DiaryListItem, Uuid } from "@/api/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
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
 * The elder's month of diaries, with the guardian's reaction.
 *
 * `GET /diaries/{user_id}` is range-queried a month at a time, and the risk mark
 * on a day comes from the guardian report's `trend_points` — the diary payload
 * carries no risk field, and inventing one from the mood would put a warning on
 * the screen that no analysis produced.
 */
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const REACTIONS = ["❤️", "👍", "🥺", "🫂"];
const RISK_MARK = "⚠️";

/**
 * Day number out of a `YYYY-MM-DD` key.
 *
 * Read off the string rather than through `new Date(...)`, which parses a
 * date-only string as UTC midnight and lands on the previous day for anyone
 * west of Greenwich.
 */
function dayOf(isoDate: string): number {
  return Number(isoDate.slice(8, 10));
}

function monthRange(year: number, month: number) {
  return {
    from: isoDateOf(new Date(year, month, 1)),
    to: isoDateOf(new Date(year, month + 1, 0)),
  };
}

export default function GuardianDiaryScreen() {
  const { userId, selectedElderId } = useApp();

  const today = React.useMemo(() => new Date(), []);
  const [cursor, setCursor] = React.useState(() => new Date(today.getFullYear(), today.getMonth(), 1));
  const [selected, setSelected] = React.useState<string | null>(null);
  const [reaction, setReaction] = React.useState<string | null>(null);
  const [message, setMessage] = React.useState("");
  const [submitted, setSubmitted] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [reactionError, setReactionError] = React.useState<string | null>(null);

  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const range = React.useMemo(() => monthRange(year, month), [year, month]);

  const list = useApi(
    () =>
      diariesApi.listForUser(selectedElderId as string, {
        fromDate: range.from,
        toDate: range.to,
        limit: 31,
      }),
    [selectedElderId, range.from, range.to],
    { enabled: !!selectedElderId },
  );

  // Risk marks only — the report is not required for the calendar to render.
  const report = useApi(
    () => reports.guardianReport(userId as string, selectedElderId as string),
    [userId, selectedElderId],
    { enabled: !!userId && !!selectedElderId },
  );

  // Reset the reaction form whenever a different day is opened.
  React.useEffect(() => {
    setReaction(null);
    setMessage("");
    setSubmitted(false);
    setReactionError(null);
  }, [selected]);

  const byDate = React.useMemo(() => {
    const map: Record<string, DiaryListItem> = {};
    for (const entry of list.data?.diaries ?? []) {
      map[isoDateOf(parseIso(entry.written_at))] = entry;
    }
    return map;
  }, [list.data]);

  const riskByDate = React.useMemo(() => {
    const map: Record<string, { risk: boolean; score: number | null }> = {};
    for (const point of report.data?.trend_points ?? []) {
      map[point.date] = {
        risk: point.risk_level === "warning",
        score: point.display_score,
      };
    }
    return map;
  }, [report.data]);

  const selectedEntry = selected ? byDate[selected] : undefined;
  const selectedRisk = selected ? riskByDate[selected] : undefined;

  const detail = useApi(
    () => diariesApi.detail(selectedEntry?.diary_id as Uuid),
    [selectedEntry?.diary_id],
    { enabled: !!selectedEntry },
  );

  const submit = async () => {
    if (!reaction || !selectedEntry || submitting) return;
    setSubmitting(true);
    setReactionError(null);
    try {
      await diariesApi.react(selectedEntry.diary_id, reaction, message.trim() || undefined);
      setSubmitted(true);
    } catch (cause) {
      setReactionError(apiErrorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  const header = (
    <ScreenHeader
      color={guardian.blue}
      title="일기"
      subtitle={`${list.data?.diaries.length ?? 0}일 일기 기록됨 · ${month + 1}월`}
    />
  );

  if (!selectedElderId) {
    return (
      <Screen header={header}>
        <EmptyState message="먼저 대시보드에서 어르신을 선택해 주세요." icon="people-outline" />
      </Screen>
    );
  }

  if (list.error) {
    return (
      <Screen header={header}>
        <ErrorState
          message={
            list.error.isForbidden
              ? "어르신이 아직 일기 열람에 동의하지 않았어요."
              : apiErrorMessage(list.error)
          }
          onRetry={list.error.isForbidden ? undefined : list.reload}
        />
      </Screen>
    );
  }

  const offset = new Date(year, month, 1).getDay();
  const dayCount = new Date(year, month + 1, 0).getDate();
  const isCurrentMonth = year === today.getFullYear() && month === today.getMonth();

  const shiftMonth = (by: number) => {
    setSelected(null);
    setCursor(new Date(year, month + by, 1));
  };

  return (
    <Screen header={header}>
      <View style={styles.monthRow}>
        <Pressable
          onPress={() => shiftMonth(-1)}
          accessibilityRole="button"
          accessibilityLabel="이전 달"
          style={styles.monthButton}
        >
          <Ionicons name="chevron-back" size={16} color={colors.mutedForeground} />
        </Pressable>
        <Text style={styles.monthLabel}>
          {year}년 {month + 1}월
        </Text>
        <Pressable
          onPress={() => shiftMonth(1)}
          accessibilityRole="button"
          accessibilityLabel="다음 달"
          style={styles.monthButton}
        >
          <Ionicons name="chevron-forward" size={16} color={colors.mutedForeground} />
        </Pressable>
      </View>

      <Card style={{ marginTop: spacing.lg }}>
        <View style={styles.weekRow}>
          {WEEKDAYS.map((day, i) => (
            <Text
              key={day}
              style={[
                styles.weekday,
                { color: i === 0 ? colors.destructive : i === 6 ? guardian.blue : colors.mutedForeground },
              ]}
            >
              {day}
            </Text>
          ))}
        </View>

        <View style={styles.grid}>
          {Array.from({ length: offset }).map((_, i) => (
            <View key={`pad-${i}`} style={styles.cell} />
          ))}
          {Array.from({ length: dayCount }).map((_, i) => {
            const day = i + 1;
            const date = isoDateOf(new Date(year, month, day));
            const entry = byDate[date];
            const risk = riskByDate[date]?.risk ?? false;
            const isSelected = selected === date;
            const isToday = isCurrentMonth && day === today.getDate();

            return (
              <Pressable
                key={date}
                onPress={() => setSelected(isSelected ? null : date)}
                accessibilityRole="button"
                accessibilityState={{ selected: isSelected }}
                accessibilityLabel={`${month + 1}월 ${day}일${entry ? " 일기 있음" : ""}`}
                style={[
                  styles.cell,
                  styles.dayCell,
                  {
                    backgroundColor: isSelected
                      ? guardian.blue
                      : isToday
                        ? guardian.blueLight
                        : "transparent",
                  },
                ]}
              >
                <Text
                  style={[
                    styles.dayNumber,
                    {
                      color: isSelected
                        ? colors.white
                        : isToday
                          ? guardian.blueDark
                          : colors.foreground,
                    },
                  ]}
                >
                  {day}
                </Text>
                <Text style={styles.dayMark}>
                  {entry ? (risk ? RISK_MARK : moodEmoji(entry.mood, entry.mood_level)) : " "}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </Card>

      <View style={styles.legend}>
        {[
          { mark: "😄", label: "좋음" },
          { mark: "😐", label: "보통" },
          { mark: "😔", label: "힘듦" },
          { mark: RISK_MARK, label: "위험" },
        ].map((item) => (
          <View key={item.label} style={styles.legendItem}>
            <Text style={{ fontSize: 13 }}>{item.mark}</Text>
            <Caption>{item.label}</Caption>
          </View>
        ))}
      </View>

      {report.error?.isForbidden ? (
        <Text style={styles.permissionNotice}>
          검사·요약 열람 권한이 없어 날짜별 분석 표시는 생략했어요.
        </Text>
      ) : null}

      {selected && !selectedEntry ? (
        <Card style={{ marginTop: spacing.lg }}>
          <Body style={{ textAlign: "center" }}>
            {month + 1}월 {dayOf(selected)}일에는 일기가 없어요.
          </Body>
        </Card>
      ) : null}

      {selectedEntry ? (
        <View style={{ gap: spacing.md, marginTop: spacing.lg }}>
          {selectedRisk?.risk ? (
            <View style={styles.riskCard}>
              <Ionicons name="warning-outline" size={18} color={colors.destructive} />
              <View style={{ flex: 1 }}>
                <Text style={styles.riskTitle}>인지 저하 신호 감지</Text>
                <Text style={styles.riskBody}>
                  이 날의 분석에서 주의가 필요한 신호가 확인되었습니다.
                </Text>
              </View>
            </View>
          ) : null}

          <Card>
            <View style={styles.moodRow}>
              <Text style={{ fontSize: 30 }}>
                {moodEmoji(selectedEntry.mood, selectedEntry.mood_level)}
              </Text>
              <View style={{ flex: 1 }}>
                <View style={styles.moodMeta}>
                  {selectedRisk?.score != null ? (
                    <Text
                      style={[
                        styles.moodScore,
                        { color: selectedRisk.risk ? colors.destructive : guardian.blue },
                      ]}
                    >
                      {selectedRisk.score}점
                    </Text>
                  ) : null}
                  {selectedRisk ? (
                    <Badge
                      label={selectedRisk.risk ? "주의 필요" : "정상 범위"}
                      color={selectedRisk.risk ? colors.destructive : guardian.blueDark}
                      background={selectedRisk.risk ? colors.destructiveLight : guardian.blueLight}
                    />
                  ) : (
                    <Badge
                      label="분석 없음"
                      color={colors.mutedForeground}
                      background={colors.muted}
                    />
                  )}
                </View>
                <Caption style={{ marginTop: 2 }}>
                  {month + 1}월 {dayOf(selected as string)}일 · AI 일기
                </Caption>
              </View>
            </View>
          </Card>

          <Card>
            {detail.error ? (
              <ErrorState
                message={
                  detail.error.isForbidden
                    ? "이 일기를 볼 수 있는 권한이 없어요."
                    : apiErrorMessage(detail.error)
                }
                onRetry={detail.error.isForbidden ? undefined : detail.reload}
              />
            ) : detail.loading && !detail.data ? (
              <LoadingState label="일기를 불러오는 중이에요" />
            ) : (
              <Body style={{ lineHeight: 26 }}>
                {detail.data?.content ?? selectedEntry.preview ?? ""}
              </Body>
            )}
          </Card>

          <Card>
            <Body style={{ fontWeight: fontWeight.semibold }}>반응 남기기</Body>
            <View style={styles.reactionRow}>
              {REACTIONS.map((emoji) => {
                const on = reaction === emoji;
                return (
                  <Pressable
                    key={emoji}
                    onPress={() => setReaction(emoji)}
                    disabled={submitted}
                    accessibilityRole="button"
                    accessibilityState={{ selected: on }}
                    accessibilityLabel={`${emoji} 반응 선택`}
                    style={[
                      styles.reactionButton,
                      {
                        backgroundColor: on ? guardian.blueLight : colors.muted,
                        borderColor: on ? guardian.blue : "transparent",
                      },
                    ]}
                  >
                    <Text style={{ fontSize: 20 }}>{emoji}</Text>
                  </Pressable>
                );
              })}
            </View>

            {submitted ? (
              <View style={styles.submitted}>
                <Ionicons name="checkmark-circle" size={18} color={guardian.blue} />
                <Text style={styles.submittedLabel}>반응을 전달했습니다.</Text>
              </View>
            ) : (
              <>
                <View style={styles.inputBox}>
                  <TextInput
                    value={message}
                    onChangeText={setMessage}
                    placeholder="응원 메시지를 입력하세요 (선택)"
                    placeholderTextColor={colors.mutedForeground}
                    style={styles.input}
                    accessibilityLabel="응원 메시지"
                  />
                </View>
                <Pressable
                  onPress={() => void submit()}
                  disabled={!reaction || submitting}
                  accessibilityRole="button"
                  accessibilityLabel="반응 전달하기"
                  accessibilityState={{ disabled: !reaction || submitting }}
                  style={[
                    styles.submitButton,
                    { backgroundColor: reaction && !submitting ? guardian.blue : colors.muted },
                  ]}
                >
                  <Text
                    style={[
                      styles.submitLabel,
                      { color: reaction && !submitting ? colors.white : colors.mutedForeground },
                    ]}
                  >
                    {submitting ? "반응 전달 중" : "반응 전달하기"}
                  </Text>
                </Pressable>
                {reactionError ? <Text style={styles.reactionError}>{reactionError}</Text> : null}
              </>
            )}
          </Card>
        </View>
      ) : null}

      {list.loading && !list.data ? <LoadingState /> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  monthRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  permissionNotice: {
    marginTop: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.warningLight,
    padding: spacing.md,
    fontSize: fontSize.caption,
    color: colors.warning,
    textAlign: "center",
  },
  monthButton: {
    width: 36,
    height: 36,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
  },
  monthLabel: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold, color: colors.foreground },

  weekRow: { flexDirection: "row", marginBottom: spacing.sm },
  weekday: {
    flex: 1,
    textAlign: "center",
    fontSize: fontSize.micro,
    fontWeight: fontWeight.semibold,
  },
  grid: { flexDirection: "row", flexWrap: "wrap" },
  cell: { width: `${100 / 7}%`, aspectRatio: 1 },
  dayCell: { alignItems: "center", justifyContent: "center", borderRadius: radius.md, gap: 1 },
  dayNumber: { fontSize: fontSize.micro, fontWeight: fontWeight.semibold },
  dayMark: { fontSize: 11 },

  legend: {
    flexDirection: "row",
    justifyContent: "center",
    flexWrap: "wrap",
    gap: spacing.md,
    marginTop: spacing.md,
  },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 5 },

  riskCard: {
    flexDirection: "row",
    gap: spacing.md,
    backgroundColor: colors.destructiveLight,
    borderWidth: 1,
    borderColor: guardian.dangerBorderSoft,
    borderRadius: radius.lg,
    padding: spacing.lg,
  },
  riskTitle: {
    fontSize: fontSize.caption,
    fontWeight: fontWeight.semibold,
    color: colors.destructive,
  },
  riskBody: { fontSize: fontSize.caption, color: guardian.dangerText, lineHeight: 20, marginTop: 2 },

  moodRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  moodMeta: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  moodScore: { fontSize: 22, fontWeight: fontWeight.bold },

  reactionRow: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.md },
  reactionButton: {
    flex: 1,
    height: 44,
    borderRadius: radius.md,
    borderWidth: 2,
    alignItems: "center",
    justifyContent: "center",
  },
  inputBox: {
    marginTop: spacing.md,
    backgroundColor: colors.muted,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    justifyContent: "center",
    minHeight: 44,
  },
  input: { fontSize: fontSize.body, color: colors.foreground, paddingVertical: spacing.sm },
  submitButton: {
    marginTop: spacing.md,
    height: 44,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
  },
  submitLabel: { fontSize: fontSize.body, fontWeight: fontWeight.semibold },
  submitted: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    marginTop: spacing.md,
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: guardian.blueLight,
  },
  submittedLabel: {
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
    color: guardian.blueDark,
  },
  reactionError: {
    marginTop: spacing.sm,
    fontSize: fontSize.caption,
    color: colors.destructive,
    textAlign: "center",
  },
});
