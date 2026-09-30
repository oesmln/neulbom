import React from "react";
import { View, Pressable, StyleSheet } from "react-native";
import { useIsFocused } from "@react-navigation/native";

import { useApp } from "@/store/AppContext";
import { reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { guardianAccessErrorMessage } from "@/api/errors";
import { PERIODS, dateRangeInSeoul, nextPeriodContainingBaseline, trendView } from "@/utils/aiRiskTrend";
import type { PeriodKey, ViewMode } from "@/utils/aiRiskTrend";
import type { GuardianAiRiskTrendPoint } from "@/api/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
import AiRiskTrendChart from "@/components/AiRiskTrendChart";
import ElderSelector from "@/components/ElderSelector";
import {
  Screen,
  ScreenHeader,
  Card,
  Body,
  Caption,
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  SentenceText as Text,
} from "@/components/ui";
import GuardianHeaderActions from "@/components/GuardianHeaderActions";

const HISTORY_LIMIT = 100;

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

  const {
    visibleAiRiskPoints, cistPoints, priorCistBaseline, recentCist, previousCist, recentDaily, isEmpty,
  } = trendView(history.data, viewMode, dateRange);
  const expandPeriod = () => {
    if (!priorCistBaseline) return;
    setPeriod(nextPeriodContainingBaseline(period, priorCistBaseline.date));
  };

  return (
    <Screen header={header}>
      <ElderSelector />
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
        {priorCistBaseline ? (
          <View style={styles.baselineCard}>
            <Caption style={styles.baselineEyebrow}>조회 기간 이전 검사</Caption>
            <Body style={{ fontWeight: fontWeight.semibold }}>
              {cistPoints.length === 0 ? "현재 적용 중인 CIST 기준점" : "조회 시작 시 CIST 기준점"}
            </Body>
            <Caption>{priorCistBaseline.date} · 위험 신호 지수 {riskIndex(priorCistBaseline)}</Caption>
          </View>
        ) : null}
        {!isEmpty ? (
          <>
            <AiRiskTrendChart points={visibleAiRiskPoints} hasPriorCistBaseline={!!priorCistBaseline} />
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
          <View style={styles.emptyTrend}>
            <Body>{viewMode === "cist" ? "이 기간의 CIST 검사는 없어요." : "이 기간의 분석 결과가 없어요."}</Body>
            {viewMode === "cist" && priorCistBaseline ? (
              <Button label="기간 넓혀 보기" variant="outline" size="sm" onPress={expandPeriod}
                style={{ marginTop: spacing.md }} />
            ) : null}
            {viewMode === "cist" && !priorCistBaseline && period === "all" ? (
              <Caption style={{ marginTop: spacing.sm }}>완료된 CIST 검사가 아직 없어요.</Caption>
            ) : null}
          </View>
        )}
      </Card>

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
  baselineCard: {
    padding: spacing.md,
    marginBottom: spacing.md,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.card,
  },
  baselineEyebrow: { marginBottom: spacing.xs },
  emptyTrend: { marginTop: spacing.md },
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

});
