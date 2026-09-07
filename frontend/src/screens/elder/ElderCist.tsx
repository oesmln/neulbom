import React from "react";
import { View, Pressable, StyleSheet, ScrollView } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav, ElderStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { cistAi, newClientId, sessions } from "@/api";
import { useApi } from "@/hooks/useApi";
import { useAnswerRecording } from "@/hooks/useAnswerRecording";
import { useSpeechPlayback } from "@/hooks/useSpeechPlayback";
import { apiErrorMessage } from "@/api/errors";
import type { CistRecognitionPlanResponse, QuestionResponse, Uuid } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Badge, Button, ErrorState, LoadingState, ProgressBar, SentenceText as Text } from "@/components/ui";
import VoicePlaybackButton from "@/components/VoicePlaybackButton";

/**
 * CIST initial screening.
 *
 * The flow follows the backend one step at a time:
 *   `POST /sessions` (`session_type=baseline`) → `GET /questions/daily`
 *   → Q11 저장 → `POST /sessions/{id}/cist-ai/recognition-plan`
 *   → 선택된 Q12~Q16만 시행 → 세션 종료 → AI 분석 생성.
 *
 * Spoken answers are recorded with expo-audio, uploaded through
 * `POST /recordings`, and saved with the returned `recording_id`.
 */
const WAVE_BARS = 28;

/**
 * The domain chip above each question.
 *
 * The backend question `type` is the CIST domain used for the compact chip.
 */
function domainOf(question: QuestionResponse): string {
  switch (question.type) {
    case "orientation": return "지남력";
    case "memory": return "기억력";
    case "attention": return "주의 집중";
    case "language": return "언어 능력";
    default: return `문항 ${question.order}`;
  }
}

type CompletedTurn = {
  questionId: Uuid;
  domain: string;
  question: string;
  answer: string;
};

function answerTextForTurn(isListenQuestion: boolean, transcript: string | null): string {
  const cleaned = transcript?.trim();
  return cleaned || (isListenQuestion ? "질문을 들었어요." : "음성 답변을 완료했어요.");
}

