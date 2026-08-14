import React from "react";
import { View, Text, TextInput, Pressable, StyleSheet, ScrollView } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";

import { RootNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { auth, guardian } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import type { AuthTokenResponse } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight, sizes } from "@/theme";
import { Button, ScreenHeader } from "@/components/ui";

/**
 * Sign-in, sign-up, invite code and password reset.
 *
 * These are four states of one flow rather than four routes: the invite step
 * has to hand its code to the account that sign-up is about to create, and
 * api-spec 3.1 requires the app to hold the form values until the user type is
 * picked and only then POST /auth/register once.
 *
 * Signing in is a different path on purpose. `POST /auth/login` already returns
 * `role`, so an existing user goes straight to their own area — the invite code
 * and the role picker belong to registration and must not reappear at every
 * sign-in.
 */
type Step = "form" | "invite" | "socialRole" | "forgot" | "forgotSent";
type Tab = "login" | "signup";
type SocialProvider = "kakao" | "naver";

WebBrowser.maybeCompleteAuthSession();

const KAKAO_CLIENT_ID = process.env.EXPO_PUBLIC_KAKAO_CLIENT_ID?.trim() ?? "";
const NAVER_CLIENT_ID = process.env.EXPO_PUBLIC_NAVER_CLIENT_ID?.trim() ?? "";
const KAKAO_REDIRECT_URI = process.env.EXPO_PUBLIC_KAKAO_REDIRECT_URI?.trim() ?? "";
const NAVER_REDIRECT_URI = process.env.EXPO_PUBLIC_NAVER_REDIRECT_URI?.trim() ?? "";
const KAKAO_REQUEST_REDIRECT_URI =
  KAKAO_REDIRECT_URI || AuthSession.makeRedirectUri({ scheme: "memocare", path: "auth/callback/kakao" });
const NAVER_REQUEST_REDIRECT_URI =
  NAVER_REDIRECT_URI || AuthSession.makeRedirectUri({ scheme: "memocare", path: "auth/callback/naver" });

const SOCIAL_CONFIG = {
  kakao: {
    label: "카카오",
    clientId: KAKAO_CLIENT_ID,
    redirectUri: KAKAO_REDIRECT_URI,
    discovery: {
      authorizationEndpoint:
        process.env.EXPO_PUBLIC_KAKAO_AUTHORIZATION_ENDPOINT?.trim() ||
        "https://kauth.kakao.com/oauth/authorize",
    },
  },
  naver: {
    label: "네이버",
    clientId: NAVER_CLIENT_ID,
    redirectUri: NAVER_REDIRECT_URI,
    discovery: {
      authorizationEndpoint:
        process.env.EXPO_PUBLIC_NAVER_AUTHORIZATION_ENDPOINT?.trim() ||
        "https://nid.naver.com/oauth2.0/authorize",
    },
  },
} as const;

const CODE_LENGTH = 6;
const KEYPAD = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "←"] as const;

