import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi, guardian as guardianApi, reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage, guardianAccessErrorMessage } from "@/api/errors";
import { monthDayLabel, moodEmoji } from "@/utils/format";
import type { GuardianNav } from "@/navigation/types";
import type { GuardianReportResponse } from "@/api/types";
import { colors, guardian, onHeader, spacing, radius, fontSize, fontWeight } from "@/theme";
import ScoreTrendChart, { type TrendPoint } from "@/components/ScoreTrendChart";
import {
  Screen,
  ScreenHeader,
  Card,
  Badge,
  Button,
  Body,
  Caption,
  ProgressBar,
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/components/ui";

/**
 * Guardian dashboard: `GET /guardian/{guardian_id}/elders` picks the elder, then
 * `GET /guardian/{guardian_id}/report` fills the cards.
 *
 * Scores appear here and only here — the guardian audience is the one the spec
 * allows them for. A link that is not consented to yet comes back as a 403, and
 * the spec asks for an explanation instead of an empty dashboard.
 *
 * Card order is the design's: 피보호자 현황 → 경보 → 지표 → 추이 → 최근 일기.
 */
const WEEKLY_TARGET_SESSIONS = 7;
const DEFAULT_SCORE_MAX = 30;
const RECENT_DIARY_LIMIT = 3;

const RISK_BADGE: Record<string, { label: string; color: string; background: string }> = {
  low: { label: "정상 범위", color: guardian.blueDark, background: guardian.blueLight },
  caution: { label: "관찰 필요", color: colors.warning, background: colors.warningLight },
  warning: { label: "주의 필요", color: colors.destructive, background: colors.destructiveLight },
};

function riskBadge(level: string | null) {
  return RISK_BADGE[level ?? "low"] ?? RISK_BADGE.low;
}

/** `trend_points` is already ordered by the server; only scored days can be drawn. */
function chartPoints(report: GuardianReportResponse): TrendPoint[] {
  return report.trend_points.flatMap((p) => {
    if (p.display_score === null) return [];
    return [{ label: monthDayLabel(p.date), score: p.display_score }];
  });
}

function averageScore(points: TrendPoint[]): string {
  if (points.length === 0) return "—";
  const mean = points.reduce((sum, p) => sum + p.score, 0) / points.length;
  return mean.toFixed(1);
}

function Indicator({ label, value, unit, color }: { label: string; value: string; unit?: string; color: string }) {
  return (
    <View style={styles.indicator}>
      <Text style={[styles.indicatorValue, { color }]}>{value}</Text>
      {unit ? <Caption style={{ marginTop: 2 }}>{unit}</Caption> : null}
      <Caption style={{ marginTop: 6 }}>{label}</Caption>
    </View>
  );
}

export default function GuardianDashboardScreen() {
  const navigation = useNavigation<GuardianNav>();
  const { userId, userName, selectedElderId, setSelectedElderId } = useApp();

  const elders = useApi(() => guardianApi.elders(userId as string, "active"), [userId], {
    enabled: !!userId,
  });

  // The dashboard is what chooses the elder the other guardian tabs read.
  React.useEffect(() => {
    if (!elders.data) return;
    const stillLinked = elders.data.elders.some((elder) => elder.elder_id === selectedElderId);
    if (!stillLinked) setSelectedElderId(elders.data.elders[0]?.elder_id ?? null);
  }, [elders.data, selectedElderId, setSelectedElderId]);

  const elderId = selectedElderId ?? elders.data?.elders[0]?.elder_id ?? null;
  const selectedElder = elders.data?.elders.find((elder) => elder.elder_id === elderId) ?? null;

  const report = useApi(
    () => reports.guardianReport(userId as string, elderId as string),
    [userId, elderId],
    { enabled: !!userId && !!elderId },
  );

  const recentDiaries = useApi(
    () => diariesApi.listForUser(elderId as string, { limit: RECENT_DIARY_LIMIT }),
    [elderId],
    { enabled: !!elderId },
  );

  const header = (
    <ScreenHeader
      color={guardian.blue}
      eyebrow="보호자 모드"
      title={userName ? `${userName} 님` : "보호자"}
      subtitle={
        report.data?.elder_id === elderId
          ? `${report.data.elder_name}님의 인지 상태를 모니터링 중`
          : selectedElder
            ? `${selectedElder.elder_name}님의 인지 상태를 확인 중`
            : undefined
      }
      right={
        <View style={styles.headerActions}>
          <Pressable
            onPress={() => navigation.navigate("GuardianConnections")}
            accessibilityRole="button"
            accessibilityLabel="보호자 연결 관리"
            hitSlop={8}
            style={styles.gear}
          >
            <Ionicons name="people-outline" size={18} color={colors.white} />
          </Pressable>
          <Pressable
            onPress={() => navigation.navigate("GuardianAppSettings")}
            accessibilityRole="button"
            accessibilityLabel="앱 설정"
            hitSlop={8}
            style={styles.gear}
          >
            <Ionicons name="settings-outline" size={18} color={colors.white} />
          </Pressable>
        </View>
      }
    />
  );

  if (elders.error) {
    return (
      <Screen header={header}>
        <ErrorState message={apiErrorMessage(elders.error)} onRetry={elders.reload} />
      </Screen>
    );
  }

  if (elders.data && elders.data.elders.length === 0) {
    return (
      <Screen header={header}>
        <EmptyState
          message={"연결된 어르신이 없어요.\n초대 코드로 먼저 연결해 주세요."}
          icon="people-outline"
        />
        <Button
          label="초대 코드 만들기"
          icon="person-add-outline"
          onPress={() => navigation.navigate("GuardianConnections")}
          style={{ backgroundColor: guardian.blue }}
        />
      </Screen>
    );
  }

  if (report.error) {
    return (
      <Screen header={header}>
        <ErrorState
          message={
            guardianAccessErrorMessage(report.error, "검사·요약")
          }
          onRetry={report.error.isForbidden ? undefined : report.reload}
        />
      </Screen>
    );
  }

  if (!report.data) {
    return (
      <Screen header={header}>
        <LoadingState />
      </Screen>
    );
  }

  const data = report.data;
  const elderItems = elders.data?.elders ?? [];
  const badge = riskBadge(data.latest_risk_level);
  const scoreMax = data.latest_score_max ?? DEFAULT_SCORE_MAX;
  const score = data.latest_display_score;
  const hasScreening = score !== null;
  const scoreColor = !hasScreening
    ? colors.mutedForeground
    : data.latest_risk_level === "low"
      ? guardian.blue
      : colors.destructive;
  const points = chartPoints(data);
  const alert = data.recent_alerts[0] ?? null;

  // A missing `activity_summary7d` means the week has not been aggregated, not
  // that participation was zero — so the count is left unknown rather than 0.
  const sessions7d = data.activity_summary7d?.session_count ?? null;
  const scoreDelta = points.length >= 2 ? points[points.length - 1].score - points[0].score : null;

  return (
    <Screen header={header}>
      {elderItems.length > 1 ? (
        <Card style={styles.elderSelector}>
          <Caption>확인할 어르신</Caption>
          <View style={styles.elderOptions}>
            {elderItems.map((elder) => {
              const selected = elder.elder_id === elderId;
              return (
                <Pressable
                  key={elder.elder_id}
                  onPress={() => setSelectedElderId(elder.elder_id)}
                  accessibilityRole="button"
                  accessibilityLabel={`${elder.elder_name} 어르신 선택`}
                  accessibilityState={{ selected }}
                  style={[styles.elderOption, selected && styles.elderOptionSelected]}
                >
                  <Text style={[styles.elderOptionLabel, selected && { color: guardian.blueDark }]}>
                    {elder.elder_name}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </Card>
      ) : null}

      {/* ② 피보호자 현황 */}
      <Card>
        <Caption style={styles.eyebrow}>피보호자 현황</Caption>
        <View style={styles.rowBetween}>
          <View style={{ flex: 1 }}>
            <Text style={styles.elderName}>{data.elder_name} 어르신</Text>
            <Caption style={{ marginTop: 2 }}>
              {data.last_session_at
                ? `마지막 검사 · ${monthDayLabel(data.last_session_at)}`
                : "아직 검사 기록이 없어요"}
            </Caption>
          </View>
          <View style={{ alignItems: "flex-end", gap: 4 }}>
            <Text style={[styles.score, { color: scoreColor }]}>
              {score !== null ? `${score}점` : "—"}
            </Text>
            {hasScreening ? (
              <Badge label={badge.label} color={badge.color} background={badge.background} />
            ) : (
              <Badge label="분석 없음" color={colors.mutedForeground} background={colors.muted} />
            )}
          </View>
        </View>
        {score !== null ? (
          <>
            <ProgressBar
              value={Math.round((score / scoreMax) * 100)}
              color={scoreColor}
              height={8}
            />
            <View style={styles.rowBetween}>
              <Caption>0점</Caption>
              <Caption>{scoreMax}점</Caption>
            </View>
          </>
        ) : null}
      </Card>

      {/* ③ 경보 — only when the server actually raised one */}
      {alert ? (
        <View style={styles.alertCard}>
          <View style={styles.alertHead}>
            <Ionicons name="warning-outline" size={20} color={colors.destructive} />
            <View style={{ flex: 1 }}>
              <Text style={styles.alertTitle}>{alert.title}</Text>
              <Text style={styles.alertBody}>{alert.body}</Text>
            </View>
          </View>
          <Pressable
            onPress={() => navigation.navigate("GuardianCounselingCenters")}
            accessibilityRole="button"
            accessibilityLabel="전문의 상담 예약"
            style={styles.alertAction}
          >
            <Text style={styles.alertActionLabel}>전문의 상담 예약</Text>
          </Pressable>
        </View>
      ) : null}

      {/* ④ 이번 주 핵심 지표 */}
      <Card style={{ marginTop: spacing.lg }}>
        <Caption style={styles.eyebrow}>이번 주 핵심 지표</Caption>
        <View style={styles.indicatorRow}>
          <Indicator
            label="대화 완료"
            value={sessions7d === null ? "—" : `${sessions7d}/${WEEKLY_TARGET_SESSIONS}`}
            unit={sessions7d === null ? undefined : "일"}
            color={guardian.blue}
          />
          <Indicator label="평균 점수" value={averageScore(points)} unit="점" color={colors.accent} />
          <Indicator
            label="위험 지표"
            value={hasScreening ? badge.label : "—"}
            color={hasScreening ? badge.color : colors.mutedForeground}
          />
        </View>
      </Card>

      {/* ⑤ 인지 점수 추이 */}
      <Card style={{ marginTop: spacing.lg }}>
        <View style={styles.rowBetween}>
          <Body style={{ fontWeight: fontWeight.semibold }}>인지 점수 추이</Body>
          <Pressable
            onPress={() => navigation.navigate("GuardianTabs", { screen: "GuardianChart" })}
            accessibilityRole="button"
            accessibilityLabel="인지 점수 추이 상세 보기"
            hitSlop={8}
            style={styles.linkRow}
          >
            <Text style={styles.link}>상세 보기</Text>
            <Ionicons name="chevron-forward" size={14} color={guardian.blue} />
          </Pressable>
        </View>

        {points.length === 0 ? (
          <Body style={{ marginTop: spacing.md }}>아직 분석된 검사가 없어요.</Body>
        ) : (
          <>
            <View style={styles.trendMeta}>
              <Caption>최근 {points.length}회</Caption>
              {scoreDelta !== null ? (
                <View
                  style={[
                    styles.deltaPill,
                    {
                      backgroundColor:
                        scoreDelta < 0 ? colors.destructiveLight : guardian.blueLight,
                    },
                  ]}
                >
                  <Ionicons
                    name={scoreDelta < 0 ? "trending-down" : "trending-up"}
                    size={12}
                    color={scoreDelta < 0 ? colors.destructive : guardian.blue}
                  />
                  <Text
                    style={[
                      styles.deltaLabel,
                      { color: scoreDelta < 0 ? colors.destructive : guardian.blue },
                    ]}
                  >
                    {scoreDelta > 0 ? `+${scoreDelta}` : scoreDelta}점
                  </Text>
                </View>
              ) : null}
            </View>

            <ScoreTrendChart points={points} variant="compact" />

            <View style={styles.legendRow}>
              <View style={styles.legendItem}>
                <View style={[styles.legendDot, { backgroundColor: guardian.blue }]} />
                <Caption>정상 범위 ≥ 24</Caption>
              </View>
              <View style={styles.legendItem}>
                <View style={[styles.legendDot, { backgroundColor: colors.accent }]} />
                <Caption>정상 하한 24</Caption>
              </View>
            </View>
          </>
        )}
      </Card>

      {/* ⑥ 최근 일기 */}
      <Card style={{ marginTop: spacing.lg }}>
        <View style={styles.rowBetween}>
          <Body style={{ fontWeight: fontWeight.semibold }}>최근 일기</Body>
          <Pressable
            onPress={() => navigation.navigate("GuardianTabs", { screen: "GuardianDiary" })}
            accessibilityRole="button"
            accessibilityLabel="일기 전체 보기"
            hitSlop={8}
          >
            <Text style={styles.link}>전체 보기</Text>
          </Pressable>
        </View>

        {recentDiaries.data && recentDiaries.data.diaries.length > 0 ? (
          recentDiaries.data.diaries.map((d, i, arr) => (
            <Pressable
              key={d.diary_id}
              onPress={() => navigation.navigate("GuardianTabs", { screen: "GuardianDiary" })}
              accessibilityRole="button"
              accessibilityLabel={`${monthDayLabel(d.written_at)} 일기 보기`}
              style={[styles.diaryRow, i < arr.length - 1 && styles.diaryBorder]}
            >
              <Text style={styles.diaryMood}>{moodEmoji(d.mood, d.mood_level)}</Text>
              <View style={{ flex: 1 }}>
                <Caption>{monthDayLabel(d.written_at)}</Caption>
                <Body numberOfLines={1} style={{ marginTop: 2 }}>
                  {d.preview ?? d.title ?? ""}
                </Body>
              </View>
              <Ionicons name="chevron-forward" size={16} color={colors.mutedForeground} />
            </Pressable>
          ))
        ) : (
          <Body style={{ marginTop: spacing.md }}>아직 작성된 일기가 없어요.</Body>
        )}
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerActions: { flexDirection: "row", gap: spacing.sm },
  gear: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: onHeader.surface,
    alignItems: "center",
    justifyContent: "center",
  },
  elderSelector: { marginBottom: spacing.lg, gap: spacing.sm },
  elderOptions: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  elderOption: {
    minHeight: 44,
    justifyContent: "center",
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    paddingHorizontal: spacing.lg,
  },
  elderOptionSelected: { borderColor: guardian.blue, backgroundColor: guardian.blueLight },
  elderOptionLabel: {
    fontSize: fontSize.caption,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
  },
  eyebrow: { letterSpacing: 0.7, marginBottom: spacing.md },
  rowBetween: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: spacing.xs,
  },
  elderName: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },
  score: { fontSize: 32, fontWeight: fontWeight.bold, lineHeight: 34 },

  alertCard: {
    marginTop: spacing.lg,
    backgroundColor: colors.destructiveLight,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: guardian.dangerBorder,
    padding: spacing.lg,
  },
  alertHead: { flexDirection: "row", gap: spacing.md, marginBottom: spacing.md },
  alertTitle: {
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
    color: colors.destructive,
  },
  alertBody: {
    fontSize: fontSize.caption,
    color: guardian.dangerText,
    lineHeight: 20,
    marginTop: 2,
  },
  alertAction: {
    height: 40,
    borderRadius: radius.md,
    backgroundColor: colors.destructive,
    alignItems: "center",
    justifyContent: "center",
  },
  alertActionLabel: {
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
    color: colors.white,
  },

  indicatorRow: { flexDirection: "row", gap: spacing.lg },
  indicator: { flex: 1, alignItems: "center" },
  indicatorValue: { fontSize: 22, fontWeight: fontWeight.bold, lineHeight: 24 },

  linkRow: { flexDirection: "row", alignItems: "center", gap: 2 },
  link: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: guardian.blue },

  trendMeta: { flexDirection: "row", alignItems: "center", gap: spacing.md, marginVertical: spacing.md },
  deltaPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 3,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
  },
  deltaLabel: { fontSize: fontSize.micro, fontWeight: fontWeight.semibold },

  legendRow: {
    flexDirection: "row",
    gap: spacing.lg,
    marginTop: spacing.sm,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 6 },
  legendDot: { width: 8, height: 8, borderRadius: 4 },

  diaryRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
  },
  diaryBorder: { borderBottomWidth: 1, borderBottomColor: colors.border },
  diaryMood: { fontSize: 18 },
});
