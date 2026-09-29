import type { GuardianAiRiskTrendPoint } from "@/api/types";

export function sharesCistBaseline(a: GuardianAiRiskTrendPoint, b: GuardianAiRiskTrendPoint): boolean {
  if (a.baseline_snapshot_id && b.baseline_snapshot_id) {
    return a.baseline_snapshot_id === b.baseline_snapshot_id;
  }
  return a.baseline_session_id === b.baseline_session_id;
}
