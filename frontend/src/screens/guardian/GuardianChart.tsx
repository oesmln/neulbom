import React from "react";
import { View, Text, StyleSheet } from "react-native";

import { useApp } from "@/store/AppContext";
import { reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import type { HistoryRecordResponse } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight, chartColors } from "@/theme";
import {
  Screen,
  Card,
  Body,
  Caption,
  Pill,
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/components/ui";

/**
 * Score trend from `GET /analysis/cognitive/{user_id}/history`, plus the
 * per-domain change the same records carry in `domain_scores`.
 *
 * Bars are plain Views on purpose — no chart library, per the project rules.
 */
const DEFAULT_MAX = 30;
const AGGREGATION = "weekly";
const HISTORY_LIMIT = 6;

/** `domain_scores` is a free-form JsonNode; only known keys are shown. */
const DOMAIN_LABELS: { key: string; label: string }[] = [
  { key: "memory", label: "기억력" },
  { key: "attention", label: "주의력" },
  { key: "language", label: "언어능력" },
  { key: "visuospatial", label: "시공간" },
];

function domainDeltas(record: HistoryRecordResponse | undefined) {
  if (!record?.domain_scores || typeof record.domain_scores !== "object") return [];
  const scores = record.domain_scores as Record<string, unknown>;
  return DOMAIN_LABELS.flatMap(({ key, label }) => {
    const value = scores[key];
    if (typeof value !== "number") return [];
    return [{ label, delta: `${value > 0 ? "+" : ""}${value}%`, up: value >= 0 }];
  });
}

function trendPill(trend: string | undefined) {
  if (trend === "declining") {
    return { label: "▼ 하락 추세", icon: "trending-down" as const };
  }
  if (trend === "improving") {
    return { label: "▲ 개선 추세", icon: "trending-up" as const };
  }
  return { label: "— 유지", icon: "remove" as const };
}

function weekLabel(index: number, total: number) {
  return `${total - index}주 전`;
}

export default function GuardianChartScreen() {
  const { userId, selectedElderId } = useApp();

  // The written summary lives on the guardian report, not on the history rows.
  const report = useApi(
    () => reports.guardianReport(userId as string, selectedElderId as string),
    [userId, selectedElderId],
    { enabled: !!userId && !!selectedElderId },
  );

  const history = useApi(
    () =>
      reports.cognitiveHistory(selectedElderId as string, {
        limit: HISTORY_LIMIT,
        aggregation: AGGREGATION,
      }),
    [selectedElderId],
    { enabled: !!selectedElderId },
  );

  if (!selectedElderId) {
    return (
      <Screen>
        <EmptyState message="먼저 대시보드에서 어르신을 선택해 주세요." icon="people-outline" />
      </Screen>
    );
  }

  if (history.error) {
    return (
      <Screen>
        <ErrorState
          message={
            history.error.isForbidden
              ? "어르신이 아직 정보 열람에 동의하지 않았어요."
              : apiErrorMessage(history.error)
          }
          onRetry={history.error.isForbidden ? undefined : history.reload}
        />
      </Screen>
    );
  }

  if (!history.data) {
    return (
      <Screen>
        <LoadingState />
      </Screen>
    );
  }

  // Oldest first so the bars read left to right. Records without a score are
  // dropped rather than drawn as a zero bar — a zero-height bar reads as "very
  // low", which is the opposite of "not measured".
  const records = [...history.data.records]
    .filter((r) => (r.display_score ?? r.screening_reference_score) != null)
    .sort((a, b) => (a.analyzed_at ?? "").localeCompare(b.analyzed_at ?? ""));
  const latest = records[records.length - 1];
  const max = latest?.score_max ?? DEFAULT_MAX;
  const pill = trendPill(latest?.trend);
  const deltas = domainDeltas(latest);

  return (
    <Screen>
      <Text style={styles.h1}>인지 위험 추이</Text>
      <Caption style={{ marginTop: 4 }}>주간 CIST 점수 변화 ({max}점 만점)</Caption>

      {records.length === 0 ? (
        <EmptyState message="아직 분석된 검사가 없어요." icon="bar-chart-outline" />
      ) : (
        <Card style={{ marginTop: spacing.lg }}>
          <View style={styles.rowBetween}>
            <Body style={{ fontWeight: fontWeight.bold }}>최근 {records.length}주</Body>
            <Pill
              label={pill.label}
              color={colors.secondary}
              textColor={colors.secondaryForeground}
              icon={pill.icon}
            />
          </View>

          <View style={styles.chart}>
            {records.map((record, i) => {
              const score = (record.display_score ?? record.screening_reference_score) as number;
              const h = (score / max) * 140;
              return (
                <View key={record.analysis_id} style={styles.barCol}>
                  <Text style={styles.barValue}>{score}</Text>
                  <View style={[styles.bar, { height: h, backgroundColor: chartColors[0] }]} />
                  <Caption>{weekLabel(i, records.length)}</Caption>
                </View>
              );
            })}
          </View>

          {history.data.sample_sufficient ? null : (
            <Caption style={{ marginTop: spacing.md }}>
              표본이 아직 적어 추세는 참고용이에요.
            </Caption>
          )}
        </Card>
      )}

      {deltas.length > 0 ? (
        <Card style={{ marginTop: spacing.lg }}>
          <Body style={{ fontWeight: fontWeight.bold, marginBottom: spacing.md }}>
            영역별 변화 (전월 대비)
          </Body>
          {deltas.map((d, i, arr) => (
            <View key={d.label} style={[styles.deltaRow, i < arr.length - 1 && styles.deltaBorder]}>
              <Body style={{ fontWeight: fontWeight.semibold }}>{d.label}</Body>
              <Text style={[styles.delta, { color: d.up ? colors.primary : colors.destructive }]}>
                {d.delta}
              </Text>
            </View>
          ))}
        </Card>
      ) : null}

      {report.data?.latest_summary ? (
        <Card style={{ marginTop: spacing.lg }} color={colors.secondary}>
          <Body style={{ fontWeight: fontWeight.bold, color: colors.secondaryForeground }}>
            AI 요약
          </Body>
          <Body style={{ marginTop: spacing.sm, color: colors.secondaryForeground }}>
            {report.data.latest_summary}
          </Body>
        </Card>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  h1: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.foreground },
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: spacing.lg },
  chart: { flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between", height: 190 },
  barCol: { alignItems: "center", flex: 1, gap: 6 },
  bar: { width: 22, borderRadius: radius.sm },
  barValue: { fontSize: fontSize.caption, fontWeight: fontWeight.bold, color: colors.foreground },
  deltaRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingVertical: spacing.md },
  deltaBorder: { borderBottomWidth: 1, borderBottomColor: colors.border },
  delta: { fontSize: fontSize.body, fontWeight: fontWeight.bold },
});
