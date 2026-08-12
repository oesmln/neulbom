import React from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav, ElderTabParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import type {
  DashboardCognitiveActivity,
  DashboardDiarySummary,
  DashboardTask,
} from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight, cognitiveStages } from "@/theme";
import { Badge, Card, ErrorState, LoadingState } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import { DEFAULT_MEMOI } from "@/components/memoiCharacters";

/**
 * Elder home — everything on this screen comes from `GET /dashboard/{user_id}`.
 *
 * Nothing here is derived locally: the greeting card reads
 * `conversation_streak_days`, the two feature cards are `today_tasks`, and the
 * status card renders `cognitive_activity` exactly as the server wrote it.
 */
function greetingFor(hour: number) {
  if (hour < 12) return "좋은 아침이에요";
  if (hour < 18) return "좋은 오후예요";
  return "좋은 저녁이에요";
}

/** `target_route` is a screen name; only elder tabs are reachable from here. */
const TAB_ROUTES: (keyof ElderTabParamList)[] = [
  "ElderAiChat",
  "ElderCalendar",
  "ElderHome",
  "ElderGameHub",
  "ElderMyPage",
];

function tabRoute(target: string | null): keyof ElderTabParamList | null {
  return TAB_ROUTES.find((r) => r === target) ?? null;
}

function taskBadge(task: DashboardTask): { label: string; color: string; background: string } {
  if (task.task_type === "emotional_qa") {
    return task.status === "completed"
      ? { label: "오늘 완료", color: colors.success, background: colors.successLight }
      : { label: "오늘 미완료", color: colors.accent, background: colors.accentLight };
  }
  return { label: "3가지 게임", color: colors.success, background: colors.successLight };
}

/**
 * Yesterday's diary, rendered as one more feature card alongside 정서 문답 and
 * 두뇌 게임 — same `Card`, same row layout, same badge treatment.
 *
 * The status wording is the server's — `display_label` and `message` come from
 * `latest_diary`, and only the badge colour is decided here. The prototype
 * hardcoded "생성 완료"; deriving it from `generation_status` keeps the card
 * honest when the overnight job is still running or failed.
 */
function diaryBadge(status: string): { color: string; background: string } {
  if (status === "completed") {
    return { color: colors.success, background: colors.successLight };
  }
  return { color: colors.warning, background: colors.warningLight };
}

function referenceLabel(date: string | null): string | null {
  if (!date) return null;
  const [y, m, d] = date.split("-");
  return `${Number(y)}년 ${Number(m)}월 ${Number(d)}일 문답 기준`;
}

