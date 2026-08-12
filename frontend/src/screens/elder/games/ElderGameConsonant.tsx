import React from "react";
import { View, Text, StyleSheet, Pressable, TextInput } from "react-native";
import { useNavigation } from "@react-navigation/native";

import type { ElderNav } from "@/navigation/types";
import { colors, spacing, radius, fontSize, fontWeight, sizes } from "@/theme";
import { Screen, ScreenHeader, Badge } from "@/components/ui";
import { useGameSubmit, Stars, ProgressStrip, SubmitStatus, DoneButtons } from "./shared";

/**
 * 초성 맞추기 — `game_type=consonant`, ported from the Figma prototype.
 *
 * Seven questions show a set of initial consonants plus a category hint; the
 * player types any word whose initials match. Grading decomposes the typed
 * word's Hangul initials, so 사과 and 수건 both pass ㅅㄱ — the game rewards
 * recall, not one canonical answer. "모르겠어요" reveals an example and counts
 * as wrong.
 */
const CHOSUNG = ["ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"];

function extractInitials(word: string): string {
  return [...word]
    .map((ch) => {
      const code = ch.charCodeAt(0) - 0xac00;
      if (code < 0 || code > 11171) return "";
      return CHOSUNG[Math.floor(code / 28 / 21)];
    })
    .join("");
}

const QUESTIONS = [
  { initials: "ㅅㄱ", hint: "과일", example: "사과" },
  { initials: "ㄴㅂ", hint: "곤충", example: "나비" },
  { initials: "ㅂㅅ", hint: "탈것", example: "버스" },
  { initials: "ㄷㄱ", hint: "과일", example: "딸기" },
  { initials: "ㄱㅇㅇ", hint: "동물", example: "고양이" },
  { initials: "ㅈㄷㅊ", hint: "탈것", example: "자동차" },
  { initials: "ㅅㄴㅁ", hint: "나무", example: "소나무" },
];

const TOTAL = QUESTIONS.length;

