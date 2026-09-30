import type { GuardianAiRiskTrendPoint } from "@/api/types";

export interface RiskThresholds {
  decision: number;
  review: number;
  version: string | null;
}

// Compatibility for results created before the guardian trend API returned its
// stored threshold metadata. These values are the current fusion-threshold-v2 contract.
export const CURRENT_RISK_THRESHOLDS: RiskThresholds = {
  decision: 0.38592870327757767,
  review: 0.8061380697921943,
  version: "fusion-threshold-v2",
};

export function thresholdsForPoints(points: GuardianAiRiskTrendPoint[]): RiskThresholds {
  const latest = [...points].sort((a, b) => b.analyzed_at.localeCompare(a.analyzed_at))
    .find((point) => {
      const decision = point.decision_threshold;
      const review = point.review_threshold;
      return typeof decision === "number" && typeof review === "number"
        && Number.isFinite(decision) && Number.isFinite(review)
        && decision > 0 && decision < review && review < 1;
    });
  return latest
    ? { decision: latest.decision_threshold!, review: latest.review_threshold!, version: latest.threshold_version }
    : CURRENT_RISK_THRESHOLDS;
}

export function riskBand(score: number, thresholds: RiskThresholds): "stable" | "monitoring_needed" | "review_needed" {
  if (score < thresholds.decision) return "stable";
  if (score < thresholds.review) return "monitoring_needed";
  return "review_needed";
}

export function riskBandLabel(band: ReturnType<typeof riskBand>): string {
  switch (band) {
    case "stable": return "안정적";
    case "monitoring_needed": return "꾸준한 관찰";
    case "review_needed": return "확인 필요";
  }
}

export function riskBoundaryLabel(value: number): string {
  return (value * 100).toFixed(1);
}
