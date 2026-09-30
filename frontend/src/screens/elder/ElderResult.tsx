import React from "react";
import { View, StyleSheet, ScrollView, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";

import { ElderNav, ElderStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { cistAi, reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Button, ErrorState, SentenceText as Text, SpeechBubble } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import { DEFAULT_CHARACTER_NAME, DEFAULT_MEMOI } from "@/components/memoiCharacters";
import { withParticle } from "@/utils/format";

/**
 * What the elder sees after a session — and deliberately not a score.
 *
 * `GET /screenings/{session_id}/result` answers with `audience=elder` for an
 * elder token, and that response is annotated `@JsonInclude(NON_NULL)` with the
 * score fields left unset: `screening_reference_score`, `display_score`,
 * `risk_level` and `domain_scores` are simply not in the payload. Only
 * `result_type`, `display_label`, `message` and `recommendation` are read here.
 *
 * Analysis is asynchronous, so a `pending` / `processing` result is re-fetched
 * rather than rendered as an empty card.
 */
const POLL_INTERVAL_MS = 5000;

const RISK_MESSAGES = {
  stable: "이번 결과는 안정적으로 확인됐어요. 지금처럼 꾸준히 이어가 주세요.",
  monitoring_needed: "앞으로의 변화를 꾸준히 관찰해 볼게요. 편안하게 활동을 이어가 주세요.",
  review_needed: "조금 더 확인해 보면 좋겠어요. 보호자와 함께 전문기관 상담을 고려해 주세요.",
} as const;

function todayLabel() {
  const now = new Date();
  return `${now.getFullYear()}년 ${now.getMonth() + 1}월 ${now.getDate()}일`;
}

function formatKoreanDate(date: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  return match
    ? `${match[1]}년 ${Number(match[2])}월 ${Number(match[3])}일`
    : date;
}

export default function ElderResultScreen() {
  const navigation = useNavigation<ElderNav>();
  const route = useRoute<RouteProp<ElderStackParamList, "ElderResult">>();
  const sessionId = route.params?.sessionId ?? null;
  const mode = route.params?.mode ?? "daily";
  const { userName, characterName, completeBaseline } = useApp();
  const companionName = characterName?.trim() || DEFAULT_CHARACTER_NAME;
  const baseline = mode === "baseline";
  const [aiPolling, setAiPolling] = React.useState(baseline);

  const { data: result, error, reload } = useApi(
    () => reports.screeningResult(sessionId as string, "elder"),
    [sessionId],
    {
      // 정서 문답의 검사 결과는 이 화면에서 분석하지 않는다. 일기는 세션 종료 후 생성된다.
      // `result_status`는 계속 `pending`이라, 폴링하면 끝나지 않는 대기가 된다.
      enabled: !!sessionId && !baseline,
    },
  );

  const {
    data: aiAnalysis,
    error: aiError,
    reload: reloadAi,
  } = useApi(
    () => cistAi.getAnalysis(sessionId as string),
    [sessionId],
    {
      enabled: !!sessionId && baseline,
      intervalMs: baseline && aiPolling ? POLL_INTERVAL_MS : undefined,
    },
  );
  const {
    data: retestSchedule,
    error: scheduleError,
    loading: scheduleLoading,
    reload: reloadSchedule,
  } = useApi(
    () => cistAi.getRetestSchedule(),
    [sessionId],
    { enabled: !!sessionId && baseline && aiAnalysis?.status === "completed" },
  );
  const [retrying, setRetrying] = React.useState(false);
  const [retryError, setRetryError] = React.useState<string | null>(null);

  // 초기 검사는 분석이 끝나야 결과가 나오지만, 정서 문답은 응답을 받은 시점이
  // 곧 마무리다.
  const resultSettled = baseline
    ? aiAnalysis?.status === "completed" || aiAnalysis?.status === "failed"
    : !!result;

  React.useEffect(() => {
    if (!baseline || !aiAnalysis) return;
    setAiPolling(["pending", "processing"].includes(aiAnalysis.status));
  }, [aiAnalysis, baseline]);

  React.useEffect(() => {
    if (baseline && resultSettled) void completeBaseline();
  }, [baseline, completeBaseline, resultSettled]);

  const replacementQuestionCodes = (aiAnalysis?.retry_items ?? [])
    .filter((item) => item.required_action === "REPLACE_RESPONSE")
    .map((item) => item.question_code);

  const retryAnalysis = async () => {
    if (!sessionId || retrying) return;
    setRetrying(true);
    setRetryError(null);
    try {
      await cistAi.retryAnalysis(sessionId);
      reloadAi();
    } catch (cause) {
      setRetryError(apiErrorMessage(cause));
    } finally {
      setRetrying(false);
    }
  };

  const requestAnalysis = async () => {
    if (!sessionId || retrying) return;
    setRetrying(true);
    setRetryError(null);
    try {
      await cistAi.createAnalysis(sessionId);
      setAiPolling(true);
      reloadAi();
    } catch (cause) {
      setRetryError(apiErrorMessage(cause));
    } finally {
      setRetrying(false);
    }
  };

  const message =
    baseline && aiAnalysis?.status === "failed"
      ? "결과를 준비하지 못했어요. 잠시 후 다시 시도해 주세요."
      : baseline && aiAnalysis?.status === "completed" && aiAnalysis.risk_level
        ? RISK_MESSAGES[aiAnalysis.risk_level]
        : result?.result_status === "failed"
      ? "결과를 준비하지 못했어요. 잠시 후 다시 대화해 주세요."
      : result?.result_type === "insufficient_data"
        ? "오늘은 답변이 충분히 담기지 않았어요. 내일 다시 이야기해요."
        : result?.message ?? "";

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <View style={styles.header}>
        <Text style={styles.date}>{todayLabel()}</Text>
        <Text style={styles.title}>{mode === "baseline" ? "초기 설정이 완료됐어요" : "대화 완료"}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
        <View style={styles.characterBlock}>
          <Memoi3D
            character={DEFAULT_MEMOI}
            height={160}
            spinnerColor={colors.primary}
            style={{ width: 200 }}
          />

          {!sessionId ? (
            <SpeechBubble text="오늘 대화가 잘 마무리됐어요." side="below" />
          ) : (baseline ? aiError : error) && !(baseline ? aiAnalysis : result) ? (
            <View style={styles.stateWrap}>
              <ErrorState
                message={apiErrorMessage(baseline ? aiError : error)}
                onRetry={baseline ? () => void requestAnalysis() : reload}
              />
              {baseline && retryError ? <Text style={styles.retryError}>{retryError}</Text> : null}
            </View>
          ) : baseline && aiAnalysis?.status === "needs_retry" ? (
            <View style={styles.stateWrap}>
              <SpeechBubble
                text={replacementQuestionCodes.length > 0
                  ? "또렷하게 확인하기 위해 일부 답변을 한 번 더 들려주세요."
                  : "음성 주소를 새로 준비해 다시 분석할게요."}
                side="below"
              />
            </View>
          ) : (
              <SpeechBubble
                text={baseline
                  ? message || `이제 ${withParticle(companionName, "과", "와")} 매일 편하게 이야기할 수 있어요.`
                  : result?.result_status === "completed"
                    ? `${userName ? `${userName}님, ` : ""}${message}`
                    : `${userName ? `${userName}님, ` : ""}오늘 대화가 잘 마무리됐어요.`}
                side="below"
              />
          )}
        </View>

        {baseline && aiAnalysis?.status === "completed" ? (
          <View style={styles.scheduleCard}>
            <View style={styles.scheduleHeading}>
              <View style={styles.scheduleIcon}>
                <Ionicons name="calendar-outline" size={21} color={colors.primaryDark} />
              </View>
              <View style={styles.scheduleHeadingText}>
                <Text style={styles.scheduleLabel}>다음 검사 예정일</Text>
                {retestSchedule?.next_due_date ? (
                  <Text style={styles.scheduleDate}>
                    {formatKoreanDate(retestSchedule.next_due_date)}
                  </Text>
                ) : (
                  <Text style={styles.schedulePending}>
                    {scheduleLoading ? "예정일을 확인하고 있어요" : "예정일을 확인하지 못했어요"}
                  </Text>
                )}
              </View>
            </View>
            {scheduleError || (!scheduleLoading && !retestSchedule?.next_due_date) ? (
              <Pressable
                accessibilityRole="button"
                onPress={reloadSchedule}
                style={styles.scheduleRetry}
              >
                <Text style={styles.scheduleRetryText}>다시 조회하기</Text>
              </Pressable>
            ) : null}
            <View style={styles.scheduleDivider} />
            <Text style={styles.scheduleDescription}>
              CIST 인지 검사는 3개월마다 진행돼요. 다음 검사 시기가 되면 홈 화면에서 알려드릴게요.
            </Text>
          </View>
        ) : null}

        {!baseline && result?.result_status === "completed" && result.recommendation ? (
          <View style={styles.note}>
            <Text style={styles.noteText}>{result.recommendation}</Text>
          </View>
        ) : null}

        <View style={styles.note}>
          <Text style={styles.noteText}>
            {mode === "baseline"
              ? "이 검사는 진단이 아니라 앞으로의 변화를 비교하기 위한 기준이에요."
              : "오늘 대화를 마치면 일기를 바로 준비해요.\n일기 탭에서 확인하실 수 있어요."}
          </Text>
        </View>

        {baseline && (
          aiAnalysis?.status === "needs_retry" ||
          (aiAnalysis?.status === "failed" &&
            aiAnalysis.reason_code === "INTERNAL_ERROR" &&
            aiAnalysis.retry_count < 2)
        ) ? (
          <View style={styles.retryAction}>
            {retryError ? <Text style={styles.retryError}>{retryError}</Text> : null}
            <Button
              label={aiAnalysis?.status === "needs_retry" && replacementQuestionCodes.length > 0
                ? "필요한 답변 다시 녹음" : "분석 다시 요청"}
              disabled={retrying}
              onPress={() => {
                if (aiAnalysis?.status === "needs_retry" && replacementQuestionCodes.length > 0) {
                  navigation.replace("ElderCist", { sessionId: sessionId as string, retryQuestionCodes: replacementQuestionCodes });
                } else {
                  void retryAnalysis();
                }
              }}
            />
          </View>
        ) : null}
      </ScrollView>

      <View style={styles.footer}>
        {!baseline ? (
          <Button
            label="일기 확인하기"
            onPress={() => navigation.navigate("ElderTabs", { screen: "ElderCalendar" })}
          />
        ) : null}
        <Button
          label={mode === "baseline" ? `${companionName} 시작하기` : "홈으로 돌아가기"}
          onPress={() => navigation.navigate("ElderTabs", { screen: "ElderHome" })}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: {
    backgroundColor: colors.primary,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.xxl - 4,
    paddingBottom: spacing.xl,
  },
  date: { fontSize: fontSize.caption, color: "rgba(255,255,255,0.6)", marginBottom: 2 },
  title: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.white },

  body: { paddingBottom: spacing.xl },
  characterBlock: {
    alignItems: "center",
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.xxl + 4,
    paddingBottom: spacing.xxl,
  },
  stateWrap: { alignSelf: "stretch" },
  note: {
    marginHorizontal: spacing.xl,
    marginBottom: spacing.md,
    backgroundColor: colors.secondary,
    borderRadius: radius.xl,
    padding: spacing.lg,
  },
  noteText: { fontSize: fontSize.body, color: colors.primaryDark, lineHeight: 24 },

  scheduleCard: {
    marginHorizontal: spacing.xl,
    marginBottom: spacing.md,
    backgroundColor: colors.secondary,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: radius.xl,
    padding: spacing.lg,
  },
  scheduleHeading: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  scheduleIcon: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: "rgba(90,143,104,0.12)",
    alignItems: "center",
    justifyContent: "center",
  },
  scheduleHeadingText: { flex: 1 },
  scheduleLabel: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.primaryDark },
  scheduleDate: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.primary, marginTop: spacing.xs },
  schedulePending: { fontSize: fontSize.body, color: colors.mutedForeground, marginTop: spacing.xs },
  scheduleRetry: { alignSelf: "flex-start", marginLeft: 40 + spacing.md, marginTop: spacing.sm },
  scheduleRetryText: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.primaryDark },
  scheduleDivider: { borderTopWidth: 1, borderTopColor: colors.border, marginTop: spacing.lg, marginBottom: spacing.md },
  scheduleDescription: { fontSize: fontSize.body, lineHeight: 23, color: colors.primaryDark },

  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing.xl },
  retryAction: { paddingHorizontal: spacing.xl, gap: spacing.sm },
  retryError: { color: colors.destructive, textAlign: "center" },
});
