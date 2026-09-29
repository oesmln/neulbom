import React from "react";
import { View, Pressable, StyleSheet } from "react-native";
import { useIsFocused } from "@react-navigation/native";

import { useApp } from "@/store/AppContext";
import { reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { guardianAccessErrorMessage } from "@/api/errors";
import { monthDayLabel } from "@/utils/format";
import { sharesCistBaseline } from "@/utils/aiRiskTrend";
import type { GuardianAiRiskTrendPoint, HistoryRecordResponse } from "@/api/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
import ScoreTrendChart, { type TrendPoint } from "@/components/ScoreTrendChart";
import AiRiskTrendChart from "@/components/AiRiskTrendChart";
import {
  Screen,
  ScreenHeader,
  Card,
  Body,
  Caption,
  EmptyState,
  ErrorState,
  LoadingState,
  SentenceText as Text,
} from "@/components/ui";
import GuardianHeaderActions from "@/components/GuardianHeaderActions";

const PERIODS = [
  { key: "1m", label: "1개월", months: 1 },
  { key: "3m", label: "3개월", months: 3 },
  { key: "6m", label: "6개월", months: 6 },
  { key: "1y", label: "1년", months: 12 },
] as const;

type PeriodKey = (typeof PERIODS)[number]["key"];
type ViewMode = "all" | "cist";

const HISTORY_LIMIT = 100;
const SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000;

function dateRangeInSeoul(months: number) {
  const today = new Date(Date.now() + SEOUL_OFFSET_MS);
  const year = today.getUTCFullYear();
  const month = today.getUTCMonth();
  const day = today.getUTCDate();
  const lastDayOfStartMonth = new Date(Date.UTC(year, month - months + 1, 0)).getUTCDate();
  const from = new Date(Date.UTC(year, month - months, Math.min(day, lastDayOfStartMonth)));
  return {
    fromDate: from.toISOString().slice(0, 10),
    toDate: today.toISOString().slice(0, 10),
  };
}

function scoreOf(record: HistoryRecordResponse): number | null {
  return record.display_score ?? record.screening_reference_score ?? null;
}

/**
 * How many of the most recent readings fell in a row.
 *
 * The design labels this in days, but the endpoint aggregates by period — so it
 * is reported in readings (회), which is what the data actually supports.
 */
function decliningRun(points: TrendPoint[]): number {
  let run = 0;
  for (let i = points.length - 1; i > 0; i -= 1) {
    if (points[i].score < points[i - 1].score) run += 1;
    else break;
  }
  return run;
}

function riskIndex(point: GuardianAiRiskTrendPoint): number {
  return Math.round(point.risk_score * 100);
}

function riskChange(first: GuardianAiRiskTrendPoint, last: GuardianAiRiskTrendPoint): string {
  const difference = riskIndex(last) - riskIndex(first);
  return difference === 0 ? "변화 없음" : `${Math.abs(difference)} ${difference > 0 ? "상승" : "하락"}`;
}

export default function GuardianChartScreen() {
  const isFocused = useIsFocused();
  const { userId, selectedElderId } = useApp();
  const [period, setPeriod] = React.useState<PeriodKey>("6m");
  const [viewMode, setViewMode] = React.useState<ViewMode>("all");

  const dateRange = React.useMemo(() => {
    const months = PERIODS.find((item) => item.key === period)?.months ?? 6;
    return dateRangeInSeoul(months);
  }, [period]);

  const report = useApi(
    () => reports.guardianReport(userId as string, selectedElderId as string),
    [userId, selectedElderId, isFocused],
    { enabled: !!userId && !!selectedElderId && isFocused },
  );

  const history = useApi(
    () =>
      reports.cognitiveHistory(selectedElderId as string, {
        limit: HISTORY_LIMIT,
        aggregation: "day",
        ...dateRange,
      }),
    [selectedElderId, dateRange.fromDate, dateRange.toDate, isFocused],
    { enabled: !!selectedElderId && isFocused },
  );

  const header = (
    <ScreenHeader
      color={guardian.blue}
      title="인지 위험 신호 추이"
      subtitle={
        report.data ? `${report.data.elder_name} · 보호자 모니터링` : "보호자 모니터링"
      }
      right={<GuardianHeaderActions />}
    />
  );

  if (!selectedElderId) {
    return (
      <Screen header={header}>
        <EmptyState message="먼저 대시보드에서 어르신을 선택해 주세요." icon="people-outline" />
      </Screen>
    );
  }

  if (history.error) {
    return (
      <Screen header={header}>
        <ErrorState
          message={
            guardianAccessErrorMessage(history.error, "인지 추이")
          }
          onRetry={history.error.isForbidden ? undefined : history.reload}
        />
      </Screen>
    );
  }

  if (!history.data) {
    return (
      <Screen header={header}>
        <LoadingState />
      </Screen>
    );
  }

  // Oldest first so the line reads left to right. Records without a score are
  // dropped rather than plotted at zero — a zero point reads as "very low",
  // which is the opposite of "not measured".
  const points: TrendPoint[] = [...history.data.records]
    .sort((a, b) => (a.analyzed_at ?? "").localeCompare(b.analyzed_at ?? ""))
    .flatMap((record) => {
      const score = scoreOf(record);
      if (score === null) return [];
      return [{ label: record.analyzed_at ? monthDayLabel(record.analyzed_at) : "", score }];
    });

  const delta = points.length >= 2 ? points[points.length - 1].score - points[0].score : null;
  const run = decliningRun(points);
  const periodLabel = PERIODS.find((p) => p.key === period)?.label ?? "";
  const aiRiskPoints = history.data.ai_risk_trend_points ?? [];
  const visibleAiRiskPoints = viewMode === "cist"
    ? aiRiskPoints.filter((point) => point.point_type === "full_cist")
    : aiRiskPoints;
  const cistPoints = aiRiskPoints.filter((point) => point.point_type === "full_cist")
    .sort((a, b) => a.analyzed_at.localeCompare(b.analyzed_at));
  const recentCist = cistPoints.at(-1);
  const previousCist = cistPoints.at(-2);
  const recentDaily = recentCist && aiRiskPoints
    .filter((point) => point.point_type === "daily_partial_estimate"
      && sharesCistBaseline(point, recentCist)
      && point.analyzed_at > recentCist.analyzed_at)
    .sort((a, b) => a.analyzed_at.localeCompare(b.analyzed_at)).at(-1);

  return (
    <Screen header={header}>
      <Card>
        <Body style={{ fontWeight: fontWeight.semibold, marginBottom: spacing.md }}>
          AI 인지 위험 신호 추이
        </Body>
        <View style={styles.modeRow}>
          {([
            { key: "all", label: "전체 추이" },
            { key: "cist", label: "CIST 검사만" },
          ] as const).map((mode) => {
            const selected = viewMode === mode.key;
            return (
              <Pressable
                key={mode.key}
                onPress={() => setViewMode(mode.key)}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                accessibilityLabel={mode.label}
                style={[styles.modeChip, { backgroundColor: selected ? guardian.blue : colors.card }]}
              >
                <Text style={[styles.periodLabel, { color: selected ? colors.white : colors.mutedForeground }]}>
                  {mode.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
        <View style={styles.periodRow}>
          {PERIODS.map((p) => {
            const selected = p.key === period;
            return (
              <Pressable
                key={p.key}
                onPress={() => setPeriod(p.key)}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                accessibilityLabel={`${p.label} 보기`}
                style={[
                  styles.periodChip,
                  {
                    backgroundColor: selected ? guardian.blue : colors.card,
                    borderColor: selected ? guardian.blue : colors.border,
                  },
                ]}
              >
                <Text style={[styles.periodLabel, { color: selected ? colors.white : colors.mutedForeground }]}>
                  {p.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
        {visibleAiRiskPoints.length > 0 ? (
          <>
            <AiRiskTrendChart points={visibleAiRiskPoints} />
            {previousCist && recentCist ? (
              <View style={styles.retestNotice}>
                <Body style={{ fontWeight: fontWeight.semibold, color: guardian.blue }}>CIST 재검사 · 새 기준점</Body>
                <Caption>{recentCist.date} · 위험 신호 지수 {riskIndex(recentCist)}</Caption>
              </View>
            ) : null}
            <View style={styles.changeSection}>
              <Body style={{ fontWeight: fontWeight.semibold }}>이전 CIST → 최근 CIST</Body>
              <Caption>{previousCist && recentCist
                ? `${previousCist.date} ${riskIndex(previousCist)} → ${recentCist.date} ${riskIndex(recentCist)} · ${riskChange(previousCist, recentCist)}`
                : "조회 기간에 비교할 CIST 검사가 2회 이상 필요해요."}</Caption>
              {viewMode === "all" ? (
                <>
                  <Body style={{ fontWeight: fontWeight.semibold, marginTop: spacing.md }}>최근 CIST 기준점 → 최근 일상 문답 추정</Body>
                  <Caption>{recentCist && recentDaily
                    ? `${recentCist.date} ${riskIndex(recentCist)} → ${recentDaily.date} ${riskIndex(recentDaily)} · ${riskChange(recentCist, recentDaily)}`
                    : "최근 CIST 이후 일상 문답 추정 결과가 없어요."}</Caption>
                </>
              ) : null}
            </View>
            <Caption>AI 위험 신호 지수를 0~100 눈금으로 표시했어요. 높을수록 추가 확인이 필요한 신호이며 진단 결과는 아닙니다.{viewMode === "all" ? " 일상 문답 추정점은 일부 문항만 갱신한 결과예요." : ""}</Caption>
            {visibleAiRiskPoints.length < 2 ? (
              <Caption style={{ marginTop: spacing.sm }}>표시된 점이 하나뿐이라 변화 추이는 판단할 수 없어요.</Caption>
            ) : null}
          </>
        ) : (
          <Body style={{ marginTop: spacing.md }}>
            {viewMode === "cist" ? "이 기간의 CIST 검사는 없어요." : "이 기간의 분석 결과가 없어요."}
          </Body>
        )}
      </Card>

      {points.length === 0 ? null : (
        <>
          <Card style={{ marginTop: spacing.lg }}>
            <View style={styles.chartHead}>
              <Body style={{ fontWeight: fontWeight.semibold }}>CIST 인지 점수</Body>
              <View style={styles.legendRow}>
                <View style={styles.legendItem}>
                  <View style={[styles.legendRule, { backgroundColor: colors.accent }]} />
                  <Caption style={styles.legendLabel}>정상 하한 24</Caption>
                </View>
                <View style={styles.legendItem}>
                  <View style={[styles.legendRule, { backgroundColor: colors.destructive }]} />
                  <Caption style={styles.legendLabel}>경도 치매 18</Caption>
                </View>
              </View>
            </View>

            <ScoreTrendChart points={points} variant="full" />
          </Card>

          <View style={styles.summaryRow}>
            <Card style={{ flex: 1 }}>
              <Caption style={styles.eyebrow}>{periodLabel} 변화</Caption>
              <Text
                style={[
                  styles.summaryValue,
                  { color: delta !== null && delta < 0 ? colors.destructive : guardian.blue },
                ]}
              >
                {delta === null ? "—" : `${delta > 0 ? "+" : ""}${delta}점`}
              </Text>
            </Card>
            <Card style={{ flex: 1 }}>
              <Caption style={styles.eyebrow}>연속 하락</Caption>
              <Text
                style={[
                  styles.summaryValue,
                  { color: run > 0 ? colors.accent : colors.mutedForeground },
                ]}
              >
                {run > 0 ? `${run}회` : "없음"}
              </Text>
            </Card>
          </View>

          {history.data.sample_sufficient ? null : (
            <Caption style={{ marginTop: spacing.md }}>
              표본이 아직 적어 추세는 참고용이에요.
            </Caption>
          )}
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  modeRow: {
    flexDirection: "row",
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    overflow: "hidden",
  },
  modeChip: { flex: 1, alignItems: "center", paddingVertical: spacing.sm },
  periodRow: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.md, marginBottom: spacing.md },
  periodChip: {
    flex: 1,
    alignItems: "center",
    paddingHorizontal: spacing.xs,
    paddingVertical: 6,
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  periodLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold },
  retestNotice: {
    padding: spacing.sm,
    marginBottom: spacing.md,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: guardian.blue,
  },
  changeSection: {
    paddingVertical: spacing.md,
    marginBottom: spacing.md,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: colors.border,
  },

  chartHead: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.md,
  },
  legendRow: { flexDirection: "row", gap: spacing.md },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 4 },
  legendRule: { width: 12, height: 2, borderRadius: 1 },
  legendLabel: { fontSize: fontSize.badge },

  summaryRow: { flexDirection: "row", gap: spacing.md, marginTop: spacing.lg },
  eyebrow: { letterSpacing: 0.5, marginBottom: spacing.sm },
  summaryValue: { fontSize: 22, fontWeight: fontWeight.bold },
});
