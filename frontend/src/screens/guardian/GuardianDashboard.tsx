import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { guardian, reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { notificationTime } from "@/utils/format";
import type { GuardianReportResponse } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import {
  Screen,
  Card,
  ProgressBar,
  Body,
  Caption,
  Pill,
  Avatar,
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/components/ui";

/**
 * Guardian dashboard: `GET /guardian/{guardian_id}/elders` picks the elder,
 * then `GET /guardian/{guardian_id}/report` fills the cards.
 *
 * Scores appear here and only here — the guardian audience is the one the spec
 * allows them for. A link that is not consented to yet comes back as a 403, and
 * the spec asks for an explanation instead of an empty dashboard.
 */
const WEEKLY_TARGET_SESSIONS = 7;

const RISK_COPY: Record<string, { label: string; message: string }> = {
  low: { label: "낮음 · 안정", message: "지난 4주간 점수가 안정적으로 유지되고 있어요." },
  caution: { label: "보통 · 관찰", message: "최근 점수에 작은 변화가 있어요. 며칠 더 지켜봐 주세요." },
  warning: { label: "높음 · 확인 필요", message: "최근 점수가 낮아졌어요. 전문기관 상담을 권해요." },
};

function riskCopy(level: string | null) {
  return RISK_COPY[level ?? "low"] ?? RISK_COPY.low;
}

function Stat({ label, value, sub, color }: { label: string; value: string; sub?: string; color: string }) {
  return (
    <View style={[styles.stat, { flex: 1 }]}>
      <Caption>{label}</Caption>
      <Text style={[styles.statValue, { color }]}>{value}</Text>
      {sub ? <Caption style={{ color }}>{sub}</Caption> : null}
    </View>
  );
}

/** Recent activity is assembled from the report's own counters and alerts. */
function activityRows(report: GuardianReportResponse) {
  const rows: { icon: keyof typeof Ionicons.glyphMap; text: string; time: string }[] = [];

  if (report.latest_display_score !== null && report.last_session_at) {
    rows.push({
      icon: "clipboard",
      text: `CIST 검사 완료 (${report.latest_display_score}점)`,
      time: notificationTime(report.last_session_at),
    });
  }
  const sessions = report.activity_summary7d?.session_count ?? 0;
  if (sessions > 0) {
    rows.push({
      icon: "chatbubbles",
      text: `최근 7일 AI 정서 문답 ${sessions}회`,
      time: report.last_session_at ? notificationTime(report.last_session_at) : "",
    });
  }
  const diaries = report.activity_summary7d?.diary_count ?? 0;
  if (diaries > 0) {
    rows.push({ icon: "book", text: `최근 7일 일기 ${diaries}편`, time: "" });
  }
  return rows;
}

export default function GuardianDashboardScreen() {
  const { userId, selectedElderId, setSelectedElderId } = useApp();

  const elders = useApi(() => guardian.elders(userId as string), [userId], { enabled: !!userId });

  // The dashboard is what chooses the elder the other guardian tabs read.
  React.useEffect(() => {
    const first = elders.data?.elders[0];
    if (first && !selectedElderId) setSelectedElderId(first.elder_id);
  }, [elders.data, selectedElderId, setSelectedElderId]);

  const elderId = selectedElderId ?? elders.data?.elders[0]?.elder_id ?? null;

  const report = useApi(
    () => reports.guardianReport(userId as string, elderId as string),
    [userId, elderId],
    { enabled: !!userId && !!elderId },
  );

  if (elders.error) {
    return (
      <Screen>
        <ErrorState message={apiErrorMessage(elders.error)} onRetry={elders.reload} />
      </Screen>
    );
  }

  if (elders.data && elders.data.elders.length === 0) {
    return (
      <Screen>
        <EmptyState
          message={"연결된 어르신이 없어요.\n초대 코드로 먼저 연결해 주세요."}
          icon="people-outline"
        />
      </Screen>
    );
  }

  if (report.error) {
    return (
      <Screen>
        <ErrorState
          message={
            report.error.isForbidden
              ? "어르신이 아직 정보 열람에 동의하지 않았어요."
              : apiErrorMessage(report.error)
          }
          onRetry={report.error.isForbidden ? undefined : report.reload}
        />
      </Screen>
    );
  }

  if (!report.data) {
    return (
      <Screen>
        <LoadingState />
      </Screen>
    );
  }

  const data = report.data;
  const risk = riskCopy(data.latest_risk_level);
  // A missing `activity_summary7d` means the week has not been aggregated, not
  // that participation was zero — so the rate is left unknown rather than 0%.
  const sessions7d = data.activity_summary7d?.session_count ?? null;
  const participation =
    sessions7d === null
      ? null
      : Math.min(100, Math.round((sessions7d / WEEKLY_TARGET_SESSIONS) * 100));
  const activities = activityRows(data);

  return (
    <Screen>
      <View style={styles.headerRow}>
        <View>
          <Caption>보호자 대시보드</Caption>
          <Text style={styles.h1}>{data.elder_name}</Text>
        </View>
        <Avatar size={52} emoji="👵" />
      </View>

      <Card color={colors.primary} style={{ marginTop: spacing.lg }}>
        <View style={styles.rowBetween}>
          <View>
            <Caption style={{ color: "rgba(255,255,255,0.85)" }}>현재 인지 위험도</Caption>
            <Text style={styles.riskLevel}>{risk.label}</Text>
          </View>
          <Ionicons name="shield-checkmark" size={40} color={colors.white} />
        </View>
        <Body style={{ color: colors.white, marginTop: spacing.sm }}>
          {data.latest_summary ?? risk.message}
        </Body>
      </Card>

      <View style={styles.statRow}>
        <Stat
          label="최근 CIST"
          value={
            data.latest_display_score !== null
              ? `${data.latest_display_score}/${data.latest_score_max ?? 30}`
              : "—"
          }
          sub={data.latest_risk_level === "low" ? "정상" : "관찰"}
          color={colors.primary}
        />
        <Stat
          label="이번 주 참여"
          value={sessions7d === null ? "—" : `${sessions7d}/${WEEKLY_TARGET_SESSIONS}일`}
          color={colors.accent}
        />
        <Stat
          label="30일 추세"
          value={data.trend_30d === "declining" ? "↓" : data.trend_30d === "improving" ? "↑" : "→"}
          sub={data.trend_30d === "declining" ? "하락" : data.trend_30d === "improving" ? "개선" : "유지"}
          color="#6B9CB8"
        />
      </View>

      <Text style={styles.section}>최근 활동</Text>
      <Card>
        {activities.length === 0 ? (
          <Body>최근 7일 동안의 활동이 아직 없어요.</Body>
        ) : (
          activities.map((a, i, arr) => (
            <View key={a.text} style={[styles.activity, i < arr.length - 1 && styles.activityBorder]}>
              <View style={styles.activityIcon}>
                <Ionicons name={a.icon} size={20} color={colors.primary} />
              </View>
              <View style={{ flex: 1 }}>
                <Body style={{ fontWeight: fontWeight.semibold }}>{a.text}</Body>
                {a.time ? <Caption>{a.time}</Caption> : null}
              </View>
            </View>
          ))
        )}
      </Card>

      <Text style={styles.section}>주간 참여율</Text>
      <Card>
        {participation === null ? (
          <Body>이번 주 활동이 아직 집계되지 않았어요.</Body>
        ) : (
          <>
            <View style={styles.rowBetween}>
              <Body style={{ fontWeight: fontWeight.semibold }}>목표 대비 달성</Body>
              <Pill
                label={`${participation}%`}
                color={colors.secondary}
                textColor={colors.secondaryForeground}
              />
            </View>
            <ProgressBar value={participation} />
          </>
        )}
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  h1: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.foreground, marginTop: 2 },
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  riskLevel: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.white, marginTop: 2 },
  statRow: { flexDirection: "row", gap: spacing.md, marginTop: spacing.lg },
  stat: {
    backgroundColor: colors.card,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.md,
    alignItems: "center",
  },
  statValue: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, marginVertical: 2 },
  section: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground, marginTop: spacing.xl, marginBottom: spacing.md },
  activity: { flexDirection: "row", alignItems: "center", gap: spacing.md, paddingVertical: spacing.md },
  activityBorder: { borderBottomWidth: 1, borderBottomColor: colors.border },
  activityIcon: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.secondary, alignItems: "center", justifyContent: "center" },
});
