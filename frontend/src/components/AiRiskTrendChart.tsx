import React from "react";
import { StyleSheet, Text, View } from "react-native";
import Svg, { Circle, Line, Text as SvgText } from "react-native-svg";

import type { GuardianAiRiskTrendPoint } from "@/api/types";
import { sharesCistBaseline } from "@/utils/aiRiskTrend";
import { colors, fontSize, guardian } from "@/theme";
import { useDisplaySettings } from "@/store/DisplaySettingsContext";

export type AiRiskPoint = GuardianAiRiskTrendPoint;

/** CIST AI model scores use their own 0–1 scale, separate from CIST's 0–30 score. */
export default function AiRiskTrendChart({
  points,
  compact = false,
}: {
  points: AiRiskPoint[];
  compact?: boolean;
}) {
  useDisplaySettings();
  if (points.length === 0) return null;

  const width = 318;
  const height = compact ? 112 : 160;
  const left = 34;
  const right = width - 10;
  const top = 12;
  const bottom = height - 25;
  const x = (index: number) =>
    points.length === 1 ? (left + right) / 2 : left + (index / (points.length - 1)) * (right - left);
  const y = (score: number) => bottom - Math.max(0, Math.min(1, score)) * (bottom - top);
  // A date can contain several analyses; keep the backend's analysis-time order.
  const ordered = [...points].sort((a, b) =>
    a.analyzed_at.localeCompare(b.analyzed_at) || a.session_id.localeCompare(b.session_id));
  const hasDailyEstimates = ordered.some((point) => point.is_estimated);
  const cistPositions = ordered.flatMap((point, index) => point.point_type === "full_cist" ? [index] : []);

  // Daily estimates belong only to their own CIST baseline. A retest starts a
  // new line, while the solid CIST line compares the two actual examinations.
  const dailySegments = ordered.flatMap((point, index) => {
    if (point.point_type !== "daily_partial_estimate") return [];
    let previousIndex = index - 1;
    while (previousIndex >= 0 && !sharesCistBaseline(ordered[previousIndex], point)) {
      if (ordered[previousIndex].point_type === "full_cist") return [];
      previousIndex -= 1;
    }
    return previousIndex < 0 ? [] : [{ from: previousIndex, to: index }];
  });

  return (
    <View>
      <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`}>
        {[0, 0.5, 1].map((tick) => (
          <React.Fragment key={tick}>
            <Line x1={left} x2={right} y1={y(tick)} y2={y(tick)} stroke={colors.border} />
            <SvgText x={left - 5} y={y(tick) + 4} textAnchor="end" fontSize={10} fill={colors.mutedForeground}>
              {Math.round(tick * 100)}
            </SvgText>
          </React.Fragment>
        ))}
        {cistPositions.slice(1).map((position, index) => {
          const previousPosition = cistPositions[index];
          return <Line key={`cist-${position}`} x1={x(previousPosition)} y1={y(ordered[previousPosition].risk_score)}
            x2={x(position)} y2={y(ordered[position].risk_score)} stroke={guardian.blue} strokeWidth={2.5} />;
        })}
        {dailySegments.map(({ from, to }) => (
          <Line key={`daily-${to}`} x1={x(from)} y1={y(ordered[from].risk_score)}
            x2={x(to)} y2={y(ordered[to].risk_score)} stroke={colors.accent}
            strokeWidth={2.5} strokeDasharray="4 4" />
        ))}
        {ordered.map((point, index) => (
          <React.Fragment key={point.session_id}>
            {point.point_type === "full_cist" && cistPositions[0] !== index ? (
              <Circle cx={x(index)} cy={y(point.risk_score)} r={9}
                fill="none" stroke={guardian.blue} strokeWidth={1.5} />
            ) : null}
            <Circle cx={x(index)} cy={y(point.risk_score)} r={5}
              fill={point.is_estimated ? colors.card : guardian.blue}
              stroke={point.is_estimated ? colors.accent : guardian.blue}
              strokeWidth={point.is_estimated ? 2.5 : 1} />
          </React.Fragment>
        ))}
        <SvgText x={left} y={height - 5} fontSize={10} fill={colors.mutedForeground}>
          {ordered[0].date.slice(5)}
        </SvgText>
        <SvgText x={right} y={height - 5} textAnchor="end" fontSize={10} fill={colors.mutedForeground}>
          {ordered[ordered.length - 1].date.slice(5)}
        </SvgText>
      </Svg>
      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: guardian.blue, borderColor: guardian.blue }]} />
          <Text style={[styles.legendText, { color: colors.mutedForeground }]}>전체 CIST 기준점</Text>
        </View>
        {hasDailyEstimates ? (
          <View style={styles.legendItem}>
            <View style={[styles.legendDot, { backgroundColor: colors.card, borderColor: colors.accent }]} />
            <Text style={[styles.legendText, { color: colors.mutedForeground }]}>일상 문답 추정점</Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  legend: { flexDirection: "row", flexWrap: "wrap", gap: 12, marginTop: 4, marginBottom: 8 },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 5 },
  legendDot: { width: 10, height: 10, borderRadius: 5, borderWidth: 2 },
  legendText: { fontSize: fontSize.micro },
});
