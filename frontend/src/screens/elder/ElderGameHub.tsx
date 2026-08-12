import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import type { ElderNav } from "@/navigation/types";
import { colors, spacing, fontSize, fontWeight } from "@/theme";
import { Badge, Card, ScreenHeader } from "@/components/ui";

/**
 * The three cognitive mini-games, wired to their screens (Issue #53).
 *
 * Titles, descriptions and ability badges follow the Figma hub card copy —
 * 색깔 기억하기 is the colour *memory* game the design ships, not the Stroop
 * variant an earlier draft of this hub described.
 */
const GAMES: {
  route: "ElderGameCardMatch" | "ElderGameColor" | "ElderGameConsonant";
  title: string;
  desc: string;
  badge: string;
  badgeColor: string;
  badgeBackground: string;
}[] = [
  {
    route: "ElderGameCardMatch",
    title: "카드 뒤집기",
    desc: "짝이 맞는 카드를 모두 찾아요",
    badge: "기억력",
    badgeColor: colors.primary,
    badgeBackground: colors.secondary,
  },
  {
    route: "ElderGameColor",
    title: "색깔 기억하기",
    desc: "색깔을 5초 보고 같은 색을 골라요",
    badge: "기억력",
    badgeColor: colors.accent,
    badgeBackground: colors.accentLight,
  },
  {
    route: "ElderGameConsonant",
    title: "초성 맞추기",
    desc: "초성에 맞는 단어를 직접 입력해요",
    badge: "언어력",
    badgeColor: colors.success,
    badgeBackground: colors.successLight,
  },
];

export default function ElderGameHubScreen() {
  const navigation = useNavigation<ElderNav>();

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScreenHeader title="두뇌 게임" subtitle="재미있는 게임으로 두뇌를 자극해요" />

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
        {GAMES.map((game) => (
          <Card
            key={game.route}
            onPress={() => navigation.navigate(game.route)}
            accessibilityLabel={`${game.title}. ${game.desc}`}
            style={styles.game}
          >
            <View style={styles.row}>
              <View style={{ flex: 1, paddingRight: spacing.md }}>
                <View style={styles.titleRow}>
                  <Text style={styles.title}>{game.title}</Text>
                  <Badge
                    label={game.badge}
                    color={game.badgeColor}
                    background={game.badgeBackground}
                  />
                </View>
                <Text style={styles.desc}>{game.desc}</Text>
              </View>
              <Ionicons name="chevron-forward" size={20} color={colors.mutedForeground} />
            </View>
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
  row: { flexDirection: "row", alignItems: "center" },
  titleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: 6 },
  title: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.foreground },
  desc: { fontSize: fontSize.body, color: colors.mutedForeground },
});
