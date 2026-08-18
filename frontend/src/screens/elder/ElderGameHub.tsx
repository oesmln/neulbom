import React from "react";
import { View, StyleSheet, ScrollView } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useIsFocused, useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import type { ElderNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { game } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { monthDayLabel } from "@/utils/format";
import { colors, spacing, fontSize, fontWeight } from "@/theme";
import { Badge, Card, EmptyState, ErrorState, LoadingState, ScreenHeader, SentenceText as Text } from "@/components/ui";

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
  const isFocused = useIsFocused();
  const { userId } = useApp();
  const history = useApi(() => game.history(userId as string, 3), [userId, isFocused], {
    enabled: !!userId && isFocused,
  });

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScreenHeader title="두뇌 게임" subtitle="재미있는 게임으로 두뇌를 자극해요" />

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
        <View style={styles.rewardGuide}>
          <Ionicons name="sparkles-outline" size={18} color={colors.primary} />
          <View style={{ flex: 1 }}>
            <Text style={styles.rewardTitle}>게임 경험치</Text>
            <Text style={styles.rewardText}>참여 +3 XP · 성공 시 +10 XP · 하루 최대 100 XP</Text>
          </View>
        </View>

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

        <Text style={styles.sectionTitle}>최근 게임 기록</Text>
        {history.loading && !history.data ? <LoadingState label="게임 기록을 불러오는 중이에요" /> : null}
        {history.error && !history.data ? (
          <ErrorState message={apiErrorMessage(history.error)} onRetry={history.reload} />
        ) : null}
        {history.data?.records.length === 0 ? (
          <EmptyState message="아직 완료한 게임이 없어요" icon="game-controller-outline" />
        ) : null}
        {history.data?.records.map((record) => (
          <Card key={record.game_result_id} style={styles.historyCard}>
            <View style={styles.historyRow}>
              <View style={{ flex: 1 }}>
                <Text style={styles.historyTitle}>{gameTitle(record.game_type)}</Text>
                <Text style={styles.historyDate}>{monthDayLabel(record.played_at)}</Text>
              </View>
              <View style={styles.historyScoreBlock}>
                <Text style={styles.historyScore}>{record.score}점</Text>
                <Text style={styles.historyXp}>+{record.xp_earned} XP</Text>
              </View>
            </View>
          </Card>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

function gameTitle(type: string): string {
  if (type === "image_match") return "카드 뒤집기";
  if (type === "color_match") return "색깔 기억하기";
  if (type === "consonant") return "초성 맞추기";
  if (type === "word_match") return "단어 맞추기";
  return "두뇌 게임";
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.screenBackground },
  body: { padding: spacing.xl, gap: spacing.md, paddingBottom: spacing.xxl },
  game: { paddingVertical: spacing.xl },
  rewardGuide: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    padding: spacing.lg,
    borderRadius: 12,
    backgroundColor: colors.secondary,
  },
  rewardTitle: { fontSize: fontSize.body, fontWeight: fontWeight.bold, color: colors.primaryDark },
  rewardText: { marginTop: 2, fontSize: fontSize.caption, color: colors.mutedForeground },
  row: { flexDirection: "row", alignItems: "center" },
  titleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: 6 },
  title: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.foreground },
  desc: { fontSize: fontSize.body, color: colors.mutedForeground },
  sectionTitle: {
    marginTop: spacing.lg,
    fontSize: fontSize.subtitle,
    fontWeight: fontWeight.bold,
    color: colors.foreground,
  },
  historyCard: { paddingVertical: spacing.lg },
  historyRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  historyTitle: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },
  historyDate: { marginTop: 3, fontSize: fontSize.caption, color: colors.mutedForeground },
  historyScoreBlock: { alignItems: "flex-end", gap: 2 },
  historyScore: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.primary },
  historyXp: { fontSize: fontSize.caption, color: colors.mutedForeground },
});
