import assert from "node:assert/strict";
import test from "node:test";

import {
  dateRangeInSeoul,
  nextPeriodContainingBaseline,
  trendView,
} from "../src/utils/aiRiskTrend.ts";
import { riskBand, riskBoundaryLabel, thresholdsForPoints } from "../src/utils/riskBands.ts";
import { gameDomain, gameResultSummary, recentGames } from "../src/utils/guardianGameResults.ts";

function point(date, type, session, baseline = session, score = 0.4) {
  return {
    date,
    analyzed_at: `${date}T03:00:00Z`,
    risk_score: score,
    point_type: type,
    is_estimated: type !== "full_cist",
    session_id: session,
    baseline_session_id: baseline,
    baseline_snapshot_id: `snapshot-${baseline}`,
    risk_level: "stable",
  };
}

function history(points, prior = null) {
  return {
    records: [], total: 0, aggregation: "day", sample_sufficient: false,
    ai_risk_trend_points: points, prior_cist_baseline: prior,
  };
}

test("오늘 범위는 서울 기준 하루이고, 전체 범위는 시작이 없다", () => {
  const now = new Date("2026-09-29T16:00:00Z");
  assert.deepEqual(dateRangeInSeoul(0, now), {
    fromDate: "2026-09-30", toDate: "2026-09-30",
  });
  assert.deepEqual(dateRangeInSeoul(null, now), {
    fromDate: undefined, toDate: "2026-09-30",
  });
  assert.equal(nextPeriodContainingBaseline("today", "2026-09-10", now), "1m");
});

test("1개월 날짜 범위는 서울 날짜와 월말을 따른다", () => {
  const beforeSeoulMidnight = new Date("2026-09-29T14:59:00Z");
  const afterSeoulMidnight = new Date("2026-09-29T15:01:00Z");
  assert.deepEqual(dateRangeInSeoul(1, beforeSeoulMidnight), {
    fromDate: "2026-08-29", toDate: "2026-09-29",
  });
  assert.deepEqual(dateRangeInSeoul(1, afterSeoulMidnight), {
    fromDate: "2026-08-30", toDate: "2026-09-30",
  });
  assert.deepEqual(dateRangeInSeoul(1, new Date("2024-03-30T15:30:00Z")), {
    fromDate: "2024-02-29", toDate: "2024-03-31",
  });
});

test("전체 추이는 기간 경계 안의 CIST와 일상 점만 보여주고, CIST 전환은 검사 점만 보여준다", () => {
  const before = point("2026-08-31", "full_cist", "before");
  const start = point("2026-09-01", "full_cist", "start");
  const daily = point("2026-09-15", "daily_partial_estimate", "daily", "start");
  const end = point("2026-09-30", "full_cist", "end");
  const after = point("2026-10-01", "daily_partial_estimate", "after", "end");
  const response = history([before, start, daily, end, after], before);
  const range = { fromDate: "2026-09-01", toDate: "2026-09-30" };

  assert.deepEqual(trendView(response, "all", range).visibleAiRiskPoints.map((p) => p.session_id),
    ["start", "daily", "end"]);
  const cistOnly = trendView(response, "cist", range);
  assert.deepEqual(cistOnly.visibleAiRiskPoints.map((p) => p.session_id), ["start", "end"]);
  assert.equal(cistOnly.priorCistBaseline?.session_id, "before");
  assert.equal(cistOnly.isEmpty, false);
});

test("기간 안에 CIST가 없으면 이전 기준점은 카드용으로 남고 CIST 화면은 빈 상태가 된다", () => {
  const prior = point("2026-08-10", "full_cist", "prior", "prior", 0.42);
  const daily = point("2026-09-12", "daily_partial_estimate", "daily", "prior", 0.38);
  const response = history([daily], prior);
  const range = { fromDate: "2026-08-30", toDate: "2026-09-30" };

  const cistOnly = trendView(response, "cist", range);
  assert.equal(cistOnly.isEmpty, true);
  assert.deepEqual(cistOnly.visibleAiRiskPoints, []);
  assert.equal(cistOnly.priorCistBaseline?.session_id, "prior");
  assert.equal(trendView(response, "all", range).recentDaily?.session_id, "daily");
  assert.equal(nextPeriodContainingBaseline("1m", prior.date, new Date("2026-09-29T16:00:00Z")), "3m");
});

