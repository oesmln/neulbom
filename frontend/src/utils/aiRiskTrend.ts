import type { GuardianAiRiskTrendPoint, HistoryResponse } from "@/api/types";

// 좁은 기간에서 넓은 기간 순서를 유지한다. `nextPeriodContainingBaseline`이 이
// 순서를 따라 기준점이 들어올 때까지 다음 기간으로 넓힌다. `months`가 0이면 서울
// 기준 오늘 하루, null이면 전체 기간이다.
export const PERIODS = [
  { key: "today", label: "오늘", months: 0 },
  { key: "1m", label: "1개월", months: 1 },
  { key: "3m", label: "3개월", months: 3 },
  { key: "1y", label: "1년", months: 12 },
  { key: "all", label: "전체", months: null },
] as const;

export type PeriodKey = (typeof PERIODS)[number]["key"];
export type ViewMode = "all" | "cist";

const SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000;

export function dateRangeInSeoul(months: number | null, now: Date = new Date()) {
  const today = new Date(now.getTime() + SEOUL_OFFSET_MS);
  const toDate = today.toISOString().slice(0, 10);
  if (months === null) return { fromDate: undefined, toDate };
  if (months === 0) return { fromDate: toDate, toDate };
  const year = today.getUTCFullYear();
  const month = today.getUTCMonth();
  const day = today.getUTCDate();
  const lastDayOfStartMonth = new Date(Date.UTC(year, month - months + 1, 0)).getUTCDate();
  const from = new Date(Date.UTC(year, month - months, Math.min(day, lastDayOfStartMonth)));
  return { fromDate: from.toISOString().slice(0, 10), toDate: today.toISOString().slice(0, 10) };
}

export function sharesCistBaseline(a: GuardianAiRiskTrendPoint, b: GuardianAiRiskTrendPoint): boolean {
  if (a.baseline_snapshot_id && b.baseline_snapshot_id) {
    return a.baseline_snapshot_id === b.baseline_snapshot_id;
  }
  return a.baseline_session_id === b.baseline_session_id;
}

export function trendView(
  history: HistoryResponse,
  mode: ViewMode,
  range: { fromDate?: string; toDate: string },
) {
  // Keep the graph inside the selected calendar range even if an API fixture or
  // a stale response contains an extra point. The prior baseline stays separate.
  const aiRiskPoints = (history.ai_risk_trend_points ?? []).filter((point) =>
    (!range.fromDate || point.date >= range.fromDate) && point.date <= range.toDate);
  const cistPoints = aiRiskPoints.filter((point) => point.point_type === "full_cist")
    .sort((a, b) => a.analyzed_at.localeCompare(b.analyzed_at));
  const prior = history.prior_cist_baseline;
  const priorCistBaseline = range.fromDate && prior && prior.date < range.fromDate
    ? prior
    : null;
  const visibleAiRiskPoints = mode === "cist" ? cistPoints : aiRiskPoints;
  const recentCist = cistPoints.at(-1) ?? priorCistBaseline;
  const previousCist = cistPoints.at(-2) ?? (cistPoints.length > 0 ? priorCistBaseline : null);
  const recentDaily = recentCist && aiRiskPoints
    .filter((point) => point.point_type === "daily_partial_estimate"
      && sharesCistBaseline(point, recentCist)
      && point.analyzed_at > recentCist.analyzed_at)
    .sort((a, b) => a.analyzed_at.localeCompare(b.analyzed_at)).at(-1);

  return {
    visibleAiRiskPoints,
    cistPoints,
    priorCistBaseline,
    recentCist,
    previousCist,
    recentDaily,
    isEmpty: visibleAiRiskPoints.length === 0,
  };
}

export function nextPeriodContainingBaseline(
  currentPeriod: PeriodKey,
  baselineDate: string,
  now: Date = new Date(),
): PeriodKey {
  const currentIndex = PERIODS.findIndex((item) => item.key === currentPeriod);
  const wider = PERIODS.slice(currentIndex + 1).find((item) =>
    item.months === null || (dateRangeInSeoul(item.months, now).fromDate ?? "") <= baselineDate);
  return wider?.key ?? "all";
}
