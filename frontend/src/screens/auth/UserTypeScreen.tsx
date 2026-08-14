import React from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";

import { RootNav, RootStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { auth, guardian } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import { saveRequiredSignupConsents } from "@/screens/auth/signupConsents";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Badge, Button, ScreenHeader, SpeechBubble } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import { DEFAULT_MEMOI } from "@/components/memoiCharacters";

type Choice = "elder" | "guardian";

const OPTIONS: {
  key: Choice;
  title: string;
  sub: string;
  tags: string[];
}[] = [
  {
    key: "elder",
    title: "본인 (고령자)",
    sub: "AI와 대화하며 인지 건강을 스스로 관리해요",
    tags: ["AI 정서 문답", "인지 캠페인", "나만의 일기"],
  },
  {
    key: "guardian",
    title: "보호자",
    sub: "연결된 어르신의 활동과 인지 상태를 확인해요",
    tags: ["인지 저하 그래프", "일기 열람", "위험 알림"],
  },
];

const REQUIRED_CONSENT_ITEMS = [
  { key: "terms", title: "이용약관 동의" },
  { key: "privacy", title: "개인정보 수집·이용 동의" },
] as const;

export default function UserTypeScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "UserType">>();
  const signup = route.params?.signup ?? null;
  const { setRole, signIn } = useApp();
  const [selected, setSelected] = React.useState<Choice | null>(null);
  const [acceptedConsents, setAcceptedConsents] = React.useState<Record<"terms" | "privacy", boolean>>({
    terms: false,
    privacy: false,
  });
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);
  const allRequiredConsentsAccepted = acceptedConsents.terms && acceptedConsents.privacy;

  /**
   * The role is the last piece registration was waiting for: api-spec 3.1 wants
   * one `POST /auth/register` carrying it, not an account created earlier and
   * patched afterwards. The invite code, if one was entered, is redeemed once
   * the account exists and is signed in.
   */
  const start = async () => {
    if (!selected || !allRequiredConsentsAccepted || busy) return;
    if (signup?.inviteCode && selected !== "elder") {
      setMessage("보호자 초대 코드는 본인(고령자) 계정에서만 사용할 수 있어요.");
      return;
    }
    setRole(selected);

    if (!signup) {
      // Reached without a pending sign-up (demo entry point) — just enter.
      navigation.navigate(selected === "elder" ? "Elder" : "Guardian");
      return;
    }

    setBusy(true);
    setMessage(null);
    try {
      const registration = await auth.register({
        email: signup.email,
        password: signup.password,
        name: signup.name,
        role: selected,
      });
      if (!registration.email_verified) {
        navigation.replace("EmailVerification", {
          signup: { ...signup, requiredConsentsAccepted: true },
        });
        return;
      }
      const tokens = await auth.login({ email: signup.email, password: signup.password });
      await signIn(tokens);
      await saveRequiredSignupConsents(tokens.user_id);

      if (signup.inviteCode && selected === "elder") {
        await guardian.acceptInvitation(signup.inviteCode, true);
      }

      let inviteCode: string | undefined;
      let invitationError: string | undefined;
      if (selected === "guardian") {
        try {
          const invitation = await guardian.createInvitation({
            relation: "보호자",
            access_scope: ["screening", "summary", "diary", "activity"],
            expires_in: 600,
          });
          inviteCode = invitation.invite_code;
        } catch (cause) {
          // Account creation already succeeded. Keep the completion screen
          // reachable and let the guardian issue a new code from Connections.
          invitationError = apiErrorMessage(cause);
        }
      }

      navigation.reset({
        index: 0,
        routes: [
          {
            name: "SignupComplete",
            params: { role: selected, inviteCode, invitationError },
          },
        ],
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
        title="어떻게 사용하실 건가요?"
        subtitle="사용자 유형을 선택하면 맞춤 화면이 제공됩니다."
      />

      <View style={styles.characterRow}>
        <Memoi3D
          character={DEFAULT_MEMOI}
          height={100}
          spinnerColor={colors.primary}
          style={{ width: 100 }}
        />
        <SpeechBubble text="누구를 위해 사용하실 건가요?" side="right" style={{ flexShrink: 1 }} />
      </View>

      <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
        {OPTIONS.map((opt) => {
          const on = selected === opt.key;
          return (
            <Pressable
              key={opt.key}
              onPress={() => setSelected(opt.key)}
              accessibilityRole="radio"
              accessibilityState={{ selected: on }}
              accessibilityLabel={`${opt.title}. ${opt.sub}`}
              style={[
                styles.option,
                { borderColor: on ? colors.primary : colors.border, backgroundColor: on ? colors.secondary : colors.white },
              ]}
            >
              <View style={styles.optionTitleRow}>
                <Text style={styles.optionTitle}>{opt.title}</Text>
                {on ? <Badge label="선택됨" color={colors.white} background={colors.primary} /> : null}
              </View>
              <Text style={styles.optionSub}>{opt.sub}</Text>
              <View style={styles.tagRow}>
                {opt.tags.map((tag) => (
                  <View
                    key={tag}
                    style={[styles.tag, { backgroundColor: on ? "#D6EAD9" : colors.muted }]}
                  >
                    <Text
                      style={[
                        styles.tagLabel,
                        { color: on ? colors.primaryDark : colors.mutedForeground },
                      ]}
                    >
                      {tag}
                    </Text>
                  </View>
                ))}
              </View>
            </Pressable>
          );
        })}

        <View style={styles.consentCard}>
          <Text style={styles.consentHeading}>가입에 필요한 동의</Text>
          <Text style={styles.consentDescription}>
            서비스를 시작하려면 아래 두 항목에 동의해 주세요.
          </Text>
          {REQUIRED_CONSENT_ITEMS.map((item) => (
            <Pressable
              key={item.key}
              onPress={() => setAcceptedConsents((current) => ({ ...current, [item.key]: !current[item.key] }))}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: acceptedConsents[item.key] }}
              accessibilityLabel={item.title}
              style={styles.consentRow}
            >
              <Ionicons
                name={acceptedConsents[item.key] ? "checkbox" : "square-outline"}
                size={23}
                color={acceptedConsents[item.key] ? colors.primary : colors.mutedForeground}
              />
              <Text style={styles.consentTitle}>{item.title} (필수)</Text>
            </Pressable>
          ))}
        </View>

        {message ? <Text style={styles.errorText}>{message}</Text> : null}

        <Button
          label="시작하기"
          icon={selected ? "chevron-forward" : undefined}
          disabled={!selected || !allRequiredConsentsAccepted || busy}
          onPress={() => void start()}
          style={{ marginTop: spacing.xs }}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  characterRow: {
    flexDirection: "row",
    alignItems: "flex-end",
    justifyContent: "center",
    gap: spacing.md,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.xxl - 4,
  },
  body: { paddingHorizontal: spacing.xl, paddingBottom: spacing.xxl, gap: spacing.md },

  option: { borderWidth: 2, borderRadius: radius.xl, padding: spacing.xl },
  optionTitleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginBottom: 6 },
  optionTitle: { fontSize: fontSize.cardTitle, fontWeight: fontWeight.bold, color: colors.foreground },
  optionSub: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 22, marginBottom: 10 },
  tagRow: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  tag: { paddingHorizontal: spacing.sm, paddingVertical: 3, borderRadius: radius.sm },
  tagLabel: { fontSize: fontSize.badge, fontWeight: fontWeight.semibold },
  consentCard: { borderRadius: radius.lg, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.border, padding: spacing.lg, gap: spacing.xs },
  consentHeading: { fontSize: fontSize.body, fontWeight: fontWeight.bold, color: colors.foreground },
  consentDescription: { fontSize: fontSize.caption, color: colors.mutedForeground, lineHeight: 19, marginBottom: spacing.xs },
  consentRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, paddingVertical: spacing.xs },
  consentTitle: { fontSize: fontSize.body, color: colors.foreground },
  errorText: { fontSize: fontSize.caption, color: colors.destructive, lineHeight: 20 },
});
