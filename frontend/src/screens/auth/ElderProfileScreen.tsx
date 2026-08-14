import React from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";

import { ApiError, apiErrorMessage, guardian, users } from "@/api";
import type {
  ConsentType,
  UserPreferenceUpdateRequest,
  UserProfileUpdateRequest,
  VoiceProfileResponse,
} from "@/api/types";
import { Button, Card, ScreenHeader } from "@/components/ui";
import type { RootNav, RootStackParamList } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

const CONSENT_VERSION = "elder-profile-v1";

const REQUIRED_CONSENTS: Array<{ type: ConsentType; title: string; body: string }> = [
  {
    type: "sensitive_health",
    title: "건강·민감정보 처리 동의",
    body: "입력한 건강 관련 정보를 고령자 기능 제공과 변화 관찰에 활용해요.",
  },
  {
    type: "analysis",
    title: "인지 활동 분석 동의",
    body: "대화와 활동을 진단이 아닌 변화 관찰 참고 정보로 분석해요.",
  },
  {
    type: "voice_collection",
    title: "음성 수집·STT·AI 분석 동의",
    body: "음성 답변을 저장하고 음성 인식과 고령자 기능에 사용해요.",
  },
];

const OPTIONAL_CONSENTS: Array<{ type: ConsentType; title: string; body: string }> = [
  {
    type: "data_sharing",
    title: "서비스 개선 데이터 활용",
    body: "서비스를 개선하기 위한 데이터 활용에 동의해요. (선택)",
  },
  {
    type: "research_use",
    title: "연구 목적 활용",
    body: "비식별 정보를 연구 목적으로 활용해요. (선택)",
  },
];

const GENDER_OPTIONS = [
  { value: "female", label: "여성" },
  { value: "male", label: "남성" },
  { value: "other", label: "기타" },
  { value: "unknown", label: "선택 안 함" },
] as const;

const HEARING_SIDE_OPTIONS = [
  { value: "left", label: "왼쪽 귀" },
  { value: "right", label: "오른쪽 귀" },
  { value: "both", label: "양쪽 비슷해요" },
  { value: "unknown", label: "잘 모르겠어요" },
] as const;

const HEARING_STATUS_OPTIONS = [
  { value: "no_difficulty", label: "잘 들어요" },
  { value: "difficulty", label: "듣기 어려움이 있어요" },
  { value: "unknown", label: "잘 모르겠어요" },
] as const;

const SMARTPHONE_OPTIONS = [
  { value: "low", label: "도움이 필요해요" },
  { value: "medium", label: "보통이에요" },
  { value: "high", label: "익숙해요" },
] as const;

type CommunicationChoice = boolean | "unknown" | null;

