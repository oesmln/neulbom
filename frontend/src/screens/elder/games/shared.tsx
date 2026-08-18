import React from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { useApp } from "@/store/AppContext";
import { game, sessions, newClientId } from "@/api";
import { ApiError, apiErrorMessage } from "@/api/errors";
import type { GameResultRequest, GameResultResponse, GameType, Uuid } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight, sizes } from "@/theme";
import { SentenceText as Text } from "@/components/ui";

/**
 * The pieces the three mini-games share: result submission and the finish UI.
 *
 * Submission follows api-spec 9.1 — a `game` session is opened lazily on the
 * first completion, and `client_game_result_id` is minted once per finished run
 * and reused on retry, so a flaky network can never earn XP twice. XP itself is
 * the server's job (`xp_earned` comes back in the response); nothing here posts
 * to `/character/{id}/xp`.
 */
export type GameStats = Omit<
  GameResultRequest,
  "user_id" | "session_id" | "client_game_result_id" | "game_type"
>;

export function useGameSubmit(gameType: GameType) {
  const { userId } = useApp();
  const sessionRef = React.useRef<Uuid | null>(null);
  const clientIdRef = React.useRef<Uuid | null>(null);
  const [result, setResult] = React.useState<GameResultResponse | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  const submit = React.useCallback(
    async (stats: GameStats) => {
      if (!userId) return;
      setSubmitting(true);
      setError(null);
      try {
        if (!sessionRef.current) {
          const opened = await sessions.start({ user_id: userId, session_type: "game" });
          sessionRef.current = opened.session_id;
        }
        if (!clientIdRef.current) clientIdRef.current = newClientId();
        const response = await game.submitResult({
          user_id: userId,
          session_id: sessionRef.current,
          client_game_result_id: clientIdRef.current,
          game_type: gameType,
          ...stats,
        });
        setResult(response);
      } catch (cause) {
        setError(apiErrorMessage(cause as ApiError));
      } finally {
        setSubmitting(false);
      }
    },
    [userId, gameType],
  );

  /** A fresh run is a fresh result — new idempotency key, same session. */
  const resetForNewRun = React.useCallback(() => {
    clientIdRef.current = null;
    setResult(null);
    setError(null);
  }, []);

  return { submit, result, error, submitting, resetForNewRun };
}

/* ------------------------------------------------------------------ pieces */

export function Stars({ count }: { count: number }) {
  return (
    <View style={styles.stars}>
      {[1, 2, 3].map((i) => (
        <Ionicons
          key={i}
          name={i <= count ? "star" : "star-outline"}
          size={40}
          color={i <= count ? colors.accent : colors.muted}
        />
      ))}
    </View>
  );
}

/** The 진행/점수 strip under the header, shared by 색깔·초성. */
export function ProgressStrip({
  left,
  right,
  progress,
}: {
  left: string;
  right: string;
  progress: number;
}) {
  return (
    <View style={styles.strip}>
      <View style={styles.stripRow}>
        <Text style={styles.stripLabel}>{left}</Text>
        <Text style={styles.stripLabel}>{right}</Text>
      </View>
      <View style={styles.track}>
        <View style={[styles.fill, { width: `${Math.min(100, Math.max(0, progress * 100))}%` }]} />
      </View>
    </View>
  );
}

/**
 * Shown once under the stars on the finish screen: the XP the server actually
 * awarded, or the submit error with a retry — never a silent failure.
 */
export function SubmitStatus({
  result,
  error,
  submitting,
  onRetry,
}: {
  result: GameResultResponse | null;
  error: string | null;
  submitting: boolean;
  onRetry: () => void;
}) {
  if (submitting) return <Text style={styles.xpNote}>결과를 저장하는 중이에요…</Text>;
  if (error) {
    return (
      <View style={{ alignItems: "center", gap: spacing.sm }}>
        <Text style={styles.submitError}>{error}</Text>
        <Pressable
          onPress={onRetry}
          accessibilityRole="button"
          accessibilityLabel="결과 다시 저장하기"
          style={styles.retry}
        >
          <Text style={styles.retryLabel}>다시 저장하기</Text>
        </Pressable>
      </View>
    );
  }
  if (result) {
    return (
      <View style={styles.xpResult}>
        <Text style={styles.xpNote}>
          {result.deduplicated
            ? "이미 저장된 결과예요"
            : `이번 게임에서 +${result.xp_earned} XP를 받았어요!`}
        </Text>
        {!result.deduplicated ? (
          <Text style={styles.xpPolicy}>참여 +3 XP · 성공 시 +10 XP · 하루 최대 100 XP</Text>
        ) : null}
      </View>
    );
  }
  return null;
}

export function DoneButtons({
  onRestart,
  onExit,
  exitLabel = "게임 목록",
}: {
  onRestart: () => void;
  onExit: () => void;
  exitLabel?: string;
}) {
  return (
    <View style={{ alignSelf: "stretch", gap: spacing.md }}>
      <Pressable
        onPress={onRestart}
        accessibilityRole="button"
        accessibilityLabel="다시 하기"
        style={[styles.doneButton, { backgroundColor: colors.primary }]}
      >
        <Text style={[styles.doneButtonLabel, { color: colors.white }]}>다시 하기</Text>
      </Pressable>
      <Pressable
        onPress={onExit}
        accessibilityRole="button"
        accessibilityLabel={exitLabel}
        style={[styles.doneButton, { backgroundColor: colors.muted }]}
      >
        <Text style={[styles.doneButtonLabel, { color: colors.mutedForeground }]}>{exitLabel}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  stars: { flexDirection: "row", gap: 6 },

  strip: {
    backgroundColor: colors.secondary,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.md,
  },
  stripRow: { flexDirection: "row", justifyContent: "space-between", marginBottom: 6 },
  stripLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.secondaryForeground },
  track: { height: 8, borderRadius: 4, backgroundColor: colors.border, overflow: "hidden" },
  fill: { height: "100%", borderRadius: 4, backgroundColor: colors.primary },

  xpNote: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.primaryDark },
  xpResult: { alignItems: "center", gap: 4 },
  xpPolicy: { fontSize: fontSize.caption, color: colors.mutedForeground, textAlign: "center" },
  submitError: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },
  retry: {
    height: 40,
    paddingHorizontal: spacing.xl,
    borderRadius: radius.md,
    borderWidth: 1.5,
    borderColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  retryLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.primary },

  doneButton: {
    height: sizes.buttonHeight,
    borderRadius: radius.xl,
    alignItems: "center",
    justifyContent: "center",
  },
  doneButtonLabel: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold },
});
