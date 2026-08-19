import React from "react";
import { View, Pressable, StyleSheet } from "react-native";

import { useApp } from "@/store/AppContext";
import { reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { guardianAccessErrorMessage } from "@/api/errors";
import { isoDateOf, monthDayLabel } from "@/utils/format";
import type { HistoryRecordResponse } from "@/api/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
import ScoreTrendChart, { type TrendPoint } from "@/components/ScoreTrendChart";
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

/**
 * Score trend from `GET /analysis/cognitive/{user_id}/history`.
 *
 * The backend supports `day` aggregation rather than a synthetic monthly
 * value, so the period toggle sends a date range and asks for daily points.
 */
const PERIODS = [
  { key: "3m", label: "3개월", months: 3 },
  { key: "6m", label: "6개월", months: 6 },
  { key: "1y", label: "1년", months: 12 },
] as const;

type PeriodKey = (typeof PERIODS)[number]["key"];

const HISTORY_LIMIT = 100;

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

export default function GuardianChartScreen() {
  const { userId, selectedElderId } = useApp();
  const [period, setPeriod] = React.useState<PeriodKey>("6m");

  const dateRange = React.useMemo(() => {
    const months = PERIODS.find((item) => item.key === period)?.months ?? 6;
    const to = new Date();
    const from = new Date(to.getFullYear(), to.getMonth() - months, to.getDate());
    return { fromDate: isoDateOf(from), toDate: isoDateOf(to) };
  }, [period]);

  const report = useApi(
    () => reports.guardianReport(userId as string, selectedElderId as string),
    [userId, selectedElderId],
    { enabled: !!userId && !!selectedElderId },
  );

  const history = useApi(
    () =>
      reports.cognitiveHistory(selectedElderId as string, {
        limit: HISTORY_LIMIT,
        aggregation: "day",
        ...dateRange,
      }),
    [selectedElderId, dateRange.fromDate, dateRange.toDate],
    { enabled: !!selectedElderId },
  );

  const header = (
    <ScreenHeader
      color={guardian.blue}
      title="인지 저하 추이"
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

  return (
    <Screen header={header}>
      <View style={styles.periodRow}>
        {PERIODS.map((p) => {
          const on = p.key === period;
          return (
            <Pressable
              key={p.key}
              onPress={() => setPeriod(p.key)}
              accessibilityRole="tab"
              accessibilityState={{ selected: on }}
              accessibilityLabel={`${p.label} 보기`}
              style={[
                styles.periodChip,
                {
                  // `colors.card`, not `colors.white`: white stays white in
                  // dark mode and would wash out the unselected chip label.
                  backgroundColor: on ? guardian.blue : colors.card,
                  borderColor: on ? guardian.blue : colors.border,
                },
              ]}
            >
              <Text
                style={[styles.periodLabel, { color: on ? colors.white : colors.mutedForeground }]}
              >
                {p.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {points.length === 0 ? (
        <EmptyState message="아직 분석된 검사가 없어요." icon="bar-chart-outline" />
      ) : (
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
  periodRow: { flexDirection: "row", gap: spacing.sm },
  periodChip: {
    paddingHorizontal: spacing.lg,
    paddingVertical: 6,
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  periodLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold },

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
