import type { GameHistoryItem } from "@/api/types";

export type GameDomain = "memory" | "language";

export function gameDomain(type: string): GameDomain | null {
  if (type === "image_match" || type === "color_match") return "memory";
  if (type === "consonant" || type === "word_match") return "language";
  return null;
}

export function gameTitle(type: string): string {
  switch (type) {
    case "image_match": return "카드 뒤집기";
    case "color_match": return "색깔 기억하기";
    case "consonant": return "초성 맞추기";
    case "word_match": return "단어 맞추기";
    default: return "두뇌 게임";
  }
}

export function gameResultSummary(record: GameHistoryItem): string {
  if (!record.completed) return "미완료";
  if (record.game_type === "image_match" && record.matched_pairs !== null) {
    return `${record.matched_pairs}쌍 맞춤${record.attempt_count !== null ? ` · ${record.attempt_count}회 시도` : ""}`;
  }
  return `게임 점수 ${record.score}점`;
}

export function recentGames(records: GameHistoryItem[], domain: GameDomain, limit: number): GameHistoryItem[] {
  return records.filter((record) => gameDomain(record.game_type) === domain)
    .sort((a, b) => b.played_at.localeCompare(a.played_at))
    .slice(0, limit);
}
