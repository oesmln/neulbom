import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import type { ElderNav } from "@/navigation/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, ScreenHeader } from "@/components/ui";
import { useGameSubmit, Stars, ProgressStrip, SubmitStatus, DoneButtons } from "./shared";

/**
 * 색깔 기억하기 — ported from the Figma prototype.
 *
 * Five rounds: memorise the target colour for five seconds, then pick it out of
 * four close shades. The reveal marks the right answer green and a wrong pick
 * red, names the colour, and moves on after 1.4s.
 *
 * `game_type` is sent as `color_match`, which api-spec 9.1's enum does not have
 * yet — Issue #53 tracks the backend addition. Mock mode accepts anything.
 */
const MEMO_SEC = 5;
const REVEAL_MS = 1400;

interface ColorOpt {
  hex: string;
  name: string;
}

const COLOR_ROUNDS: { target: ColorOpt; options: ColorOpt[] }[] = [
  {
    target: { hex: "#E63946", name: "딸기빨강" },
    options: [
      { hex: "#E63946", name: "딸기빨강" },
      { hex: "#B22222", name: "진빨강" },
      { hex: "#FF8A80", name: "연분홍" },
      { hex: "#E07A5F", name: "주황빨강" },
    ],
  },
  {
    target: { hex: "#4FC3F7", name: "하늘파랑" },
    options: [
      { hex: "#1565C0", name: "진파랑" },
      { hex: "#4FC3F7", name: "하늘파랑" },
      { hex: "#80DEEA", name: "민트파랑" },
      { hex: "#7986CB", name: "보라파랑" },
    ],
  },
  {
    target: { hex: "#66BB6A", name: "초록" },
    options: [
      { hex: "#388E3C", name: "진초록" },
      { hex: "#66BB6A", name: "초록" },
      { hex: "#AED581", name: "연초록" },
      { hex: "#4DB6AC", name: "청초록" },
    ],
  },
  {
    target: { hex: "#FFA726", name: "주황" },
    options: [
      { hex: "#F57F17", name: "진주황" },
      { hex: "#FFA726", name: "주황" },
      { hex: "#FFD54F", name: "노란주황" },
      { hex: "#FF7043", name: "빨간주황" },
    ],
  },
  {
    target: { hex: "#AB47BC", name: "보라" },
    options: [
      { hex: "#6A1B9A", name: "진보라" },
      { hex: "#AB47BC", name: "보라" },
      { hex: "#CE93D8", name: "연보라" },
      { hex: "#7986CB", name: "파란보라" },
    ],
  },
];

const TOTAL = COLOR_ROUNDS.length;

function shuffled<T>(arr: T[]): T[] {
  return [...arr].sort(() => Math.random() - 0.5);
}

