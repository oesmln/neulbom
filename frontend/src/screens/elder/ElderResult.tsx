import React from "react";
import { View, StyleSheet, ScrollView } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";

import { ElderNav, ElderStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { reports } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Button, ErrorState, LoadingState, SentenceText as Text, SpeechBubble } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import { DEFAULT_MEMOI } from "@/components/memoiCharacters";

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

function todayLabel() {
  const now = new Date();
  return `${now.getFullYear()}년 ${now.getMonth() + 1}월 ${now.getDate()}일`;
}

export default function ElderResultScreen() {
  const navigation = useNavigation<ElderNav>();
  const route = useRoute<RouteProp<ElderStackParamList, "ElderResult">>();
  const sessionId = route.params?.sessionId ?? null;
  const mode = route.params?.mode ?? "daily";
  const { userName, characterName, completeBaseline } = useApp();
  const companionName = characterName?.trim() || "메모이";

  // Polling stops the moment the pipeline settles: `pending` feeds `intervalMs`,
  // and dropping it clears the interval inside the hook.
  const [pending, setPending] = React.useState(true);

  const { data: result, error, loading, reload } = useApi(
    () => reports.screeningResult(sessionId as string, "elder"),
    [sessionId],
    { enabled: !!sessionId, intervalMs: pending ? POLL_INTERVAL_MS : undefined },
  );

  const resultSettled =
    result?.result_status === "completed" || result?.result_status === "failed";

  React.useEffect(() => {
    if (result) setPending(!resultSettled);
  }, [result, resultSettled]);

  React.useEffect(() => {
    if (mode === "baseline" && resultSettled) void completeBaseline();
  }, [completeBaseline, mode, resultSettled]);

  const message =
    result?.result_status === "failed"
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
          ) : error && !result ? (
            <View style={styles.stateWrap}>
              <ErrorState message={apiErrorMessage(error)} onRetry={reload} />
            </View>
          ) : !result ? (
            <View style={styles.stateWrap}>
              <LoadingState label="결과를 준비하고 있어요" />
            </View>
          ) : !resultSettled ? (
            <View style={styles.stateWrap}>
              <LoadingState label="대화를 살펴보고 있어요. 잠시만 기다려 주세요" />
            </View>
          ) : (
              <SpeechBubble
                text={mode === "baseline"
                  ? `이제 ${companionName}와 매일 편하게 이야기할 수 있어요. 함께 천천히 시작해 볼까요?`
                  : `${userName ? `${userName}님, ` : ""}${message}`}
                side="below"
              />
          )}
        </View>

        {result?.result_status === "completed" && result.recommendation ? (
          <View style={styles.note}>
            <Text style={styles.noteText}>{result.recommendation}</Text>
          </View>
        ) : null}

        <View style={styles.note}>
          <Text style={styles.noteText}>
            {mode === "baseline"
              ? "이 검사는 진단이 아니라 앞으로의 변화를 비교하기 위한 기준이에요."
              : "오늘 대화는 내일 0시에 일기로 생성돼요.\n내일 일기 탭에서 확인하실 수 있어요."}
          </Text>
        </View>
      </ScrollView>

      <View style={styles.footer}>
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

  footer: { paddingHorizontal: spacing.xl, paddingTop: spacing.md, paddingBottom: spacing.xl },
});
