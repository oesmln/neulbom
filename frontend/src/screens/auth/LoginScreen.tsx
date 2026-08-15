import React from "react";
import { Platform, View, TextInput, Pressable, StyleSheet, ScrollView } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import * as AuthSession from "expo-auth-session";

import { RootNav, RootStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { auth } from "@/api";
import { apiErrorMessage, oauthErrorMessage } from "@/api/errors";
import type { AuthTokenResponse } from "@/api/types";
import { colors, spacing, radius, fontSize, fontWeight, sizes } from "@/theme";
import { Button, ScreenHeader, SentenceText as Text } from "@/components/ui";
import { OAUTH_WEB_PENDING_KEY } from "./oauthWeb";

/**
 * Sign-in, sign-up and password reset.
 *
 * The sign-up values stay in memory while the user moves through the role and
 * elder invite screens. api-spec 3.1 requires the app to hold the form values
 * until the user type is picked and only then POST /auth/register once.
 *
 * Signing in is a different path on purpose. `POST /auth/login` already returns
 * `role`, so an existing user goes straight to their own area — the invite code
 * and the role picker belong to registration and must not reappear at every
 * sign-in.
 */
type Step = "form" | "socialRole" | "forgot" | "forgotSent";
type Tab = "login" | "signup";
type SocialProvider = "kakao" | "naver";
type SignupConsentKey = "terms" | "privacy";
type EmailCheckState = "idle" | "checking" | "available" | "taken";

const SIGNUP_CONSENT_ITEMS: Array<{ key: SignupConsentKey; title: string; body: string }> = [
  {
    key: "terms",
    title: "이용약관 동의",
    body: "늘봄 서비스 이용을 위해 필요한 약관이에요.",
  },
  {
    key: "privacy",
    title: "개인정보 수집·이용 동의",
    body: "회원가입과 서비스 제공에 필요한 개인정보를 처리해요.",
  },
];

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

export default function LoginScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "Login">>();
  const { signIn } = useApp();

  const [step, setStep] = React.useState<Step>("form");
  const [tab, setTab] = React.useState<Tab>(route.params?.mode ?? "login");
  const [showPassword, setShowPassword] = React.useState(false);
  const [name, setName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [passwordConfirmation, setPasswordConfirmation] = React.useState("");
  const [showPasswordConfirmation, setShowPasswordConfirmation] = React.useState(false);
  const [emailCheckState, setEmailCheckState] = React.useState<EmailCheckState>("idle");
  const [checkedEmail, setCheckedEmail] = React.useState("");
  const [emailCheckMessage, setEmailCheckMessage] = React.useState<string | null>(null);
  const [signupConsents, setSignupConsents] = React.useState<Record<SignupConsentKey, boolean>>({
    terms: false,
    privacy: false,
  });
  const [busy, setBusy] = React.useState(false);
  const [socialProvider, setSocialProvider] = React.useState<SocialProvider | null>(null);
  const [socialPendingToken, setSocialPendingToken] = React.useState<string | null>(null);
  const [socialDisplayName, setSocialDisplayName] = React.useState<string | null>(null);
  const [message, setMessage] = React.useState<string | null>(null);

  // Splash has two intentional entry points. Keep the selected tab tied to
  // that route mode so a previously mounted Login screen cannot leak its old
  // signup/login tab into the next entry.
  React.useEffect(() => {
    const params = route.params;
    if (!params) return;
    setStep(params.socialPendingToken ? "socialRole" : "form");
    setTab(params.mode ?? "login");
    setMessage(null);
    setSocialProvider(params.socialProvider ?? null);
    setSocialPendingToken(params.socialPendingToken ?? null);
    setSocialDisplayName(params.socialDisplayName ?? null);
    setEmailCheckState("idle");
    setCheckedEmail("");
    setEmailCheckMessage(null);
  }, [route.params]);

  const routeAfterAuth = (tokens: AuthTokenResponse) => {
    if (tokens.role === "guardian") return "Guardian" as const;
    if (!tokens.profile_completed && tokens.onboarding_step === "not_started") return "ElderProfile" as const;
    return tokens.onboarding_completed ? "Elder" as const : "Onboarding" as const;
  };

  const emailLooksValid = email.includes("@") && email.includes(".");
  const passwordMatches = tab === "login" || password === passwordConfirmation;
  const signupConsentsAccepted = signupConsents.terms && signupConsents.privacy;
  const normalizedEmail = email.trim().toLowerCase();
  const emailCheckedForSignup =
    tab === "login" || (emailCheckState === "available" && checkedEmail === normalizedEmail);
  const canSubmitForm =
    emailLooksValid &&
    password.length >= 8 &&
    passwordMatches &&
    (tab === "login" || (name.trim().length > 0 && signupConsentsAccepted && emailCheckedForSignup));

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
      // Naver can keep an earlier login/consent session. Ask it to show the
      // authentication and profile-consent step again for this login flow.
      extraParams: { auth_type: "reprompt" },
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
  const continueToUserType = () => {
    if (tab === "signup" && !emailCheckedForSignup) {
      setEmailCheckMessage("이메일 중복확인을 먼저 해 주세요.");
      return;
    }
    setMessage(null);
    goToUserType();
  };

  const checkEmailAvailability = async () => {
    if (!emailLooksValid) {
      setEmailCheckMessage("이메일 주소를 먼저 입력해 주세요.");
      return;
    }
    setEmailCheckState("checking");
    setEmailCheckMessage(null);
    setMessage(null);
    try {
      const result = await auth.checkEmailAvailability(normalizedEmail);
      setCheckedEmail(result.email);
      if (result.available) {
        setEmailCheckState("available");
        setEmailCheckMessage("사용할 수 있는 이메일이에요.");
      } else {
        setEmailCheckState("taken");
        setEmailCheckMessage("이미 가입된 이메일입니다. 로그인해 주세요.");
      }
    } catch (cause) {
      setEmailCheckState("idle");
      setCheckedEmail("");
      setEmailCheckMessage(apiErrorMessage(cause));
    }
  };

  const goToUserType = (inviteCode?: string) => {
    navigation.navigate("UserType", {
      signup: {
        name: name.trim(),
        email,
        password,
        inviteCode,
        requiredConsentsAccepted: signupConsentsAccepted,
      },
    });
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

  /** Authenticate with the provider first. Only a new app account reaches the role picker. */
  const startSocialLogin = async (provider: SocialProvider) => {
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
    setSocialPendingToken(null);
    setSocialDisplayName(null);
    const request = provider === "kakao" ? kakaoRequest : naverRequest;
    const prompt = provider === "kakao" ? promptKakao : promptNaver;
    if (!request) {
      setMessage("소셜 로그인을 준비하고 있어요. 잠시 후 다시 시도해 주세요.");
      return;
    }

    setBusy(true);
    setMessage(null);
    try {
      if (Platform.OS === "web" && typeof window !== "undefined") {
        const authorizationUrl = request.url ?? await request.makeAuthUrlAsync(config.discovery);
        window.sessionStorage.setItem(
          OAUTH_WEB_PENDING_KEY,
          JSON.stringify({ provider, state: request.state, redirectUri: config.redirectUri }),
        );
        window.location.assign(authorizationUrl);
        return;
      }
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

      const prepared = await auth.prepareOAuthLogin(provider, {
        authorization_code: authorizationCode,
        redirect_uri: config.redirectUri,
        state: request.state,
      });
      if (prepared.status === "role_required") {
        if (!prepared.pending_token) {
          setMessage("소셜 가입 정보를 이어갈 수 없어요. 처음부터 다시 시도해 주세요.");
          return;
        }
        setSocialProvider(provider);
        setSocialPendingToken(prepared.pending_token);
        setSocialDisplayName(prepared.display_name);
        setStep("socialRole");
        return;
      }
      if (!prepared.tokens) {
        setMessage("소셜 로그인 응답을 확인할 수 없어요. 다시 시도해 주세요.");
        return;
      }
      await signIn(prepared.tokens);
      navigation.reset({
        index: 0,
        routes: [{ name: routeAfterAuth(prepared.tokens) }],
      });
    } catch (cause) {
      setMessage(oauthErrorMessage(cause, config.label));
    } finally {
      setBusy(false);
    }
  };

  const submitSocialRole = async (role: "elder" | "guardian") => {
    if (!socialProvider || !socialPendingToken || busy) return;
    setBusy(true);
    setMessage(null);
    try {
      const tokens = await auth.completeOAuthLogin(socialProvider, {
        pending_token: socialPendingToken,
        role,
      });
      await signIn(tokens);
      navigation.reset({
        index: 0,
        routes: [{ name: routeAfterAuth(tokens) }],
      });
    } catch (cause) {
      setMessage(oauthErrorMessage(cause, socialProvider === "naver" ? "네이버" : "카카오"));
    } finally {
      setBusy(false);
    }
  };

  if (step === "socialRole" && socialProvider) {
    const provider = SOCIAL_CONFIG[socialProvider];
    return (
      <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
        <ScreenHeader
          title={`${provider.label} 로그인`}
          subtitle={
            socialDisplayName
              ? `${socialDisplayName}님, 처음 이용할 역할을 선택해 주세요.`
              : "처음 이용할 역할을 선택해 주세요."
          }
          onBack={() => {
            setStep("form");
            setSocialProvider(null);
            setSocialPendingToken(null);
            setSocialDisplayName(null);
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
              onPress={() => void submitSocialRole("elder")}
              disabled={busy || !socialPendingToken}
            />
            <SocialRoleCard
              title="보호자로 이용"
              description="연결된 어르신의 활동과 인지 상태를 확인해요."
              icon="people-outline"
              onPress={() => void submitSocialRole("guardian")}
              disabled={busy || !socialPendingToken}
            />
          </View>
          {message ? <Text style={styles.errorText}>{message}</Text> : null}
          {!socialPendingToken ? <Text style={styles.hint}>가입 정보를 준비하고 있어요.</Text> : null}
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
        onBack={navigation.canGoBack() ? () => navigation.goBack() : undefined}
        backLabel={navigation.canGoBack() ? "시작 화면" : undefined}
      />

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
          <View style={tab === "signup" ? styles.emailSignupRow : styles.loginEmailRow}>
            <TextInput
              value={email}
              onChangeText={(value) => {
                setEmail(value);
                if (tab === "signup") {
                  setEmailCheckState("idle");
                  setCheckedEmail("");
                  setEmailCheckMessage(null);
                }
              }}
              placeholder="example@email.com"
              placeholderTextColor={colors.mutedForeground}
              keyboardType="email-address"
              autoCapitalize="none"
              accessibilityLabel="이메일 입력"
              style={tab === "signup" ? styles.emailSignupInput : styles.loginEmailInput}
            />
            {tab === "signup" ? (
              <Pressable
                onPress={() => void checkEmailAvailability()}
                disabled={!emailLooksValid || emailCheckState === "checking"}
                accessibilityRole="button"
                accessibilityLabel="이메일 중복확인"
                style={({ pressed }) => [
                  styles.emailCheckButton,
                  (!emailLooksValid || emailCheckState === "checking") && styles.emailCheckButtonDisabled,
                  pressed && { opacity: 0.8 },
                ]}
              >
                <Text style={styles.emailCheckButtonText}>
                  {emailCheckState === "checking" ? "확인 중" : "중복확인"}
                </Text>
              </Pressable>
            ) : null}
          </View>
          {tab === "signup" && emailCheckMessage ? (
            <View style={styles.emailCheckMessageRow}>
              <Text style={emailCheckState === "available" ? styles.successText : styles.errorText}>
                {emailCheckMessage}
              </Text>
              {emailCheckState === "taken" ? (
                <Pressable
                  onPress={() => {
                    setTab("login");
                    setEmailCheckState("idle");
                    setCheckedEmail("");
                    setEmailCheckMessage(null);
                  }}
                  accessibilityRole="button"
                  accessibilityLabel="로그인 화면으로 이동"
                >
                  <Text style={styles.loginLink}>로그인으로 이동</Text>
                </Pressable>
              ) : null}
            </View>
          ) : null}
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

        {tab === "signup" ? (
          <View style={styles.signupConsentCard}>
            <Text style={styles.signupConsentHeading}>가입에 필요한 동의</Text>
            <Text style={styles.signupConsentDescription}>
              계정을 만들려면 아래 필수 항목에 동의해 주세요. 고령자 기능에 필요한 동의는 역할 선택 후 별도로 안내해요.
            </Text>
            {SIGNUP_CONSENT_ITEMS.map((item) => {
              const checked = signupConsents[item.key];
              return (
                <Pressable
                  key={item.key}
                  onPress={() => setSignupConsents((current) => ({ ...current, [item.key]: !current[item.key] }))}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked }}
                  accessibilityLabel={`${item.title} (필수)`}
                  style={styles.signupConsentRow}
                >
                  <Ionicons
                    name={checked ? "checkbox" : "square-outline"}
                    size={24}
                    color={checked ? colors.primary : colors.mutedForeground}
                  />
                  <View style={styles.signupConsentCopy}>
                    <Text style={styles.signupConsentTitle}>{item.title} (필수)</Text>
                    <Text style={styles.signupConsentBody}>{item.body}</Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
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
          onPress={() => (tab === "login" ? void submitLogin() : continueToUserType())}
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
          disabled={busy}
          onPress={() => void startSocialLogin("kakao")}
        />
        <SocialButton
          label="네이버로 계속하기"
          background="#03C75A"
          color={colors.white}
          disabled={busy}
          onPress={() => void startSocialLogin("naver")}
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
  disabled,
  onPress,
}: {
  label: string;
  background: string;
  color: string;
  disabled?: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      style={({ pressed }) => [
        styles.social,
        { backgroundColor: background, opacity: disabled ? 0.55 : pressed ? 0.85 : 1 },
      ]}
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
  emailSignupRow: {
    height: sizes.inputHeight,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    paddingLeft: spacing.lg,
    paddingRight: spacing.sm,
    borderRadius: radius.lg,
    backgroundColor: colors.muted,
    borderWidth: 1,
    borderColor: colors.border,
  },
  emailSignupInput: { flex: 1, height: "100%", fontSize: fontSize.bodyLg, color: colors.foreground },
  loginEmailRow: {
    height: sizes.inputHeight,
    borderRadius: radius.lg,
    backgroundColor: colors.muted,
    borderWidth: 1,
    borderColor: colors.border,
  },
  loginEmailInput: {
    height: "100%",
    paddingHorizontal: spacing.lg,
    fontSize: fontSize.bodyLg,
    color: colors.foreground,
  },
  emailCheckButton: {
    minHeight: sizes.buttonHeightSm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primary,
  },
  emailCheckButtonDisabled: { backgroundColor: colors.mutedForeground, opacity: 0.5 },
  emailCheckButtonText: { color: colors.white, fontSize: fontSize.caption, fontWeight: fontWeight.semibold },
  emailCheckMessageRow: { gap: spacing.xs },
  successText: { fontSize: fontSize.caption, color: colors.primaryDark, lineHeight: 20 },
  loginLink: { fontSize: fontSize.caption, color: colors.primary, fontWeight: fontWeight.semibold },
  hint: { fontSize: fontSize.micro, color: colors.mutedForeground },
  errorText: { fontSize: fontSize.caption, color: colors.destructive, lineHeight: 20 },
  eye: { position: "absolute", right: spacing.lg, top: 0, bottom: 0, justifyContent: "center" },
  forgotLink: { fontSize: fontSize.caption, color: colors.primary },

  signupConsentCard: {
    borderRadius: radius.lg,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    gap: spacing.md,
  },
  signupConsentHeading: { fontSize: fontSize.bodyLg, lineHeight: 23, fontWeight: fontWeight.bold, color: colors.foreground },
  signupConsentDescription: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 22, marginBottom: spacing.sm },
  signupConsentRow: { flexDirection: "row", alignItems: "flex-start", gap: spacing.md, paddingVertical: spacing.md },
  signupConsentCopy: { flex: 1, gap: spacing.xs },
  signupConsentTitle: { fontSize: fontSize.bodyLg, lineHeight: 23, fontWeight: fontWeight.semibold, color: colors.foreground },
  signupConsentBody: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 22 },

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
