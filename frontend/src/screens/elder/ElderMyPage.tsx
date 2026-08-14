import React from "react";
import { View, Text, Pressable, StyleSheet, ScrollView, Alert, Modal, TextInput } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav, RootNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { auth, game, reports, users } from "@/api";
import { useApi } from "@/hooks/useApi";
import { ApiError, apiErrorMessage } from "@/api/errors";
import { monthDayLabel } from "@/utils/format";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Button, Card, ErrorState, LoadingState, ProgressBar, ScreenHeader } from "@/components/ui";

/**
 * Character growth, month-to-date activity and account settings.
 *
 * Levels and XP are presentation of what the server already tracks — the app
 * reads `GET /character/{user_id}` and `/xp-history` and never posts XP itself
 * (api-spec 9.5 has the server award it on session and game completion, which
 * is also what stops double-crediting). Month-to-date counts come from
 * `GET /dashboard/{user_id}`'s `monthly_activity`.
 *
 * `LEVELS` below is presentation only — emoji, title and blurb per level. The
 * level number, current XP and the goal all come from the server; nothing here
 * decides when someone levels up.
 */
const LEVELS = [
  { level: 1, name: "아기 메모이", min: 0, emoji: "🐣", desc: "막 태어난 메모이예요. 함께 시작해봐요!" },
  { level: 2, name: "꼬마 메모이", min: 100, emoji: "🐶", desc: "조금씩 자라고 있어요. 꾸준히 함께해요!" },
  { level: 3, name: "활발한 메모이", min: 250, emoji: "🌱", desc: "에너지가 넘치는 메모이가 됐어요!" },
  { level: 4, name: "씩씩한 메모이", min: 500, emoji: "🌸", desc: "메모이가 훌쩍 성장했어요. 대단해요!" },
  { level: 5, name: "지혜로운 메모이", min: 800, emoji: "⭐", desc: "최고 등급! 메모이와 함께라서 행복해요." },
];

function levelMeta(level: number) {
  return LEVELS.find((l) => l.level === level) ?? LEVELS[0];
}

