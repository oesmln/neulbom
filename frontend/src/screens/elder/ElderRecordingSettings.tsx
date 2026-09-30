import React from "react";
import { Modal, Pressable, StyleSheet, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import type { ElderMyPageNav } from "@/navigation/types";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";
import { Screen, ScreenHeader, SentenceText as Text } from "@/components/ui";
import { recordings } from "@/api";
import { ApiError, apiErrorMessage } from "@/api/errors";

type RecordingDialog =
  | { kind: "confirm" }
  | { kind: "result"; title: string; message: string }
  | null;

function RecordingDeletionSettings() {
  const [deleting, setDeleting] = React.useState(false);
  const [dialog, setDialog] = React.useState<RecordingDialog>(null);

  const deleteAllAudio = () => {
    if (deleting) return;
    setDeleting(true);
    void recordings
      .deleteAllAudio()
      .then(() =>
        setDialog({
          kind: "result",
          title: "삭제 완료",
          message: "서버의 녹음 원본을 삭제했어요.",
        }),
      )
      .catch((cause: ApiError) =>
        setDialog({
          kind: "result",
          title: "삭제하지 못했어요",
          message: `${apiErrorMessage(cause)}${
            cause.status === 0 || cause.status >= 500
              ? "\n삭제가 일부 진행됐을 수 있어요. 다시 시도하면 남은 원본을 삭제합니다."
              : ""
          }`,
        }),
      )
      .finally(() => setDeleting(false));
  };

  const confirmDeleteAudio = () => {
    if (deleting) return;
    setDialog({ kind: "confirm" });
  };

  const closeDialog = () => {
    if (!deleting) setDialog(null);
  };

  const isConfirmDialog = dialog?.kind === "confirm";

  return (
    <>
      <View style={styles.recordingCard}>
        <Text style={styles.groupTitle}>보관 및 삭제 안내</Text>
        <Text style={styles.recordingDescription}>
          서버 녹음 원본은 암호화해 보관하고 기본 30일 후 자동 삭제해요. 직접 삭제하면 원본 음성만 바로 삭제되고, 전사문·답변·분석·검사 기록은 남아요.
        </Text>
        <Pressable
          onPress={confirmDeleteAudio}
          accessibilityRole="button"
          accessibilityLabel="서버 녹음 원본 삭제"
          style={styles.deleteButton}
        >
          <Ionicons name="mic-off-outline" size={18} color={colors.destructive} />
          <Text style={styles.deleteButtonText}>서버 녹음 원본 삭제</Text>
        </Pressable>
      </View>

      <Modal
        visible={dialog !== null}
        transparent
        animationType="fade"
        onRequestClose={closeDialog}
      >
        <View style={styles.modalBackdrop}>
          <View
            style={styles.modalCard}
            accessibilityViewIsModal
            accessibilityRole="alert"
          >
            <Text style={styles.modalTitle}>
              {isConfirmDialog ? "녹음 원본을 정말 삭제하시겠어요?" : dialog?.title}
            </Text>
            <Text style={styles.modalDescription}>
              {isConfirmDialog
                ? "서버에 저장된 본인의 모든 녹음 원본을 지금 삭제합니다. 전사문·답변·분석·검사 기록은 남으며, 삭제한 음성은 복구할 수 없어요."
                : dialog?.message}
            </Text>
            {isConfirmDialog ? (
              <View style={styles.dialogActions}>
                <Pressable
                  onPress={closeDialog}
                  disabled={deleting}
                  accessibilityRole="button"
                  style={[styles.dialogButton, styles.cancelButton]}
                >
                  <Text style={styles.cancelButtonText}>취소</Text>
                </Pressable>
                <Pressable
                  onPress={deleteAllAudio}
                  disabled={deleting}
                  accessibilityRole="button"
                  accessibilityState={{ disabled: deleting, busy: deleting }}
                  style={[styles.dialogButton, styles.confirmButton, deleting && styles.confirmButtonDisabled]}
                >
                  <Text style={styles.confirmButtonText}>
                    {deleting ? "삭제 중…" : "삭제 확인"}
                  </Text>
                </Pressable>
              </View>
            ) : (
              <Pressable
                onPress={closeDialog}
                accessibilityRole="button"
                style={[styles.dialogButton, styles.resultButton]}
              >
                <Text style={styles.confirmButtonText}>확인</Text>
              </Pressable>
            )}
          </View>
        </View>
      </Modal>
    </>
  );
}

export default function ElderRecordingSettingsScreen() {
  const navigation = useNavigation<ElderMyPageNav>();
  return (
    <Screen header={<ScreenHeader title="녹음 원본 관리" onBack={() => navigation.goBack()} backLabel="마이페이지" />} contentStyle={{ width: "100%", maxWidth: 760, alignSelf: "center" }}>
      <RecordingDeletionSettings />
    </Screen>
  );
}

const styles = StyleSheet.create({
  recordingCard: {
    backgroundColor: colors.card,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
  },
  groupTitle: {
    fontSize: fontSize.badge,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
    letterSpacing: 0.7,
    marginBottom: spacing.md,
  },
  recordingDescription: {
    fontSize: fontSize.body,
    color: colors.mutedForeground,
    lineHeight: 22,
    marginBottom: spacing.md,
  },
  deleteButton: {
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    gap: spacing.sm,
    minHeight: 48,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.destructive,
  },
  deleteButtonText: {
    color: colors.destructive,
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
  },
  modalBackdrop: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: spacing.xl,
    backgroundColor: "rgba(20, 25, 24, 0.52)",
  },
  modalCard: {
    width: "100%",
    maxWidth: 420,
    padding: spacing.xl,
    borderRadius: radius.lg,
    backgroundColor: colors.card,
    elevation: 6,
  },
  modalTitle: {
    color: colors.foreground,
    fontSize: fontSize.cardTitle,
    fontWeight: fontWeight.semibold,
  },
  modalDescription: {
    marginTop: spacing.md,
    color: colors.mutedForeground,
    fontSize: fontSize.body,
    lineHeight: 23,
  },
  dialogActions: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: spacing.md,
    marginTop: spacing.xl,
  },
  dialogButton: {
    minHeight: 48,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    borderWidth: 1,
  },
  cancelButton: {
    flex: 1,
    borderColor: colors.border,
    backgroundColor: colors.card,
  },
  cancelButtonText: {
    color: colors.foreground,
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
  },
  confirmButton: {
    flex: 1,
    borderColor: colors.destructive,
    backgroundColor: colors.destructive,
  },
  confirmButtonDisabled: { opacity: 0.65 },
  confirmButtonText: {
    color: colors.white,
    fontSize: fontSize.body,
    fontWeight: fontWeight.semibold,
  },
  resultButton: {
    alignSelf: "flex-end",
    minWidth: 96,
    marginTop: spacing.xl,
    borderColor: colors.primary,
    backgroundColor: colors.primary,
  },
});
