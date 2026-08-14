import React from "react";
import { ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { auth } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import { Button, Card, ScreenHeader } from "@/components/ui";
import type { RootNav, RootStackParamList } from "@/navigation/types";
import { colors, fontSize, fontWeight, radius, spacing, sizes } from "@/theme";

/** Opened by the one-time link in an email verification message. */
export default function EmailVerificationLinkScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "EmailVerificationLink">>();
  const [token, setToken] = React.useState(route.params?.token ?? "");
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);
  const [completed, setCompleted] = React.useState(false);

  const confirm = async () => {
    if (!token.trim() || busy) return;
    setBusy(true);
    setMessage(null);
    try {
      await auth.confirmEmailVerification(token.trim());
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
        <ScreenHeader title="이메일 인증 완료" subtitle="이제 로그인해서 늘봄을 시작해 주세요." />
        <View style={styles.completedBody}>
          <Card style={styles.successCard}>
            <Ionicons name="checkmark-circle" size={56} color={colors.primary} />
            <Text style={styles.successTitle}>인증이 완료됐어요</Text>
            <Text style={styles.successBody}>가입하신 이메일로 로그인할 수 있어요.</Text>
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
        title="이메일 인증"
        subtitle="메일의 인증 링크를 확인하고 가입을 완료해 주세요."
        onBack={() => navigation.reset({ index: 0, routes: [{ name: "Login", params: { mode: "login" } }] })}
        backLabel="로그인"
      />
      <ScrollView contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
        {!route.params?.token ? (
          <View style={styles.field}>
            <Text style={styles.label}>인증 코드</Text>
            <TextInput
              value={token}
              onChangeText={setToken}
              placeholder="메일의 인증 코드를 입력해 주세요"
              placeholderTextColor={colors.mutedForeground}
              autoCapitalize="none"
              autoCorrect={false}
              accessibilityLabel="이메일 인증 코드"
              style={styles.input}
            />
          </View>
        ) : null}
        {message ? <Text style={styles.errorText}>{message}</Text> : null}
        <Button label={busy ? "인증하고 있어요" : "이메일 인증하기"} disabled={!token.trim() || busy} onPress={() => void confirm()} size="lg" />
      </ScrollView>
    </SafeAreaView>
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
  errorText: { color: colors.destructive, fontSize: fontSize.caption, lineHeight: 20 },
});
