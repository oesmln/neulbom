import React from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { newClientId, sessions } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import type { QuestionResponse } from "@/api/types";
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
 * Answers are meant to be spoken: each one should carry the `recording_id` that
 * `POST /recordings` returns so STT and the AST/KcELECTRA pipeline can score it.
 * The recorder below still does not capture audio — it only advances the UI —
 * so `recording_id` is omitted and the answer is saved without one. Wiring
 * `expo-audio` plus the offline `client_recording_id` queue is the next step.
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
  const [submitting, setSubmitting] = React.useState(false);
  const [askedAt, setAskedAt] = React.useState(() => Date.now());

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

  React.useEffect(() => {
    setAskedAt(Date.now());
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
        client_answer_id: newClientId(),
        question_id: question.question_id,
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
            accessibilityRole="button"
            accessibilityLabel="이전 문항"
            hitSlop={10}
            style={styles.backButton}
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
              <MicRecorder answered={answered} onAnswer={() => setAnswered(true)} />
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

function MicRecorder({ answered, onAnswer }: { answered: boolean; onAnswer: () => void }) {
  const [recording, setRecording] = React.useState(false);
  const [elapsed, setElapsed] = React.useState(0);

  React.useEffect(() => {
    if (!recording) {
      setElapsed(0);
      return;
    }
    const timer = setInterval(() => setElapsed((e) => e + 1), 1000);
    return () => clearInterval(timer);
  }, [recording]);

  const tap = () => {
    if (answered) return;
    if (!recording) {
      setRecording(true);
    } else {
      setRecording(false);
      onAnswer();
    }
  };

  const buttonColor = answered ? colors.success : recording ? colors.destructive : colors.primary;
  const status = answered
    ? "답변 완료"
    : recording
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
              height: recording
                ? Math.max(3, Math.sin(i * 0.7) * 14 + 14)
                : answered
                  ? 6 + (i % 4) * 4
                  : 3,
              backgroundColor: recording ? colors.primary : answered ? colors.success : colors.muted,
            }}
          />
        ))}
      </View>

      <Pressable
        onPress={tap}
        accessibilityRole="button"
        accessibilityLabel={status}
        style={({ pressed }) => [
          styles.micButton,
          { backgroundColor: buttonColor, opacity: pressed ? 0.9 : 1 },
        ]}
      >
        {answered ? (
          <Ionicons name="checkmark-circle" size={36} color={colors.white} />
        ) : recording ? (
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
});
