import React from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { useNavigation } from "@react-navigation/native";

import type { ElderNav } from "@/navigation/types";
import { colors, onHeader, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, ScreenHeader, SentenceText as Text } from "@/components/ui";
import { useGameSubmit, Stars, SubmitStatus, DoneButtons } from "./shared";

/**
 * 카드 뒤집기 — `game_type=image_match`, ported from the Figma prototype.
 *
 * Six emoji pairs in a four-column grid; two flips make one attempt, a mismatch
 * flips back after 900ms with input locked while it shows. The result payload
 * carries the fields api-spec 9.1 marks conditional for this game —
 * `matched_pairs` and `attempt_count` — plus per-attempt response times.
 */
const PAIR_COUNT = 6;
const EMOJI_POOL = ["🌸", "🍎", "🐶", "⭐", "🎵", "🌈", "🍀", "🦋", "🍊", "🐱", "🎀", "🌻"];
const MISMATCH_MS = 900;

interface CardState {
  id: number;
  emoji: string;
  flipped: boolean;
  matched: boolean;
}

function makeCards(): CardState[] {
  const emojis = EMOJI_POOL.slice(0, PAIR_COUNT);
  return [...emojis, ...emojis]
    .map((emoji, id) => ({ id, emoji, flipped: false, matched: false }))
    .sort(() => Math.random() - 0.5);
}

