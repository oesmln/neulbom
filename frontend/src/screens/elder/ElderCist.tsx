import React from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { newClientId, sessions } from "@/api";
import { useApi } from "@/hooks/useApi";
import { useAnswerRecording } from "@/hooks/useAnswerRecording";
import { apiErrorMessage } from "@/api/errors";
import type { QuestionResponse, Uuid } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Badge, Button, ErrorState, LoadingState, ProgressBar } from "@/components/ui";

/**
 * CIST initial screening.
 *
 * The flow follows the backend one step at a time:
 *   `POST /sessions` (`session_type=cist`) → `GET /questions/daily`
 *   → `POST /sessions/{id}/answers` per question → `PATCH /sessions/{id}/end`
 * and then the result screen reads `GET /screenings/{session_id}/result`.
 *
 * Spoken answers are recorded with expo-audio, uploaded through
 * `POST /recordings`, and saved with the returned `recording_id`.
 */
const WAVE_BARS = 28;

/**
 * The domain chip above each question.
 *
 * `QuestionResponse` carries `type` (voice / listen) but no cognitive domain, so
 * there is nothing on the wire to render here — this is the Figma ordering of
 * the CIST domains, kept as presentation only. When the API grows a domain
 * field this table goes away and the label comes from the question.
 */
const CIST_DOMAINS = [
  "날짜 지남력",
  "장소 지남력",
  "단어 등록",
  "주의 집중",
  "단어 회상",
];

function domainOf(question: QuestionResponse, index: number): string {
  return CIST_DOMAINS[index] ?? `문항 ${question.order}`;
}

export default function ElderCistScreen() {
  const navigation = useNavigation<ElderNav>();
  const { userId } = useApp();

  const [index, setIndex] = React.useState(0);
  const [listened, setListened] = React.useState(false);
  const [answered, setAnswered] = React.useState(false);
  const [recordingId, setRecordingId] = React.useState<Uuid | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [askedAt, setAskedAt] = React.useState(() => Date.now());
  const answerClientIds = React.useRef<Record<string, Uuid>>({});

  const session = useApi(
    () => sessions.start({ user_id: userId as string, session_type: "cist" }),
    [userId],
    { enabled: !!userId },
  );

  const questions = useApi(
    () => sessions.dailyQuestions(userId as string, "cist"),
    [userId],
    { enabled: !!userId },
  );

  const list = questions.data?.questions ?? [];
  const question = list[index];
  const isLast = index === list.length - 1;
  const isListenQuestion = question?.type === "listen";
  const canAdvance = isListenQuestion ? listened : answered;
  const canGoPrevious = index > 0 || navigation.canGoBack();

  React.useEffect(() => {
    setAskedAt(Date.now());
    setRecordingId(null);
  }, [index]);

  const goBack = () => {
    if (index > 0) {
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
    const sessionId = session.data.session_id;

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

      if (isLast) {
        await sessions.end(sessionId);
        navigation.replace("ElderResult", { sessionId });
        return;
      }
      setListened(false);
      setAnswered(false);
      setIndex(index + 1);
    } catch {
      // Saving failed; leave the question answered so the user can retry the
      // same tap rather than losing their place.
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
          <Text style={styles.headerTitle}>CIST 초기 검사</Text>
          <Text style={styles.headerCount}>
            {list.length > 0 ? `${index + 1} / ${list.length}` : ""}
          </Text>
        </View>
        <ProgressBar
          value={list.length > 0 ? ((index + 1) / list.length) * 100 : 0}
          color={colors.white}
          track="rgba(255,255,255,0.25)"
        />
      </View>

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
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

        {question ? (
          <>
            <Badge label={domainOf(question, index)} />

            <View style={{ gap: spacing.md }}>
              <Text style={styles.prompt}>{question.content}</Text>
              {question.hint ? <Text style={styles.hint}>{question.hint}</Text> : null}
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
                userId={userId}
                sessionId={session.data?.session_id ?? null}
                questionId={question.question_id}
                answered={answered}
                onAnswer={(id) => {
                  setRecordingId(id);
                  setAnswered(true);
                }}
              />
            )}

            <View style={{ marginTop: "auto", paddingTop: spacing.sm }}>
              <Button
                label={isLast ? "검사 완료" : "다음 문항"}
                disabled={!canAdvance || submitting || !session.data}
                onPress={() => void advance()}
              />
            </View>
          </>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function MicRecorder({
  userId,
  sessionId,
  questionId,
  answered,
  onAnswer,
}: {
  userId: Uuid | null;
  sessionId: Uuid | null;
  questionId: Uuid;
  answered: boolean;
  onAnswer: (recordingId: Uuid) => void;
}) {
  const recording = useAnswerRecording({ userId, sessionId, questionId }, onAnswer);
  const elapsed = Math.floor(recording.durationMillis / 1000);

  const tap = async () => {
    if (answered || recording.uploading) return;
    await recording.toggle();
  };

  const buttonColor = answered
    ? colors.success
    : recording.isRecording
      ? colors.destructive
      : colors.primary;
  const status = answered
    ? "답변 완료"
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
        disabled={recording.uploading || !userId || !sessionId}
        accessibilityRole="button"
        accessibilityLabel={status}
        style={({ pressed }) => [
          styles.micButton,
          {
            backgroundColor: buttonColor,
            opacity: pressed || recording.uploading || !userId || !sessionId ? 0.7 : 1,
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
  prompt: { fontSize: fontSize.title, fontWeight: fontWeight.bold, lineHeight: 31, color: colors.foreground },
  hint: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 22 },

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
  recordingError: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },
});
