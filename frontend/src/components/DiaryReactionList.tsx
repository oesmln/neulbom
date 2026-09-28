import React from "react";
import { StyleSheet, View } from "react-native";

import type { ReactionResponse } from "@/api/types";
import { Body, Caption, SentenceText as Text } from "@/components/ui";
import { diaryReactionEmoji } from "@/utils/diaryReactions";
import { colors, fontSize, fontWeight, spacing } from "@/theme";

export default function DiaryReactionList({
  reactions,
  title = "보호자가 남긴 반응",
}: {
  reactions: ReactionResponse[];
  title?: string;
}) {
  if (reactions.length === 0) return null;

  return (
    <View style={styles.container}>
      <Body style={styles.title}>{title}</Body>
      {reactions.map((reaction) => (
        <View key={reaction.reaction_id} style={styles.row}>
          <Text style={styles.emoji}>{diaryReactionEmoji(reaction.reaction_type)}</Text>
          <View style={styles.text}>
            <Caption>{reaction.reactor_name ?? "보호자"}</Caption>
            {reaction.reaction_type === "message" && reaction.message ? (
              <Body>{reaction.message}</Body>
            ) : null}
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.sm },
  title: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  row: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm },
  emoji: { fontSize: fontSize.title },
  text: { flex: 1, gap: 2 },
});
