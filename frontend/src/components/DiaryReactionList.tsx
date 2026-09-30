import React from "react";
import { StyleSheet, View } from "react-native";

import type { ReactionResponse } from "@/api/types";
import { Body, Caption, SentenceText as Text } from "@/components/ui";
import { diaryReactionEmoji } from "@/utils/diaryReactions";
import { colors, fontSize, fontWeight, spacing } from "@/theme";

export default function DiaryReactionList({
  reactions,
  title = "보호자가 남긴 반응",
  grouped = false,
}: {
  reactions: ReactionResponse[];
  title?: string;
  grouped?: boolean;
}) {
  if (reactions.length === 0) return null;

  if (grouped) {
    const groups = new Map<string, ReactionResponse[]>();
    for (const reaction of reactions) {
      const items = groups.get(reaction.reactor_id) ?? [];
      items.push(reaction);
      groups.set(reaction.reactor_id, items);
    }
    return (
      <View style={styles.container}>
        <Body style={styles.title}>{title}</Body>
        {[...groups].map(([reactorId, items]) => (
          <View key={reactorId} style={styles.text}>
            <View style={styles.groupHeading}>
              <Caption>{items[0].reactor_name ?? "보호자"}</Caption>
              <Text style={styles.groupEmoji}>
                {items.filter((item) => item.reaction_type !== "message")
                  .map((item) => diaryReactionEmoji(item.reaction_type)).join(" ")}
              </Text>
            </View>
            {items.filter((item) => item.reaction_type === "message" && item.message)
              .map((item) => <Body key={item.reaction_id}>{item.message}</Body>)}
          </View>
        ))}
      </View>
    );
  }

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
  groupHeading: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  groupEmoji: { fontSize: fontSize.bodyLg },
});