export default function ElderMyPageScreen() {
  const navigation = useNavigation<RootNav>();
  // Same navigator object, typed for the elder stack — 비밀번호 변경 and 앱 설정
  // are pushed there while 로그아웃 resets the root stack.
  const elderNavigation = useNavigation<ElderNav>();
  const { userId, characterName, signOut, updateOnboardingState } = useApp();
  const [xpOpen, setXpOpen] = React.useState(false);
  const [notificationsOn, setNotificationsOn] = React.useState(true);
  const [nameModalOpen, setNameModalOpen] = React.useState(false);
  const [nameDraft, setNameDraft] = React.useState("");
  const [nameSaving, setNameSaving] = React.useState(false);
  const [nameError, setNameError] = React.useState<string | null>(null);

  const character = useApi(() => game.character(userId as string), [userId], { enabled: !!userId });
  const xpHistory = useApi(() => game.xpHistory(userId as string), [userId], { enabled: !!userId });
  const dashboard = useApi(() => reports.dashboard(userId as string), [userId], { enabled: !!userId });
  const preferences = useApi(() => users.preferences(userId as string), [userId], { enabled: !!userId });

  React.useEffect(() => {
    if (preferences.data) setNotificationsOn(preferences.data.push_notification_enabled);
  }, [preferences.data]);

  const toggleNotifications = () => {
    const next = !notificationsOn;
    setNotificationsOn(next);
    void users
      .updatePreferences(userId as string, { push_notification_enabled: next })
      .catch(() => setNotificationsOn(!next));
  };

  /**
   * 계정 탈퇴 is irreversible, so it goes through a confirm dialog and ends in
   * the same signed-out state as 로그아웃. A failure leaves the session alone
   * and says so rather than pretending the account is gone.
   */
  const confirmWithdraw = () => {
    Alert.alert(
      "계정을 탈퇴하시겠어요?",
      "그동안의 대화, 일기, 검사 기록이 모두 삭제되고 되돌릴 수 없어요.",
      [
        { text: "취소", style: "cancel" },
        {
          text: "탈퇴하기",
          style: "destructive",
          onPress: () => {
            void auth
              .withdraw()
              .then(async () => {
                await signOut();
                navigation.reset({ index: 0, routes: [{ name: "Login" }] });
              })
              .catch((cause: ApiError) =>
                Alert.alert("탈퇴하지 못했어요", apiErrorMessage(cause)),
              );
          },
        },
      ],
    );
  };

  const logout = async () => {
    await signOut();
    navigation.reset({ index: 0, routes: [{ name: "Login" }] });
  };

  const openNameEditor = () => {
    setNameDraft(character.data?.display_name || characterName || "메모이");
    setNameError(null);
    setNameModalOpen(true);
  };

  const saveCharacterName = async () => {
    const normalized = nameDraft.trim();
    if (!userId || !normalized || nameSaving) return;
    setNameSaving(true);
    setNameError(null);
    try {
      await users.updateProfile(userId, { character_name: normalized });
      await updateOnboardingState({ characterName: normalized });
      await character.reload();
      setNameModalOpen(false);
    } catch (cause) {
      setNameError(apiErrorMessage(cause));
    } finally {
      setNameSaving(false);
    }
  };

  if (!character.data) {
    return (
      <SafeAreaView style={styles.safe} edges={["top"]}>
        <ScreenHeader title="마이페이지" subtitle="나의 성장 기록" />
        {character.error ? (
          <ErrorState message={apiErrorMessage(character.error)} onRetry={character.reload} />
        ) : (
          <LoadingState />
        )}
      </SafeAreaView>
    );
  }

  const { level: levelNumber, xp_current, xp_goal, xp_remaining } = character.data;
  const meta = levelMeta(levelNumber);
  const displayName = character.data.display_name || characterName || "메모이";
  const span = Math.max(1, xp_goal - meta.min);
  const level = {
    ...meta,
    progress: Math.min(Math.max(((xp_current - meta.min) / span) * 100, 0), 100),
    toNext: xp_remaining,
  };
  const monthly = dashboard.data?.monthly_activity ?? null;
  // No `monthly_activity` means the server has not aggregated this month yet —
  // that is not the same as "0회", so the slot stays empty instead of claiming
  // the user did nothing.
  const monthlyActivity = [
    { label: "AI 문답", value: monthly ? String(monthly.emotional_qa_completed_count) : "—", unit: "회" },
    { label: "게임 완료", value: monthly ? String(monthly.game_completed_count) : "—", unit: "회" },
    { label: "연속 출석", value: monthly ? String(monthly.current_attendance_streak_days) : "—", unit: "일" },
  ];
  const xpEntries = (xpHistory.data?.records ?? []).map((record) => ({
    key: record.xp_ledger_id,
    label: record.display_title ?? record.reason,
    xp: `+${record.amount}`,
    date: monthDayLabel(record.earned_at),
  }));

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScreenHeader
        title="마이페이지"
        subtitle="나의 성장 기록"
      />

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
        <Card style={{ gap: spacing.lg }}>
          <View style={styles.levelRow}>
            <View style={styles.levelBadge}>
              <Text style={{ fontSize: 28 }}>{level.emoji}</Text>
              <Text style={styles.levelBadgeLabel}>Lv.{level.level}</Text>
            </View>

            <View style={{ flex: 1 }}>
              <View style={styles.nameRow}>
                <Text style={styles.characterName}>{displayName}</Text>
                <Pressable
                  onPress={openNameEditor}
                  accessibilityRole="button"
                  accessibilityLabel="캐릭터 이름 변경"
                  hitSlop={8}
                  style={({ pressed }) => ({ opacity: pressed ? 0.6 : 1 })}
                >
                  <Ionicons name="pencil-outline" size={17} color={colors.mutedForeground} />
                </Pressable>
                <View style={styles.levelPill}>
                  <Text style={styles.levelPillLabel}>Lv.{level.level}</Text>
                </View>
              </View>
              <Text style={styles.characterDesc}>{level.desc.replace(/메모이/g, displayName)}</Text>
            </View>
          </View>

          <View>
            <View style={styles.xpRow}>
              <Text style={styles.xpLabel}>경험치</Text>
              <Text style={styles.xpValue}>
                {xp_current} / {xp_goal} XP
              </Text>
            </View>
            <ProgressBar value={level.progress} height={10} />
            {level.toNext > 0 ? (
              <Text style={styles.xpNote}>
                다음 레벨까지 <Text style={styles.xpNoteStrong}>{level.toNext} XP</Text> 남았어요
              </Text>
            ) : null}
          </View>

          <View>
            <Text style={styles.kicker}>성장 단계</Text>
            <View style={styles.roadmap}>
              {LEVELS.map((l, i) => {
                const done = levelNumber >= l.level;
                const current = level.level === l.level;
                const isLast = i === LEVELS.length - 1;
                return (
                  <React.Fragment key={l.level}>
                    <View style={{ alignItems: "center", gap: 4 }}>
                      <View
                        style={[
                          styles.roadmapNode,
                          {
                            backgroundColor: current
                              ? colors.primary
                              : done
                                ? colors.primaryDark
                                : colors.muted,
                            borderColor: current ? `${colors.primary}30` : "transparent",
                          },
                        ]}
                      >
                        {current ? (
                          <View style={styles.roadmapDot} />
                        ) : done ? (
                          <Ionicons name="checkmark" size={11} color={colors.white} />
                        ) : null}
                      </View>
                      <Text style={{ fontSize: 10 }}>{l.emoji}</Text>
                    </View>
                    {isLast ? null : (
                      <View
                        style={[
                          styles.roadmapLine,
                          {
                            backgroundColor:
                              levelNumber >= LEVELS[i + 1].level ? colors.primaryDark : colors.muted,
                          },
                        ]}
                      />
                    )}
                  </React.Fragment>
                );
              })}
            </View>
          </View>
        </Card>

        <Card>
          <Text style={[styles.kicker, { marginBottom: spacing.lg }]}>이번 달 활동</Text>
          <View style={styles.statRow}>
            {monthlyActivity.map((s) => (
              <View key={s.label} style={styles.stat}>
                <Text style={styles.statValue}>{s.value}</Text>
                <Text style={styles.statUnit}>{s.unit}</Text>
                <Text style={styles.statLabel}>{s.label}</Text>
              </View>
            ))}
          </View>
        </Card>

        <Card style={styles.listCard}>
          <Pressable
            onPress={() => setXpOpen((v) => !v)}
            accessibilityRole="button"
            accessibilityLabel="경험치 획득 내역"
            accessibilityState={{ expanded: xpOpen }}
            style={styles.listRow}
          >
            <Text style={styles.listLabel}>경험치 획득 내역</Text>
            <Ionicons
              name={xpOpen ? "chevron-up" : "chevron-down"}
              size={16}
              color={colors.mutedForeground}
            />
          </Pressable>

          {xpOpen
            ? xpEntries.map((x) => (
                <View key={x.key} style={[styles.listRow, styles.listRowDivided]}>
                  <View>
                    <Text style={styles.historyLabel}>{x.label}</Text>
                    <Text style={styles.historyDate}>{x.date}</Text>
                  </View>
                  <Text style={styles.historyXp}>{x.xp}</Text>
                </View>
              ))
            : null}
        </Card>

        <Card style={styles.listCard}>
          <Text style={[styles.kicker, styles.listKicker]}>설정</Text>

          <SettingsRow
            icon="settings-outline"
            label="앱 설정"
            onPress={() => elderNavigation.navigate("ElderAppSettings")}
          />

          <View style={[styles.listRow, styles.listRowDivided]}>
            <View style={styles.listLeft}>
              <Ionicons name="notifications-outline" size={16} color={colors.mutedForeground} />
              <Text style={styles.listLabel}>알림 설정</Text>
            </View>
            <Pressable
              onPress={toggleNotifications}
              accessibilityRole="switch"
              accessibilityLabel="알림 설정"
              accessibilityState={{ checked: notificationsOn }}
              style={[
                styles.switch,
                { backgroundColor: notificationsOn ? colors.primary : colors.muted },
              ]}
            >
              <View style={[styles.switchKnob, { left: notificationsOn ? 22 : 4 }]} />
            </Pressable>
          </View>

          <SettingsRow
            icon="lock-closed-outline"
            label="비밀번호 변경"
            onPress={() => elderNavigation.navigate("ElderPasswordChange")}
          />
          <SettingsRow
            icon="log-out-outline"
            label="로그아웃"
            onPress={() => void logout()}
          />
          <SettingsRow
            icon="trash-outline"
            label="계정 탈퇴"
            tone={colors.destructive}
            onPress={confirmWithdraw}
          />
        </Card>
      </ScrollView>

      <Modal
        visible={nameModalOpen}
        transparent
        animationType="fade"
        onRequestClose={() => {
          if (!nameSaving) setNameModalOpen(false);
        }}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>캐릭터 이름 변경</Text>
            <Text style={styles.modalDescription}>AI가 앞으로 이 이름으로 인사할게요.</Text>
            <TextInput
              value={nameDraft}
              onChangeText={setNameDraft}
              maxLength={20}
              autoFocus
              placeholder="캐릭터 이름"
              placeholderTextColor={colors.mutedForeground}
              style={styles.modalInput}
              accessibilityLabel="새 캐릭터 이름"
            />
            {nameError ? <Text style={styles.modalError}>{nameError}</Text> : null}
            <View style={styles.modalActions}>
              <Button
                label="취소"
                variant="ghost"
                disabled={nameSaving}
                onPress={() => setNameModalOpen(false)}
                style={{ flex: 1 }}
              />
              <Button
                label={nameSaving ? "저장 중" : "저장"}
                disabled={nameSaving || !nameDraft.trim()}
                onPress={() => void saveCharacterName()}
                style={{ flex: 1 }}
              />
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

function SettingsRow({
  icon,
  label,
  tone = colors.foreground,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  tone?: string;
  onPress?: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => [
        styles.listRow,
        styles.listRowDivided,
        { opacity: pressed && onPress ? 0.6 : 1 },
      ]}
    >
      <View style={styles.listLeft}>
        <Ionicons name={icon} size={16} color={tone === colors.destructive ? tone : colors.mutedForeground} />
        <Text style={[styles.listLabel, { color: tone }]}>{label}</Text>
      </View>
      {onPress ? <Ionicons name="chevron-forward" size={16} color={colors.mutedForeground} /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { padding: spacing.xl, gap: spacing.lg, paddingBottom: spacing.xxl },

  levelRow: { flexDirection: "row", alignItems: "center", gap: spacing.lg },
  levelBadge: {
    width: 72,
    height: 72,
    borderRadius: radius.xl,
    backgroundColor: colors.secondary,
    borderWidth: 2,
    borderColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    gap: 2,
  },
  levelBadgeLabel: { fontSize: 10, fontWeight: fontWeight.bold, color: colors.primaryDark },

  nameRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: 2 },
  characterName: { fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold, color: colors.foreground },
  levelPill: {
    backgroundColor: colors.secondary,
    borderRadius: radius.pill,
    paddingHorizontal: 7,
    paddingVertical: 1,
  },
  levelPillLabel: { fontSize: fontSize.badge, fontWeight: fontWeight.bold, color: colors.primary },
  characterDesc: { fontSize: fontSize.micro, color: colors.mutedForeground, lineHeight: 18 },

  xpRow: { flexDirection: "row", justifyContent: "space-between", marginBottom: 6 },
  xpLabel: { fontSize: fontSize.micro, fontWeight: fontWeight.semibold, color: colors.mutedForeground },
  xpValue: { fontSize: fontSize.micro, fontWeight: fontWeight.bold, color: colors.primary },
  xpNote: { fontSize: fontSize.micro, color: colors.mutedForeground, marginTop: 6 },
  xpNoteStrong: { fontWeight: fontWeight.bold, color: colors.primary },

  kicker: {
    fontSize: fontSize.badge,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
    letterSpacing: 0.6,
    marginBottom: 10,
  },
  roadmap: { flexDirection: "row", alignItems: "flex-start" },
  roadmapNode: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 3,
    alignItems: "center",
    justifyContent: "center",
  },
  roadmapDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.white },
  roadmapLine: { flex: 1, height: 2, borderRadius: 1, marginHorizontal: 2, marginTop: 11 },

  statRow: { flexDirection: "row", gap: spacing.md },
  stat: { flex: 1, backgroundColor: colors.secondary, borderRadius: radius.md, paddingVertical: spacing.md, alignItems: "center" },
  statValue: { fontSize: 22, fontWeight: fontWeight.bold, color: colors.primary },
  statUnit: { fontSize: fontSize.badge, color: colors.mutedForeground, marginTop: 2 },
  statLabel: { fontSize: 10, fontWeight: fontWeight.semibold, color: colors.primaryDark, marginTop: 3 },

  listCard: { padding: 0, overflow: "hidden" },
  listKicker: { paddingHorizontal: spacing.xl, paddingTop: spacing.lg, marginBottom: spacing.sm },
  listRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.lg,
  },
  listRowDivided: { borderTopWidth: 1, borderTopColor: colors.border },
  listLeft: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  listLabel: { fontSize: fontSize.bodyLg - 1, color: colors.foreground },

  historyLabel: { fontSize: fontSize.body, color: colors.foreground },
  historyDate: { fontSize: fontSize.badge, color: colors.mutedForeground, marginTop: 1 },
  historyXp: { fontSize: fontSize.body, fontWeight: fontWeight.bold, color: colors.primary },

  switch: { width: 44, height: 26, borderRadius: 13, justifyContent: "center" },
  switchKnob: {
    position: "absolute",
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.white,
  },

  modalBackdrop: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: spacing.xl,
    backgroundColor: "rgba(0,0,0,0.35)",
  },
  modalCard: {
    width: "100%",
    maxWidth: 420,
    backgroundColor: colors.background,
    borderRadius: radius.xl,
    padding: spacing.xl,
    gap: spacing.md,
  },
  modalTitle: { fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold, color: colors.foreground },
  modalDescription: { fontSize: fontSize.body, color: colors.mutedForeground },
  modalInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    fontSize: fontSize.bodyLg,
    color: colors.foreground,
    backgroundColor: colors.white,
  },
  modalError: { fontSize: fontSize.caption, color: colors.destructive },
  modalActions: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.sm },
});