export default function ElderHomeScreen() {
  const navigation = useNavigation<ElderNav>();
  const { userId, userName } = useApp();
  const greeting = greetingFor(new Date().getHours());

  const { data, error, loading, reload } = useApi(
    () => reports.dashboard(userId as string),
    [userId],
    { enabled: !!userId },
  );

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: spacing.xxl }}>
        <View style={styles.stage}>
          <Pressable
            onPress={() => navigation.navigate("ElderNotifications")}
            accessibilityRole="button"
            accessibilityLabel={
              data && data.unread_notification_count > 0
                ? `알림 ${data.unread_notification_count}건`
                : "알림"
            }
            hitSlop={8}
            style={styles.bell}
          >
            <Ionicons name="notifications-outline" size={20} color={colors.white} />
            {data && data.unread_notification_count > 0 ? (
              <View style={styles.bellBadge}>
                <Text style={styles.bellCount}>
                  {data.unread_notification_count > 9 ? "9+" : data.unread_notification_count}
                </Text>
              </View>
            ) : null}
          </Pressable>

          <Memoi3D character={DEFAULT_MEMOI} height={116} style={{ width: 150 }} />
          <Text style={styles.greeting}>{greeting}</Text>
          <Text style={styles.name}>{userName ? `${userName} 어르신` : "어르신"}</Text>
          {data ? (
            <View style={styles.streak}>
              <Text style={styles.streakLabel}>
                {data.conversation_streak_days}일 연속 대화 완료
              </Text>
            </View>
          ) : null}
        </View>

        <View style={styles.body}>
          {loading && !data ? <LoadingState /> : null}

          {error && !data ? (
            <ErrorState message={apiErrorMessage(error)} onRetry={reload} />
          ) : null}

          {data
            ? data.today_tasks.map((task) => {
                const badge = taskBadge(task);
                const route = tabRoute(task.target_route);
                return (
                  <Card
                    key={task.task_type}
                    onPress={
                      route
                        ? () => navigation.navigate("ElderTabs", { screen: route })
                        : undefined
                    }
                    accessibilityLabel={`${task.title}. ${task.description ?? ""}`}
                    style={styles.feature}
                  >
                    <View style={styles.featureRow}>
                      <View style={{ flex: 1, paddingRight: spacing.md }}>
                        <View style={styles.featureTitleRow}>
                          <Text style={styles.featureTitle}>{task.title}</Text>
                          <Badge
                            label={badge.label}
                            color={badge.color}
                            background={badge.background}
                          />
                        </View>
                        {task.description ? (
                          <Text style={styles.featureDesc}>{task.description}</Text>
                        ) : null}
                      </View>
                      <Ionicons name="chevron-forward" size={18} color={colors.mutedForeground} />
                    </View>
                  </Card>
                );
              })
            : null}

          {data?.latest_diary ? <DiaryCard diary={data.latest_diary} /> : null}

          {data?.cognitive_activity ? (
            <CognitiveStatusCard activity={data.cognitive_activity} />
          ) : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function DiaryCard({ diary }: { diary: DashboardDiarySummary }) {
  const navigation = useNavigation<ElderNav>();
  const badge = diaryBadge(diary.generation_status);

  return (
    <Card
      onPress={() => navigation.navigate("ElderTabs", { screen: "ElderCalendar" })}
      accessibilityLabel={`일기. ${diary.message ?? ""}`}
      style={styles.feature}
    >
      <View style={styles.featureRow}>
        <View style={{ flex: 1, paddingRight: spacing.md }}>
          <View style={styles.featureTitleRow}>
            <Text style={styles.featureTitle}>일기</Text>
            {diary.display_label ? (
              <Badge
                label={diary.display_label}
                color={badge.color}
                background={badge.background}
              />
            ) : null}
          </View>
          {diary.message ? <Text style={styles.featureDesc}>{diary.message}</Text> : null}
        </View>
        <Ionicons name="chevron-forward" size={18} color={colors.mutedForeground} />
      </View>
    </Card>
  );
}

/**
 * Where a score card used to be.
 *
 * The elder is shown a stage and a sentence, never a number — api-spec 5.1 has
 * the server pick the stage and send the wording with it, precisely so the app
 * cannot turn a screening score into something that reads as a diagnosis. The
 * title and message below are the server's `title` / `message`, not local copy.
 */
function CognitiveStatusCard({ activity }: { activity: DashboardCognitiveActivity }) {
  const index = Math.max(0, cognitiveStages.findIndex((s) => s.key === activity.status));
  const current = cognitiveStages[index];
  const footnote = referenceLabel(activity.reference_date);

  return (
    <Card style={{ gap: spacing.lg }}>
      <Text style={styles.cardKicker}>인지 활동 상태</Text>

      <View style={[styles.stageBanner, { backgroundColor: current.bg }]}>
        <Text style={[styles.stageTitle, { color: current.color }]}>{activity.title}</Text>
        <Text style={[styles.stageDesc, { color: current.color }]}>{activity.message}</Text>
      </View>

      <View
        style={styles.stepper}
        accessibilityRole="progressbar"
        accessibilityLabel={`인지 활동 상태: ${activity.display_label}`}
      >
        {cognitiveStages.map((s, i) => {
          const active = i === index;
          const passed = i < index;
          const isLast = i === cognitiveStages.length - 1;
          return (
            <React.Fragment key={s.key}>
              <View style={styles.stepNodeColumn}>
                <View
                  style={[
                    styles.stepNode,
                    {
                      backgroundColor: active ? current.color : passed ? "#C8D8CB" : colors.muted,
                      borderColor: active ? `${current.color}30` : "transparent",
                    },
                  ]}
                >
                  {active ? <View style={styles.stepNodeDot} /> : null}
                </View>
                <Text
                  style={[
                    styles.stepLabel,
                    {
                      color: active ? current.color : colors.mutedForeground,
                      fontWeight: active ? fontWeight.bold : fontWeight.normal,
                    },
                  ]}
                >
                  {s.step}
                </Text>
              </View>
              {isLast ? null : (
                <View style={[styles.stepLine, { backgroundColor: passed ? "#C8D8CB" : colors.muted }]} />
              )}
            </React.Fragment>
          );
        })}
      </View>

      {footnote ? <Text style={styles.cardFootnote}>{footnote}</Text> : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },

  bell: {
    position: "absolute",
    top: spacing.lg,
    right: spacing.xl,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "rgba(255,255,255,0.18)",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 1,
  },
  bellBadge: {
    position: "absolute",
    top: -2,
    right: -2,
    minWidth: 16,
    height: 16,
    borderRadius: 8,
    paddingHorizontal: 3,
    backgroundColor: colors.destructive,
    alignItems: "center",
    justifyContent: "center",
  },
  bellCount: { fontSize: 9, fontWeight: fontWeight.bold, color: colors.white },
  stage: {
    backgroundColor: colors.primary,
    alignItems: "center",
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.xl,
    paddingBottom: spacing.xl,
  },
  greeting: { fontSize: fontSize.caption, color: "rgba(255,255,255,0.65)", marginTop: spacing.sm },
  name: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.white, marginTop: 2 },
  streak: {
    marginTop: spacing.sm,
    backgroundColor: "rgba(255,255,255,0.12)",
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
  },
  streakLabel: {
    fontSize: fontSize.caption,
    fontWeight: fontWeight.semibold,
    color: "rgba(255,255,255,0.85)",
  },

  body: { padding: spacing.xl, gap: spacing.md },

  feature: { paddingVertical: spacing.lg, paddingHorizontal: spacing.xl, minHeight: 72 },
  featureRow: { flexDirection: "row", alignItems: "center" },
  featureTitleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: 4 },
  featureTitle: { fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold, color: colors.foreground },
  featureDesc: { fontSize: fontSize.caption, color: colors.mutedForeground },

  cardKicker: {
    fontSize: fontSize.badge,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
    letterSpacing: 0.7,
  },
  stageBanner: { borderRadius: radius.md, paddingHorizontal: spacing.lg, paddingVertical: 14 },
  stageTitle: { fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold },
  stageDesc: { fontSize: fontSize.caption, lineHeight: 20, marginTop: 4, opacity: 0.8 },

  stepper: { flexDirection: "row", alignItems: "flex-start" },
  stepNodeColumn: { alignItems: "center", gap: 6 },
  stepNode: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 3,
    alignItems: "center",
    justifyContent: "center",
  },
  stepNodeDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.white },
  stepLabel: { fontSize: 10 },
  stepLine: { flex: 1, height: 2, borderRadius: 1, marginHorizontal: 4, marginTop: 12 },

  cardFootnote: { fontSize: fontSize.micro, color: colors.mutedForeground },
});
