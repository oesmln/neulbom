import React from "react";
import { Pressable, StyleSheet, TextInput } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { guardian as guardianApi } from "@/api";
import { ApiError, apiErrorMessage } from "@/api/errors";
import type { InvitationVerifyResponse } from "@/api/types";
import type { ElderMyPageNav } from "@/navigation/types";
import { Button, Card, Screen, ScreenHeader, SentenceText as Text } from "@/components/ui";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

const CODE_LENGTH = 6;
const SCOPE_LABELS: Record<string, string> = {
  screening: "검사 결과",
  summary: "대화 요약",
  diary: "일기",
  activity: "활동·게임",
  campaign: "캠페인",
  all: "모든 기록",
};

function invitationErrorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.status === 404) return "초대코드를 찾을 수 없어요. 숫자를 확인해 주세요.";
    if (cause.status === 409) return "이미 연결된 보호자예요.";
    if (cause.status === 410) return "만료되었거나 이미 사용된 코드예요. 새 코드를 받아 주세요.";
    if (cause.status === 429) return "입력 시도가 너무 많아요. 잠시 후 다시 시도해 주세요.";
  }
  return apiErrorMessage(cause);
}

function scopeDescription(scopes: string[] | null): string {
  if (!scopes?.length) return "보호자가 설정한 공유 범위";
  if (scopes.includes("all")) return SCOPE_LABELS.all;
  return scopes.map((scope) => SCOPE_LABELS[scope] ?? scope).join(" · ");
}

export default function ElderGuardianInviteScreen() {
  const navigation = useNavigation<ElderMyPageNav>();
  const [code, setCode] = React.useState("");
  const [preview, setPreview] = React.useState<InvitationVerifyResponse | null>(null);
  const [consentAgreed, setConsentAgreed] = React.useState(false);
  const [busy, setBusy] = React.useState<"verify" | "accept" | null>(null);
  const [linkStatus, setLinkStatus] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const changeCode = (value: string) => {
    setCode(value.replace(/\D/g, "").slice(0, CODE_LENGTH));
    setPreview(null);
    setConsentAgreed(false);
    setError(null);
  };

  const verify = async () => {
    if (code.length !== CODE_LENGTH || busy) return;
    setBusy("verify");
    setError(null);
    try {
      setPreview(await guardianApi.verifyInvitation(code));
    } catch (cause) {
      setError(invitationErrorMessage(cause));
    } finally {
      setBusy(null);
    }
  };

  const accept = async () => {
    if (!preview || !consentAgreed || busy) return;
    setBusy("accept");
    setError(null);
    try {
      const link = await guardianApi.acceptInvitation(code, consentAgreed);
      setCode("");
      setPreview(null);
      setLinkStatus(link.status);
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 409 || cause.status === 410)) {
        setPreview(null);
        setConsentAgreed(false);
      }
      setError(invitationErrorMessage(cause));
    } finally {
      setBusy(null);
    }
  };

  const resetCode = () => {
    setCode("");
    setPreview(null);
    setConsentAgreed(false);
    setError(null);
  };

  const header = (
    <ScreenHeader
      title="보호자 연결"
      subtitle="보호자에게 받은 초대코드를 입력해 주세요."
      onBack={() => navigation.goBack()}
      backLabel="앱 설정"
    />
  );

  if (linkStatus) {
    return (
      <Screen header={header} contentStyle={styles.content}>
        <Card style={styles.successCard}>
          <Ionicons name="checkmark-circle" size={48} color={colors.success} />
          <Text style={styles.successTitle}>
            {linkStatus === "active" ? "보호자 연결이 완료됐어요" : "보호자 연결 요청을 보냈어요"}
          </Text>
          <Text style={styles.description}>보호자는 동의한 범위의 기록을 볼 수 있어요.</Text>
          <Button label="설정으로 돌아가기" onPress={() => navigation.goBack()} />
        </Card>
      </Screen>
    );
  }

  const expiryDate = preview ? new Date(preview.expires_at) : null;
  const expiryLabel = expiryDate && !Number.isNaN(expiryDate.getTime())
    ? expiryDate.toLocaleString("ko-KR")
    : null;

  return (
    <Screen header={header} contentStyle={styles.content}>
      <Card style={styles.card}>
        <Text style={styles.title}>초대코드 6자리</Text>
        <Text style={styles.description}>
          코드 확인만으로는 연결되지 않아요. 공유 내용을 확인한 뒤 동의할 수 있어요.
        </Text>
        <TextInput
          value={code}
          onChangeText={changeCode}
          editable={!busy}
          keyboardType="number-pad"
          maxLength={CODE_LENGTH}
          placeholder="000000"
          placeholderTextColor={colors.mutedForeground}
          accessibilityLabel="보호자 초대코드 6자리"
          style={styles.codeInput}
        />
        {!preview ? (
          <Button
            label={busy === "verify" ? "코드 확인 중" : "코드 확인"}
            disabled={code.length !== CODE_LENGTH || !!busy}
            onPress={() => void verify()}
          />
        ) : null}
      </Card>

      {preview ? (
        <Card style={styles.card}>
          <Text style={styles.title}>연결 정보</Text>
          <Text style={styles.detail}>관계: {preview.relation || "보호자"}</Text>
          <Text style={styles.detail}>공유 범위: {scopeDescription(preview.access_scope)}</Text>
          {expiryLabel ? <Text style={styles.hint}>코드 만료: {expiryLabel}</Text> : null}
          <Pressable
            onPress={() => setConsentAgreed((current) => !current)}
            disabled={!!busy}
            accessibilityRole="checkbox"
            accessibilityLabel="표시된 범위의 기록을 보호자와 공유하는 데 동의합니다"
            accessibilityState={{ checked: consentAgreed, disabled: !!busy }}
            style={styles.consentRow}
          >
            <Ionicons
              name={consentAgreed ? "checkbox" : "square-outline"}
              size={26}
              color={consentAgreed ? colors.primary : colors.mutedForeground}
            />
            <Text style={styles.consentText}>
              표시된 범위의 기록을 보호자와 공유하는 데 동의합니다.
            </Text>
          </Pressable>
          <Button
            label={busy === "accept" ? "연결 중" : "보호자 연결하기"}
            disabled={!consentAgreed || !!busy}
            onPress={() => void accept()}
          />
          <Button label="다른 코드 입력" variant="outline" disabled={!!busy} onPress={resetCode} />
        </Card>
      ) : null}

      {error ? <Text accessibilityLiveRegion="polite" style={styles.error}>{error}</Text> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg, paddingBottom: spacing.xxl },
  card: { gap: spacing.md },
  title: { color: colors.foreground, fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold },
  description: { color: colors.mutedForeground, fontSize: fontSize.body, lineHeight: 23 },
  codeInput: {
    minHeight: 60,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.background,
    color: colors.foreground,
    fontSize: 28,
    fontWeight: fontWeight.semibold,
    letterSpacing: 8,
    textAlign: "center",
  },
  detail: { color: colors.foreground, fontSize: fontSize.body, lineHeight: 24 },
  hint: { color: colors.mutedForeground, fontSize: fontSize.caption, lineHeight: 21 },
  consentRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: spacing.md,
    minHeight: 56,
    paddingVertical: spacing.sm,
  },
  consentText: { flex: 1, color: colors.foreground, fontSize: fontSize.body, lineHeight: 24 },
  error: { color: colors.destructive, fontSize: fontSize.body, lineHeight: 23 },
  successCard: { alignItems: "center", gap: spacing.lg, paddingVertical: spacing.xxl },
  successTitle: { color: colors.foreground, fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, textAlign: "center" },
});
