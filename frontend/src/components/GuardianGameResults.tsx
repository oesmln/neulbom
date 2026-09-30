import React from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useIsFocused } from "@react-navigation/native";

import { game } from "@/api";
import { guardianAccessErrorMessage } from "@/api/errors";
import type { GameHistoryItem } from "@/api/types";
import { useApi } from "@/hooks/useApi";
import { monthDayLabel } from "@/utils/format";
import { gameResultSummary, gameTitle, recentGames } from "@/utils/guardianGameResults";
import type { GameDomain } from "@/utils/guardianGameResults";
import { colors, fontSize, fontWeight, guardian, radius, spacing } from "@/theme";
import { Body, Caption, Card, ErrorState, LoadingState, SentenceText as Text } from "@/components/ui";

const DOMAINS: { key: GameDomain; label: string; icon: keyof typeof Ionicons.glyphMap; tint: string; ink: string }[] = [
  { key: "memory", label: "기억력 활동", icon: "albums-outline", tint: colors.secondary, ink: colors.primaryDark },
  { key: "language", label: "언어력 활동", icon: "chatbubble-ellipses-outline", tint: guardian.blueLight, ink: guardian.blueDark },
];

function ResultRow({ record }: { record: GameHistoryItem }) {
  return (
    <View style={styles.resultRow}>
      <View style={{ flex: 1 }}>
        <Text style={styles.resultTitle}>{gameTitle(record.game_type)}</Text>
        <Caption>{monthDayLabel(record.played_at)} · {record.completed ? "완료" : "미완료"}</Caption>
      </View>
      <Text style={styles.resultValue}>{gameResultSummary(record)}</Text>
    </View>
  );
}

export default function GuardianGameResults({
  elderId,
  compact = false,
  onViewAll,
}: {
  elderId: string;
  compact?: boolean;
  onViewAll?: () => void;
}) {
  const isFocused = useIsFocused();
  const history = useApi(() => game.history(elderId, 100), [elderId, isFocused], { enabled: isFocused });

  return (
    <Card style={styles.card}>
      <View style={styles.headingRow}>
        <View style={{ flex: 1 }}>
          <Body style={styles.heading}>두뇌 게임 결과</Body>
          <Caption>기억력·언어력 활동 기록</Caption>
        </View>
        {onViewAll ? (
          <Pressable onPress={onViewAll} accessibilityRole="button" accessibilityLabel="두뇌 게임 결과 자세히 보기">
            <Text style={styles.link}>자세히 보기</Text>
          </Pressable>
        ) : null}
      </View>
      {history.loading && !history.data ? <LoadingState label="게임 기록을 불러오는 중이에요" /> : null}
      {history.error ? (
        history.error.isForbidden
          ? <Caption style={styles.message}>{guardianAccessErrorMessage(history.error, "게임 기록")}</Caption>
          : <ErrorState message={guardianAccessErrorMessage(history.error, "게임 기록")} onRetry={history.reload} />
      ) : null}
      {history.data && !history.error ? (
        <>
          {DOMAINS.map((domain) => {
            const records = recentGames(history.data!.records, domain.key, compact ? 1 : 3);
            return (
              <View key={domain.key} style={styles.domain}>
                <View style={[styles.domainIcon, { backgroundColor: domain.tint }]}>
                  <Ionicons name={domain.icon} size={19} color={domain.ink} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.domainTitle}>{domain.label}</Text>
                  {records.length > 0
                    ? records.map((record) => <ResultRow key={record.game_result_id} record={record} />)
                    : <Caption style={styles.message}>아직 게임 기록이 없어요.</Caption>}
                </View>
              </View>
            );
          })}
          <Caption style={styles.footnote}>게임 결과는 활동 기록이며 AI 위험 신호 지수나 진단 결과에 합산되지 않아요.</Caption>
        </>
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { marginTop: spacing.lg },
  headingRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: spacing.md },
  heading: { fontWeight: fontWeight.semibold },
  link: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: guardian.blue },
  domain: { flexDirection: "row", gap: spacing.md, paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.border },
  domainIcon: { width: 36, height: 36, borderRadius: radius.sm, alignItems: "center", justifyContent: "center" },
  domainTitle: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  resultRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginTop: spacing.sm },
  resultTitle: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.foreground },
  resultValue: { fontSize: fontSize.caption, color: guardian.blueDark, textAlign: "right", maxWidth: "48%" },
  message: { marginTop: spacing.sm },
  footnote: { marginTop: spacing.sm },
});
