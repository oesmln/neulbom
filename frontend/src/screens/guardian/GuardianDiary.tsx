import React from "react";
import { View, StyleSheet, Pressable, TextInput, ScrollView, useWindowDimensions } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useIsFocused, useRoute, type RouteProp } from "@react-navigation/native";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi, reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage, guardianAccessErrorMessage } from "@/api/errors";
import { isoDateOf, moodEmoji } from "@/utils/format";
import { diariesByDay, diaryDay, diaryTime } from "@/utils/guardianDiary";
import { DIARY_REACTIONS, type DiaryReactionType } from "@/utils/diaryReactions";
import DiaryReactionList from "@/components/DiaryReactionList";
import ElderSelector from "@/components/ElderSelector";
import type { Uuid } from "@/api/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, ScreenHeader, Card, Body, Caption, EmptyState, ErrorState, LoadingState, SentenceText as Text } from "@/components/ui";
import GuardianHeaderActions from "@/components/GuardianHeaderActions";
import type { GuardianTabParamList } from "@/navigation/types";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function dateLabel(date: string) {
  return `${Number(date.slice(5, 7))}월 ${Number(date.slice(8, 10))}일`;
}

function monthRange(year: number, month: number) {
  return {
    from: isoDateOf(new Date(year, month, 1)),
    to: isoDateOf(new Date(year, month + 1, 0)),
  };
}