function formatTime(total: number) {
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

export default function ElderGameCardMatchScreen() {
  const navigation = useNavigation<ElderNav>();
  const { submit, result, error, submitting, resetForNewRun } = useGameSubmit("image_match");

  const [cards, setCards] = React.useState<CardState[]>(makeCards);
  const [flipped, setFlipped] = React.useState<number[]>([]);
  const [moves, setMoves] = React.useState(0);
  const [elapsed, setElapsed] = React.useState(0);
  const [running, setRunning] = React.useState(false);
  const [done, setDone] = React.useState(false);
  const [locked, setLocked] = React.useState(false);
  const [restarts, setRestarts] = React.useState(0);
  // Per-attempt response times (seconds since the previous attempt resolved).
  const responseTimes = React.useRef<number[]>([]);
  const attemptStartedAt = React.useRef<number>(Date.now());

  const matchedCount = cards.filter((c) => c.matched).length;

  React.useEffect(() => {
    if (!running || done) return;
    const timer = setInterval(() => setElapsed((e) => e + 1), 1000);
    return () => clearInterval(timer);
  }, [running, done]);

  React.useEffect(() => {
    if (flipped.length !== 2) return;
    setLocked(true);
    responseTimes.current.push((Date.now() - attemptStartedAt.current) / 1000);
    attemptStartedAt.current = Date.now();
    const [a, b] = flipped;
    if (cards[a].emoji === cards[b].emoji) {
      setCards((prev) => prev.map((c, i) => (i === a || i === b ? { ...c, matched: true } : c)));
      setFlipped([]);
      setLocked(false);
    } else {
      setTimeout(() => {
        setCards((prev) => prev.map((c, i) => (i === a || i === b ? { ...c, flipped: false } : c)));
        setFlipped([]);
        setLocked(false);
      }, MISMATCH_MS);
    }
    setMoves((m) => m + 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flipped]);

  React.useEffect(() => {
    // `moves` is already final here: the match effect batches its setCards and
    // setMoves into one commit, and this effect runs after that commit.
    if (cards.length > 0 && cards.every((c) => c.matched) && !done) {
      setDone(true);
      void submit({
        score: PAIR_COUNT,
        total_questions: PAIR_COUNT,
        error_count: Math.max(0, moves - PAIR_COUNT),
        response_times: responseTimes.current,
        matched_pairs: PAIR_COUNT,
        attempt_count: moves,
        duration_sec: elapsed,
        restarted_count: restarts,
        completed: true,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cards]);

  const flip = (idx: number) => {
    if (locked || done || cards[idx].flipped || cards[idx].matched || flipped.length >= 2) return;
    if (!running) setRunning(true);
    setCards((prev) => prev.map((c, i) => (i === idx ? { ...c, flipped: true } : c)));
    setFlipped((prev) => [...prev, idx]);
  };

  const restart = (countIt: boolean) => {
    setCards(makeCards());
    setFlipped([]);
    setMoves(0);
    setElapsed(0);
    setRunning(false);
    setDone(false);
    setLocked(false);
    if (countIt) setRestarts((r) => r + 1);
    else setRestarts(0);
    responseTimes.current = [];
    attemptStartedAt.current = Date.now();
    resetForNewRun();
  };

  const stars = moves <= 8 ? 3 : moves <= 12 ? 2 : 1;

  return (
    <Screen
      scroll={done}
      header={
        <ScreenHeader
          onBack={() => navigation.goBack()}
          backLabel="게임 목록"
          title="카드 뒤집기"
          subtitle="같은 그림 카드 6쌍을 모두 찾아보세요!"
        />
      }
    >
      {done ? (
        <View style={styles.doneBox}>
          <Stars count={stars} />
          <View style={{ alignItems: "center", gap: 4 }}>
            <Text style={styles.doneTitle}>완료!</Text>
            <Text style={styles.doneSub}>모든 짝을 찾으셨어요 🎉</Text>
          </View>
          <View style={styles.summary}>
            {[
              { label: "걸린 시간", value: formatTime(elapsed) },
              { label: "총 시도", value: `${moves}회` },
              { label: "별점", value: "⭐".repeat(stars) },
            ].map((s) => (
              <View key={s.label} style={{ flex: 1, alignItems: "center" }}>
                <Text style={styles.summaryValue}>{s.value}</Text>
                <Text style={styles.summaryLabel}>{s.label}</Text>
              </View>
            ))}
          </View>
          <SubmitStatus
            result={result}
            error={error}
            submitting={submitting}
            onRetry={() =>
              void submit({
                score: PAIR_COUNT,
                total_questions: PAIR_COUNT,
                error_count: Math.max(0, moves - PAIR_COUNT),
                response_times: responseTimes.current,
                matched_pairs: PAIR_COUNT,
                attempt_count: moves,
                duration_sec: elapsed,
                restarted_count: restarts,
                completed: true,
              })
            }
          />
          <DoneButtons onRestart={() => restart(false)} onExit={() => navigation.goBack()} />
        </View>
      ) : (
        <>
          <View style={styles.stats}>
            <Text style={styles.stat}>
              짝 맞춤 <Text style={styles.statStrong}>{matchedCount / 2}</Text> / {PAIR_COUNT}
            </Text>
            <Text style={styles.stat}>
              시도 <Text style={styles.statStrong}>{moves}</Text>회
            </Text>
            <Text style={styles.statStrong}>{formatTime(elapsed)}</Text>
            <Pressable
              onPress={() => restart(true)}
              accessibilityRole="button"
              accessibilityLabel="다시 시작"
              style={styles.restart}
            >
              <Text style={styles.restartLabel}>다시 시작</Text>
            </Pressable>
          </View>

          <View style={styles.grid}>
            {cards.map((card, idx) => {
              const show = card.flipped || card.matched;
              return (
                <Pressable
                  key={card.id}
                  onPress={() => flip(idx)}
                  accessibilityRole="button"
                  accessibilityLabel={show ? `카드 ${card.emoji}` : "뒤집힌 카드"}
                  style={[
                    styles.card,
                    {
                      backgroundColor: card.matched
                        ? colors.secondary
                        : show
                          ? colors.card
                          : colors.primary,
                      borderColor: card.matched
                        ? colors.success
                        : show
                          ? colors.border
                          : colors.primaryDark,
                    },
                  ]}
                >
                  {show ? (
                    <Text style={{ fontSize: 30 }}>{card.emoji}</Text>
                  ) : (
                    <Text style={[styles.cardBack, { color: onHeader.action }]}>?</Text>
                  )}
                </Pressable>
              );
            })}
          </View>

          <Text style={styles.hint}>
            {!running
              ? "카드를 눌러 게임을 시작하세요"
              : `${PAIR_COUNT - matchedCount / 2}쌍 남았어요!`}
          </Text>
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  stats: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  stat: { fontSize: fontSize.micro, color: colors.mutedForeground },
  statStrong: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.primary },
  restart: {
    height: 32,
    paddingHorizontal: spacing.md,
    borderRadius: radius.sm,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  restartLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.white },

  grid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md },
  card: {
    flexBasis: "22%",
    flexGrow: 1,
    height: 72,
    borderRadius: radius.lg,
    borderWidth: 2,
    alignItems: "center",
    justifyContent: "center",
  },

  cardBack: { fontSize: 22 },
  hint: {
    marginTop: spacing.xl,
    textAlign: "center",
    fontSize: fontSize.caption,
    color: colors.mutedForeground,
  },

  doneBox: { alignItems: "center", gap: spacing.xl, paddingVertical: spacing.xl },
  doneTitle: { fontSize: 24, fontWeight: fontWeight.bold, color: colors.foreground },
  doneSub: { fontSize: fontSize.bodyLg, color: colors.mutedForeground },
  summary: {
    alignSelf: "stretch",
    flexDirection: "row",
    backgroundColor: colors.secondary,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
  },
  summaryValue: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.primary },
  summaryLabel: { fontSize: fontSize.badge, color: colors.mutedForeground, marginTop: 2 },
});
