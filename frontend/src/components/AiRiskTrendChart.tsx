import React from "react";
import Svg, { Circle, Line, Polyline, Text as SvgText } from "react-native-svg";

import { colors, guardian } from "@/theme";
import { useDisplaySettings } from "@/store/DisplaySettingsContext";

export type AiRiskPoint = { date: string; risk_score: number };

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
  const ordered = [...points].sort((a, b) => a.date.localeCompare(b.date));
  const line = ordered.map((point, index) => `${x(index)},${y(point.risk_score)}`).join(" ");

  return (
    <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`}>
      {[0, 0.5, 1].map((tick) => (
        <React.Fragment key={tick}>
          <Line x1={left} x2={right} y1={y(tick)} y2={y(tick)} stroke={colors.border} />
          <SvgText x={left - 5} y={y(tick) + 4} textAnchor="end" fontSize={10} fill={colors.mutedForeground}>
            {Math.round(tick * 100)}
          </SvgText>
        </React.Fragment>
      ))}
      <Polyline points={line} fill="none" stroke={guardian.blue} strokeWidth={2.5} />
      {ordered.map((point, index) => (
        <Circle key={`${point.date}-${index}`} cx={x(index)} cy={y(point.risk_score)} r={4} fill={guardian.blue} />
      ))}
      <SvgText x={left} y={height - 5} fontSize={10} fill={colors.mutedForeground}>
        {ordered[0].date.slice(5)}
      </SvgText>
      <SvgText x={right} y={height - 5} textAnchor="end" fontSize={10} fill={colors.mutedForeground}>
        {ordered[ordered.length - 1].date.slice(5)}
      </SvgText>
    </Svg>
  );
}