export default function ElderGameConsonantScreen() {
  const navigation = useNavigation<ElderNav>();
  const { submit, result, error, submitting, resetForNewRun } = useGameSubmit("consonant");

  const [qIdx, setQIdx] = React.useState(0);
  const [score, setScore] = React.useState(0);
  const [done, setDone] = React.useState(false);
  const [input, setInput] = React.useState("");
  const [verdict, setVerdict] = React.useState<"correct" | "wrong" | null>(null);
  const [skipped, setSkipped] = React.useState(false);
  const startedAt = React.useRef(Date.now());
  const questionShownAt = React.useRef(Date.now());
  const responseTimes = React.useRef<number[]>([]);
  const wrongCount = React.useRef(0);

  const q = QUESTIONS[qIdx];

  const finish = (finalScore: number) => {
    setDone(true);
    void submit({
      score: finalScore,
      total_questions: TOTAL,
      error_count: wrongCount.current,
      response_times: responseTimes.current,
      duration_sec: Math.round((Date.now() - startedAt.current) / 1000),
      restarted_count: 0,
      completed: true,
    });
  };

  const advance = (wasCorrect: boolean) => {
    const nextScore = wasCorrect ? score + 1 : score;
    if (wasCorrect) setScore(nextScore);
    if (qIdx + 1 >= TOTAL) {
      finish(nextScore);
      return;
    }
    setQIdx((i) => i + 1);
    setInput("");
    setVerdict(null);
    setSkipped(false);
    questionShownAt.current = Date.now();
  };

  const submitAnswer = () => {
    if (!input.trim() || verdict !== null) return;
    responseTimes.current.push((Date.now() - questionShownAt.current) / 1000);
    const correct = extractInitials(input.trim()) === q.initials;
    if (!correct) wrongCount.current += 1;
    setVerdict(correct ? "correct" : "wrong");
    if (correct) setTimeout(() => advance(true), 900);
  };

  const skip = () => {
    if (verdict !== null) return;
    responseTimes.current.push((Date.now() - questionShownAt.current) / 1000);
    wrongCount.current += 1;
    setSkipped(true);
    setVerdict("wrong");
  };

  const restart = () => {
    setQIdx(0);
    setScore(0);
    setDone(false);
    setInput("");
    setVerdict(null);
    setSkipped(false);
    startedAt.current = Date.now();
    questionShownAt.current = Date.now();
    responseTimes.current = [];
    wrongCount.current = 0;
    resetForNewRun();
  };

  const header = (
    <ScreenHeader
      onBack={() => navigation.goBack()}
      backLabel="게임 목록"
      title="초성 맞추기"
      subtitle="초성에 맞는 단어를 직접 입력해요"
    />
  );

  if (done) {
    const pct = Math.round((score / TOTAL) * 100);
    const stars = pct >= 80 ? 3 : pct >= 57 ? 2 : 1;
    return (
      <Screen header={header}>
        <View style={styles.doneBox}>
          <Stars count={stars} />
          <View style={{ alignItems: "center" }}>
            <Text style={styles.doneScore}>
              {score}
              <Text style={styles.doneScoreTotal}>/{TOTAL}</Text>
            </Text>
            <Text style={styles.doneMessage}>
              {pct >= 80 ? "언어 능력이 대단해요!" : pct >= 57 ? "잘 하셨어요!" : "다시 도전해봐요"}
            </Text>
          </View>
          <SubmitStatus
            result={result}
            error={error}
            submitting={submitting}
            onRetry={() => finish(score)}
          />
          <DoneButtons onRestart={restart} onExit={() => navigation.goBack()} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen
      header={
        <>
          {header}
          <ProgressStrip
            left={`${qIdx + 1} / ${TOTAL}문제`}
            right={`점수 ${score}`}
            progress={qIdx / TOTAL}
          />
        </>
      }
    >
      <View style={styles.body}>
        <View style={{ alignItems: "center", gap: spacing.md }}>
          <Badge label={q.hint} color={colors.primary} background={colors.secondary} />
          <View style={styles.initialsBox}>
            {[...q.initials].map((ch, i) => (
              <Text key={i} style={styles.initial}>
                {ch}
              </Text>
            ))}
          </View>
          <Text style={styles.hint}>이 초성으로 이루어진 단어를 입력하세요</Text>
        </View>

        <View style={{ alignSelf: "stretch", gap: spacing.md }}>
          <View
            style={[
              styles.inputBox,
              {
                borderColor:
                  verdict === "correct"
                    ? colors.success
                    : verdict === "wrong" && !skipped
                      ? colors.destructive
                      : colors.primary,
              },
            ]}
          >
            <TextInput
              value={input}
              onChangeText={(v) => verdict === null && setInput(v)}
              onSubmitEditing={submitAnswer}
              editable={verdict === null}
              placeholder="여기에 단어를 입력하세요"
              placeholderTextColor={colors.mutedForeground}
              style={styles.input}
              accessibilityLabel="단어 입력"
            />
          </View>

          {verdict === "correct" ? (
            <Text style={[styles.verdict, { color: colors.success }]}>정답입니다!</Text>
          ) : null}

          {verdict === "wrong" ? (
            <View style={{ gap: spacing.sm }}>
              <Text style={styles.wrongNote}>
                {skipped
                  ? `예시 정답: ${q.example}`
                  : `"${input}"의 초성이 "${q.initials}"과 다릅니다.`}
              </Text>
              <Pressable
                onPress={() => advance(false)}
                accessibilityRole="button"
                accessibilityLabel="다음 문제"
                style={styles.nextButton}
              >
                <Text style={styles.nextLabel}>다음 문제</Text>
              </Pressable>
            </View>
          ) : null}

          {verdict === null ? (
            <View style={{ flexDirection: "row", gap: spacing.sm }}>
              <Pressable
                onPress={skip}
                accessibilityRole="button"
                accessibilityLabel="모르겠어요"
                style={[styles.action, { backgroundColor: colors.muted }]}
              >
                <Text style={[styles.actionLabel, { color: colors.mutedForeground }]}>
                  모르겠어요
                </Text>
              </Pressable>
              <Pressable
                onPress={submitAnswer}
                disabled={!input.trim()}
                accessibilityRole="button"
                accessibilityLabel="확인"
                accessibilityState={{ disabled: !input.trim() }}
                style={[
                  styles.action,
                  { backgroundColor: input.trim() ? colors.primary : colors.muted },
                ]}
              >
                <Text
                  style={[
                    styles.actionLabel,
                    { color: input.trim() ? colors.white : colors.mutedForeground },
                  ]}
                >
                  확인
                </Text>
              </Pressable>
            </View>
          ) : null}
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.xxl, paddingVertical: spacing.xl },
  initialsBox: {
    minWidth: 160,
    minHeight: 110,
    borderRadius: radius.xl,
    backgroundColor: colors.secondary,
    borderWidth: 3,
    borderColor: colors.primary,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.md,
    paddingHorizontal: spacing.xxl,
  },
  initial: { fontSize: 48, fontWeight: fontWeight.bold, color: colors.primary },
  hint: { fontSize: fontSize.body, color: colors.mutedForeground },

  inputBox: {
    height: 56,
    borderRadius: radius.lg,
    borderWidth: 2,
    backgroundColor: colors.muted,
    paddingHorizontal: spacing.lg,
    justifyContent: "center",
  },
  input: {
    fontSize: 22,
    fontWeight: fontWeight.bold,
    color: colors.foreground,
    textAlign: "center",
  },
  verdict: { textAlign: "center", fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold },
  wrongNote: { textAlign: "center", fontSize: fontSize.body, color: colors.destructive },
  nextButton: {
    height: 44,
    borderRadius: radius.md,
    backgroundColor: colors.muted,
    alignItems: "center",
    justifyContent: "center",
  },
  nextLabel: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.mutedForeground },
  action: {
    flex: 1,
    height: sizes.buttonHeight,
    borderRadius: radius.xl,
    alignItems: "center",
    justifyContent: "center",
  },
  actionLabel: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold },

  doneBox: { alignItems: "center", gap: spacing.xl, paddingVertical: spacing.xxl },
  doneScore: { fontSize: 48, fontWeight: fontWeight.bold, color: colors.primary },
  doneScoreTotal: { fontSize: 20, color: colors.mutedForeground },
  doneMessage: { fontSize: fontSize.bodyLg, color: colors.mutedForeground, marginTop: 4 },
});
