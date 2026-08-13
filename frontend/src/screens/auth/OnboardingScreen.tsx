import React from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useNavigation } from "@react-navigation/native";

import { users } from "@/api";
import { ApiError, apiErrorMessage } from "@/api/errors";
import type { ConsentType } from "@/api/types";
import { Button, Card, ErrorState, LoadingState, Screen, ScreenHeader } from "@/components/ui";
import { useApi } from "@/hooks/useApi";
import type { RootNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

const CONSENT_VERSION = "onboarding-v1";

const AGE_GROUPS = [
  { value: "60s", label: "60대" },
  { value: "70s", label: "70대" },
  { value: "80s_plus", label: "80대 이상" },
  { value: "unknown", label: "선택 안 함" },
] as const;

const GENDERS = [
  { value: "female", label: "여성" },
  { value: "male", label: "남성" },
  { value: "other", label: "기타" },
  { value: "unknown", label: "선택 안 함" },
] as const;

type ConsentOption = {
  type: ConsentType;
  label: string;
  description: string;
  elderOnly?: boolean;
  requiredForElder?: boolean;
};

const CONSENT_OPTIONS: ConsentOption[] = [
  {
    type: "analysis",
    label: "인지 활동 분석 동의",
    description: "검사와 대화 결과를 인지 활동 참고 정보로 분석합니다.",
    elderOnly: true,
    requiredForElder: true,
  },
  {
    type: "voice_collection",
    label: "음성 수집 동의",
    description: "답변 녹음과 음성 특성 분석을 위해 음성을 저장합니다.",
    elderOnly: true,
    requiredForElder: true,
  },
  {
    type: "data_sharing",
    label: "서비스 개선 데이터 공유",
    description: "서비스 품질 개선을 위한 데이터 활용에 동의합니다. (선택)",
  },
  {
    type: "research_use",
    label: "연구 목적 활용",
    description: "비식별 정보의 연구 목적 활용에 동의합니다. (선택)",
    elderOnly: true,
  },
];

export default function OnboardingScreen() {
  const navigation = useNavigation<RootNav>();
  const { userId, role, completeOnboarding } = useApp();
  const [birthDate, setBirthDate] = React.useState("");
  const [ageGroup, setAgeGroup] = React.useState<string | null>(null);
  const [gender, setGender] = React.useState<string | null>(null);
  const [selectedConsents, setSelectedConsents] = React.useState<Set<ConsentType>>(new Set());
  const [initialized, setInitialized] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);

  const profileRequest = useApi(
    () => users.profile(userId as string, role ?? "elder"),
    [userId, role],
    { enabled: !!userId },
  );
  const consentRequest = useApi(
    () => users.consents(userId as string),
    [userId],
    { enabled: !!userId },
  );

  React.useEffect(() => {
    if (initialized || !profileRequest.data || !consentRequest.data) return;
    setBirthDate(profileRequest.data.birth_date ?? "");
    setAgeGroup(profileRequest.data.age_group);
    setGender(profileRequest.data.gender);
    setSelectedConsents(
      new Set(
        consentRequest.data.consents
          .filter((consent) => consent.version === CONSENT_VERSION && consent.agreed)
          .map((consent) => consent.consent_type),
      ),
    );
    setInitialized(true);
  }, [consentRequest.data, initialized, profileRequest.data]);

  const visibleConsents = CONSENT_OPTIONS.filter((option) => !option.elderOnly || role === "elder");
  const requiredConsentsAccepted = visibleConsents
    .filter((option) => role === "elder" && option.requiredForElder)
    .every((option) => selectedConsents.has(option.type));
  const birthDateValid = /^\d{4}-\d{2}-\d{2}$/.test(birthDate);
  const canSubmit = Boolean(userId && role && birthDateValid && ageGroup && gender && requiredConsentsAccepted);

  const toggleConsent = (type: ConsentType) => {
    setSelectedConsents((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });
  };

  const submit = async () => {
    if (!userId || !role || !ageGroup || !gender || !canSubmit || busy) return;
    setBusy(true);
    setMessage(null);
    try {
      const updated = await users.updateProfile(userId, {
        birth_date: birthDate,
        age_group: ageGroup,
        gender,
      });
      if (!updated.profile_completed) {
        setMessage("프로필 정보를 모두 입력해 주세요.");
        return;
      }

      const existing = new Set(
        (consentRequest.data?.consents ?? [])
          .filter((consent) => consent.version === CONSENT_VERSION && consent.agreed)
          .map((consent) => consent.consent_type),
      );
      const agreedAt = new Date().toISOString();
      for (const consentType of selectedConsents) {
        if (existing.has(consentType)) continue;
        try {
          await users.saveConsent(userId, {
            consent_type: consentType,
            agreed: true,
            agreed_at: agreedAt,
            version: CONSENT_VERSION,
          });
        } catch (cause) {
          if (!(cause instanceof ApiError) || cause.status !== 409) throw cause;
        }
      }

      await completeOnboarding();
      navigation.reset({
        index: 0,
        routes: [{ name: role === "guardian" ? "Guardian" : "Elder" }],
      });
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const loading = profileRequest.loading || consentRequest.loading;
  const loadError = profileRequest.error ?? consentRequest.error;

  if (loading && !initialized) {
    return <Screen header={<ScreenHeader title="초기 정보 설정" />}><LoadingState /></Screen>;
  }

  if (loadError && !initialized) {
    return (
      <Screen header={<ScreenHeader title="초기 정보 설정" />}>
        <ErrorState
          message={apiErrorMessage(loadError)}
          onRetry={() => {
            profileRequest.reload();
            consentRequest.reload();
          }}
        />
      </Screen>
    );
  }

  return (
    <Screen
      header={
        <ScreenHeader
          title="초기 정보 설정"
          subtitle="검사 결과를 더 정확하게 비교하기 위한 기본 정보예요."
        />
      }
      contentStyle={styles.content}
      edges={["top", "bottom"]}
    >
      <Card style={styles.section}>
        <Text style={styles.sectionTitle}>생년월일</Text>
        <TextInput
          value={birthDate}
          onChangeText={setBirthDate}
          placeholder="1945-01-01"
          placeholderTextColor={colors.mutedForeground}
          autoCapitalize="none"
          keyboardType="numbers-and-punctuation"
          accessibilityLabel="생년월일"
          style={styles.input}
        />
        <Text style={styles.help}>YYYY-MM-DD 형식으로 입력해 주세요.</Text>
      </Card>

      <ChoiceSection title="연령대" options={AGE_GROUPS} value={ageGroup} onChange={setAgeGroup} />
      <ChoiceSection title="성별" options={GENDERS} value={gender} onChange={setGender} />

      <Card style={styles.section}>
        <Text style={styles.sectionTitle}>동의 항목</Text>
        {visibleConsents.map((option) => {
          const selected = selectedConsents.has(option.type);
          const required = role === "elder" && option.requiredForElder;
          return (
            <Pressable
              key={option.type}
              onPress={() => toggleConsent(option.type)}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: selected }}
              accessibilityLabel={`${option.label}${required ? " 필수" : " 선택"}`}
              style={styles.consentRow}
            >
              <Ionicons
                name={selected ? "checkbox" : "square-outline"}
                size={24}
                color={selected ? colors.primary : colors.mutedForeground}
              />
              <View style={styles.consentCopy}>
                <Text style={styles.consentTitle}>
                  {option.label}{required ? " (필수)" : ""}
                </Text>
                <Text style={styles.consentDescription}>{option.description}</Text>
              </View>
            </Pressable>
          );
        })}
      </Card>

      {message ? <Text style={styles.error}>{message}</Text> : null}
      <Button
        label={busy ? "저장하고 있어요" : "설정 완료"}
        disabled={!canSubmit || busy}
        onPress={() => void submit()}
        size="lg"
      />
    </Screen>
  );
}