export default function ElderCistScreen() {
  const navigation = useNavigation<ElderNav>();
  const route = useRoute<RouteProp<ElderStackParamList, "ElderCist">>();
  const { userId } = useApp();
  const retrySessionId = route.params?.sessionId ?? null;
  const retryQuestionCodes = route.params?.retryQuestionCodes ?? [];
  const retryMode = !!retrySessionId;

  const [index, setIndex] = React.useState(0);
  const [listened, setListened] = React.useState(false);
  const [answered, setAnswered] = React.useState(false);
  const [recordingId, setRecordingId] = React.useState<Uuid | null>(null);
  const [currentTranscript, setCurrentTranscript] = React.useState<string | null>(null);
  const [completedTurns, setCompletedTurns] = React.useState<CompletedTurn[]>([]);
  const [submitting, setSubmitting] = React.useState(false);
  const [submissionError, setSubmissionError] = React.useState<string | null>(null);
  const [askedAt, setAskedAt] = React.useState(() => Date.now());
  const [recognitionPlan, setRecognitionPlan] = React.useState<CistRecognitionPlanResponse | null>(null);
  const [recordingAttempt, setRecordingAttempt] = React.useState(0);
  const answerClientIds = React.useRef<Record<string, Uuid>>({});
  const scrollRef = React.useRef<ScrollView>(null);

  const session = useApi(
    () => retrySessionId
      ? sessions.get(retrySessionId)
      : sessions.start({ user_id: userId as string, session_type: "baseline" }),
    [userId, retrySessionId],
    { enabled: !!userId },
  );

  const questions = useApi(
    () => sessions.dailyQuestions(userId as string, "baseline"),
    [userId],
    { enabled: !!userId },
  );

  const list = React.useMemo(() => {
    const all = questions.data?.questions ?? [];
    if (retryMode) {
      const requested = new Set(retryQuestionCodes);
      return all.filter((item) => item.question_code && requested.has(item.question_code));
    }
    if (!recognitionPlan) {
      return all.filter((item) => item.order <= 11);
    }
    const selected = new Set(recognitionPlan.next_question_codes ?? []);
    return all.filter((item) => item.administration_mode !== "conditional"
      || (item.question_code != null && selected.has(item.question_code)));
  }, [questions.data?.questions, recognitionPlan, retryMode, retryQuestionCodes]);
  const question = list[index];
  const isLast = index === list.length - 1;
  const isQ11PlanningStep = !retryMode
    && recognitionPlan == null
    && question?.question_code === "memory_delayed_free_recall";
  const displayTotal = retryMode || recognitionPlan
    ? list.length
    : questions.data?.questions.length ?? list.length;
  const isListenQuestion = question?.type === "listen";
  const canAdvance = isListenQuestion ? listened : answered;
  const recognitionPlanBoundary = !retryMode && recognitionPlan != null && index === 11;
  const canGoPrevious = !recognitionPlanBoundary && (index > 0 || navigation.canGoBack());
  const voice = useSpeechPlayback(answered ? null : question?.content ?? null);

  React.useEffect(() => {
    setAskedAt(Date.now());
    setRecordingId(null);
    setCurrentTranscript(null);
    setSubmissionError(null);
  }, [index]);

  React.useEffect(() => {
    if (!question) return;
    const timer = setTimeout(() => {
      scrollRef.current?.scrollToEnd({ animated: true });
    }, 0);
    return () => clearTimeout(timer);
  }, [completedTurns.length, question?.question_id]);

  const goBack = () => {
    if (recognitionPlanBoundary) return;
    if (index > 0) {
      const previousQuestion = list[index - 1];
      if (previousQuestion) delete answerClientIds.current[previousQuestion.question_id];
      setCompletedTurns((current) => current.slice(0, Math.max(0, index - 1)));
      setIndex(index - 1);
      setListened(false);
      setAnswered(false);
    } else {
      navigation.goBack();
    }
  };

  const advance = async () => {
    if (!question || !session.data || submitting) return;
    setSubmitting(true);
    setSubmissionError(null);
    const sessionId = session.data.session_id;
    const completedTurn: CompletedTurn = {
      questionId: question.question_id,
      domain: domainOf(question),
      question: question.content,
      answer: answerTextForTurn(isListenQuestion, currentTranscript),
    };

    try {
      await sessions.saveAnswer(sessionId, {
        client_answer_id:
          answerClientIds.current[question.question_id] ??=
            newClientId(),
        question_id: question.question_id,
        answer_text: isListenQuestion ? "listened" : undefined,
        recording_id: recordingId ?? undefined,
        response_time_ms: Date.now() - askedAt,
        answered_at: new Date().toISOString(),
      });

      if (!retryMode && question.question_code === "memory_delayed_free_recall") {
        const plan = await cistAi.createRecognitionPlan(sessionId);
        if (plan.status === "needs_retry") {
          delete answerClientIds.current[question.question_id];
          setAnswered(false);
          setListened(false);
          setRecordingId(null);
          setCurrentTranscript(null);
          setAskedAt(Date.now());
          setRecordingAttempt((attempt) => attempt + 1);
          setSubmissionError("음성이 또렷하게 들리지 않았어요. 이 문항만 다시 말씀해 주세요.");
          return;
        }
        setRecognitionPlan(plan);
        setCompletedTurns((current) => [...current, completedTurn]);
        setListened(false);
        setAnswered(false);
        setIndex(index + 1);
        return;
      }

      if (isLast) {
        if (retryMode) {
          await cistAi.retryAnalysis(sessionId);
        } else {
          await sessions.end(sessionId);
          try {
            await cistAi.createAnalysis(sessionId);
          } catch {
            // The ended session is recoverable. ElderResult offers the same
            // idempotent create call again instead of trapping the user here.
          }
        }
        navigation.replace("ElderResult", { sessionId, mode: "baseline" });
        return;
      }
      setCompletedTurns((current) => [...current, completedTurn]);
      setListened(false);
      setAnswered(false);
      setIndex(index + 1);
    } catch (cause) {
      // Saving failed; leave the question answered so the user can retry the
      // same tap rather than losing their place.
      setSubmissionError(apiErrorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  const loading = session.loading || questions.loading;
  const error = session.error ?? questions.error;

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <View style={styles.header}>
        <View style={styles.headerRow}>
          <Pressable
            onPress={goBack}
            disabled={!canGoPrevious}
            accessibilityRole="button"
            accessibilityLabel="이전 문항"
            accessibilityState={{ disabled: !canGoPrevious }}
            hitSlop={10}
            style={[styles.backButton, !canGoPrevious && { opacity: 0.35 }]}
          >
            <Ionicons name="chevron-back" size={18} color={colors.white} />
          </Pressable>
          <Text style={styles.headerTitle}>{retryMode ? "음성 답변 다시 확인" : "초기 인지 활동 확인"}</Text>
          <Text style={styles.headerCount}>
            {list.length > 0 ? `${index + 1} / ${displayTotal}` : ""}
          </Text>
        </View>
        <ProgressBar
          value={displayTotal > 0 ? ((index + 1) / displayTotal) * 100 : 0}
          color={colors.white}
          track="rgba(255,255,255,0.25)"
        />
      </View>

      <ScrollView
        ref={scrollRef}
        contentContainerStyle={styles.body}
        showsVerticalScrollIndicator={false}
      >
        {loading && !question ? <LoadingState label="문항을 불러오는 중이에요" /> : null}

        {error && !question ? (
          <ErrorState
            message={apiErrorMessage(error)}
            onRetry={() => {
              session.reload();
              questions.reload();
            }}
          />
        ) : null}

        {completedTurns.map((turn) => (
          <CompletedTurnCard key={turn.questionId} turn={turn} />
        ))}

        {question ? (
          <View style={styles.currentTurn}>
            {completedTurns.length > 0 ? <Text style={styles.currentLabel}>다음 질문</Text> : null}
            <Badge label={domainOf(question)} />

            <View style={{ gap: spacing.md }}>
              <Text style={styles.prompt}>{question.content}</Text>
              {question.hint ? <Text style={styles.hint}>{question.hint}</Text> : null}
              <VoicePlaybackButton
                enabled={voice.enabled}
                loading={voice.loading}
                speaking={voice.speaking}
                onPress={voice.toggle}
                onReplay={voice.replay}
              />
              {voice.error ? <Text style={styles.voiceError}>{voice.error}</Text> : null}
            </View>

            {isListenQuestion ? (
              <Button
                label={listened ? "들었어요" : "들려드릴게요"}
                onPress={() => {
                  setListened(true);
                  setAnswered(true);
                }}
                style={listened ? { backgroundColor: colors.success } : undefined}
              />
            ) : (
              <MicRecorder
                key={`${question.question_id}:${recordingAttempt}`}
                userId={userId}
                sessionId={session.data?.session_id ?? null}
                questionId={question.question_id}
                answered={answered}
                disabled={voice.loading || voice.speaking}
                onAnswer={(id) => {
                  setRecordingId(id);
                  setAnswered(true);
                }}
                onTranscript={setCurrentTranscript}
              />
            )}

            <View style={{ marginTop: "auto", paddingTop: spacing.sm }}>
              {submissionError ? (
                <Text style={styles.submissionError}>{submissionError}</Text>
              ) : null}
              <Button
                label={isQ11PlanningStep
                  ? "다음 문항"
                  : isLast
                    ? (retryMode ? "재분석 요청" : "검사 완료")
                    : "다음 문항"}
                disabled={!canAdvance || submitting || !session.data}
                onPress={() => void advance()}
              />
            </View>
          </View>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function CompletedTurnCard({ turn }: { turn: CompletedTurn }) {
  return (
    <View style={styles.completedTurn}>
      <View style={styles.completedTurnHeader}>
        <Badge label={turn.domain} />
        <Text style={styles.completedTurnStatus}>답변 완료</Text>
      </View>
      <View style={styles.questionBubble}>
        <Text style={styles.bubbleLabel}>질문</Text>
        <Text style={styles.historyQuestion}>{turn.question}</Text>
      </View>
      <View style={styles.answerBubble}>
        <Text style={styles.bubbleLabel}>내 답변</Text>
        <Text style={styles.historyAnswer}>{turn.answer}</Text>
      </View>
    </View>
  );
}

function MicRecorder({
  userId,
  sessionId,
  questionId,
  answered,
  disabled,
  onAnswer,
  onTranscript,
}: {
  userId: Uuid | null;
  sessionId: Uuid | null;
  questionId: Uuid;
  answered: boolean;
  disabled: boolean;
  onAnswer: (recordingId: Uuid) => void;
  onTranscript: (transcript: string) => void;
}) {
  const [transcript, setTranscript] = React.useState<string | null>(null);
  const recording = useAnswerRecording(
    { userId, sessionId, questionId },
    onAnswer,
    (text) => {
      setTranscript(text);
      onTranscript(text);
    },
  );
  const elapsed = Math.floor(recording.durationMillis / 1000);

  React.useEffect(() => {
    setTranscript(null);
  }, [questionId]);

  const tap = async () => {
    if (disabled || answered || recording.uploading) return;
    await recording.toggle();
  };

  const buttonColor = answered
    ? colors.success
    : recording.isRecording
      ? colors.destructive
      : colors.primary;
  const status = answered
    ? "답변 완료"
    : disabled
      ? "질문을 들은 뒤 답변해 주세요"
    : recording.syncStatus === "pending"
      ? "기기에 저장됨 · 연결되면 자동 전송"
      : recording.syncStatus === "failed"
        ? "전송 대기 중 · 눌러서 다시 시도"
        : recording.uploading
      ? "녹음을 저장하고 있어요"
      : recording.isRecording
      ? "탭하면 녹음 완료"
      : "버튼을 눌러 말씀해 주세요";

  return (
    <View style={styles.recorder}>
      <View style={styles.wave}>
        {Array.from({ length: WAVE_BARS }).map((_, i) => (
          <View
            key={i}
            style={{
              width: 4,
              borderRadius: 2,
              height: recording.isRecording
                ? Math.max(3, Math.sin(i * 0.7) * 14 + 14)
                : answered
                  ? 6 + (i % 4) * 4
                  : 3,
              backgroundColor: recording.isRecording
                ? colors.primary
                : answered
                  ? colors.success
                  : colors.muted,
            }}
          />
        ))}
      </View>

      <Pressable
        onPress={() => void tap()}
        disabled={disabled || recording.uploading || !userId || !sessionId}
        accessibilityRole="button"
        accessibilityLabel={status}
        style={({ pressed }) => [
          styles.micButton,
          {
            backgroundColor: buttonColor,
            opacity: pressed || disabled || recording.uploading || !userId || !sessionId ? 0.7 : 1,
          },
        ]}
      >
        {answered ? (
          <Ionicons name="checkmark-circle" size={36} color={colors.white} />
        ) : recording.isRecording ? (
          <>
            <Ionicons name="mic-off" size={28} color={colors.white} />
            <Text style={styles.timer}>
              {String(Math.floor(elapsed / 60)).padStart(2, "0")}:
              {String(elapsed % 60).padStart(2, "0")}
            </Text>
          </>
        ) : (
          <Ionicons name="mic" size={36} color={colors.white} />
        )}
      </Pressable>

      <Text style={styles.status}>{status}</Text>
      {transcript ? (
        <View style={styles.transcriptCard}>
          <Text style={styles.transcriptLabel}>음성 인식 결과</Text>
          <Text style={styles.transcriptText}>{transcript}</Text>
        </View>
      ) : null}
      {recording.error ? <Text style={styles.recordingError}>{recording.error}</Text> : null}
    </View>
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
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.lg,
  },
  backButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.2)",
  },
  headerTitle: { fontSize: fontSize.bodyLg - 1, fontWeight: fontWeight.semibold, color: colors.white },
  headerCount: { fontSize: fontSize.caption, color: "rgba(255,255,255,0.7)", minWidth: 32, textAlign: "right" },

  body: {
    flexGrow: 1,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.xl,
    gap: spacing.xl,
  },
  completedTurn: {
    gap: spacing.md,
    padding: spacing.lg,
    borderRadius: radius.xl,
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
  },
  completedTurnHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  completedTurnStatus: { fontSize: fontSize.caption, color: colors.success, fontWeight: fontWeight.semibold },
  currentTurn: { gap: spacing.xl },
  currentLabel: { fontSize: fontSize.caption, color: colors.primaryDark, fontWeight: fontWeight.semibold },
  questionBubble: {
    alignSelf: "flex-start",
    maxWidth: "92%",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderRadius: radius.lg,
    borderTopLeftRadius: 4,
    backgroundColor: colors.muted,
  },
  answerBubble: {
    alignSelf: "flex-end",
    maxWidth: "92%",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderRadius: radius.lg,
    borderTopRightRadius: 4,
    backgroundColor: colors.primary,
  },
  bubbleLabel: { fontSize: fontSize.badge, fontWeight: fontWeight.semibold, color: colors.mutedForeground, marginBottom: spacing.xs },
  historyQuestion: { fontSize: fontSize.body, lineHeight: 23, color: colors.foreground },
  historyAnswer: { fontSize: fontSize.body, lineHeight: 23, color: colors.white },
  prompt: { fontSize: fontSize.title, fontWeight: fontWeight.bold, lineHeight: 31, color: colors.foreground },
  hint: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 22 },
  voiceError: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },

  recorder: { alignItems: "center", gap: spacing.lg, paddingVertical: spacing.sm },
  wave: { flexDirection: "row", alignItems: "center", gap: 2, height: 32 },
  micButton: {
    width: 84,
    height: 84,
    borderRadius: 42,
    alignItems: "center",
    justifyContent: "center",
    gap: 2,
  },
  timer: { fontSize: fontSize.badge, color: colors.white },
  status: { fontSize: fontSize.caption, color: colors.mutedForeground },
  transcriptCard: {
    width: "100%",
    gap: spacing.xs,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderRadius: radius.lg,
    backgroundColor: colors.secondary,
    borderWidth: 1,
    borderColor: colors.border,
  },
  transcriptLabel: { fontSize: fontSize.badge, fontWeight: fontWeight.semibold, color: colors.primaryDark },
  transcriptText: { fontSize: fontSize.body, lineHeight: 23, color: colors.foreground },
  recordingError: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },
  submissionError: {
    marginBottom: spacing.md,
    fontSize: fontSize.caption,
    color: colors.destructive,
    textAlign: "center",
  },
});
