import React from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";

import { guardian } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import type { RootNav, RootStackParamList } from "@/navigation/types";
import { Button, ScreenHeader, SentenceText as Text } from "@/components/ui";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

const CODE_LENGTH = 6;
const KEYPAD = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "←"] as const;

/** Elder sign-up's invite step, shown after the elder role is selected. */
export default function SignupInviteScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "SignupInvite">>();
  const signup = route.params.signup;
  const [code, setCode] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const goToProfile = (inviteCode?: string) => {
    navigation.navigate("ElderProfile", {
      signup: inviteCode ? { ...signup, inviteCode } : signup,
      inviteCode,
    });
  };

  const submit = async () => {
    if (code.length !== CODE_LENGTH || busy) return;
    setBusy(true);
    setError(null);
    try {
      await guardian.verifyInvitation(code);
      goToProfile(code);
    } catch (cause) {
      setError(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader
        title="초대 코드 입력"
        subtitle="보호자에게 받은 코드가 있다면 입력해 주세요."
        onBack={() => navigation.goBack()}
        backLabel="사용자 유형"
      />

      <View style={styles.body}>
        <Text style={styles.guide}>
          보호자 초대코드가 없어도 가입할 수 있어요. 나중에 설정에서 연결할 수 있습니다.
        </Text>

        <View style={styles.codeRow}>
          {Array.from({ length: CODE_LENGTH }).map((_, index) => {
            const filled = code.length > index;
            return (
              <View
                key={index}
                style={[
                  styles.codeCell,
                  {
                    borderColor: filled ? colors.primary : colors.border,
                    backgroundColor: filled ? colors.secondary : colors.muted,
                  },
                ]}
              >
                <Text style={styles.codeText}>{code[index] ?? ""}</Text>
              </View>
            );
          })}
        </View>

        <View style={styles.keypad}>
          {KEYPAD.map((key, index) => (
            <Pressable
              key={index}
              disabled={key === "" || busy}
              accessibilityRole="button"
              accessibilityLabel={key === "←" ? "한 자리 지우기" : key || "빈 칸"}
              onPress={() => {
                if (key === "←") setCode((current) => current.slice(0, -1));
                else if (key && code.length < CODE_LENGTH) setCode((current) => current + key);
              }}
              style={({ pressed }) => [
                styles.key,
                {
                  backgroundColor: key ? colors.muted : "transparent",
                  opacity: pressed ? 0.7 : busy ? 0.5 : 1,
                },
              ]}
            >
              <Text style={styles.keyText}>{key}</Text>
            </Pressable>
          ))}
        </View>

        {error ? <Text style={styles.error}>{error}</Text> : null}

        <View style={styles.actions}>
          <Button
            label={busy ? "코드 확인 중" : "확인"}
            disabled={code.length !== CODE_LENGTH || busy}
            onPress={() => void submit()}
          />
          <Pressable
            onPress={() => goToProfile()}
            disabled={busy}
            accessibilityRole="button"
            accessibilityLabel="초대 코드 없이 계속하기"
            style={styles.skip}
          >
            <Text style={styles.skipText}>초대 코드 없이 계속하기</Text>
          </Pressable>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { flex: 1, padding: spacing.xl, gap: spacing.xl },
  guide: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 24 },
  codeRow: { flexDirection: "row", justifyContent: "center", gap: spacing.sm },
  codeCell: {
    width: 42,
    height: 52,
    borderWidth: 1.5,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
  },
  codeText: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.foreground },
  keypad: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm, justifyContent: "center" },
  key: {
    width: "30%",
    minHeight: 56,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
  },
  keyText: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold, color: colors.foreground },
  error: { fontSize: fontSize.caption, color: colors.destructive, lineHeight: 20, textAlign: "center" },
  actions: { marginTop: "auto", gap: spacing.md },
  skip: { alignItems: "center", paddingVertical: spacing.sm },
  skipText: { fontSize: fontSize.caption, color: colors.mutedForeground, textDecorationLine: "underline" },
});
