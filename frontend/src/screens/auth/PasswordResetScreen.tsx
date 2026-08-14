import React from "react";
import { Pressable, ScrollView, StyleSheet, TextInput, View } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { auth } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import { Button, Card, ScreenHeader, SentenceText as Text } from "@/components/ui";
import type { RootNav, RootStackParamList } from "@/navigation/types";
import { colors, fontSize, fontWeight, radius, spacing, sizes } from "@/theme";

/** Opened by the one-time link in a password reset email. */
export default function PasswordResetScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "PasswordReset">>();
  const [resetToken, setResetToken] = React.useState(route.params?.token ?? "");
  const [password, setPassword] = React.useState("");
  const [confirmation, setConfirmation] = React.useState("");
  const [showPassword, setShowPassword] = React.useState(false);
  const [showConfirmation, setShowConfirmation] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);
  const [completed, setCompleted] = React.useState(false);

  const passwordsMatch = password.length === 0 || password === confirmation;
  const canSubmit = resetToken.trim().length > 0 && password.length >= 8 && password === confirmation;

  const submit = async () => {
    if (!canSubmit || busy) return;
    setBusy(true);
    setMessage(null);
    try {
      await auth.confirmPasswordReset(resetToken.trim(), password);
      setCompleted(true);
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  if (completed) {
    return (
      <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
        <ScreenHeader title="비밀번호가 변경됐어요" subtitle="새 비밀번호로 다시 로그인해 주세요." />
        <View style={styles.completedBody}>
          <Card style={styles.successCard}>
            <Ionicons name="checkmark-circle" size={56} color={colors.primary} />
            <Text style={styles.successTitle}>변경이 완료됐어요</Text>
            <Text style={styles.successBody}>이제 새 비밀번호로 늘봄을 이용할 수 있어요.</Text>
          </Card>
          <Button
            label="로그인하러 가기"
            size="lg"
            onPress={() => navigation.reset({ index: 0, routes: [{ name: "Login", params: { mode: "login" } }] })}
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader
        title="비밀번호 재설정"
        subtitle="새로 사용할 비밀번호를 입력해 주세요."
        onBack={() => navigation.reset({ index: 0, routes: [{ name: "Login", params: { mode: "login" } }] })}
        backLabel="로그인"
      />
      <ScrollView contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
        {!route.params?.token ? (
          <Field label="재설정 코드">
            <TextInput
              value={resetToken}
              onChangeText={setResetToken}
              placeholder="메일의 링크 또는 코드를 입력해 주세요"
              placeholderTextColor={colors.mutedForeground}
              autoCapitalize="none"
              autoCorrect={false}
              accessibilityLabel="비밀번호 재설정 코드"
              style={styles.input}
            />
          </Field>
        ) : null}

        <Field label="새 비밀번호">
          <View>
            <TextInput
              value={password}
              onChangeText={setPassword}
              placeholder="8자 이상 입력해 주세요"
              placeholderTextColor={colors.mutedForeground}
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              accessibilityLabel="새 비밀번호"
              style={[styles.input, styles.passwordInput]}
            />
            <Pressable
              onPress={() => setShowPassword((current) => !current)}
              accessibilityRole="button"
              accessibilityLabel={showPassword ? "새 비밀번호 숨기기" : "새 비밀번호 표시"}
              hitSlop={10}
              style={styles.eye}
            >
              <Ionicons name={showPassword ? "eye-off-outline" : "eye-outline"} size={20} color={colors.mutedForeground} />
            </Pressable>
          </View>
        </Field>

        <Field label="새 비밀번호 확인">
          <View>
            <TextInput
              value={confirmation}
              onChangeText={setConfirmation}
              placeholder="새 비밀번호를 다시 입력해 주세요"
              placeholderTextColor={colors.mutedForeground}
              secureTextEntry={!showConfirmation}
              autoCapitalize="none"
              accessibilityLabel="새 비밀번호 확인"
              style={[styles.input, styles.passwordInput, confirmation.length > 0 && !passwordsMatch && styles.invalidInput]}
            />
            <Pressable
              onPress={() => setShowConfirmation((current) => !current)}
              accessibilityRole="button"
              accessibilityLabel={showConfirmation ? "새 비밀번호 확인 숨기기" : "새 비밀번호 확인 표시"}
              hitSlop={10}
              style={styles.eye}
            >
              <Ionicons name={showConfirmation ? "eye-off-outline" : "eye-outline"} size={20} color={colors.mutedForeground} />
            </Pressable>
          </View>
          {confirmation.length > 0 && !passwordsMatch ? <Text style={styles.errorText}>비밀번호가 서로 일치하지 않아요.</Text> : null}
        </Field>

        {message ? <Text style={styles.errorText}>{message}</Text> : null}
        <Button label={busy ? "변경하고 있어요" : "비밀번호 변경하기"} disabled={!canSubmit || busy} onPress={() => void submit()} size="lg" />
      </ScrollView>
    </SafeAreaView>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { flexGrow: 1, padding: spacing.xl, gap: spacing.lg },
  completedBody: { flex: 1, padding: spacing.xl, justifyContent: "center", gap: spacing.xl },
  successCard: { alignItems: "center", gap: spacing.md, paddingVertical: spacing.xxl },
  successTitle: { color: colors.foreground, fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold },
  successBody: { color: colors.mutedForeground, fontSize: fontSize.body, textAlign: "center" },
  field: { gap: spacing.sm },
  label: { color: colors.foreground, fontSize: fontSize.body, fontWeight: fontWeight.semibold },
  input: {
    minHeight: sizes.inputHeight,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    backgroundColor: colors.muted,
    paddingHorizontal: spacing.lg,
    color: colors.foreground,
    fontSize: fontSize.body,
  },
  passwordInput: { paddingRight: 52 },
  invalidInput: { borderColor: colors.destructive },
  eye: { position: "absolute", right: spacing.lg, top: 0, bottom: 0, justifyContent: "center" },
  errorText: { color: colors.destructive, fontSize: fontSize.caption, lineHeight: 20 },
});
