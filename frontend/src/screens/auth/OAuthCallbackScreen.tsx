import React from "react";
import { Platform, StyleSheet, View } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";

import { auth } from "@/api";
import { oauthErrorMessage } from "@/api/errors";
import { Button, ScreenHeader, SentenceText as Text } from "@/components/ui";
import type { AuthTokenResponse } from "@/api/types";
import type { RootNav, RootStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { colors, fontSize, spacing } from "@/theme";
import { OAUTH_WEB_PENDING_KEY } from "./oauthWeb";

type Provider = "kakao" | "naver";

function isProvider(value: string | undefined): value is Provider {
  return value === "kakao" || value === "naver";
}

const REDIRECT_URIS: Record<Provider, string> = {
  kakao: process.env.EXPO_PUBLIC_KAKAO_REDIRECT_URI?.trim() ?? "",
  naver: process.env.EXPO_PUBLIC_NAVER_REDIRECT_URI?.trim() ?? "",
};

function routeAfterAuth(tokens: AuthTokenResponse) {
  if (tokens.role === "guardian") return "Guardian" as const;
  if (!tokens.profile_completed && tokens.onboarding_step === "not_started") return "ElderProfile" as const;
  return tokens.onboarding_completed ? "Elder" as const : "Onboarding" as const;
}

export default function OAuthCallbackScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "OAuthCallback">>();
  const { signIn } = useApp();
  const [message, setMessage] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(true);
  const handled = React.useRef(false);

  React.useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const providerParam = route.params?.provider;
    const code = route.params?.code;
    const state = route.params?.state;
    const errorDescription = route.params?.error_description;
    if (!isProvider(providerParam) || !code || !state) {
      setBusy(false);
      setMessage(errorDescription || "소셜 로그인 응답을 확인할 수 없어요. 다시 시도해 주세요.");
      return;
    }
    const provider = providerParam;

    if (Platform.OS === "web" && typeof window !== "undefined") {
      try {
        const rawPending = window.sessionStorage.getItem(OAUTH_WEB_PENDING_KEY);
        window.sessionStorage.removeItem(OAUTH_WEB_PENDING_KEY);
        const pending = rawPending ? (JSON.parse(rawPending) as { provider?: string; state?: string }) : null;
        if (!pending || pending.provider !== provider || pending.state !== state) {
          setBusy(false);
          setMessage("로그인 요청이 만료되었거나 일치하지 않아요. 처음부터 다시 시도해 주세요.");
          return;
        }
      } catch {
        setBusy(false);
        setMessage("로그인 요청을 확인할 수 없어요. 처음부터 다시 시도해 주세요.");
        return;
      }
    }

    void (async () => {
      try {
        const prepared = await auth.prepareOAuthLogin(provider, {
          authorization_code: code,
          redirect_uri: REDIRECT_URIS[provider],
          state,
        });
        if (prepared.status === "role_required" && prepared.pending_token) {
          navigation.reset({
            index: 0,
            routes: [
              {
                name: "Login",
                params: {
                  mode: "login",
                  socialProvider: provider,
                  socialPendingToken: prepared.pending_token,
                  socialDisplayName: prepared.display_name,
                },
              },
            ],
          });
          return;
        }
        if (!prepared.tokens) {
          throw new Error("소셜 로그인 응답을 확인할 수 없어요.");
        }
        await signIn(prepared.tokens);
        navigation.reset({ index: 0, routes: [{ name: routeAfterAuth(prepared.tokens) }] });
      } catch (cause) {
        setBusy(false);
        setMessage(oauthErrorMessage(cause, provider === "naver" ? "네이버" : "카카오"));
      }
    })();
  }, [navigation, route.params, signIn]);

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader title="로그인 처리 중" subtitle="잠시만 기다려 주세요." />
      <View style={styles.body}>
        {busy ? <Text style={styles.status}>소셜 계정을 확인하고 있어요.</Text> : null}
        {message ? <Text style={styles.error}>{message}</Text> : null}
        {!busy ? <Button label="로그인 화면으로 돌아가기" onPress={() => navigation.replace("Login", { mode: "login" })} /> : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { flex: 1, padding: spacing.xl, justifyContent: "center", gap: spacing.lg },
  status: { textAlign: "center", fontSize: fontSize.body, color: colors.mutedForeground },
  error: { textAlign: "center", fontSize: fontSize.body, color: colors.destructive, lineHeight: 24 },
});
