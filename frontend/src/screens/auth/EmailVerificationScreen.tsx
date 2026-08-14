import React from "react";
import { ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";

import { auth, guardian } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import { Button, Card, ScreenHeader } from "@/components/ui";
import type { RootNav, RootStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { saveRequiredSignupConsents } from "@/screens/auth/signupConsents";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

/**
 * Registration is intentionally paused here when the backend requires email
 * verification. Passwords and the optional invite code stay in memory only;
 * after the one-time token is accepted we sign in and continue the same role
 * routing used by the ordinary login screen.
 */
export default function EmailVerificationScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "EmailVerification">>();
  const { signIn } = useApp();
  const signup = route.params.signup;
  const [token, setToken] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [resending, setResending] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);
  const [sentMessage, setSentMessage] = React.useState<string | null>(null);

  const sendVerificationMail = async () => {
    setResending(true);
    setMessage(null);
    try {
      await auth.requestEmailVerification(signup.email);
      setSentMessage("인증 메일을 다시 보냈어요. 메일함을 확인해 주세요.");
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setResending(false);
    }
  };

  React.useEffect(() => {
    void sendVerificationMail();
    // The signup route is immutable while this screen is mounted.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const confirm = async () => {
    const trimmed = token.trim();
    if (!trimmed || busy) return;
    setBusy(true);
    setMessage(null);
    try {
      await auth.confirmEmailVerification(trimmed);
      const tokens = await auth.login({ email: signup.email, password: signup.password });
      await signIn(tokens);
      await saveRequiredSignupConsents(tokens.user_id);
      if (signup.inviteCode && tokens.role === "elder") {
        await guardian.acceptInvitation(signup.inviteCode, true);
      }

      let inviteCode: string | undefined;
      let invitationError: string | undefined;
      if (tokens.role === "guardian") {
        try {
          const invitation = await guardian.createInvitation({
            relation: "보호자",
            access_scope: ["screening", "summary", "diary", "activity"],
            expires_in: 600,
          });
          inviteCode = invitation.invite_code;
        } catch (invitationCause) {
          invitationError = apiErrorMessage(invitationCause);
        }
      }

      navigation.reset({
        index: 0,
        routes: [{ name: "SignupComplete", params: { role: tokens.role, inviteCode, invitationError } }],
      });
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader
        title="이메일을 확인해 주세요"
        subtitle="가입을 마치려면 이메일 인증이 필요해요."
        onBack={() => navigation.goBack()}
        backLabel="회원가입"
      />
      <ScrollView contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
        <Card style={styles.card}>
          <Text style={styles.eyebrow}>인증 메일을 보냈어요</Text>
          <Text style={styles.title}>{signup.email}</Text>
          <Text style={styles.description}>
            메일에 있는 인증 코드를 입력하면 계정이 활성화돼요. 메일이 보이지 않으면 스팸함도 확인해 주세요.
          </Text>
        </Card>

        <View style={styles.fieldGroup}>
          <Text style={styles.label}>인증 코드</Text>
          <TextInput
            value={token}
            onChangeText={setToken}
            placeholder="이메일의 인증 코드를 입력해 주세요"
            placeholderTextColor={colors.mutedForeground}
            autoCapitalize="none"
            autoCorrect={false}
            accessibilityLabel="이메일 인증 코드 입력"
            style={styles.input}
          />
        </View>

        {message ? <Text style={styles.errorText}>{message}</Text> : null}
        {sentMessage ? <Text style={styles.successText}>{sentMessage}</Text> : null}

        <Button
          label={busy ? "확인하고 있어요" : "이메일 인증하고 시작하기"}
          disabled={!token.trim() || busy}
          onPress={() => void confirm()}
          size="lg"
        />
        <Button
          label={resending ? "다시 보내고 있어요" : "인증 메일 다시 보내기"}
          variant="outline"
          disabled={resending || busy}
          onPress={() => void sendVerificationMail()}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { flexGrow: 1, padding: spacing.xl, gap: spacing.lg },
  card: { gap: spacing.sm, borderRadius: radius.xl },
  eyebrow: { color: colors.primary, fontSize: fontSize.caption, fontWeight: fontWeight.bold },
  title: { color: colors.foreground, fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold },
  description: { color: colors.mutedForeground, fontSize: fontSize.body, lineHeight: 23 },
  fieldGroup: { gap: spacing.sm },
  label: { color: colors.foreground, fontSize: fontSize.body, fontWeight: fontWeight.semibold },
  input: {
    minHeight: 56,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radius.lg,
    backgroundColor: colors.white,
    paddingHorizontal: spacing.lg,
    color: colors.foreground,
    fontSize: fontSize.body,
  },
  errorText: { color: colors.destructive, fontSize: fontSize.caption, lineHeight: 20 },
  successText: { color: colors.primaryDark, fontSize: fontSize.caption, lineHeight: 20 },
});
