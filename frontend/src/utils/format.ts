/**
 * Turning backend values into the Korean strings the screens show.
 *
 * The API sends ISO-8601 instants and "YYYY-MM-DD" dates; nothing arrives
 * pre-formatted, so every screen that used to hard-code "오늘 오전 8:00" reads
 * these instead.
 */

const DAY_MS = 86_400_000;

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** "2026-08-12" for a Date, in local time — matches the backend's LocalDate. */
export function isoDateOf(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")}`;
}

export function parseIso(value: string): Date {
  return new Date(value);
}

function timeLabel(d: Date): string {
  const hour = d.getHours();
  const minute = String(d.getMinutes()).padStart(2, "0");
  const meridiem = hour < 12 ? "오전" : "오후";
  const display = hour % 12 === 0 ? 12 : hour % 12;
  return `${meridiem} ${display}:${minute}`;
}

/** "오늘 오전 8:00" / "어제 오후 3:00" / "6월 30일" — the notification list format. */
export function notificationTime(iso: string): string {
  const d = parseIso(iso);
  if (Number.isNaN(d.getTime())) return "";
  const dayDiff = Math.round((startOfDay(new Date()) - startOfDay(d)) / DAY_MS);
  if (dayDiff <= 0) {
    const minutes = Math.floor((Date.now() - d.getTime()) / 60_000);
    if (minutes < 1) return "방금 전";
    if (minutes < 60) return `${minutes}분 전`;
    return `오늘 ${timeLabel(d)}`;
  }
  if (dayDiff === 1) return `어제 ${timeLabel(d)}`;
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/** "7월 7일" — used by diary and calendar cards. */
export function monthDayLabel(value: string): string {
  const d = parseIso(value);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/** "7월 7일 · 오늘" when the date is today. */
export function diaryDateLabel(value: string): string {
  const d = parseIso(value);
  if (Number.isNaN(d.getTime())) return "";
  const dayDiff = Math.round((startOfDay(new Date()) - startOfDay(d)) / DAY_MS);
  const base = monthDayLabel(value);
  if (dayDiff === 0) return `${base} · 오늘`;
  if (dayDiff === 1) return `${base} · 어제`;
  return base;
}

/**
 * Mood emoji for the calendar and diary cards.
 *
 * `mood` is a backend string and `mood_level` a 1–5 scale; the emoji is a
 * presentation choice, so the mapping lives here rather than in each screen.
 */
export function moodEmoji(mood: string | null, level: number | null): string {
  if (mood === "very_happy" || mood === "good" || (level ?? 0) >= 5) return "😄";
  if (mood === "happy" || level === 4) return "😊";
  if (
    mood === "very_sad" ||
    mood === "sad" ||
    mood === "bad" ||
    (level !== null && level <= 2)
  ) return "😔";
  if (mood === "neutral" || level === 3) return "😐";
  return "😊";
}
