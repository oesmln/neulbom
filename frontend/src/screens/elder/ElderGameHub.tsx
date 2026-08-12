import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { colors, spacing, fontSize, fontWeight } from "@/theme";
import { Badge, Card, ScreenHeader } from "@/components/ui";

/**
 * The three cognitive mini-games.
 *
 * The games themselves are not built yet — this is the hub only. Card flip is
 * the one the API spec pins down (`POST /game/result` wants matched pairs,
 * attempts, elapsed time and restart count, and rejects duplicate results), so
 * it is the one to build first.
 */
const GAMES = [
  {
    id: "card-flip",
    title: "카드 뒤집기",
    desc: "짝이 맞는 카드를 모두 찾아요",
    badge: "기억력",
    badgeColor: colors.primary,
    badgeBackground: colors.secondary,
  },
  {
    id: "color-match",
    title: "색깔 맞추기",
    desc: "글자의 색깔을 빠르게 맞춰요",
    badge: "집중력",
    badgeColor: colors.accent,
    badgeBackground: colors.accentLight,
  },
  {
    id: "initial-quiz",
    title: "초성 맞추기",
    desc: "초성을 보고 알맞은 단어를 찾아요",
    badge: "언어력",
    badgeColor: colors.success,
    badgeBackground: colors.successLight,
  },
];

export default function ElderGameHubScreen() {
  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScreenHeader title="두뇌 게임" subtitle="재미있는 게임으로 두뇌를 자극해요" />

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
        {GAMES.map((game) => (
          <Card key={game.id} style={styles.game}>
            <View style={styles.titleRow}>
              <Text style={styles.title}>{game.title}</Text>
              <Badge
                label={game.badge}
                color={game.badgeColor}
                background={game.badgeBackground}
              />
            </View>
            <Text style={styles.desc}>{game.desc}</Text>
          </Card>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.screenBackground },
  body: { padding: spacing.xl, gap: spacing.md, paddingBottom: spacing.xxl },
  game: { paddingVertical: spacing.xl },
  titleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: 6 },
  title: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.foreground },
  desc: { fontSize: fontSize.body, color: colors.mutedForeground },
});
