import React from "react";
import { Modal, Pressable, StyleSheet, View } from "react-native";
import { useNavigation } from "@react-navigation/native";

import { auth } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import AccountActionsView from "@/components/AccountActionsView";
import { SentenceText as Text } from "@/components/ui";
import type { GuardianNav, RootNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

/** 계정 기능은 보호자도 고령자와 같은 API 흐름을 사용한다. */
export default function GuardianAccountActions() {
  const guardianNavigation = useNavigation<GuardianNav>();
  const rootNavigation = useNavigation<RootNav>();
  const { signOut } = useApp();
  const [withdrawOpen, setWithdrawOpen] = React.useState(false);
  const [withdrawSubmitting, setWithdrawSubmitting] = React.useState(false);
  const [withdrawError, setWithdrawError] = React.useState<string | null>(null);

  const resetToLogin = () => {
    rootNavigation.reset({ index: 0, routes: [{ name: "Login" }] });
  };

  const logout = async () => {
    await signOut();
    resetToLogin();
  };

  const openWithdrawConfirmation = () => {
    setWithdrawError(null);
    setWithdrawOpen(true);
  };

  const withdraw = async () => {
    if (withdrawSubmitting) return;
    setWithdrawSubmitting(true);
    setWithdrawError(null);
    try {
      await auth.withdraw();
      await signOut();
      resetToLogin();
    } catch (cause) {
      setWithdrawError(apiErrorMessage(cause));
      setWithdrawSubmitting(false);
    }
  };

  return (
    <>
      <AccountActionsView
        onPasswordChange={() => guardianNavigation.navigate("GuardianPasswordChange")}
        onLogout={() => void logout()}
        onWithdraw={openWithdrawConfirmation}
      />

      <Modal
        visible={withdrawOpen}
        transparent
        animationType="fade"
        onRequestClose={() => {
          if (!withdrawSubmitting) setWithdrawOpen(false);
        }}
      >
        <View style={styles.backdrop}>
          <View style={styles.dialog}>
            <Text style={styles.title}>계정을 탈퇴하시겠어요?</Text>
            <Text style={styles.message}>
              그동안의 대화, 일기, 검사 기록이 모두 삭제되고 되돌릴 수 없어요.
            </Text>
            {withdrawError ? <Text style={styles.error}>{withdrawError}</Text> : null}
            <View style={styles.actions}>
              <Pressable
                onPress={() => setWithdrawOpen(false)}
                disabled={withdrawSubmitting}
                accessibilityRole="button"
                accessibilityLabel="취소"
                style={[styles.action, styles.cancelAction]}
              >
                <Text style={styles.cancelLabel}>취소</Text>
              </Pressable>
              <Pressable
                onPress={() => void withdraw()}
                disabled={withdrawSubmitting}
                accessibilityRole="button"
                accessibilityLabel="탈퇴하기"
                style={[styles.action, styles.withdrawAction]}
              >
                <Text style={styles.withdrawLabel}>
                  {withdrawSubmitting ? "탈퇴 중이에요" : "탈퇴하기"}
                </Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: spacing.xl,
    backgroundColor: "rgba(0,0,0,0.35)",
  },
  dialog: {
    width: "100%",
    maxWidth: 420,
    gap: spacing.md,
    padding: spacing.xl,
    borderRadius: radius.xl,
    backgroundColor: colors.background,
  },
  title: { fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold, color: colors.foreground },
  message: { fontSize: fontSize.body, lineHeight: 23, color: colors.mutedForeground },
  error: { fontSize: fontSize.caption, color: colors.destructive },
  actions: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.sm },
  action: {
    flex: 1,
    minHeight: 46,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.md,
    borderWidth: 1,
  },
  cancelAction: { borderColor: colors.border, backgroundColor: colors.card },
  withdrawAction: { borderColor: colors.destructive, backgroundColor: colors.destructive },
  cancelLabel: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  withdrawLabel: {
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
    color: colors.destructiveForeground,
  },
});