/** 날짜 선택 → 일기 한 편 → 반응. 리포트 요약은 대시보드에서 확인한다. */
export default function GuardianDiaryScreen() {
  const isFocused = useIsFocused();
  const route = useRoute<RouteProp<GuardianTabParamList, "GuardianRecord">>();
  const { userId, selectedElderId } = useApp();
  const { width } = useWindowDimensions();
  const wide = width >= 960;
  const today = diaryDay(new Date());
  const [cursor, setCursor] = React.useState(() => new Date(`${today.slice(0, 7)}-01T12:00:00`));
  const [selected, setSelected] = React.useState<string | null>(today);
  const [selectedDiaryId, setSelectedDiaryId] = React.useState<string | null>(null);
  const [reaction, setReaction] = React.useState<Exclude<DiaryReactionType, "message"> | null>(null);
  const [message, setMessage] = React.useState("");
  const [submitted, setSubmitted] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [reactionError, setReactionError] = React.useState<string | null>(null);
  const formVersion = React.useRef(0);

  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const range = React.useMemo(() => monthRange(year, month), [year, month]);

  React.useEffect(() => {
    setSelectedDiaryId(null);
  }, [selectedElderId]);

  React.useEffect(() => {
    const diaryId = route.params?.diaryId;
    if (!diaryId || !selectedElderId) return;
    let cancelled = false;
    void diariesApi.detail(diaryId).then((entry) => {
      if (cancelled || entry.user_id !== selectedElderId) return;
      const day = diaryDay(entry.written_at);
      setCursor(new Date(`${day.slice(0, 7)}-01T12:00:00`));
      setSelected(day);
      setSelectedDiaryId(diaryId);
    }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [route.params?.diaryId, selectedElderId]);

  // useApi는 이전 응답을 보존하므로 대상자·조회월을 함께 확인한다.
  const list = useApi(async () => ({
    elderId: selectedElderId,
    month: range.from,
    response: await diariesApi.listForUser(selectedElderId as string, {
      fromDate: range.from, toDate: range.to, limit: 100,
    }),
  }), [selectedElderId, range.from, range.to, isFocused], {
    enabled: !!selectedElderId && isFocused, intervalMs: isFocused ? 5000 : undefined,
  });
  const currentList = list.data?.elderId === selectedElderId && list.data?.month === range.from
    ? list.data.response : null;

  // 날짜별 경고는 서버가 제공한 검사 결과만 사용한다. 기분으로 위험을 추정하지 않는다.
  const report = useApi(async () => ({
    elderId: selectedElderId,
    month: range.from,
    response: await reports.guardianReport(userId as string, selectedElderId as string, {
      fromDate: range.from, toDate: range.to,
    }),
  }), [userId, selectedElderId, range.from, range.to, isFocused], {
    enabled: !!userId && !!selectedElderId && isFocused,
  });
  const currentReport = report.data?.elderId === selectedElderId && report.data?.month === range.from
    ? report.data.response : null;
  const byDate = React.useMemo(() => diariesByDay(currentList?.diaries ?? [], range.from, range.to),
    [currentList, range.from, range.to]);
  const warningDays = React.useMemo(() => new Set(
    (currentReport?.trend_points ?? []).filter((point) => point.risk_level === "warning").map((point) => point.date),
  ), [currentReport]);
  const selectedEntries = selected ? byDate[selected] ?? [] : [];
  const selectedEntry = selectedEntries.find((entry) => entry.diary_id === selectedDiaryId) ?? selectedEntries[0];

  const detail = useApi(() => diariesApi.detail(selectedEntry?.diary_id as Uuid),
    [selectedEntry?.diary_id, selectedElderId, isFocused], { enabled: !!selectedEntry && isFocused });
  const selectedDetail = detail.data?.diary_id === selectedEntry?.diary_id
    && detail.data?.user_id === selectedElderId ? detail.data : null;

  React.useEffect(() => {
    formVersion.current += 1;
    setReaction(null);
    setMessage("");
    setSubmitted(false);
    setSubmitting(false);
    setReactionError(null);
  }, [selectedEntry?.diary_id, selectedElderId]);

  const ownReactions = selectedDetail?.reactions.filter((item) => item.reactor_id === userId) ?? [];
  const alreadySentMessage = ownReactions.some((item) => item.reaction_type === "message");
  const messageToSend = message.trim();
  const canSendReaction = !!reaction && !ownReactions.some((item) => item.reaction_type === reaction);
  const canSendMessage = !!messageToSend && !alreadySentMessage;
  const canSubmit = !!selectedDetail && !detail.loading && (canSendReaction || canSendMessage) && !submitting;

  const submit = async () => {
    if (!selectedEntry || !canSubmit) return;
    const version = formVersion.current;
    setSubmitting(true);
    setReactionError(null);
    try {
      if (canSendReaction && reaction) await diariesApi.react(selectedEntry.diary_id, reaction);
      if (canSendMessage) await diariesApi.react(selectedEntry.diary_id, "message", messageToSend);
      if (version !== formVersion.current) return;
      setSubmitted(true);
      setReaction(null);
      setMessage("");
    } catch (cause) {
      if (version === formVersion.current) setReactionError(apiErrorMessage(cause));
    } finally {
      list.reload();
      if (version === formVersion.current) {
        detail.reload();
        setSubmitting(false);
      }
    }
  };

  const shiftMonth = (by: number) => {
    setSelected(null);
    setSelectedDiaryId(null);
    setCursor(new Date(year, month + by, 1));
  };
  const goToday = () => {
    setCursor(new Date(`${today.slice(0, 7)}-01T12:00:00`));
    setSelected(today);
    setSelectedDiaryId(null);
  };
  const header = <ScreenHeader color={guardian.blue} title="기록" subtitle="하루의 이야기를 읽고 마음을 전해 주세요." right={<GuardianHeaderActions />} />;
  const offset = new Date(year, month, 1).getDay();
  const dayCount = new Date(year, month + 1, 0).getDate();
  const loadedCount = Object.values(byDate).reduce((count, entries) => count + entries.length, 0);

  return (
    <Screen header={header} background={colors.screenBackground} contentStyle={styles.screenContent}>
      <View style={styles.container}>
        <ElderSelector />
        {!selectedElderId ? (
          <EmptyState message="먼저 대시보드에서 어르신을 선택해 주세요." icon="people-outline" />
        ) : list.error ? (
          <ErrorState message={guardianAccessErrorMessage(list.error, "일기")} onRetry={list.error.isForbidden ? undefined : list.reload} />
        ) : (
          <View style={[styles.layout, wide && styles.wideLayout]}>
            <View style={[styles.sidebar, wide && styles.wideSidebar]}>
              <Card style={styles.calendar}>
                <View style={styles.monthRow}>
                  <Pressable onPress={() => shiftMonth(-1)} accessibilityRole="button" accessibilityLabel="이전 달" style={styles.monthButton}>
                    <Ionicons name="chevron-back" size={20} color={colors.foreground} />
                  </Pressable>
                  <Text style={styles.monthLabel}>{year}년 {month + 1}월</Text>
                  <Pressable onPress={() => shiftMonth(1)} accessibilityRole="button" accessibilityLabel="다음 달" style={styles.monthButton}>
                    <Ionicons name="chevron-forward" size={20} color={colors.foreground} />
                  </Pressable>
                </View>
                <View style={styles.calendarMeta}>
                  <Caption>{currentList ? `일기 ${loadedCount}편 · 기록한 날 ${Object.keys(byDate).length}일` : "기록을 불러오는 중"}</Caption>
                  <Pressable onPress={goToday} accessibilityRole="button" accessibilityLabel="오늘 기록 보기" style={styles.todayButton}>
                    <Text style={styles.todayLabel}>오늘</Text>
                  </Pressable>
                </View>
                <View style={styles.weekRow}>
                  {WEEKDAYS.map((day, i) => <Text key={day} style={[styles.weekday, { color: i === 0 ? colors.destructive : i === 6 ? guardian.blue : colors.mutedForeground }]}>{day}</Text>)}
                </View>
                <View style={styles.grid}>
                  {Array.from({ length: offset }, (_, i) => <View key={`pad-${i}`} style={styles.cell} />)}
                  {Array.from({ length: dayCount }, (_, i) => {
                    const day = i + 1;
                    const date = isoDateOf(new Date(year, month, day));
                    const entries = byDate[date] ?? [];
                    const warning = warningDays.has(date);
                    const active = selected === date;
                    const isToday = date === today;
                    return (
                      <Pressable key={date} onPress={() => { setSelected(date); setSelectedDiaryId(null); }}
                        accessibilityRole="button" accessibilityState={{ selected: active }}
                        accessibilityLabel={`${dateLabel(date)}${isToday ? " 오늘" : ""}, 일기 ${entries.length}편${warning ? ", 검사 확인 필요" : ""}`}
                        style={[styles.cell, styles.dayCell, active && styles.daySelected, !active && isToday && styles.dayToday]}>
                        <Text style={[styles.dayNumber, active && styles.daySelectedText]}>{day}</Text>
                        <View style={styles.dayMarker}>
                          {warning ? <Ionicons name="alert-circle" size={13} color={active ? colors.white : colors.destructive} />
                            : entries.length ? <View style={[styles.diaryDot, active && { backgroundColor: colors.white }]} /> : null}
                        </View>
                      </Pressable>
                    );
                  })}
                </View>
                <View style={styles.legend}>
                  <View style={styles.legendItem}><View style={styles.diaryDot} /><Caption>일기 있는 날</Caption></View>
                  <View style={styles.legendItem}><Ionicons name="alert-circle" size={13} color={colors.destructive} /><Caption>검사 확인 필요</Caption></View>
                </View>
              </Card>
              <Caption style={styles.calendarHint}>날짜를 선택하면 그날의 이야기를 볼 수 있어요.</Caption>
              {report.error?.isForbidden ? <Caption style={styles.permissionNotice}>{guardianAccessErrorMessage(report.error, "검사")}</Caption> : null}
            </View>

            <View style={[styles.readingColumn, wide && styles.wideReadingColumn]}>
              {!currentList ? <Card><LoadingState label="기록을 불러오는 중이에요" /></Card>
                : !selected ? <Card><EmptyState message="달력에서 읽고 싶은 날짜를 선택해 주세요." icon="calendar-outline" /></Card>
                : !selectedEntry ? <Card><EmptyState message={`${dateLabel(selected)}에는 아직 일기가 없어요.`} icon="book-outline" /></Card>
                : (
                  <>
                    <View style={styles.dayHeading}>
                      <View>
                        <Caption style={styles.eyebrow}>{selected === today ? "오늘의 기록" : "선택한 날의 기록"}</Caption>
                        <Text style={styles.dateTitle}>{dateLabel(selected)}</Text>
                      </View>
                      <Caption>일기 {selectedEntries.length}편</Caption>
                    </View>
                    {selectedEntries.length > 1 ? (
                      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.diaryChoices}>
                        {selectedEntries.map((entry, index) => {
                          const active = entry.diary_id === selectedEntry.diary_id;
                          return <Pressable key={entry.diary_id} onPress={() => setSelectedDiaryId(entry.diary_id)}
                            accessibilityRole="button" accessibilityState={{ selected: active }} accessibilityLabel={`${diaryTime(entry.written_at)}, ${entry.title ?? `일기 ${index + 1}`} 선택`}
                            style={[styles.diaryChoice, active && styles.diaryChoiceSelected]}>
                            <Text numberOfLines={1} style={[styles.diaryChoiceTitle, active && { color: guardian.blueDark }]}>{entry.title ?? `일기 ${index + 1}`}</Text>
                            <Caption>{diaryTime(entry.written_at)}</Caption>
                          </Pressable>;
                        })}
                      </ScrollView>
                    ) : null}
                    <Card style={styles.diaryCard}>
                      <View style={styles.diaryHeader}>
                        <View style={styles.diaryHeading}>
                          <Text style={styles.diaryTitle}>{selectedEntry.title ?? "오늘의 이야기"}</Text>
                          <Caption>{diaryTime(selectedEntry.written_at)} · {["session", "daily_summary"].includes(selectedEntry.source_type) ? "AI가 정리한 일기" : "직접 남긴 일기"}</Caption>
                        </View>
                        {selectedEntry.mood || selectedEntry.mood_level != null ? <Text style={styles.mood} accessibilityLabel="일기에 기록된 기분">{moodEmoji(selectedEntry.mood, selectedEntry.mood_level)}</Text> : null}
                      </View>
                      <View style={styles.divider} />
                      {detail.error ? <ErrorState message={guardianAccessErrorMessage(detail.error, "일기")} onRetry={detail.error.isForbidden ? undefined : detail.reload} />
                        : !selectedDetail ? <LoadingState label="일기를 불러오는 중이에요" />
                        : (
                          <>
                            <Body style={styles.detailBody}>{selectedDetail.content}</Body>
                            {warningDays.has(selected) ? <View style={styles.riskNotice}><Ionicons name="alert-circle-outline" size={18} color={colors.destructive} /><Caption style={styles.riskText}>이 날짜의 검사에서 확인이 필요한 신호가 있어요. 추이 탭에서 자세히 확인해 주세요.</Caption></View> : null}
                            <View style={styles.divider} />
                            {selectedDetail.reactions.length > 0 ? <View style={styles.sentReactions}><DiaryReactionList reactions={selectedDetail.reactions} title="전달된 마음" grouped /></View> : null}
                            <Body style={styles.sectionTitle}>마음 전하기</Body>
                            <Caption>따뜻한 반응이나 응원 한마디를 남겨 주세요.</Caption>
                            <View style={styles.reactionRow}>
                              {DIARY_REACTIONS.map(({ type, emoji, label }) => {
                                const active = reaction === type;
                                const sent = ownReactions.some((item) => item.reaction_type === type);
                                const disabled = sent || submitting || detail.loading;
                                return <Pressable key={type} onPress={() => { setReaction(active ? null : type); setSubmitted(false); }} disabled={disabled}
                                  accessibilityRole="button" accessibilityState={{ selected: active, disabled }} accessibilityLabel={`${label}${sent ? " 전달됨" : " 반응 선택"}`}
                                  style={[styles.reactionButton, active && styles.reactionSelected, sent && styles.reactionSent]}>
                                  <Text style={styles.reactionEmoji}>{emoji}</Text>
                                  <Text style={[styles.reactionLabel, (active || sent) && { color: guardian.blueDark }]}>{sent ? "전달됨" : label}</Text>
                                </Pressable>;
                              })}
                            </View>
                            <TextInput value={message} onChangeText={(value) => { setMessage(value); setSubmitted(false); }} editable={!alreadySentMessage && !submitting}
                              multiline placeholder={alreadySentMessage ? "응원 메시지를 이미 전달했어요" : "예: 즐거운 하루 보내셨네요. 내일 전화드릴게요."}
                              placeholderTextColor={colors.mutedForeground} style={styles.input} accessibilityLabel="응원 메시지 (선택)" />
                            <View style={styles.formFooter}>
                              <Caption style={styles.formHint}>{alreadySentMessage ? "전달한 메시지는 위에서 확인할 수 있어요." : "메시지는 선택 사항이에요."}</Caption>
                              <Pressable onPress={() => void submit()} disabled={!canSubmit} accessibilityRole="button" accessibilityLabel="마음 전달하기" accessibilityState={{ disabled: !canSubmit }}
                                style={[styles.submitButton, !canSubmit && styles.submitDisabled]}>
                                <Text style={[styles.submitLabel, !canSubmit && { color: colors.mutedForeground }]}>{submitting ? "전달 중…" : "마음 전달하기"}</Text>
                              </Pressable>
                            </View>
                            {submitted ? <View style={styles.submitted} accessibilityLiveRegion="polite"><Ionicons name="checkmark-circle" size={18} color={guardian.blue} /><Text style={styles.submittedLabel}>마음을 전달했어요.</Text></View> : null}
                            {reactionError ? <Text style={styles.reactionError} accessibilityLiveRegion="polite">{reactionError}</Text> : null}
                          </>
                        )}
                    </Card>
                  </>
                )}
            </View>
          </View>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screenContent: { paddingTop: spacing.xl },
  container: { width: "100%", maxWidth: 1160, alignSelf: "center" },
  layout: { gap: spacing.xl },
  wideLayout: { flexDirection: "row", alignItems: "flex-start", gap: spacing.xxl },
  sidebar: { width: "100%" },
  wideSidebar: { width: 336, flexShrink: 0 },
  calendar: { padding: spacing.lg },
  monthRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  monthButton: { width: 44, height: 44, alignItems: "center", justifyContent: "center", borderRadius: radius.md },
  monthLabel: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold, color: colors.foreground },
  calendarMeta: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: spacing.xs, marginBottom: spacing.lg },
  todayButton: { minHeight: 36, paddingHorizontal: spacing.md, justifyContent: "center", borderRadius: radius.pill, backgroundColor: guardian.blueLight },
  todayLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: guardian.blueDark },
  weekRow: { flexDirection: "row", marginBottom: spacing.sm },
  weekday: { flex: 1, textAlign: "center", fontSize: fontSize.caption, fontWeight: fontWeight.medium },
  grid: { flexDirection: "row", flexWrap: "wrap" },
  cell: { width: `${100 / 7}%`, height: 48 },
  dayCell: { justifyContent: "center", alignItems: "center", borderRadius: radius.md },
  daySelected: { backgroundColor: guardian.blue },
  dayToday: { backgroundColor: guardian.blueLight },
  dayNumber: { fontSize: fontSize.body, fontWeight: fontWeight.medium, color: colors.foreground },
  daySelectedText: { color: colors.white, fontWeight: fontWeight.bold },
  dayMarker: { height: 14, alignItems: "center", justifyContent: "center", marginTop: 2 },
  diaryDot: { width: 5, height: 5, borderRadius: radius.pill, backgroundColor: guardian.blue },
  legend: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.md, marginTop: spacing.md },
  legendItem: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  calendarHint: { marginTop: spacing.md, paddingHorizontal: spacing.xs, lineHeight: 20 },
  permissionNotice: { marginTop: spacing.md, color: colors.warning },
  readingColumn: { width: "100%", minWidth: 0, gap: spacing.lg },
  wideReadingColumn: { flex: 1, width: undefined },
  dayHeading: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md },
  eyebrow: { color: guardian.blueDark, marginBottom: spacing.xs },
  dateTitle: { fontSize: fontSize.display, fontWeight: fontWeight.bold, color: colors.foreground },
  diaryChoices: { gap: spacing.sm, paddingBottom: spacing.xs },
  diaryChoice: { minWidth: 140, maxWidth: 230, padding: spacing.md, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, backgroundColor: colors.card, gap: spacing.xs },
  diaryChoiceSelected: { borderColor: guardian.blue, backgroundColor: guardian.blueLight },
  diaryChoiceTitle: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.foreground },
  diaryCard: { padding: spacing.xl },
  diaryHeader: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  diaryHeading: { flex: 1, gap: spacing.sm },
  diaryTitle: { fontSize: fontSize.title, fontWeight: fontWeight.semibold, color: colors.foreground },
  mood: { fontSize: 30 },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: spacing.xl },
  detailBody: { fontSize: fontSize.bodyLg, lineHeight: 28 },
  riskNotice: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, backgroundColor: colors.destructiveLight, borderRadius: radius.md, padding: spacing.md, marginTop: spacing.lg },
  riskText: { flex: 1, color: guardian.dangerText, lineHeight: 20 },
  sentReactions: { marginBottom: spacing.xl, padding: spacing.md, backgroundColor: guardian.blueLight, borderRadius: radius.md },
  sectionTitle: { fontWeight: fontWeight.semibold, marginBottom: spacing.xs },
  reactionRow: { flexDirection: "row", gap: spacing.xs, marginTop: spacing.lg },
  reactionButton: { flex: 1, minHeight: 64, borderRadius: radius.md, borderWidth: 1, borderColor: colors.border, alignItems: "center", justifyContent: "center", gap: spacing.xs },
  reactionSelected: { borderColor: guardian.blue, backgroundColor: guardian.blueLight },
  reactionSent: { borderColor: guardian.blueLight, backgroundColor: guardian.blueLight },
  reactionEmoji: { fontSize: 24 },
  reactionLabel: { fontSize: fontSize.micro, color: colors.mutedForeground },
  input: { marginTop: spacing.md, minHeight: 88, textAlignVertical: "top", padding: spacing.md, backgroundColor: colors.inputBackground, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, fontSize: fontSize.body, color: colors.foreground, lineHeight: 22 },
  formFooter: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: spacing.md, marginTop: spacing.md },
  formHint: { flexShrink: 1 },
  submitButton: { minHeight: 44, paddingHorizontal: spacing.lg, justifyContent: "center", alignItems: "center", backgroundColor: guardian.blue, borderRadius: radius.md },
  submitDisabled: { backgroundColor: colors.muted },
  submitLabel: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.white },
  submitted: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginTop: spacing.md },
  submittedLabel: { fontSize: fontSize.caption, color: guardian.blueDark },
  reactionError: { fontSize: fontSize.caption, color: colors.destructive, marginTop: spacing.md },
});
