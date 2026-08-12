import React from "react";
import Svg, {
  Circle,
  Line,
  Path,
  Polyline,
  Rect,
  Text as SvgText,
} from "react-native-svg";

import { colors, guardian } from "@/theme";

/**
 * CIST score trend, drawn to the Figma guardian design.
 *
 * `react-native-svg` is here because the design is a line chart: a polyline, two
 * dashed threshold rules and per-point dots. `View` can only draw rectangles, so
 * the previous bar rendering was the shape the platform allowed rather than the
 * shape that was designed. This is still not a charting library — the geometry
 * below is the whole implementation, which is what the "no chart library" rule
 * in CLAUDE.md is protecting.
 *
 * Both call sites share one score domain (14-31) so the dashboard sparkline and
 * the full chart cannot disagree about where the 24-point line sits.
 */

/** CIST cut-offs from api-spec: 24 is the normal floor, 18 the mild-dementia line. */
const NORMAL_FLOOR = 24;
const MILD_FLOOR = 18;

const DOMAIN_MIN = 14;
const DOMAIN_MAX = 31;

export interface TrendPoint {
  label: string;
  score: number;
}

interface Variant {
  width: number;
  height: number;
  padLeft: number;
  padBottom: number;
  yTicks: number[];
  /** The 18-point band and rule only appear on the full chart. */
  showMildFloor: boolean;
  /** The dashboard enlarges the newest dot and prints its value above. */
  emphasiseLast: boolean;
  lastLabelSize: number;
}

const VARIANTS: Record<"compact" | "full", Variant> = {
  compact: {
    width: 315,
    height: 110,
    padLeft: 24,
    padBottom: 20,
    yTicks: [30, 24, 18],
    showMildFloor: false,
    emphasiseLast: true,
    lastLabelSize: 11,
  },
  full: {
    width: 318,
    height: 155,
    padLeft: 26,
    padBottom: 22,
    yTicks: [30, 24, 18, 15],
    showMildFloor: true,
    emphasiseLast: false,
    lastLabelSize: 10,
  },
};

export default function ScoreTrendChart({
  points,
  variant = "full",
}: {
  points: TrendPoint[];
  variant?: "compact" | "full";
}) {
  const v = VARIANTS[variant];
  if (points.length === 0) return null;

  const plotW = v.width - v.padLeft;
  const plotH = v.height - v.padBottom;

  const toY = (score: number) =>
    plotH - ((score - DOMAIN_MIN) / (DOMAIN_MAX - DOMAIN_MIN)) * plotH;

  // A single record has no interval to spread across, so it sits centred rather
  // than dividing by zero.
  const toX = (index: number) =>
    points.length === 1
      ? v.padLeft + plotW / 2
      : v.padLeft + (index / (points.length - 1)) * plotW;

  const yNormal = toY(NORMAL_FLOOR);
  const yMild = toY(MILD_FLOOR);

  const pts = points.map((p, i) => ({ x: toX(i), y: toY(p.score), ...p }));
  const polyline = pts.map((p) => `${p.x},${p.y}`).join(" ");
  const area =
    `M${pts[0].x},${plotH} ` +
    pts.map((p) => `L${p.x},${p.y}`).join(" ") +
    ` L${pts[pts.length - 1].x},${plotH} Z`;

  const dotColor = (score: number) => {
    if (v.showMildFloor) {
      if (score < MILD_FLOOR) return colors.destructive;
      if (score < NORMAL_FLOOR) return colors.accent;
      return guardian.blue;
    }
    return score < NORMAL_FLOOR ? colors.destructive : guardian.blue;
  };

  return (
    <Svg width="100%" height={v.height} viewBox={`0 0 ${v.width} ${v.height}`}>
      {/* Score bands: normal above 24, caution 18-24, mild dementia below 18. */}
      <Rect
        x={v.padLeft}
        y={0}
        width={plotW}
        height={yNormal}
        fill={guardian.bandNormal}
        opacity={0.8}
      />
      <Rect
        x={v.padLeft}
        y={yNormal}
        width={plotW}
        height={(v.showMildFloor ? yMild : plotH) - yNormal}
        fill={guardian.bandCaution}
        opacity={0.8}
      />
      {v.showMildFloor ? (
        <Rect
          x={v.padLeft}
          y={yMild}
          width={plotW}
          height={plotH - yMild}
          fill={colors.destructiveLight}
          opacity={0.4}
        />
      ) : null}

      <Line
        x1={v.padLeft}
        y1={yNormal}
        x2={v.width}
        y2={yNormal}
        stroke={colors.accent}
        strokeWidth={1}
        strokeDasharray={[4, 3]}
      />
      {v.showMildFloor ? (
        <Line
          x1={v.padLeft}
          y1={yMild}
          x2={v.width}
          y2={yMild}
          stroke={colors.destructive}
          strokeWidth={1}
          strokeDasharray={[4, 3]}
        />
      ) : null}

      {v.yTicks.map((tick) => (
        <SvgText
          key={tick}
          x={v.padLeft - 4}
          y={toY(tick) + 4}
          textAnchor="end"
          fontSize={9}
          fill={colors.mutedForeground}
        >
          {String(tick)}
        </SvgText>
      ))}

      <Path d={area} fill={guardian.blue} opacity={0.07} />
      <Polyline
        points={polyline}
        fill="none"
        stroke={guardian.blue}
        strokeWidth={2.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {pts.map((p, i) => {
        const isLast = i === pts.length - 1;
        return (
          <React.Fragment key={`${p.label}-${i}`}>
            <Circle
              cx={p.x}
              cy={p.y}
              r={v.emphasiseLast && isLast ? 5 : 3.5}
              fill={dotColor(p.score)}
              stroke={colors.white}
              strokeWidth={1.5}
            />
            {isLast ? (
              <SvgText
                x={p.x}
                y={p.y - (v.emphasiseLast ? 9 : 7)}
                textAnchor="middle"
                fontSize={v.lastLabelSize}
                fontWeight="700"
                fill={dotColor(p.score)}
              >
                {String(p.score)}
              </SvgText>
            ) : null}
          </React.Fragment>
        );
      })}

      {pts.map((p, i) => (
        <SvgText
          key={`x-${p.label}-${i}`}
          x={p.x}
          y={v.height - 4}
          // The first and last points sit on the plot edges, so a centred label
          // would hang outside the viewBox — which the web prototype got away
          // with and React Native clips. Anchor the end labels inward instead.
          textAnchor={i === 0 ? "start" : i === pts.length - 1 ? "end" : "middle"}
          fontSize={9}
          fill={colors.mutedForeground}
        >
          {p.label}
        </SvgText>
      ))}
    </Svg>
  );
}