export default function ElderProfileScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "ElderProfile">>();
  const { userId, role } = useApp();
  const inviteCode = route.params?.inviteCode;

  const [gender, setGender] = React.useState<string | null>(null);
  const [birthYear, setBirthYear] = React.useState("");
  const [educationYears, setEducationYears] = React.useState("");
  const [healthConditions, setHealthConditions] = React.useState("");
  const [hearingSide, setHearingSide] = React.useState<string | null>(null);
  const [hearingStatus, setHearingStatus] = React.useState<string | null>(null);
  const [communicationDifficulty, setCommunicationDifficulty] = React.useState<CommunicationChoice>(null);
  const [smartphoneSkill, setSmartphoneSkill] = React.useState<string | null>(null);
  const [voiceProfileId, setVoiceProfileId] = React.useState<string | null>(null);
  const [voiceProfiles, setVoiceProfiles] = React.useState<VoiceProfileResponse[]>([]);
  const [selectedConsents, setSelectedConsents] = React.useState<Set<ConsentType>>(new Set());
  const [guardianConsentAccepted, setGuardianConsentAccepted] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);

  const requiredAccepted =
    REQUIRED_CONSENTS.every((consent) => selectedConsents.has(consent.type)) &&
    (!inviteCode || guardianConsentAccepted);

  React.useEffect(() => {
    if (role === "guardian") {
      navigation.reset({ index: 0, routes: [{ name: "Guardian" }] });
      return;
    }
    if (!userId) {
      navigation.reset({ index: 0, routes: [{ name: "Login" }] });
      return;
    }

    let cancelled = false;
    void users
      .voiceProfiles()
      .then((response) => {
        if (!cancelled) setVoiceProfiles(response.voice_profiles);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [navigation, role, userId]);

  const toggleConsent = (type: ConsentType) => {
    setSelectedConsents((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });
  };

  const ageGroupForBirthYear = (year: number): string => {
    const age = new Date().getFullYear() - year;
    if (age >= 80) return "80s_plus";
    if (age >= 70) return "70s";
    if (age >= 60) return "60s";
    return "unknown";
  };

  const saveConsent = async (consentType: ConsentType, agreedAt: string) => {
    try {
      await users.saveConsent(userId!, {
        consent_type: consentType,
        agreed: true,
        agreed_at: agreedAt,
        version: CONSENT_VERSION,
      });
    } catch (cause) {
      // A retry after a network interruption should not prevent the user from
      // continuing when this exact document version is already stored.
      if (!(cause instanceof ApiError) || cause.status !== 409) throw cause;
    }
  };

  const submit = async () => {
    if (!userId || busy) return;
    setMessage(null);
    if (!requiredAccepted) {
      setMessage(inviteCode
        ? "필수 동의와 보호자 공유 동의를 모두 확인해 주세요."
        : "고령자 기능에 필요한 필수 동의를 모두 확인해 주세요.");
      return;
    }

    const trimmedYear = birthYear.trim();
    const currentYear = new Date().getFullYear();
    const parsedYear = trimmedYear ? Number(trimmedYear) : null;
    if (trimmedYear && (!/^\d{4}$/.test(trimmedYear) || !parsedYear || parsedYear < currentYear - 120 || parsedYear > currentYear)) {
      setMessage("출생연도는 네 자리 숫자로 입력해 주세요.");
      return;
    }

    const trimmedEducation = educationYears.trim();
    const parsedEducation = trimmedEducation ? Number(trimmedEducation) : null;
    if (trimmedEducation && (!/^\d+$/.test(trimmedEducation) || parsedEducation === null || parsedEducation > 100)) {
      setMessage("학력은 이수 연수를 숫자로 입력해 주세요.");
      return;
    }

    setBusy(true);
    try {
      const profile: UserProfileUpdateRequest = {};
      if (gender) profile.gender = gender;
      if (parsedYear) {
        profile.birth_date = `${parsedYear}-01-01`;
        profile.age_group = ageGroupForBirthYear(parsedYear);
      }
      if (parsedEducation !== null) profile.education_years = parsedEducation;
      if (healthConditions.trim()) {
        profile.health_conditions = healthConditions
          .split(",")
          .map((condition) => condition.trim())
          .filter(Boolean);
      }
      if (hearingStatus) profile.hearing_status = hearingStatus;
      if (typeof communicationDifficulty === "boolean") profile.communication_difficulty = communicationDifficulty;
      if (smartphoneSkill) profile.smartphone_skill = smartphoneSkill;
      if (Object.keys(profile).length > 0) await users.updateProfile(userId, profile);

      const preferences: UserPreferenceUpdateRequest = {};
      if (hearingSide) preferences.preferred_hearing_side = hearingSide;
      if (voiceProfileId) preferences.voice_profile_id = voiceProfileId;
      if (Object.keys(preferences).length > 0) await users.updatePreferences(userId, preferences);

      const agreedAt = new Date().toISOString();
      for (const consent of REQUIRED_CONSENTS) await saveConsent(consent.type, agreedAt);
      for (const consent of OPTIONAL_CONSENTS) {
        if (selectedConsents.has(consent.type)) await saveConsent(consent.type, agreedAt);
      }
      if (inviteCode) {
        await saveConsent("guardian_access", agreedAt);
        await saveConsent("report_sharing", agreedAt);
        await guardian.acceptInvitation(inviteCode, true);
      }

      navigation.reset({ index: 0, routes: [{ name: "Onboarding" }] });
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  if (role === "guardian" || !userId) return null;

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader
        title="고령자 기본정보"
        subtitle="더 편안하게 이용할 수 있도록 선택해서 알려 주세요."
        onBack={() => navigation.goBack()}
        backLabel="가입 완료"
      />
      <ScrollView
        contentContainerStyle={styles.body}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.note}>모든 기본정보는 선택 사항이에요. 입력하지 않아도 서비스를 시작할 수 있어요.</Text>

        <Section title="성별">
          <ChoiceGrid options={GENDER_OPTIONS} value={gender} onChange={setGender} />
        </Section>

        <Section title="출생연도">
          <TextInput
            value={birthYear}
            onChangeText={(value) => setBirthYear(value.replace(/\D/g, "").slice(0, 4))}
            placeholder="예: 1948"
            placeholderTextColor={colors.mutedForeground}
            keyboardType="number-pad"
            maxLength={4}
            accessibilityLabel="출생연도"
            style={styles.input}
          />
        </Section>

        <Section title="학력 (이수 연수)">
          <TextInput
            value={educationYears}
            onChangeText={(value) => setEducationYears(value.replace(/\D/g, "").slice(0, 3))}
            placeholder="예: 6"
            placeholderTextColor={colors.mutedForeground}
            keyboardType="number-pad"
            maxLength={3}
            accessibilityLabel="학력 이수 연수"
            style={styles.input}
          />
        </Section>

        <Section title="기존 병력·건강 상태">
          <TextInput
            value={healthConditions}
            onChangeText={setHealthConditions}
            placeholder="없으면 비워 두셔도 돼요. 여러 개는 쉼표로 구분해 주세요."
            placeholderTextColor={colors.mutedForeground}
            accessibilityLabel="기존 병력과 건강 상태"
            style={[styles.input, styles.multilineInput]}
            multiline
            textAlignVertical="top"
          />
        </Section>

        <Section title="더 잘 들리는 귀">
          <ChoiceGrid options={HEARING_SIDE_OPTIONS} value={hearingSide} onChange={setHearingSide} />
        </Section>

        <Section title="청력 상태">
          <ChoiceGrid options={HEARING_STATUS_OPTIONS} value={hearingStatus} onChange={setHearingStatus} />
        </Section>

        <Section title="의사소통이 어려운 순간이 있나요?">
          <ChoiceGrid
            options={[
              { value: true, label: "있어요" },
              { value: false, label: "없어요" },
              { value: "unknown", label: "잘 모르겠어요" },
            ]}
            value={communicationDifficulty}
            onChange={setCommunicationDifficulty}
          />
        </Section>

        <Section title="스마트폰 사용은 어떤가요?">
          <ChoiceGrid options={SMARTPHONE_OPTIONS} value={smartphoneSkill} onChange={setSmartphoneSkill} />
        </Section>

        {voiceProfiles.length > 0 ? (
          <Section title="AI 안내 음성">
            <ChoiceGrid
              options={voiceProfiles.map((profile) => ({ value: profile.voice_profile_id, label: profile.name }))}
              value={voiceProfileId}
              onChange={setVoiceProfileId}
            />
          </Section>
        ) : null}

        <Card style={styles.consentCard}>
          <Text style={styles.sectionTitle}>고령자 기능에 필요한 동의</Text>
          <Text style={styles.sectionDescription}>
            아래 동의는 고령자 기능을 사용하기 위해 필요한 항목이에요. 회원가입 때 동의한 약관과는 별도로 확인해요.
          </Text>
          {REQUIRED_CONSENTS.map((consent) => (
            <ConsentRow
              key={consent.type}
              title={`${consent.title} (필수)`}
              body={consent.body}
              checked={selectedConsents.has(consent.type)}
              onPress={() => toggleConsent(consent.type)}
            />
          ))}
          {OPTIONAL_CONSENTS.map((consent) => (
            <ConsentRow
              key={consent.type}
              title={consent.title}
              body={consent.body}
              checked={selectedConsents.has(consent.type)}
              onPress={() => toggleConsent(consent.type)}
            />
          ))}
          {inviteCode ? (
            <ConsentRow
              title="보호자 접근·일기·리포트 공유 동의 (필수)"
              body="초대코드로 연결할 보호자가 활동 기록과 리포트를 볼 수 있어요."
              checked={guardianConsentAccepted}
              onPress={() => setGuardianConsentAccepted((current) => !current)}
            />
          ) : null}
        </Card>

        {message ? <Text style={styles.error}>{message}</Text> : null}
        <Button
          label={busy ? "저장하고 있어요" : "저장하고 계속하기"}
          disabled={busy || !requiredAccepted}
          onPress={() => void submit()}
          size="lg"
        />
      </ScrollView>
    </SafeAreaView>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {children}
    </View>
  );
}

function ChoiceGrid<T extends string | boolean | null>({
  options,
  value,
  onChange,
}: {
  options: ReadonlyArray<{ value: T; label: string }>;
  value: T | null;
  onChange: (value: T) => void;
}) {
  return (
    <View style={styles.choiceGrid}>
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Pressable
            key={String(option.value)}
            onPress={() => onChange(option.value)}
            accessibilityRole="radio"
            accessibilityState={{ selected }}
            accessibilityLabel={option.label}
            style={[styles.choice, selected && styles.choiceSelected]}
          >
            <Text style={[styles.choiceLabel, selected && styles.choiceLabelSelected]}>{option.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function ConsentRow({
  title,
  body,
  checked,
  onPress,
}: {
  title: string;
  body: string;
  checked: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      accessibilityLabel={title}
      style={styles.consentRow}
    >
      <Ionicons name={checked ? "checkbox" : "square-outline"} size={24} color={checked ? colors.primary : colors.mutedForeground} />
      <View style={styles.consentCopy}>
        <Text style={styles.consentTitle}>{title}</Text>
        <Text style={styles.consentBody}>{body}</Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { paddingHorizontal: spacing.xl, paddingVertical: spacing.xl, gap: spacing.xl },
  note: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 23 },
  section: { gap: spacing.sm },
  sectionTitle: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },
  sectionDescription: { fontSize: fontSize.caption, color: colors.mutedForeground, lineHeight: 20 },
  input: {
    minHeight: 52,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    backgroundColor: colors.muted,
    paddingHorizontal: spacing.lg,
    color: colors.foreground,
    fontSize: fontSize.bodyLg,
  },
  multilineInput: { minHeight: 92, paddingTop: spacing.md, paddingBottom: spacing.md },
  choiceGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  choice: {
    minHeight: 46,
    paddingHorizontal: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    backgroundColor: colors.white,
    justifyContent: "center",
  },
  choiceSelected: { borderColor: colors.primary, backgroundColor: colors.secondary },
  choiceLabel: { fontSize: fontSize.body, color: colors.foreground },
  choiceLabelSelected: { color: colors.primaryDark, fontWeight: fontWeight.semibold },
  consentCard: { gap: spacing.xs, padding: spacing.lg },
  consentRow: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, paddingVertical: spacing.sm },
  consentCopy: { flex: 1, gap: 2 },
  consentTitle: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  consentBody: { fontSize: fontSize.caption, color: colors.mutedForeground, lineHeight: 19 },
  error: { color: colors.destructive, fontSize: fontSize.caption, lineHeight: 20 },
});
