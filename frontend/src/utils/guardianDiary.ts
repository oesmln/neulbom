import type { DiaryListItem } from "../api/types";

/** 일기 API의 날짜 범위와 같은 서울 날짜로 묶는다. */
export function diaryDay(value: string | Date): string {
  const instant = value instanceof Date ? value.getTime() : Date.parse(value);
  return new Date(instant + 9 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

export function diaryTime(value: string): string {
  return new Date(value).toLocaleTimeString("ko-KR", {
    timeZone: "Asia/Seoul", hour: "numeric", minute: "2-digit",
  });
}

export function diariesByDay(entries: DiaryListItem[], from: string, to: string) {
  const days: Record<string, DiaryListItem[]> = {};
  const seen = new Set<string>();
  for (const entry of [...entries].sort((a, b) => Date.parse(b.written_at) - Date.parse(a.written_at))) {
    const day = diaryDay(entry.written_at);
    if (seen.has(entry.diary_id) || day < from || day > to) continue;
    seen.add(entry.diary_id);
    (days[day] ??= []).push(entry);
  }
  return days;
}