export default function LoginScreen() {
  const navigation = useNavigation<RootNav>();
  const { signIn } = useApp();

  const [step, setStep] = React.useState<Step>("form");
  const [tab, setTab] = React.useState<Tab>("login");
  const [showPassword, setShowPassword] = React.useState(false);
  const [code, setCode] = React.useState("");
  const [name, setName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [passwordConfirmation, setPasswordConfirmation] = React.useState("");
  const [showPasswordConfirmation, setShowPasswordConfirmation] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [socialProvider, setSocialProvider] = React.useState<SocialProvider | null>(null);
  const [message, setMessage] = React.useState<string | null>(null);
  const [codeError, setCodeError] = React.useState<string | null>(null);

  const routeAfterAuth = (tokens: AuthTokenResponse) => {
    if (tokens.role === "guardian") return "Guardian" as const;
    return (tokens.onboarding_completed ?? tokens.profile_completed) ? "Elder" as const : "Onboarding" as const;
  };

  const emailLooksValid = email.includes("@") && email.includes(".");
  const passwordMatches = tab === "login" || password === passwordConfirmation;
  const canSubmitForm =
    emailLooksValid &&
    password.length >= 8 &&
    passwordMatches &&
    (tab === "login" || name.trim().length > 0);

  const [kakaoRequest, , promptKakao] = AuthSession.useAuthRequest(
    {
      clientId: KAKAO_CLIENT_ID || "not-configured",
      redirectUri: KAKAO_REQUEST_REDIRECT_URI,
      responseType: AuthSession.ResponseType.Code,
      usePKCE: false,
    },
    SOCIAL_CONFIG.kakao.discovery,
  );
  const [naverRequest, , promptNaver] = AuthSession.useAuthRequest(
    {
      clientId: NAVER_CLIENT_ID || "not-configured",
      redirectUri: NAVER_REQUEST_REDIRECT_URI,
      responseType: AuthSession.ResponseType.Code,
      usePKCE: false,
    },
    SOCIAL_CONFIG.naver.discovery,
  );

  /** Existing account: the token says where to go. */
  const submitLogin = async () => {
    setBusy(true);
    setMessage(null);
    try {
      const tokens = await auth.login({ email, password });
      await signIn(tokens);
      navigation.reset({
        index: 0,
        routes: [
          {
            name: routeAfterAuth(tokens),
          },
        ],
      });
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  /** New account: hold the form and let the role choice create it. */
  const continueToInvite = () => {
    setMessage(null);
    setCode("");
    setCodeError(null);
    setStep("invite");
  };

  const goToUserType = (inviteCode?: string) => {
    navigation.navigate("UserType", {
      signup: { name: name.trim(), email, password, inviteCode },
    });
  };

  const submitInviteCode = async () => {
    setBusy(true);
    setCodeError(null);
    try {
      await guardian.verifyInvitation(code);
      goToUserType(code);
    } catch (cause) {
      setCodeError(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const sendResetLink = async () => {
    setBusy(true);
    setMessage(null);
    try {
      await auth.requestPasswordReset(email);
      setStep("forgotSent");
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const chooseSocialRole = (provider: SocialProvider) => {
    const config = SOCIAL_CONFIG[provider];
    setMessage(null);
    if (!config.clientId || !config.redirectUri) {
      setMessage(`${config.label} 로그인 설정이 아직 준비되지 않았어요.`);
      return;
    }
    if (!/^https?:\/\//i.test(config.redirectUri)) {
      setMessage(`${config.label} redirect URI는 등록된 HTTP 또는 HTTPS 주소여야 해요.`);
      return;
    }
    setSocialProvider(provider);
    setStep("socialRole");
  };

  const submitSocialLogin = async (role: "elder" | "guardian") => {
    if (!socialProvider || busy) return;
    const config = SOCIAL_CONFIG[socialProvider];
    const request = socialProvider === "kakao" ? kakaoRequest : naverRequest;
    const prompt = socialProvider === "kakao" ? promptKakao : promptNaver;
    if (!request) {
      setMessage("소셜 로그인을 준비하고 있어요. 잠시 후 다시 시도해 주세요.");
      return;
    }

    setBusy(true);
    setMessage(null);
    try {
      const result = await prompt();
      if (result.type === "cancel" || result.type === "dismiss") {
        setMessage(`${config.label} 로그인을 취소했어요.`);
        return;
      }
      if (result.type !== "success") {
        setMessage(`${config.label} 인증을 완료하지 못했어요. 다시 시도해 주세요.`);
        return;
      }
      if (!request.state || result.params.state !== request.state) {
        setMessage("로그인 요청을 확인할 수 없어요. 처음부터 다시 시도해 주세요.");
        return;
      }
      const authorizationCode = result.params.code;
      if (!authorizationCode) {
        setMessage(`${config.label}에서 로그인 코드를 받지 못했어요.`);
        return;
      }

      const tokens = await auth.oauthLogin(socialProvider, {
        authorization_code: authorizationCode,
        redirect_uri: config.redirectUri,
        role,
        state: request.state,
      });
      await signIn(tokens);
      navigation.reset({
        index: 0,
        routes: [{ name: routeAfterAuth(tokens) }],
      });
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  if (step === "invite") {
    return (
      <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
        <ScreenHeader
          title="초대 코드 입력"
          subtitle="보호자 또는 기관에서 받은 6자리 코드를 입력해 주세요."
          onBack={() => setStep("form")}
          backLabel="뒤로"
        />

        <View style={styles.body}>
          <View style={styles.codeRow}>
            {Array.from({ length: CODE_LENGTH }).map((_, i) => {
              const filled = code.length > i;
              return (
                <View
                  key={i}
                  style={[
                    styles.codeCell,
                    {
                      borderColor: filled ? colors.primary : colors.border,
                      backgroundColor: filled ? colors.secondary : colors.muted,
                    },
                  ]}
                >
                  <Text style={styles.codeText}>{code[i] ?? ""}</Text>
                </View>
              );
            })}
          </View>

          <View style={styles.keypad}>
            {KEYPAD.map((key, i) => (
              <Pressable
                key={i}
                disabled={key === ""}
                accessibilityRole="button"
                accessibilityLabel={key === "←" ? "한 자리 지우기" : key}
                onPress={() => {
                  if (key === "←") setCode((c) => c.slice(0, -1));
                  else if (key !== "" && code.length < CODE_LENGTH) setCode((c) => c + key);
                }}
                style={({ pressed }) => [
                  styles.key,
                  {
                    backgroundColor: key === "" ? "transparent" : colors.muted,
                    opacity: pressed ? 0.7 : 1,
                  },
                ]}
              >
                <Text style={styles.keyText}>{key}</Text>
              </Pressable>
            ))}
          </View>

          <View style={styles.bottomStack}>
            {codeError ? <Text style={styles.errorText}>{codeError}</Text> : null}
            <Button
              label="확인"
              disabled={code.length < CODE_LENGTH || busy}
              onPress={() => void submitInviteCode()}
            />
            <Pressable
              onPress={() => goToUserType()}
              accessibilityRole="button"
              accessibilityLabel="초대 코드 없이 계속하기"
              style={styles.textLink}
            >
              <Text style={styles.textLinkLabel}>초대 코드 없이 계속하기</Text>
            </Pressable>
          </View>
        </View>
      </SafeAreaView>
    );
  }

  if (step === "socialRole" && socialProvider) {
    const provider = SOCIAL_CONFIG[socialProvider];
    const requestReady = socialProvider === "kakao" ? !!kakaoRequest : !!naverRequest;
    return (
      <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
        <ScreenHeader
          title={`${provider.label} 로그인`}
          subtitle="처음 이용할 때 사용할 역할을 선택해 주세요. 기존 계정은 저장된 역할로 로그인돼요."
          onBack={() => {
            setStep("form");
            setSocialProvider(null);
            setMessage(null);
          }}
          backLabel="로그인"
        />

        <View style={[styles.body, styles.socialRoleBody]}>
          <View style={{ gap: spacing.md }}>
            <SocialRoleCard
              title="어르신으로 이용"
              description="인지 검사, AI 문답, 일기와 두뇌 게임을 이용해요."
              icon="heart-outline"
              onPress={() => void submitSocialLogin("elder")}
              disabled={busy || !requestReady}
            />
            <SocialRoleCard
              title="보호자로 이용"
              description="연결된 어르신의 활동과 인지 상태를 확인해요."
              icon="people-outline"
              onPress={() => void submitSocialLogin("guardian")}
              disabled={busy || !requestReady}
            />
          </View>
          {message ? <Text style={styles.errorText}>{message}</Text> : null}
          {!requestReady ? <Text style={styles.hint}>인증 화면을 준비하고 있어요.</Text> : null}
        </View>
      </SafeAreaView>
    );
  }

  if (step === "forgotSent") {
    return (
      <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
        <ScreenHeader title="비밀번호 찾기" onBack={() => setStep("form")} backLabel="로그인" />

        <View style={[styles.body, { alignItems: "center", justifyContent: "center", gap: spacing.xl }]}>
          <View style={styles.successCircle}>
            <Ionicons name="checkmark-circle" size={40} color={colors.primary} />
          </View>

          <View style={{ alignItems: "center", gap: spacing.sm }}>
            <Text style={styles.successTitle}>이메일을 보냈어요</Text>
            <Text style={styles.successBody}>
              <Text style={styles.successEmail}>{email}</Text>으로{"\n"}
              비밀번호 재설정 링크를 전송했어요.{"\n"}
              메일함을 확인해 주세요.
            </Text>
          </View>

          <View style={styles.noteBox}>
            <Text style={styles.noteText}>
              메일이 오지 않았다면 스팸함을 확인하거나, 이메일 주소가 정확한지 다시 확인해 주세요.
            </Text>
          </View>

          <View style={{ alignSelf: "stretch", gap: spacing.md }}>
            <Button label="다시 보내기" variant="outline" onPress={() => setStep("forgot")} />
            <Button label="로그인으로 돌아가기" onPress={() => setStep("form")} />
          </View>
        </View>
      </SafeAreaView>
    );
  }

  if (step === "forgot") {
    return (
      <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
        <ScreenHeader
          title="비밀번호 찾기"
          subtitle="가입하신 이메일로 재설정 링크를 보내드려요."
          onBack={() => setStep("form")}
          backLabel="로그인"
        />

        <View style={styles.body}>
          <View style={{ gap: spacing.sm }}>
            <Text style={styles.label}>이메일</Text>
            <View
              style={[
                styles.inputRow,
                {
                  borderColor: email.length === 0
                    ? colors.border
                    : emailLooksValid
                      ? colors.primary
                      : "rgba(45,49,50,0.22)",
                },
              ]}
            >
              <Ionicons name="mail-outline" size={16} color={colors.mutedForeground} />
              <TextInput
                value={email}
                onChangeText={setEmail}
                placeholder="example@email.com"
                placeholderTextColor={colors.mutedForeground}
                keyboardType="email-address"
                autoCapitalize="none"
                accessibilityLabel="이메일 입력"
                style={styles.inputField}
              />
            </View>
            <Text style={styles.hint}>가입 시 사용한 이메일을 입력해 주세요.</Text>
          </View>

          <View style={styles.bottomStack}>
            {message ? <Text style={styles.errorText}>{message}</Text> : null}
            <Button
              label="재설정 링크 보내기"
              disabled={!emailLooksValid || busy}
              onPress={() => void sendResetLink()}
            />
          </View>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader
        title={tab === "login" ? "다시 만났어요" : "처음 오셨군요"}
        subtitle={
          tab === "login" ? "로그인해서 오늘의 검사를 시작하세요." : "계정을 만들어 시작해보세요."
        }
      />

      <View style={styles.tabRow}>
        {(["login", "signup"] as const).map((t) => {
          const on = tab === t;
          return (
            <Pressable
              key={t}
              onPress={() => setTab(t)}
              accessibilityRole="tab"
              accessibilityState={{ selected: on }}
              accessibilityLabel={t === "login" ? "로그인" : "회원가입"}
              style={[styles.tab, { borderBottomColor: on ? colors.primary : "transparent" }]}
            >
              <Text style={[styles.tabLabel, { color: on ? colors.primary : colors.mutedForeground }]}>
                {t === "login" ? "로그인" : "회원가입"}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <ScrollView
        contentContainerStyle={styles.formBody}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        {tab === "signup" ? (
          <Field label="이름">
            <TextInput
              value={name}
              onChangeText={setName}
              placeholder="홍길동"
              placeholderTextColor={colors.mutedForeground}
              accessibilityLabel="이름 입력"
              style={styles.input}
            />
          </Field>
        ) : null}

        <Field label="이메일">
          <TextInput
            value={email}
            onChangeText={setEmail}
            placeholder="example@email.com"
            placeholderTextColor={colors.mutedForeground}
            keyboardType="email-address"
            autoCapitalize="none"
            accessibilityLabel="이메일 입력"
            style={styles.input}
          />
        </Field>

        <Field label="비밀번호">
          <View>
            <TextInput
              value={password}
              onChangeText={setPassword}
              placeholder="••••••••"
              placeholderTextColor={colors.mutedForeground}
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              accessibilityLabel="비밀번호 입력"
              style={[styles.input, { paddingRight: 48 }]}
            />
            <Pressable
              onPress={() => setShowPassword((v) => !v)}
              accessibilityRole="button"
              accessibilityLabel={showPassword ? "비밀번호 숨기기" : "비밀번호 표시"}
              hitSlop={10}
              style={styles.eye}
            >
              <Ionicons
                name={showPassword ? "eye-off-outline" : "eye-outline"}
                size={20}
                color={colors.mutedForeground}
              />
            </Pressable>
          </View>
        </Field>

        {tab === "signup" ? (
          <Field label="비밀번호 확인">
            <View>
              <TextInput
                value={passwordConfirmation}
                onChangeText={setPasswordConfirmation}
                placeholder="비밀번호를 다시 입력해 주세요"
                placeholderTextColor={colors.mutedForeground}
                secureTextEntry={!showPasswordConfirmation}
                autoCapitalize="none"
                accessibilityLabel="비밀번호 확인 입력"
                style={[
                  styles.input,
                  {
                    paddingRight: 48,
                    borderColor:
                      passwordConfirmation.length === 0
                        ? colors.border
                        : passwordMatches
                          ? colors.primary
                          : colors.destructive,
                  },
                ]}
              />
              <Pressable
                onPress={() => setShowPasswordConfirmation((v) => !v)}
                accessibilityRole="button"
                accessibilityLabel={showPasswordConfirmation ? "비밀번호 확인 숨기기" : "비밀번호 확인 표시"}
                hitSlop={10}
                style={styles.eye}
              >
                <Ionicons
                  name={showPasswordConfirmation ? "eye-off-outline" : "eye-outline"}
                  size={20}
                  color={colors.mutedForeground}
                />
              </Pressable>
            </View>
            {passwordConfirmation.length > 0 && !passwordMatches ? (
              <Text style={styles.errorText}>비밀번호가 서로 일치하지 않아요.</Text>
            ) : null}
          </Field>
        ) : null}

        {tab === "login" ? (
          <Pressable
            onPress={() => setStep("forgot")}
            accessibilityRole="button"
            accessibilityLabel="비밀번호를 잊으셨나요?"
            style={{ alignSelf: "flex-end" }}
          >
            <Text style={styles.forgotLink}>비밀번호를 잊으셨나요?</Text>
          </Pressable>
        ) : null}

        {message ? <Text style={styles.errorText}>{message}</Text> : null}

        <Button
          label={tab === "login" ? "로그인" : "다음"}
          disabled={!canSubmitForm || busy}
          onPress={() => (tab === "login" ? void submitLogin() : continueToInvite())}
        />

        <View style={styles.dividerRow}>
          <View style={styles.dividerLine} />
          <Text style={styles.dividerLabel}>또는</Text>
          <View style={styles.dividerLine} />
        </View>

        {/* Provider colours are brand marks. AuthSession obtains only the
            one-time authorization code; the backend exchanges it so no client
            secret or provider access token is stored in the app. */}
        <SocialButton
          label="카카오로 계속하기"
          background="#FEE500"
          color={colors.foreground}
          onPress={() => chooseSocialRole("kakao")}
        />
        <SocialButton
          label="네이버로 계속하기"
          background="#03C75A"
          color={colors.white}
          onPress={() => chooseSocialRole("naver")}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={styles.label}>{label}</Text>
      {children}
    </View>
  );
}

function SocialButton({
  label,
  background,
  color,
  onPress,
}: {
  label: string;
  background: string;
  color: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => [styles.social, { backgroundColor: background, opacity: pressed ? 0.85 : 1 }]}
    >
      <Text style={{ color, fontSize: fontSize.body, fontWeight: fontWeight.semibold }}>{label}</Text>
    </Pressable>
  );
}

function SocialRoleCard({
  title,
  description,
  icon,
  onPress,
  disabled,
}: {
  title: string;
  description: string;
  icon: keyof typeof Ionicons.glyphMap;
  onPress: () => void;
  disabled: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled }}
      style={({ pressed }) => [
        styles.socialRoleCard,
        { opacity: disabled ? 0.55 : pressed ? 0.85 : 1 },
      ]}
    >
      <View style={styles.socialRoleIcon}>
        <Ionicons name={icon} size={26} color={colors.primary} />
      </View>
      <View style={{ flex: 1 }}>
        <Text style={styles.socialRoleTitle}>{title}</Text>
        <Text style={styles.socialRoleDescription}>{description}</Text>
      </View>
      <Ionicons name="chevron-forward" size={20} color={colors.mutedForeground} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { flex: 1, paddingHorizontal: spacing.xl, paddingVertical: spacing.xxl - 4, gap: spacing.xxl - 4 },
  formBody: { paddingHorizontal: spacing.xl, paddingVertical: spacing.xl, gap: spacing.lg },

  tabRow: { flexDirection: "row", borderBottomWidth: 1, borderBottomColor: colors.border },
  tab: { flex: 1, paddingVertical: 14, alignItems: "center", borderBottomWidth: 2, marginBottom: -1 },
  tabLabel: { fontSize: fontSize.bodyLg - 1, fontWeight: fontWeight.semibold },

  label: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  input: {
    height: sizes.inputHeight,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.muted,
    borderWidth: 1,
    borderColor: colors.border,
    fontSize: fontSize.bodyLg,
    color: colors.foreground,
  },
  inputRow: {
    height: sizes.inputHeight,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    backgroundColor: colors.white,
  },
  inputField: { flex: 1, fontSize: fontSize.bodyLg, color: colors.foreground },
  hint: { fontSize: fontSize.micro, color: colors.mutedForeground },
  errorText: { fontSize: fontSize.caption, color: colors.destructive, lineHeight: 20 },
  eye: { position: "absolute", right: spacing.lg, top: 0, bottom: 0, justifyContent: "center" },
  forgotLink: { fontSize: fontSize.caption, color: colors.primary },

  dividerRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  dividerLine: { flex: 1, height: 1, backgroundColor: colors.border },
  dividerLabel: { fontSize: fontSize.micro, color: colors.mutedForeground },

  social: {
    height: 50,
    borderRadius: radius.lg,
    alignItems: "center",
    justifyContent: "center",
  },
  socialRoleBody: { justifyContent: "center" },
  socialRoleCard: {
    minHeight: 96,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    borderRadius: radius.xl,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    padding: spacing.lg,
  },
  socialRoleIcon: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.secondary,
  },
  socialRoleTitle: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },
  socialRoleDescription: { marginTop: 3, fontSize: fontSize.caption, color: colors.mutedForeground, lineHeight: 20 },

  codeRow: { flexDirection: "row", gap: spacing.sm },
  codeCell: {
    flex: 1,
    height: 54,
    borderRadius: radius.md,
    borderWidth: 2,
    alignItems: "center",
    justifyContent: "center",
  },
  codeText: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.primary },

  keypad: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  key: {
    width: "31.3%",
    height: 54,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
  },
  keyText: { fontSize: fontSize.subtitle, fontWeight: fontWeight.semibold, color: colors.foreground },

  bottomStack: { marginTop: "auto", gap: spacing.sm },
  textLink: { paddingVertical: spacing.md, alignItems: "center" },
  textLinkLabel: { fontSize: fontSize.body, color: colors.mutedForeground },

  successCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: colors.secondary,
    alignItems: "center",
    justifyContent: "center",
  },
  successTitle: { fontSize: 22, fontWeight: fontWeight.bold, color: colors.foreground },
  successBody: {
    fontSize: fontSize.bodyLg - 1,
    color: colors.mutedForeground,
    textAlign: "center",
    lineHeight: 25,
  },
  successEmail: { fontWeight: fontWeight.semibold, color: colors.foreground },
  noteBox: {
    alignSelf: "stretch",
    backgroundColor: colors.secondary,
    borderRadius: radius.lg,
    paddingHorizontal: spacing.lg,
    paddingVertical: 14,
  },
  noteText: { fontSize: fontSize.caption, color: colors.primary, lineHeight: 21 },
});
