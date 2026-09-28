import React from "react";
import { View, StyleSheet, ScrollView, Pressable } from "react-native";
import { useIsFocused, useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { game, newClientId, sessions } from "@/api";
import { useApi } from "@/hooks/useApi";
import { useAnswerRecording } from "@/hooks/useAnswerRecording";
import { useSpeechPlayback } from "@/hooks/useSpeechPlayback";
import { apiErrorMessage } from "@/api/errors";
import { USE_MOCK_API } from "@/api/config";
import type { SessionResponse, Uuid } from "@/api/types";
import { colors, spacing, fontSize, fontWeight } from "@/theme";
import { Button, ErrorState, LoadingState, ScreenHeader, SentenceText as Text, SpeechBubble } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import VoicePlaybackButton from "@/components/VoicePlaybackButton";
import { DEFAULT_CHARACTER_NAME, memoiForLevel } from "@/components/memoiCharacters";
import { withParticle } from "@/utils/format";

/**
 * AI emotional Q&A — the daily conversation, answered by voice.
 *
 * Runs as its own session (`POST /sessions` with `session_type=emotional_qa`),
 * gets its questions from a Gemini generated follow up or a randomized CIST bank item, and saves each answer through
 * `POST /sessions/{id}/answers`, and ends with `PATCH /sessions/{id}/end` before
 * handing the session id to the result screen.
 *
 * The character is pinned above the conversation and never unmounts: it is the
 * one asking the questions, so it mouths each one as it arrives. The speaking
 * window is still timed from text length, while answers are recorded, uploaded,
 * and attached to the saved answer through `recording_id`.
 */
const INTRO_LINE = "오늘 하루 어떠셨어요? 편하게 이야기해 주세요.";

/** Example answers for the serverless preview. These are not speech transcripts. */
const SAMPLE_ANSWERS = [
  "오늘은 좀 피곤하긴 한데 괜찮아요.",
  "된장찌개 먹었는데 아들이 끓여줬어요. 맛있었어요.",
  "오후에 공원 산책을 했어요.",
  "손자랑 통화한 게 가장 기뻤어요.",
  "내일 병원에 가야 해서 조금 걱정이 돼요.",
  "친구와 시장에 다녀왔어요.",
  "저녁에는 가족과 같이 밥을 먹고 싶어요.",
];

export default function ElderAiChatScreen() {
  const navigation = useNavigation<ElderNav>();
  const isFocused = useIsFocused();
  const { userId, characterName } = useApp();
  const companionName = characterName?.trim() || DEFAULT_CHARACTER_NAME;

  const character = useApi(() => game.character(userId as string), [userId, isFocused], {
    enabled: !!userId && isFocused,
  });
  const companionModel = memoiForLevel(character.data?.level);

  const [phase, setPhase] = React.useState<"intro" | "chat">("intro");
  const [session, setSession] = React.useState<SessionResponse | null>(null);
  const [sessionLoading, setSessionLoading] = React.useState(false);
  const [sessionError, setSessionError] = React.useState<string | null>(null);
  const [index, setIndex] = React.useState(0);
  const [answers, setAnswers] = React.useState<string[]>([]);
  const [transcripts, setTranscripts] = React.useState<string[]>([]);
  const [recordingIds, setRecordingIds] = React.useState<Array<Uuid | null>>([]);
  const [submitting, setSubmitting] = React.useState(false);
  const [submissionError, setSubmissionError] = React.useState<string | null>(null);
  const [askedAt, setAskedAt] = React.useState(() => Date.now());
  const answerClientIds = React.useRef<Record<string, Uuid>>({});

  const currentQuestion = useApi(
    () => sessions.currentQuestion(session?.session_id as Uuid),
    [session?.session_id, index],
    { enabled: phase === "chat" && !!session?.session_id },
  );

  const currentQuestionData = currentQuestion.data;
  const question = currentQuestionData
      && currentQuestionData.session_id === session?.session_id
      && currentQuestionData.question.order === index + 1
    ? currentQuestionData.question
    : null;
  const answered = USE_MOCK_API ? answers.length > index : Boolean(recordingIds[index]);
  const isLast = !!session && index === session.total_questions - 1;

  // The character only mouths the question itself; once it has been answered it
  // goes back to resting until the next one arrives.
  const spokenLine = phase === "intro" ? INTRO_LINE : answered ? null : question?.content ?? null;
  const voice = useSpeechPlayback(spokenLine);

  React.useEffect(() => {
    setAskedAt(Date.now());
  }, [question?.question_id]);

  const beginConversation = async () => {
    if (!userId || sessionLoading) return;
    setPhase("chat");
    setSession(null);
    setSessionError(null);
    setSessionLoading(true);
    try {
      setSession(await sessions.start({ user_id: userId, session_type: "emotional_qa" }));
    } catch (cause) {
      setSessionError(apiErrorMessage(cause));
    } finally {
      setSessionLoading(false);
    }
  };

  const restart = () => {
    setPhase("intro");
    setSession(null);
    setSessionError(null);
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
    if (!question || !session || submitting) return;
    setSubmitting(true);
    setSubmissionError(null);
    const sessionId = session.session_id;

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
            character={companionModel}
            height={180}
            spinnerColor={colors.primary}
            style={{ width: 220 }}
          />
          <SpeechBubble text={INTRO_LINE} side="below" />
          <View style={styles.introVoiceToggle}>
            <VoicePlaybackButton
              enabled={voice.enabled}
              loading={voice.loading}
              speaking={voice.speaking}
              onPress={voice.toggle}
              onReplay={voice.replay}
            />
          </View>
          {voice.error ? <Text style={styles.voiceError}>{voice.error}</Text> : null}
        </View>

        <View style={styles.introFooter}>
          <Text style={styles.introGuide}>
            AI가 음성으로 질문을 드리면{"\n"}마이크 버튼을 눌러 답변해 주세요.
          </Text>
          <Button
            label="대화 시작하기"
            size="lg"
            disabled={!userId || sessionLoading}
            onPress={() => void beginConversation()}
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScreenHeader
        title="AI 정서 문답"
        subtitle={session ? `${index + 1} / ${session.total_questions}` : ""}
        onBack={restart}
      />

      {/* Outside the ScrollView on purpose — the character must not scroll away
          mid-conversation, and unmounting it would drop four loaded models. */}
      <View style={styles.stage}>
        <Memoi3D
          character={companionModel}
          height={130}
          spinnerColor={colors.primary}
          style={{ width: 170 }}
        />
        <Text style={styles.stageStatus}>{voice.speaking ? `${withParticle(companionName, "이", "가")} 말하고 있어요` : companionName}</Text>
        <View style={styles.stageVoiceToggle}>
          <VoicePlaybackButton
            enabled={voice.enabled}
            loading={voice.loading}
            speaking={voice.speaking}
            onPress={voice.toggle}
            onReplay={voice.replay}
            compact
          />
        </View>
        {voice.error ? <Text style={styles.voiceError}>{voice.error}</Text> : null}
      </View>

      <ScrollView contentContainerStyle={styles.thread} showsVerticalScrollIndicator={false}>
        {sessionError ? (
          <ErrorState message={sessionError} onRetry={() => void beginConversation()} />
        ) : !session ? (
          <LoadingState label="질문을 불러오는 중이에요" />
        ) : currentQuestion.error ? (
          <ErrorState message={apiErrorMessage(currentQuestion.error)} onRetry={currentQuestion.reload} />
        ) : !question ? (
          <LoadingState label="다음 질문을 준비하고 있어요" />
        ) : (
          <>
            <View style={styles.aiBubble}>
              <Text style={styles.aiText}>{question.content}</Text>
            </View>

            {answered ? (
              <View style={styles.myBubble}>
                <Text style={styles.transcriptLabel}>{USE_MOCK_API ? "샘플 답변" : "음성 인식 결과"}</Text>
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
            disabled={submitting || !session}
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
              companionName={companionName}
              userId={userId}
                sessionId={session?.session_id ?? null}
              questionId={question.question_id}
              disabled={voice.loading || voice.speaking}
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
  companionName,
  userId,
  sessionId,
  questionId,
  disabled,
  onAnswer,
  onTranscript,
}: {
  companionName: string;
  userId: Uuid | null;
  sessionId: Uuid | null;
  questionId: Uuid;
  disabled: boolean;
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
        {disabled
          ? `${companionName}의 질문을 들은 뒤 답변해 주세요`
          : recording.isRecording
            ? `${seconds}초 녹음 중 · 완료하려면 다시 눌러 주세요`
          : recording.uploading
            ? "녹음을 저장하고 있어요"
          : recording.syncStatus === "pending"
            ? "기기에 저장됨 · 연결되면 자동 전송"
          : recording.syncStatus === "failed"
            ? recording.canResend
              ? "전송 대기 중 · 눌러서 다시 시도"
              : "눌러서 다시 녹음해 주세요"
            : "버튼을 눌러 말씀해 주세요"}
      </Text>
      <Pressable
        onPress={() => void tap()}
        disabled={disabled || recording.uploading || !userId || !sessionId}
        accessibilityRole="button"
        accessibilityLabel={recording.isRecording ? "답변 녹음 완료" : "답변 녹음 시작"}
        style={({ pressed }) => [
          styles.micButton,
          {
            opacity: pressed || disabled || recording.uploading || !userId || !sessionId ? 0.7 : 1,
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
  introVoiceToggle: { marginTop: spacing.md },
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
  stageVoiceToggle: { marginTop: spacing.md },
  voiceError: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },

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