function ChoiceSection<T extends string>({
  title,
  options,
  value,
  onChange,
}: {
  title: string;
  options: readonly { value: T; label: string }[];
  value: string | null;
  onChange: (value: T) => void;
}) {
  return (
    <Card style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={styles.choiceRow}>
        {options.map((option) => {
          const selected = value === option.value;
          return (
            <Pressable
              key={option.value}
              onPress={() => onChange(option.value)}
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              accessibilityLabel={option.label}
              style={[styles.choice, selected && styles.choiceSelected]}
            >
              <Text style={[styles.choiceLabel, selected && styles.choiceLabelSelected]}>
                {option.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg, paddingBottom: spacing.xxl },
  section: { gap: spacing.md },
  sectionTitle: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },
  input: {
    height: 52,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    fontSize: fontSize.body,
    color: colors.foreground,
    backgroundColor: colors.white,
  },
  help: { fontSize: fontSize.caption, color: colors.mutedForeground },
  choiceRow: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  choice: {
    minHeight: 44,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
  },
  choiceSelected: { borderColor: colors.primary, backgroundColor: colors.secondary },
  choiceLabel: { fontSize: fontSize.caption, color: colors.mutedForeground },
  choiceLabelSelected: { color: colors.primaryDark, fontWeight: fontWeight.bold },
  consentRow: { flexDirection: "row", gap: spacing.md, alignItems: "flex-start", paddingVertical: spacing.xs },
  consentCopy: { flex: 1, gap: 3 },
  consentTitle: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  consentDescription: { fontSize: fontSize.caption, lineHeight: 20, color: colors.mutedForeground },
  error: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },
});
