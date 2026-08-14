import React from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { users } from "@/api";
import { ApiError, apiErrorMessage } from "@/api/errors";
import type { ConsentType, OnboardingStep } from "@/api/types";
import { Button, Card, ScreenHeader, SpeechBubble } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import { DEFAULT_MEMOI, DEFAULT_MOUTH_SET } from "@/components/memoiCharacters";
import type { RootNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

type Step = "intro" | "character" | "consent" | "baseline";

const CONSENT_VERSION = "onboarding-v2";
const REQUIRED_CONSENTS: Array<{ type: ConsentType; title: string; body: string }> = [
  {
    type: "terms_of_service",
    title: "이용약관 동의",
    body: "늘봄 서비스를 이용하기 위한 기본 약속을 확인해요.",
  },
  {
    type: "privacy_collection",
    title: "개인정보 수집·이용 동의",
    body: "서비스 제공에 필요한 최소한의 개인정보를 안전하게 처리해요.",
  },
  {
    type: "sensitive_health",
    title: "건강·민감정보 처리 동의",
    body: "인지 활동을 참고 정보로 분석하기 위해 필요한 동의예요.",
  },
  {
    type: "analysis",
    title: "인지 활동 분석 동의",
    body: "대화와 검사 결과를 진단이 아닌 변화 관찰 참고 정보로 분석해요.",
  },
  {
    type: "voice_collection",
    title: "음성 수집·분석 동의",
    body: "음성 답변을 저장하고 음성 인식과 활동 분석에 사용해요.",
  },
];

const OPTIONAL_CONSENTS: Array<{ type: ConsentType; title: string; body: string }> = [
  {
    type: "data_sharing",
    title: "서비스 개선 데이터 활용",
    body: "서비스를 더 편리하게 만드는 데 데이터를 활용해요. (선택)",
  },
  {
    type: "research_use",
    title: "연구 목적 활용",
    body: "비식별 정보를 연구 목적으로 활용해요. (선택)",
  },
];

const STEP_META: Array<{ key: Step; label: string }> = [
  { key: "intro", label: "인사" },
  { key: "character", label: "이름" },
  { key: "consent", label: "동의" },
  { key: "baseline", label: "검사" },
];

function apiStep(step: Step): OnboardingStep {
  if (step === "character") return "character_name";
  if (step === "consent") return "consent";
  if (step === "baseline") return "baseline";
  return "intro";
}

export default function OnboardingScreen() {
  const navigation = useNavigation<RootNav>();
  const { userId, role, onboardingStep, characterName, updateOnboardingState } = useApp();
  const [step, setStep] = React.useState<Step>(() => {
    if (onboardingStep === "character_name") return "character";
    if (onboardingStep === "consent") return "consent";
    if (onboardingStep === "baseline") return "baseline";
    return "intro";
  });
  const [name, setName] = React.useState(characterName ?? "늘봄");
  const [selected, setSelected] = React.useState<Set<ConsentType>>(new Set());
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);

  const index = STEP_META.findIndex((item) => item.key === step);
  const requiredAccepted = REQUIRED_CONSENTS.every((consent) => selected.has(consent.type));

  const persistStep = async (next: Step, extra?: { completed?: boolean }) => {
    if (!userId) return;
    const normalizedName = name.trim() || "늘봄";
    await users.updateProfile(userId, {
      onboarding_step: apiStep(next),
      character_name: normalizedName,
      onboarding_completed: extra?.completed,
    });
    await updateOnboardingState({
      step: apiStep(next),
      completed: extra?.completed,
      characterName: normalizedName,
    });
  };

  const move = async (next: Step) => {
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      await persistStep(next);
      setStep(next);
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const saveConsentsAndStartBaseline = async () => {
    if (!userId || !requiredAccepted || busy) return;
    setBusy(true);
    setMessage(null);
    try {
      const agreedAt = new Date().toISOString();
      for (const consentType of selected) {
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
      await persistStep("baseline");
      setStep("baseline");
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const finishIntro = () => void move("character");
  const finishCharacter = () => void move("consent");

  React.useEffect(() => {
    if (role === "guardian") navigation.reset({ index: 0, routes: [{ name: "Guardian" }] });
  }, [navigation, role]);

  if (role === "guardian") {
    return null;
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader
        title="처음 만나는 늘봄"
        subtitle={`${Math.max(index + 1, 1)} / ${STEP_META.length} 단계`}
      />

      <View style={styles.progressRow} accessibilityLabel="초기 설정 진행 단계">
        {STEP_META.map((item, itemIndex) => (
          <View key={item.key} style={styles.progressItem}>
            <View style={[styles.progressDot, itemIndex <= index && styles.progressDotActive]}>
              {itemIndex < index ? <Ionicons name="checkmark" size={13} color={colors.white} /> : null}
            </View>
            <Text style={[styles.progressLabel, itemIndex <= index && styles.progressLabelActive]}>
              {item.label}
            </Text>
          </View>
        ))}
      </View>

      {step === "intro" ? (
        <ConversationStep
          line="안녕하세요! 저는 메모이예요. 매일 편하게 이야기하며 인지 건강을 함께 살펴볼게요."
          guide="이 초기 설정은 한 번만 진행해요. 중간에 닫아도 다음에 이어서 할 수 있어요."
          buttonLabel="다음"
          busy={busy}
          onPress={finishIntro}
        />
      ) : null}

      {step === "character" ? (
        <View style={styles.stepBody}>
          <ConversationHeader line="제가 어떤 이름으로 불리면 좋을까요?" />
          <Card style={styles.nameCard}>
            <Text style={styles.fieldLabel}>캐릭터 이름</Text>
            <TextInput
              value={name}
              onChangeText={setName}
              placeholder="예: 늘봄이"
              placeholderTextColor={colors.mutedForeground}
              maxLength={20}
              style={styles.nameInput}
              accessibilityLabel="캐릭터 이름"
            />
            <Text style={styles.help}>나중에 마이 화면에서 다시 바꿀 수 있어요.</Text>
          </Card>
          <View style={styles.footer}>
            {message ? <Text style={styles.error}>{message}</Text> : null}
            <Button label={busy ? "저장하고 있어요" : "이 이름으로 할게요"} disabled={!name.trim() || busy} onPress={finishCharacter} size="lg" />
          </View>
        </View>
      ) : null}

      {step === "consent" ? (
        <View style={styles.stepBody}>
          <ConversationHeader line="안전하게 이용하기 위해 꼭 필요한 약속을 먼저 확인할게요." />
          <View style={styles.consentList}>
            {[...REQUIRED_CONSENTS, ...OPTIONAL_CONSENTS].map((consent) => {
              const on = selected.has(consent.type);
              const required = REQUIRED_CONSENTS.some((item) => item.type === consent.type);
              return (
                <Pressable
                  key={consent.type}
                  onPress={() => setSelected((current) => {
                    const next = new Set(current);
                    if (on) next.delete(consent.type);
                    else next.add(consent.type);
                    return next;
                  })}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: on }}
                  style={styles.consentRow}
                >
                  <Ionicons name={on ? "checkbox" : "square-outline"} size={24} color={on ? colors.primary : colors.mutedForeground} />
                  <View style={styles.consentCopy}>
                    <Text style={styles.consentTitle}>{consent.title}{required ? " (필수)" : ""}</Text>
                    <Text style={styles.consentBody}>{consent.body}</Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
          <View style={styles.footer}>
            {message ? <Text style={styles.error}>{message}</Text> : null}
            <Button label={busy ? "저장하고 있어요" : "동의하고 다음"} disabled={!requiredAccepted || busy} onPress={() => void saveConsentsAndStartBaseline()} size="lg" />
          </View>
        </View>
      ) : null}

      {step === "baseline" ? (
        <ConversationStep
          line="좋아요. 이제 현재 상태를 알아보기 위한 간단한 CIST 검사를 진행할게요. 진단이 아니라 앞으로의 변화를 비교하기 위한 기준이에요."
          guide="마이크를 누르고 메모이의 질문에 천천히 답해 주세요."
          buttonLabel="검사 시작하기"
          busy={busy}
          onPress={() => navigation.reset({ index: 0, routes: [{ name: "Elder" }] })}
        />
      ) : null}
    </SafeAreaView>
  );
}

function ConversationHeader({ line }: { line: string }) {
  return (
    <View style={styles.characterBlock}>
      <Memoi3D character={DEFAULT_MEMOI} mouthSet={DEFAULT_MOUTH_SET} height={130} spinnerColor={colors.primary} style={{ width: 170 }} />
      <SpeechBubble text={line} side="below" />
    </View>
  );
}

function ConversationStep({
  line,
  guide,
  buttonLabel,
  busy,
  onPress,
}: {
  line: string;
  guide: string;
  buttonLabel: string;
  busy: boolean;
  onPress: () => void;
}) {
  return (
    <View style={styles.stepBody}>
      <ConversationHeader line={line} />
      <View style={styles.guideCard}><Text style={styles.guideText}>{guide}</Text></View>
      <View style={styles.footer}><Button label={busy ? "준비하고 있어요" : buttonLabel} disabled={busy} onPress={onPress} size="lg" /></View>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  progressRow: { flexDirection: "row", justifyContent: "space-between", paddingHorizontal: spacing.xl, paddingVertical: spacing.md, borderBottomWidth: 1, borderBottomColor: colors.border },
  progressItem: { alignItems: "center", gap: 4, flex: 1 },
  progressDot: { width: 24, height: 24, borderRadius: 12, alignItems: "center", justifyContent: "center", backgroundColor: colors.muted },
  progressDotActive: { backgroundColor: colors.primary },
  progressLabel: { fontSize: 11, color: colors.mutedForeground },
  progressLabelActive: { color: colors.primaryDark, fontWeight: fontWeight.semibold },
  stepBody: { flex: 1, paddingHorizontal: spacing.xl, paddingBottom: spacing.xl },
  characterBlock: { alignItems: "center", gap: spacing.md, paddingTop: spacing.xl, paddingBottom: spacing.xl },
  guideCard: { backgroundColor: colors.secondary, borderRadius: radius.xl, padding: spacing.lg, marginTop: spacing.md },
  guideText: { color: colors.primaryDark, fontSize: fontSize.body, lineHeight: 24, textAlign: "center" },
  nameCard: { gap: spacing.sm },
  fieldLabel: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  nameInput: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.lg, paddingHorizontal: spacing.md, paddingVertical: spacing.md, fontSize: fontSize.bodyLg, color: colors.foreground, backgroundColor: colors.white },
  help: { fontSize: fontSize.caption, color: colors.mutedForeground },
  consentList: { gap: spacing.xs },
  consentRow: { flexDirection: "row", gap: spacing.md, paddingVertical: spacing.sm, alignItems: "flex-start" },
  consentCopy: { flex: 1, gap: 3 },
  consentTitle: { fontSize: fontSize.body, fontWeight: fontWeight.semibold, color: colors.foreground },
  consentBody: { fontSize: fontSize.caption, color: colors.mutedForeground, lineHeight: 19 },
  footer: { marginTop: "auto", gap: spacing.sm },
  error: { color: colors.destructive, fontSize: fontSize.caption, lineHeight: 20 },
});