export default function ElderGameColorScreen() {
  const navigation = useNavigation<ElderNav>();
  const { submit, result, error, submitting, resetForNewRun } = useGameSubmit("color_match");

  const [roundIdx, setRoundIdx] = React.useState(0);
  const [phase, setPhase] = React.useState<"memorize" | "choose" | "result">("memorize");
  const [countdown, setCountdown] = React.useState(MEMO_SEC);
  const [score, setScore] = React.useState(0);
  const [done, setDone] = React.useState(false);
  const [selected, setSelected] = React.useState<number | null>(null);
  const [opts, setOpts] = React.useState<ColorOpt[]>(() => shuffled(COLOR_ROUNDS[0].options));
  const startedAt = React.useRef(Date.now());
  const chooseShownAt = React.useRef(0);
  const responseTimes = React.useRef<number[]>([]);

  const round = COLOR_ROUNDS[roundIdx];

  React.useEffect(() => {
    if (phase !== "memorize") return;
    setCountdown(MEMO_SEC);
    const timer = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(timer);
          chooseShownAt.current = Date.now();
          setPhase("choose");
          return 0;
        }
        return c - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [phase, roundIdx]);

  const finish = (finalScore: number) => {
    setDone(true);
    void submit({
      score: finalScore,
      total_questions: TOTAL,
      error_count: TOTAL - finalScore,
      response_times: responseTimes.current,
      duration_sec: Math.round((Date.now() - startedAt.current) / 1000),
      restarted_count: 0,
      completed: true,
    });
  };

  const pick = (i: number) => {
    if (phase !== "choose" || selected !== null) return;
    responseTimes.current.push((Date.now() - chooseShownAt.current) / 1000);
    setSelected(i);
    const correct = opts[i].hex === round.target.hex;
    const nextScore = correct ? score + 1 : score;
    if (correct) setScore(nextScore);
    setPhase("result");
    setTimeout(() => {
      if (roundIdx + 1 >= TOTAL) {
        finish(nextScore);
        return;
      }
      const next = roundIdx + 1;
      setRoundIdx(next);
      setOpts(shuffled(COLOR_ROUNDS[next].options));
      setSelected(null);
      setPhase("memorize");
    }, REVEAL_MS);
  };

  const restart = () => {
    setRoundIdx(0);
    setScore(0);
    setDone(false);
    setSelected(null);
    setOpts(shuffled(COLOR_ROUNDS[0].options));
    setPhase("memorize");
    startedAt.current = Date.now();
    responseTimes.current = [];
    resetForNewRun();
  };

  const header = (
    <ScreenHeader
      onBack={() => navigation.goBack()}
      backLabel="게임 목록"
      title="색깔 기억하기"
      subtitle="색깔을 기억하고 같은 색을 골라요"
    />
  );

  if (done) {
    const pct = Math.round((score / TOTAL) * 100);
    const stars = pct >= 80 ? 3 : pct >= 60 ? 2 : 1;
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
              {pct >= 80 ? "색깔 기억력이 대단해요!" : pct >= 60 ? "잘 하셨어요!" : "다시 도전해봐요"}
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
      scroll={false}
      header={
        <>
          {header}
          <ProgressStrip
            left={`${roundIdx + 1} / ${TOTAL} 라운드`}
            right={`점수 ${score}`}
            progress={roundIdx / TOTAL}
          />
        </>
      }
    >
      <View style={styles.body}>
        {phase === "memorize" ? (
          <>
            <Text style={styles.prompt}>이 색깔을 기억해 주세요</Text>
            <View style={[styles.target, { backgroundColor: round.target.hex }]} />
            <View style={{ alignItems: "center", gap: spacing.sm }}>
              <View style={styles.countdown}>
                <Text style={styles.countdownLabel}>{countdown}</Text>
              </View>
              <Text style={styles.hint}>초 후 문제가 시작돼요</Text>
            </View>
          </>
        ) : (
          <>
            <Text style={styles.prompt}>아까 본 색깔이 어떤 것인가요?</Text>
            <View style={styles.optionGrid}>
              {opts.map((opt, i) => {
                const isTarget = opt.hex === round.target.hex;
                const isChosen = selected === i;
                const reveal = phase === "result";
                return (
                  <Pressable
                    key={`${opt.hex}-${i}`}
                    onPress={() => pick(i)}
                    accessibilityRole="button"
                    accessibilityLabel={`색깔 선택지 ${i + 1}`}
                    style={[
                      styles.option,
                      {
                        backgroundColor: opt.hex,
                        borderColor: reveal
                          ? isTarget
                            ? colors.success
                            : isChosen
                              ? colors.destructive
                              : "transparent"
                          : "transparent",
                        opacity: reveal && !isTarget && !isChosen ? 0.5 : 1,
                      },
                    ]}
                  >
                    {reveal && isTarget ? (
                      <Ionicons name="checkmark-circle" size={22} color={colors.white} />
                    ) : null}
                  </Pressable>
                );
              })}
            </View>
            {phase === "result" && selected !== null ? (
              <Text
                style={[
                  styles.verdict,
                  {
                    color:
                      opts[selected].hex === round.target.hex ? colors.success : colors.destructive,
                  },
                ]}
              >
                {opts[selected].hex === round.target.hex
                  ? "정답! 잘 기억하셨어요"
                  : `정답은 ${round.target.name}이에요`}
              </Text>
            ) : null}
          </>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.xl },
  prompt: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold, color: colors.foreground },
  target: { width: 180, height: 180, borderRadius: radius.xl },
  countdown: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.secondary,
    borderWidth: 3,
    borderColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  countdownLabel: { fontSize: 24, fontWeight: fontWeight.bold, color: colors.primary },
  hint: { fontSize: fontSize.caption, color: colors.mutedForeground },

  optionGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md, alignSelf: "stretch" },
  option: {
    flexBasis: "45%",
    flexGrow: 1,
    height: 90,
    borderRadius: radius.lg,
    borderWidth: 4,
    alignItems: "center",
    justifyContent: "flex-end",
    paddingBottom: spacing.md,
  },
  verdict: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold },

  doneBox: { alignItems: "center", gap: spacing.xl, paddingVertical: spacing.xxl },
  doneScore: { fontSize: 48, fontWeight: fontWeight.bold, color: colors.primary },
  doneScoreTotal: { fontSize: 20, color: colors.mutedForeground },
  doneMessage: { fontSize: fontSize.bodyLg, color: colors.mutedForeground, marginTop: 4 },
});
