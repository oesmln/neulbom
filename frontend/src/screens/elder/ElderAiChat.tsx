import React from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { newClientId, sessions } from "@/api";
import { useApi } from "@/hooks/useApi";
import { useAnswerRecording } from "@/hooks/useAnswerRecording";
import { apiErrorMessage } from "@/api/errors";
import { USE_MOCK_API } from "@/api/config";
import type { Uuid } from "@/api/types";
import { colors, spacing, fontSize, fontWeight } from "@/theme";
import { Button, ErrorState, LoadingState, ScreenHeader, SpeechBubble } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import { DEFAULT_MEMOI, DEFAULT_MOUTH_SET } from "@/components/memoiCharacters";

/**
 * AI emotional Q&A — the daily conversation, answered by voice.
 *
 * Runs as its own session (`POST /sessions` with `session_type=emotional_qa`),
 * takes its questions from `GET /questions/daily`, saves each answer through
 * `POST /sessions/{id}/answers`, and ends with `PATCH /sessions/{id}/end` before
 * handing the session id to the result screen.
 *
 * The character is pinned above the conversation and never unmounts: it is the
 * one asking the questions, so it mouths each one as it arrives. The speaking
 * window is still timed from text length, while answers are recorded, uploaded,
 * and attached to the saved answer through `recording_id`.
 */
const INTRO_LINE = "어르신, 오늘 하루 어떠셨어요? 편하게 이야기해 주세요.";

/**
 * Stands in for what STT would return, and only without a server — against a
 * real backend the app must not invent a transcript it never received.
 */
const SAMPLE_ANSWERS = [
  "오늘은 좀 피곤하긴 한데 괜찮아요.",
  "된장찌개 먹었는데 아들이 끓여줬어요. 맛있었어요.",
  "오후에 공원 산책을 했어요.",
  "손자랑 통화한 게 가장 기뻤어요.",
  "내일 병원에 가야 해서 조금 걱정이 돼요.",
];

/** Roughly how long the character would take to say a line, in ms. */
function spokenDuration(line: string) {
  return Math.min(6000, Math.max(1600, line.length * 130));
}

/**
 * Drives `speaking` for one line, restarting whenever the line changes.
 * Returns false while there is nothing to say.
 */
function useSpokenLine(line: string | null) {
  const [speaking, setSpeaking] = React.useState(false);

  React.useEffect(() => {
    if (!line) {
      setSpeaking(false);
      return;
    }
    setSpeaking(true);
    const timer = setTimeout(() => setSpeaking(false), spokenDuration(line));
    return () => clearTimeout(timer);
  }, [line]);

  return speaking;
}

