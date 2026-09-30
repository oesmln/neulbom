import React from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";

import { useApp } from "@/store/AppContext";
import { reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { guardianAccessErrorMessage } from "@/api/errors";
import { monthDayLabel } from "@/utils/format";
import type { GuardianNav, GuardianStackParamList } from "@/navigation/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
import {
  Screen,
  ScreenHeader,
  Card,
  Badge,
  Body,
  Caption,
  ErrorState,
  LoadingState,
  SentenceText as Text,
} from "@/components/ui";

/**
 * 알림에서 진입하는 검사 결과 상세 (`GET /screenings/{session_id}/result`, guardian audience).
 * 고령자 화면과 달리 점수·위험도 같은 수치를 그대로 보여준다.
 */
export default function GuardianScreeningResultScreen() {
  const navigation = useNavigation<GuardianNav>();
  const route = useRoute<RouteProp<GuardianStackParamList, "GuardianScreeningResult">>();
  const { userId, selectedElderId } = useApp();
  const sessionId = route.params.sessionId;

  const result = useApi(
    () => reports.screeningResult(sessionId, "guardian"),
    [sessionId],
    { enabled: !!sessionId },
  );
  const report = useApi(
    () => reports.guardianReport(userId as string, selectedElderId as string),
    [userId, selectedElderId],
    { enabled: !!userId && !!selectedElderId },
  );

  const header = (
    <ScreenHeader
      color={guardian.blue}
      title="검사 결과"
      subtitle={report.data ? `${report.data.elder_name} 어르신` : undefined}
      onBack={() => navigation.goBack()}
    />
  );

  if (result.error) {
    return (
      <Screen header={header}>
        <ErrorState
          message={guardianAccessErrorMessage(result.error, "검사 결과")}
          onRetry={result.reload}
        />
      </Screen>
    );
  }
  if (!result.data) {
    return (
      <Screen header={header}>
        <LoadingState />
      </Screen>
    );
  }

  const r = result.data;
  const score = r.display_score ?? r.screening_reference_score ?? null;
  const max = r.score_max ?? 30;
  const low = r.risk_level === "low";
  const badgeColor = low ? guardian.blue : colors.warning;
  const badgeBg = low ? guardian.blueLight : colors.accentLight;
  const percent = Math.round(((score ?? 0) / max) * 100);

  return (
    <Screen header={header}>
      <Card style={{ gap: spacing.md }}>
        <View style={styles.rowBetween}>
          <View>
            <Caption style={styles.eyebrow}>CIST 인지 선별검사</Caption>
            <Body style={{ fontWeight: fontWeight.semibold }}>
              {r.completed_at ? `${monthDayLabel(r.completed_at)} 검사` : "최근 검사"}
            </Body>
          </View>
          <Badge
            label={r.screening_label ?? r.display_label ?? "결과"}
            color={badgeColor}
            background={badgeBg}
          />
        </View>
        <View style={styles.scoreRow}>
          <Text style={styles.score}>{score === null ? "—" : `${score}점`}</Text>
          <Caption style={styles.scoreMax}>/ {max}점</Caption>
        </View>
        <View style={styles.track}>
          <View style={[styles.fill, { width: `${percent}%`, backgroundColor: badgeColor }]} />
        </View>
        <Caption>정상 하한 24점 · 경도 치매 의심 18점 이하</Caption>
      </Card>

      <Card style={{ marginTop: spacing.md, gap: spacing.sm }}>
        <Caption style={styles.eyebrow}>결과 안내</Caption>
        <Body>{r.message ?? "분석 결과를 준비하고 있어요."}</Body>
        {r.recommendation ? (
          <Body style={{ color: colors.mutedForeground }}>{r.recommendation}</Body>
        ) : null}
        <Caption style={{ marginTop: spacing.xs }}>
          이 점수는 참고용 스크리닝 결과이며, 정확한 진단은 전문의와 확인해 주세요.
        </Caption>
      </Card>

      <Pressable
        onPress={() => navigation.navigate("GuardianTabs", { screen: "GuardianChart" })}
        accessibilityRole="button"
        accessibilityLabel="변화 추이 보기"
        style={({ pressed }) => [styles.cta, { opacity: pressed ? 0.85 : 1 }]}
      >
        <Text style={styles.ctaLabel}>변화 추이 보기</Text>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start" },
  eyebrow: { color: colors.mutedForeground, marginBottom: 2 },
  scoreRow: { flexDirection: "row", alignItems: "flex-end", gap: spacing.xs },
  score: { fontSize: 40, fontWeight: fontWeight.bold, color: guardian.blue, lineHeight: 46 },
  scoreMax: { marginBottom: 8 },
  track: { height: 10, borderRadius: radius.pill, backgroundColor: colors.muted, overflow: "hidden" },
  fill: { height: "100%", borderRadius: radius.pill },
  cta: {
    marginTop: spacing.lg,
    backgroundColor: guardian.blue,
    borderRadius: radius.lg,
    paddingVertical: spacing.md,
    alignItems: "center",
  },
  ctaLabel: { color: colors.white, fontSize: fontSize.body, fontWeight: fontWeight.semibold },
});
