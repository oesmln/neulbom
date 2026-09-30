import assert from "node:assert/strict";
import test from "node:test";
import { diariesByDay, diaryDay, diaryTime } from "../src/utils/guardianDiary.ts";

const entry = (diary_id, written_at) => ({ diary_id, written_at, title: "오늘의 이야기" });

test("자정 부근 일기를 API와 같은 서울 날짜에 표시한다", () => {
  assert.equal(diaryDay("2026-09-30T15:01:00Z"), "2026-10-01");
  assert.equal(diaryDay("2026-09-30T14:59:00Z"), "2026-09-30");
  assert.match(diaryTime("2026-09-30T15:49:00Z"), /12:49/);
});

test("동일 ID 재수신은 한 번만 표시하고 같은 제목의 별도 일기는 유지한다", () => {
  const first = entry("first", "2026-09-30T15:49:00Z");
  const second = entry("second", "2026-10-01T01:00:00Z");
  const days = diariesByDay([first, second, first], "2026-10-01", "2026-10-31");
  assert.deepEqual(days["2026-10-01"].map((item) => item.diary_id), ["second", "first"]);
});

test("조회월 밖의 응답은 달력과 일기 선택 목록에서 제외한다", () => {
  const days = diariesByDay([
    entry("previous", "2026-09-30T14:59:00Z"),
    entry("start", "2026-09-30T15:00:00Z"),
    entry("end", "2026-10-31T14:59:00Z"),
    entry("next", "2026-10-31T15:00:00Z"),
  ], "2026-10-01", "2026-10-31");
  assert.deepEqual(Object.keys(days).sort(), ["2026-10-01", "2026-10-31"]);
});