export default function ElderAiChatScreen() {
  const navigation = useNavigation<ElderNav>();
  const { userId } = useApp();

  const [phase, setPhase] = React.useState<"intro" | "chat">("intro");
  const [index, setIndex] = React.useState(0);
  const [answers, setAnswers] = React.useState<string[]>([]);
  const [transcripts, setTranscripts] = React.useState<string[]>([]);
  const [recordingIds, setRecordingIds] = React.useState<Array<Uuid | null>>([]);
  const [submitting, setSubmitting] = React.useState(false);
  const [submissionError, setSubmissionError] = React.useState<string | null>(null);
  const [askedAt, setAskedAt] = React.useState(() => Date.now());
  const answerClientIds = React.useRef<Record<string, Uuid>>({});

  // The session only starts once the elder taps into the conversation, so an
  // opened-and-abandoned tab does not leave an empty session behind.
  const session = useApi(
    () => sessions.start({ user_id: userId as string, session_type: "emotional_qa" }),
    [userId],
    { enabled: !!userId && phase === "chat" },
  );

  const questions = useApi(
    () => sessions.dailyQuestions(userId as string, "emotional_qa"),
    [userId],
    { enabled: !!userId },
  );

  const list = questions.data?.questions ?? [];
  const question = list[index];
  const answered = USE_MOCK_API ? answers.length > index : Boolean(recordingIds[index]);
  const isLast = list.length > 0 && index === list.length - 1;

  // The character only mouths the question itself; once it has been answered it
  // goes back to resting until the next one arrives.
  const spokenLine = phase === "intro" ? INTRO_LINE : answered ? null : question?.content ?? null;
  const speaking = useSpokenLine(spokenLine);

  React.useEffect(() => {
    setAskedAt(Date.now());
  }, [index]);

  const restart = () => {
    setPhase("intro");
    setIndex(0);
    setAnswers([]);
    setTranscripts([]);
    setRecordingIds([]);
    setSubmissionError(null);
  };

  const recordAnswer = () => {
    setAnswers((prev) => [...prev, USE_MOCK_API ? SAMPLE_ANSWERS[index] ?? "" : ""]);
  };

  const advance = async () => {
    if (!question || !session.data || submitting) return;
    setSubmitting(true);
    setSubmissionError(null);
    const sessionId = session.data.session_id;

    try {
      await sessions.saveAnswer(sessionId, {
        client_answer_id:
          answerClientIds.current[question.question_id] ??=
            newClientId(),
        question_id: question.question_id,
        answer_text: USE_MOCK_API ? answers[index] || undefined : undefined,
        recording_id: recordingIds[index] ?? undefined,
        response_time_ms: Date.now() - askedAt,
        answered_at: new Date().toISOString(),
      });

      if (isLast) {
        await sessions.end(sessionId);
        restart();
        navigation.navigate("ElderResult", { sessionId });
        return;
      }
      setIndex(index + 1);
    } catch (cause) {
      // Keep the answer on screen so the same tap can be retried.
      setSubmissionError(apiErrorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  if (phase === "intro") {
    return (
      <SafeAreaView style={styles.safe} edges={["top"]}>
        <ScreenHeader title="AI 정서 문답" subtitle="오늘 있었던 이야기를 편하게 들려주세요." />

        <View style={styles.introCharacter}>
          <Memoi3D
            character={DEFAULT_MEMOI}
            mouthSet={DEFAULT_MOUTH_SET}
            speaking={speaking}
            height={180}
            spinnerColor={colors.primary}
            style={{ width: 220 }}
          />
          <SpeechBubble text={INTRO_LINE} side="below" />
        </View>

        <View style={styles.introFooter}>
          <Text style={styles.introGuide}>
            AI가 음성으로 질문을 드리면{"\n"}마이크 버튼을 눌러 답변해 주세요.
          </Text>
          <Button
            label="대화 시작하기"
            size="lg"
            disabled={questions.loading || list.length === 0}
            onPress={() => setPhase("chat")}
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScreenHeader
        title="AI 정서 문답"
        subtitle={list.length > 0 ? `${index + 1} / ${list.length}` : ""}
        onBack={restart}
      />

      {/* Outside the ScrollView on purpose — the character must not scroll away
          mid-conversation, and unmounting it would drop four loaded models. */}
      <View style={styles.stage}>
        <Memoi3D
          character={DEFAULT_MEMOI}
          mouthSet={DEFAULT_MOUTH_SET}
          speaking={speaking}
          height={130}
          spinnerColor={colors.primary}
          style={{ width: 170 }}
        />
        <Text style={styles.stageStatus}>{speaking ? "메모이가 말하고 있어요" : "메모이"}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.thread} showsVerticalScrollIndicator={false}>
        {session.error ? (
          <ErrorState message={apiErrorMessage(session.error)} onRetry={session.reload} />
        ) : !question ? (
          <LoadingState label="질문을 불러오는 중이에요" />
        ) : (
          <>
            <View style={styles.aiBubble}>
              <Text style={styles.aiText}>{question.content}</Text>
            </View>

            {answered ? (
              <View style={styles.myBubble}>
                <Text style={styles.transcriptLabel}>음성 인식 결과</Text>
                <Text style={styles.myText}>
                  {answers[index] || transcripts[index] || "음성 답변을 글자로 옮기고 있어요"}
                </Text>
              </View>
            ) : null}
          </>
        )}
      </ScrollView>

      <View style={styles.composer}>
        {submissionError ? (
          <Text style={styles.submissionError}>{submissionError}</Text>
        ) : null}
        {answered ? (
          <Button
            label={isLast ? "대화 마치기" : "다음 질문"}
            disabled={submitting || !session.data}
            onPress={() => void advance()}
          />
        ) : (
          USE_MOCK_API ? (
            <View style={styles.recorderRow}>
              <Text style={styles.recorderHint}>버튼을 눌러 샘플 답변을 입력해 주세요</Text>
              <Pressable
                onPress={recordAnswer}
                disabled={!question}
                accessibilityRole="button"
                accessibilityLabel="샘플 답변 입력하기"
                style={({ pressed }) => [styles.micButton, { opacity: pressed ? 0.9 : 1 }]}
              >
                <Ionicons name="mic" size={32} color={colors.white} />
              </Pressable>
            </View>
          ) : question ? (
            <ChatRecorder
              key={question.question_id}
              userId={userId}
              sessionId={session.data?.session_id ?? null}
              questionId={question.question_id}
              onAnswer={(id) =>
                setRecordingIds((current) => {
                  const next = [...current];
                  next[index] = id;
                  return next;
                })
              }
              onTranscript={(text) =>
                setTranscripts((current) => {
                  const next = [...current];
                  next[index] = text;
                  return next;
                })
              }
            />
          ) : null
        )}
      </View>
    </SafeAreaView>
  );
}

function ChatRecorder({
  userId,
  sessionId,
  questionId,
  onAnswer,
  onTranscript,
}: {
  userId: Uuid | null;
  sessionId: Uuid | null;
  questionId: Uuid;
  onAnswer: (recordingId: Uuid) => void;
  onTranscript: (transcript: string) => void;
}) {
  const recording = useAnswerRecording({ userId, sessionId, questionId }, onAnswer, onTranscript);
  const seconds = Math.floor(recording.durationMillis / 1000);

  const tap = async () => {
    await recording.toggle();
  };

  return (
    <View style={styles.recorderRow}>
      <Text style={styles.recorderHint}>
        {recording.syncStatus === "pending"
          ? "기기에 저장됨 · 연결되면 자동 전송"
          : recording.syncStatus === "failed"
            ? "전송 대기 중 · 눌러서 다시 시도"
            : recording.uploading
          ? "녹음을 저장하고 있어요"
          : recording.isRecording
            ? `${seconds}초 녹음 중 · 완료하려면 다시 눌러 주세요`
            : "버튼을 눌러 말씀해 주세요"}
      </Text>
      <Pressable
        onPress={() => void tap()}
        disabled={recording.uploading || !userId || !sessionId}
        accessibilityRole="button"
        accessibilityLabel={recording.isRecording ? "답변 녹음 완료" : "답변 녹음 시작"}
        style={({ pressed }) => [
          styles.micButton,
          {
            opacity: pressed || recording.uploading || !userId || !sessionId ? 0.7 : 1,
            backgroundColor: recording.isRecording ? colors.destructive : colors.primary,
          },
        ]}
      >
        <Ionicons name={recording.isRecording ? "stop" : "mic"} size={32} color={colors.white} />
      </Pressable>
      {recording.error ? <Text style={styles.recordingError}>{recording.error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },

  introCharacter: { alignItems: "center", paddingHorizontal: spacing.xl, paddingTop: spacing.xxl, paddingBottom: spacing.xl },
  introFooter: { flex: 1, justifyContent: "flex-end", paddingHorizontal: spacing.xl, paddingBottom: spacing.xxl, gap: spacing.lg },
  introGuide: { fontSize: fontSize.body, color: colors.mutedForeground, textAlign: "center", lineHeight: 24 },

  stage: {
    alignItems: "center",
    backgroundColor: colors.secondary,
    paddingTop: spacing.lg,
    paddingBottom: spacing.md,
  },
  stageStatus: {
    fontSize: fontSize.caption,
    fontWeight: fontWeight.semibold,
    color: colors.primaryDark,
    marginTop: spacing.xs,
  },

  thread: { padding: spacing.xl, gap: spacing.md },
  aiBubble: {
    alignSelf: "flex-start",
    maxWidth: "88%",
    backgroundColor: colors.muted,
    borderRadius: 18,
    borderTopLeftRadius: 4,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  aiText: { fontSize: fontSize.bodyLg, color: colors.foreground, lineHeight: 25 },
  myBubble: {
    alignSelf: "flex-end",
    maxWidth: "88%",
    backgroundColor: colors.primary,
    borderRadius: 18,
    borderTopRightRadius: 4,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  transcriptLabel: { fontSize: fontSize.badge, fontWeight: fontWeight.semibold, color: colors.white, marginBottom: spacing.xs },
  myText: { fontSize: fontSize.body, color: colors.white, lineHeight: 22 },
  submissionError: {
    fontSize: fontSize.caption,
    color: colors.destructive,
    textAlign: "center",
  },

  composer: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.lg,
    paddingBottom: spacing.xl,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  recorderRow: { alignItems: "center", gap: spacing.md },
  recorderHint: { fontSize: fontSize.caption, color: colors.mutedForeground },
  recordingError: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },
  micButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
});
