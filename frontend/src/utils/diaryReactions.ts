import type { DiaryReactionType } from "@/api/types";

export type { DiaryReactionType } from "@/api/types";

export const DIARY_REACTIONS: ReadonlyArray<{ type: Exclude<DiaryReactionType, "message">; emoji: string; label: string }> = [
  { type: "heart", emoji: "❤️", label: "하트" },
  { type: "smile", emoji: "😊", label: "미소" },
  { type: "cheer", emoji: "👏", label: "응원" },
  { type: "pray", emoji: "🙏", label: "기도" },
  { type: "cry", emoji: "🥺", label: "공감" },
];

export function diaryReactionEmoji(type: string): string {
  return DIARY_REACTIONS.find((item) => item.type === type)?.emoji ?? "💬";
}
