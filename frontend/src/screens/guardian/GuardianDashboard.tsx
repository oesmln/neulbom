import React from "react";
import { View, Pressable, StyleSheet } from "react-native";
import { useIsFocused, useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { diaries as diariesApi, guardian as guardianApi, reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage, guardianAccessErrorMessage } from "@/api/errors";
import { monthDayLabel, moodEmoji } from "@/utils/format";
import type { GuardianNav } from "@/navigation/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
import AiRiskTrendChart from "@/components/AiRiskTrendChart";
import GuardianGameResults from "@/components/GuardianGameResults";
import { riskBand, riskBandLabel, thresholdsForPoints } from "@/utils/riskBands";
import ElderSelector from "@/components/ElderSelector";
import GuardianHeaderActions from "@/components/GuardianHeaderActions";
import {
  Screen,
  ScreenHeader,
  Card,
  Button,
  Body,
  Caption,
  EmptyState,
  ErrorState,
  LoadingState,
  SentenceText as Text,
} from "@/components/ui";

/**
 * Guardian dashboard: `GET /guardian/{guardian_id}/elders` picks the elder, then
 * `GET /guardian/{guardian_id}/report` fills the cards.
 *
 * A link that is not consented to yet comes back as a 403, and the spec asks
 * for an explanation instead of an empty dashboard.
 *
 * Card order: 피보호자 현황 → 경보 → 지표 → 추이 → 게임 → 최근 일기.
 */
const WEEKLY_TARGET_SESSIONS = 7;
const RECENT_DIARY_LIMIT = 3;

function Indicator({ label, value, unit, color }: { label: string; value: string; unit?: string; color: string }) {
  return (
    <View style={styles.indicator}>
      <Text style={[styles.indicatorValue, { color }]}>{value}</Text>
      {unit ? <Caption style={{ marginTop: 2 }}>{unit}</Caption> : null}
      <Caption style={{ marginTop: 6 }}>{label}</Caption>
    </View>
  );
}

export default function GuardianDashboardScreen() {
  const isFocused = useIsFocused();
  const navigation = useNavigation<GuardianNav>();
  const { userId, userName, selectedElderId, setSelectedElderId } = useApp();

  const elders = useApi(() => guardianApi.elders(userId as string, "active"), [userId], {
    enabled: !!userId,
  });

  // The dashboard is what chooses the elder the other guardian tabs read.
  React.useEffect(() => {
    if (!elders.data) return;
    const stillLinked = elders.data.elders.some((elder) => elder.elder_id === selectedElderId);
    if (!stillLinked) setSelectedElderId(elders.data.elders[0]?.elder_id ?? null);
  }, [elders.data, selectedElderId, setSelectedElderId]);

  const elderId = selectedElderId ?? elders.data?.elders[0]?.elder_id ?? null;
  const selectedElder = elders.data?.elders.find((elder) => elder.elder_id === elderId) ?? null;

  const report = useApi(
    () => reports.guardianReport(userId as string, elderId as string),
    [userId, elderId, isFocused],
    { enabled: !!userId && !!elderId && isFocused },
  );

  const recentDiaries = useApi(
    () => diariesApi.listForUser(elderId as string, { limit: RECENT_DIARY_LIMIT }),
    [elderId, isFocused],
    { enabled: !!elderId && isFocused },
  );

  const header = (
    <ScreenHeader
      color={guardian.blue}
      eyebrow="보호자 모드"
      title={userName ? `${userName} 님` : "보호자"}
      subtitle={
        report.data?.elder_id === elderId
          ? `${report.data.elder_name}님의 인지 상태를 모니터링 중`
          : selectedElder
            ? `${selectedElder.elder_name}님의 인지 상태를 확인 중`
            : undefined
      }
      right={<GuardianHeaderActions />}
    />
  );

  if (elders.error) {
    return (
      <Screen header={header}>
        <ErrorState message={apiErrorMessage(elders.error)} onRetry={elders.reload} />
      </Screen>
    );
  }

  if (elders.data && elders.data.elders.length === 0) {
    return (
      <Screen header={header}>
        <EmptyState
          message={"연결된 어르신이 없어요.\n초대 코드로 먼저 연결해 주세요."}
          icon="people-outline"
        />
        <Button
          label="초대 코드 생성"
          icon="person-add-outline"
          onPress={() => navigation.navigate("GuardianConnections")}
          style={{ backgroundColor: guardian.blue }}
        />
      </Screen>
    );
  }

  if (report.error) {
    return (
      <Screen header={header}>
        <ErrorState
          message={
            guardianAccessErrorMessage(report.error, "검사·요약")
          }
          onRetry={report.error.isForbidden ? undefined : report.reload}
        />
      </Screen>
    );
  }

  if (!report.data) {
    return (
      <Screen header={header}>
        <LoadingState />
      </Screen>
    );
  }

  const data = report.data;
  const elderItems = elders.data?.elders ?? [];
  const aiRiskPoints = data.ai_risk_trend_points ?? [];
  const latestAiRisk = [...aiRiskPoints].sort((a, b) => a.analyzed_at.localeCompare(b.analyzed_at)).at(-1);
  const latestBand = latestAiRisk ? riskBand(latestAiRisk.risk_score, thresholdsForPoints(aiRiskPoints)) : null;
  const alert = data.recent_alerts[0] ?? null;

  // A missing `activity_summary7d` means the week has not been aggregated, not
  // that participation was zero — so the count is left unknown rather than 0.
  const sessions7d = data.activity_summary7d?.session_count ?? null;
  const games7d = data.activity_summary7d?.game_count ?? null;

  return (
    <Screen header={header}>
      <ElderSelector elders={elderItems} />

      {/* ② 피보호자 현황 */}
      <Card>
        <Caption style={styles.eyebrow}>피보호자 현황</Caption>
        <Text style={styles.elderName}>{data.elder_name} 어르신</Text>
        <Caption style={{ marginTop: 2 }}>
          {data.last_session_at
            ? `마지막 대화 · ${monthDayLabel(data.last_session_at)}`
            : "아직 대화 기록이 없어요"}
        </Caption>
      </Card>

      {/* ③ 경보 — only when the server actually raised one */}
      {alert ? (
        <View style={styles.alertCard}>
          <View style={styles.alertHead}>
            <Ionicons name="warning-outline" size={20} color={colors.destructive} />
            <View style={{ flex: 1 }}>
              <Text style={styles.alertTitle}>{alert.title}</Text>
              <Text style={styles.alertBody}>{alert.body}</Text>
            </View>
          </View>
          <Pressable
            onPress={() => navigation.navigate("GuardianCounselingCenters")}
            accessibilityRole="button"
            accessibilityLabel="전문의 상담 예약"
            style={styles.alertAction}
          >
            <Text style={styles.alertActionLabel}>전문의 상담 예약</Text>
          </Pressable>
        </View>
      ) : null}

      {/* ④ 주요 지표 */}
      <Card style={{ marginTop: spacing.lg }}>
        <Caption style={styles.eyebrow}>주요 지표</Caption>
        <View style={styles.indicatorRow}>
          <Indicator
            label="대화 완료"
            value={sessions7d === null ? "—" : `${sessions7d}/${WEEKLY_TARGET_SESSIONS}`}
            unit={sessions7d === null ? undefined : "일"}
            color={guardian.blue}
          />
          <Indicator
            label="게임 참여"
            value={games7d === null ? "—" : String(games7d)}
            unit={games7d === null ? undefined : "회"}
            color={games7d === null ? colors.mutedForeground : guardian.blue}
          />
        </View>
      </Card>

      {/* ⑤ AI 인지 위험 신호 추이 */}
      <Card style={{ marginTop: spacing.lg }}>
        <View style={styles.rowBetween}>
          <Body style={{ fontWeight: fontWeight.semibold }}>AI 인지 위험 신호 추이</Body>
          <Pressable
            onPress={() => navigation.navigate("GuardianTabs", { screen: "GuardianChart" })}
            accessibilityRole="button"
            accessibilityLabel="AI 인지 위험 신호 추이 상세 보기"
          >
            <Text style={styles.link}>상세 보기</Text>
          </Pressable>
        </View>
        {aiRiskPoints.length > 0 ? (
          <>
            <View style={styles.riskSummary}>
              <Text style={styles.riskValue}>{Math.round(latestAiRisk!.risk_score * 100)}<Text style={styles.riskScale}> / 100</Text></Text>
              {latestBand ? (
                <View style={[styles.riskBadge, { backgroundColor: latestBand === "stable" ? colors.secondary : latestBand === "review_needed" ? colors.destructiveLight : colors.warningLight }]}>
                  <Text style={[styles.riskBadgeText, { color: latestBand === "stable" ? colors.primaryDark : latestBand === "review_needed" ? colors.destructive : colors.warning }]}>
                    {riskBandLabel(latestBand)}
                  </Text>
                </View>
              ) : null}
            </View>
            <AiRiskTrendChart points={aiRiskPoints.slice(-6)} compact />
          </>
        ) : (
          <Body style={{ marginTop: spacing.md }}>아직 분석된 인지 위험 신호가 없어요.</Body>
        )}
        <Caption>일상 문답은 CIST 기준점의 지남력·주의력 문항을 부분 갱신해 추정해요. AI 위험 신호는 진단 결과가 아닙니다.</Caption>
      </Card>

      {elderId ? (
        <GuardianGameResults key={elderId} elderId={elderId} compact
          onViewAll={() => navigation.navigate("GuardianTabs", { screen: "GuardianChart" })} />
      ) : null}

      {/* ⑦ 최근 일기 */}
      <Card style={{ marginTop: spacing.lg }}>
        <View style={styles.rowBetween}>
          <Body style={{ fontWeight: fontWeight.semibold }}>최근 일기</Body>
          <Pressable
            onPress={() => navigation.navigate("GuardianTabs", { screen: "GuardianRecord" })}
            accessibilityRole="button"
            accessibilityLabel="일기 전체 보기"
            hitSlop={8}
          >
            <Text style={styles.link}>전체 보기</Text>
          </Pressable>
        </View>

        {recentDiaries.data && recentDiaries.data.diaries.length > 0 ? (
          recentDiaries.data.diaries.map((d, i, arr) => (
            <Pressable
              key={d.diary_id}
              onPress={() => navigation.navigate("GuardianTabs", { screen: "GuardianRecord" })}
              accessibilityRole="button"
              accessibilityLabel={`${monthDayLabel(d.written_at)} 일기 보기`}
              style={[styles.diaryRow, i < arr.length - 1 && styles.diaryBorder]}
            >
              <Text style={styles.diaryMood}>{moodEmoji(d.mood, d.mood_level)}</Text>
              <View style={{ flex: 1 }}>
                <Caption>{monthDayLabel(d.written_at)}</Caption>
                <Body numberOfLines={1} style={{ marginTop: 2 }}>
                  {d.preview ?? d.title ?? ""}
                </Body>
              </View>
              <Ionicons name="chevron-forward" size={16} color={colors.mutedForeground} />
            </Pressable>
          ))
        ) : (
          <Body style={{ marginTop: spacing.md }}>아직 작성된 일기가 없어요.</Body>
        )}
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  eyebrow: { letterSpacing: 0.7, marginBottom: spacing.md },
  rowBetween: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: spacing.xs,
  },
  elderName: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },

  alertCard: {
    marginTop: spacing.lg,
    backgroundColor: colors.destructiveLight,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: guardian.dangerBorder,
    padding: spacing.lg,
  },
  alertHead: { flexDirection: "row", gap: spacing.md, marginBottom: spacing.md },
  alertTitle: {
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
    color: colors.destructive,
  },
  alertBody: {
    fontSize: fontSize.caption,
    color: guardian.dangerText,
    lineHeight: 20,
    marginTop: 2,
  },
  alertAction: {
    height: 40,
    borderRadius: radius.md,
    backgroundColor: colors.destructive,
    alignItems: "center",
    justifyContent: "center",
  },
  alertActionLabel: {
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
    color: colors.white,
  },

  indicatorRow: { flexDirection: "row", gap: spacing.lg },
  indicator: { flex: 1, alignItems: "center" },
  indicatorValue: { fontSize: 22, fontWeight: fontWeight.bold, lineHeight: 24 },
  riskSummary: { flexDirection: "row", alignItems: "center", gap: spacing.md, marginTop: spacing.md },
  riskValue: { fontSize: 26, fontWeight: fontWeight.bold, color: guardian.blueDark },
  riskScale: { fontSize: fontSize.body, fontWeight: fontWeight.normal, color: colors.mutedForeground },
  riskBadge: { borderRadius: radius.md, paddingHorizontal: spacing.md, paddingVertical: spacing.xs },
  riskBadgeText: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold },

  linkRow: { flexDirection: "row", alignItems: "center", gap: 2 },
  link: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: guardian.blue },

  diaryRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
  },
  diaryBorder: { borderBottomWidth: 1, borderBottomColor: colors.border },
  diaryMood: { fontSize: 18 },
});
