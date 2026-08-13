import React from "react";
import { View, Text, StyleSheet, Pressable, TextInput } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { auth } from "@/api";
import { ApiError, apiErrorMessage } from "@/api/errors";
import { colors, spacing, radius, fontSize, fontWeight, sizes } from "@/theme";
import { Screen, ScreenHeader, Body, Caption } from "@/components/ui";

/**
 * 비밀번호 변경 — `PATCH /users/me/password`.
 *
 * The three rules below are checked as you type and mirrored in the checklist,
 * so the disabled submit button always has a visible reason. `current_password`
 * is sent as-is; a wrong one comes back 401/422 and is surfaced inline rather
 * than bouncing the user to the login screen.
 */
const MIN_LENGTH = 8;

function Field({
  label,
  value,
  onChangeText,
  placeholder,
  borderColor,
}: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  placeholder: string;
  borderColor: string;
}) {
  const [visible, setVisible] = React.useState(false);

  return (
    <View style={{ gap: spacing.sm }}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <View style={[styles.field, { borderColor }]}>
        <TextInput
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor={colors.mutedForeground}
          secureTextEntry={!visible}
          autoCapitalize="none"
          style={styles.input}
          accessibilityLabel={label}
        />
        <Pressable
          onPress={() => setVisible((v) => !v)}
          accessibilityRole="button"
          accessibilityLabel={visible ? `${label} 가리기` : `${label} 보기`}
          hitSlop={8}
        >
          <Ionicons
            name={visible ? "eye-off-outline" : "eye-outline"}
            size={20}
            color={colors.mutedForeground}
          />
        </Pressable>
      </View>
    </View>
  );
}

export default function ElderPasswordChangeScreen() {
  const navigation = useNavigation();
  const [current, setCurrent] = React.useState("");
  const [next, setNext] = React.useState("");
  const [confirm, setConfirm] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);
  const [done, setDone] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const currentOk = current.length > 0;
  const lengthOk = next.length >= MIN_LENGTH;
  const matchOk = next.length > 0 && next === confirm;
  const canSubmit = currentOk && lengthOk && matchOk && !submitting;

  const submit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await auth.changePassword({ current_password: current, new_password: next });
      setDone(true);
    } catch (cause) {
      setError(
        cause instanceof ApiError && (cause.status === 401 || cause.status === 422)
          ? "현재 비밀번호가 맞지 않아요."
          : apiErrorMessage(cause as ApiError),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const header = (
    <ScreenHeader
      onBack={() => navigation.goBack()}
      backLabel="마이페이지"
      title="비밀번호 변경"
      subtitle={done ? undefined : "보안을 위해 현재 비밀번호를 먼저 확인해요"}
    />
  );

  if (done) {
    return (
      <Screen header={header}>
        <View style={styles.doneBox}>
          <View style={styles.doneIcon}>
            <Ionicons name="checkmark-circle" size={40} color={colors.success} />
          </View>
          <Text style={styles.doneTitle}>변경 완료!</Text>
          <Body style={styles.doneMessage}>
            {"비밀번호가 성공적으로 변경되었어요.\n새 비밀번호로 로그인해 주세요."}
          </Body>
          <Pressable
            onPress={() => navigation.goBack()}
            accessibilityRole="button"
            accessibilityLabel="마이페이지로 돌아가기"
            style={styles.submit}
          >
            <Text style={styles.submitLabel}>마이페이지로 돌아가기</Text>
          </Pressable>
        </View>
      </Screen>
    );
  }

  const rules = [
    { ok: currentOk, text: "현재 비밀번호 입력" },
    { ok: lengthOk, text: `새 비밀번호 ${MIN_LENGTH}자 이상` },
    { ok: matchOk, text: "비밀번호 일치" },
  ];

  return (
    <Screen header={header}>
      <View style={{ gap: spacing.xl }}>
        <Field
          label="현재 비밀번호"
          value={current}
          onChangeText={setCurrent}
          placeholder="현재 비밀번호 입력"
          borderColor={currentOk ? colors.primary : colors.border}
        />
        <Field
          label="새 비밀번호"
          value={next}
          onChangeText={setNext}
          placeholder={`새 비밀번호 (${MIN_LENGTH}자 이상)`}
          borderColor={
            next.length === 0 ? colors.border : lengthOk ? colors.primary : colors.destructive
          }
        />
        <Field
          label="비밀번호 확인"
          value={confirm}
          onChangeText={setConfirm}
          placeholder="새 비밀번호 재입력"
          borderColor={
            confirm.length === 0 ? colors.border : matchOk ? colors.primary : colors.destructive
          }
        />

        <View style={styles.rules}>
          {rules.map((rule) => (
            <View key={rule.text} style={styles.ruleRow}>
              <View
                style={[
                  styles.ruleDot,
                  { backgroundColor: rule.ok ? colors.success : "rgba(45,49,50,0.15)" },
                ]}
              >
                {rule.ok ? <Ionicons name="checkmark" size={12} color={colors.white} /> : null}
              </View>
              <Text
                style={[
                  styles.ruleText,
                  { color: rule.ok ? colors.foreground : colors.mutedForeground },
                ]}
              >
                {rule.text}
              </Text>
            </View>
          ))}
        </View>

        {error ? <Caption style={{ color: colors.destructive }}>{error}</Caption> : null}

        <Pressable
          onPress={() => void submit()}
          disabled={!canSubmit}
          accessibilityRole="button"
          accessibilityLabel="비밀번호 변경하기"
          accessibilityState={{ disabled: !canSubmit }}
          style={[
            styles.submit,
            { backgroundColor: canSubmit ? colors.primary : colors.muted },
          ]}
        >
          <Text
            style={[
              styles.submitLabel,
              { color: canSubmit ? colors.white : colors.mutedForeground },
            ]}
          >
            {submitting ? "변경 중이에요" : "비밀번호 변경하기"}
          </Text>
        </Pressable>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  fieldLabel: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  field: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    height: sizes.inputHeight,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    borderWidth: 1.5,
    backgroundColor: colors.background,
  },
  input: { flex: 1, fontSize: fontSize.bodyLg, color: colors.foreground },

  rules: {
    gap: spacing.md,
    padding: spacing.lg,
    borderRadius: radius.md,
    backgroundColor: colors.muted,
  },
  ruleRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  ruleDot: { width: 20, height: 20, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  ruleText: { fontSize: fontSize.caption },

  submit: {
    height: sizes.buttonHeight,
    borderRadius: radius.xl,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primary,
  },
  submitLabel: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.white },

  doneBox: { alignItems: "center", justifyContent: "center", gap: spacing.xl, paddingVertical: 56 },
  doneIcon: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: colors.secondary,
    alignItems: "center",
    justifyContent: "center",
  },
  doneTitle: { fontSize: 22, fontWeight: fontWeight.bold, color: colors.foreground },
  doneMessage: { textAlign: "center", lineHeight: 26 },
});