test("오래된 마지막 검사는 전체 기간으로 넓히고, 검사 자체가 없으면 기준점도 없다", () => {
  assert.equal(nextPeriodContainingBaseline("1y", "2024-01-01", new Date("2026-09-29T16:00:00Z")), "all");
  const empty = trendView(history([]), "cist", { fromDate: "2026-09-01", toDate: "2026-09-30" });
  assert.equal(empty.isEmpty, true);
  assert.equal(empty.priorCistBaseline, null);
});

test("재검사 후 일상 변화는 새 기준점의 추정치만 사용한다", () => {
  const prior = point("2026-08-10", "full_cist", "old");
  const current = point("2026-09-08", "full_cist", "new");
  const staleDaily = point("2026-09-10", "daily_partial_estimate", "stale", "old");
  const latestDaily = point("2026-09-11", "daily_partial_estimate", "latest", "new");
  const view = trendView(history([current, staleDaily, latestDaily], prior), "all",
    { fromDate: "2026-09-01", toDate: "2026-09-30" });
  assert.equal(view.previousCist?.session_id, "old");
  assert.equal(view.recentCist?.session_id, "new");
  assert.equal(view.recentDaily?.session_id, "latest");
});

test("저장된 모델 경계값을 0~100 눈금으로 표시하고 경계에서 다음 구간으로 넘어간다", () => {
  const older = { ...point("2026-09-01", "full_cist", "old"), decision_threshold: 0.3,
    review_threshold: 0.7, threshold_version: "old" };
  const latest = { ...point("2026-09-10", "full_cist", "new"), decision_threshold: 0.38592870327757767,
    review_threshold: 0.8061380697921943, threshold_version: "fusion-threshold-v2" };
  const thresholds = thresholdsForPoints([latest, older]);
  assert.equal(thresholds.version, "fusion-threshold-v2");
  assert.equal(riskBoundaryLabel(thresholds.decision), "38.6");
  assert.equal(riskBoundaryLabel(thresholds.review), "80.6");
  assert.equal(riskBand(thresholds.decision - 0.0001, thresholds), "stable");
  assert.equal(riskBand(thresholds.decision, thresholds), "monitoring_needed");
  assert.equal(riskBand(thresholds.review, thresholds), "review_needed");
});

test("게임 기록은 기억력·언어력으로 분리하고 저장된 결과만 설명한다", () => {
  const card = { game_result_id: "card", game_type: "image_match", score: 6,
    matched_pairs: 6, attempt_count: 9, completed: true, played_at: "2026-09-09T00:00:00Z" };
  const color = { ...card, game_result_id: "color", game_type: "color_match", score: 5,
    matched_pairs: null, attempt_count: null, played_at: "2026-09-10T00:00:00Z" };
  const word = { ...color, game_result_id: "word", game_type: "consonant", score: 7,
    played_at: "2026-09-11T00:00:00Z" };
  assert.equal(gameDomain("image_match"), "memory");
  assert.equal(gameDomain("color_match"), "memory");
  assert.equal(gameDomain("consonant"), "language");
  assert.equal(gameDomain("word_match"), "language");
  assert.equal(gameDomain("unknown"), null);
  assert.deepEqual(recentGames([card, word, color], "memory", 1).map((r) => r.game_result_id), ["color"]);
  assert.deepEqual(recentGames([card, word, color], "language", 3).map((r) => r.game_result_id), ["word"]);
  assert.deepEqual(recentGames([], "memory", 1), []);
  assert.equal(gameResultSummary(card), "6쌍 맞춤 · 9회 시도");
  assert.equal(gameResultSummary({ ...word, completed: false }), "미완료");
});
