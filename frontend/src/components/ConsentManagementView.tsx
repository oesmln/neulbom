import React from "react";
import { View, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { SentenceText as Text } from "@/components/ui";

import { users } from "@/api";
import { ApiError, apiErrorMessage } from "@/api/errors";
import type { ConsentResponse, ConsentType, Uuid } from "@/api/types";
import { useApi } from "@/hooks/useApi";
import { colors, fontSize, fontWeight, radius, spacing } from "@/theme";

const CONSENTS: Array<{
  type: ConsentType;
  title: string;
  description: string;
  required?: boolean;
  editable?: boolean;
}> = [
  { type: "terms_of_service", title: "이용약관", description: "서비스 이용을 위한 기본 약관", required: true },
  { type: "privacy_collection", title: "개인정보 수집·이용", description: "서비스 제공에 필요한 개인정보 처리", required: true },
  { type: "sensitive_health", title: "건강·민감정보 처리", description: "인지 활동 참고 정보 처리", required: true },
  { type: "analysis", title: "인지 활동 분석", description: "검사·대화 결과를 변화 관찰에 활용", editable: true },
  { type: "voice_collection", title: "음성 수집·분석", description: "음성 답변 저장 및 음성 분석", editable: true },
  { type: "guardian_access", title: "보호자 접근·공유", description: "연결된 보호자에게 기록과 리포트 공유", editable: true },
  { type: "data_sharing", title: "서비스 개선 데이터 활용", description: "서비스 품질 개선을 위한 선택 동의", editable: true },
  { type: "research_use", title: "연구 목적 활용", description: "비식별 정보의 연구 활용", editable: true },
];

function latestByType(items: ConsentResponse[]) {
  const latest = new Map<ConsentType, ConsentResponse>();
  for (const item of items) {
    if (!latest.has(item.consent_type)) latest.set(item.consent_type, item);
  }
  return latest;
}

export default function ConsentManagementView({ userId }: { userId: Uuid | null }) {
  const request = useApi(() => users.consents(userId as string), [userId], { enabled: !!userId });
  const [expanded, setExpanded] = React.useState(false);
  const [busyType, setBusyType] = React.useState<ConsentType | null>(null);
  const [message, setMessage] = React.useState<string | null>(null);
  const latest = latestByType(request.data?.consents ?? []);

  const toggle = async (type: ConsentType) => {
    if (!userId || busyType) return;
    const current = latest.get(type)?.agreed ?? false;
    setBusyType(type);
    setMessage(null);
    try {
      await users.saveConsent(userId, {
        consent_type: type,
        agreed: !current,
        agreed_at: new Date().toISOString(),
        version: `settings-${Date.now()}`,
      });
      request.reload();
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusyType(null);
    }
  };

  return (
    <View style={styles.group}>
      <Pressable
        onPress={() => setExpanded((current) => !current)}
        accessibilityRole="button"
        accessibilityLabel="동의 내역 관리"
        accessibilityState={{ expanded }}
        style={styles.header}
      >
        <Text style={styles.groupTitle}>동의 내역 관리</Text>
        <Ionicons
          name={expanded ? "chevron-up" : "chevron-down"}
          size={22}
          color={colors.mutedForeground}
        />
      </Pressable>

      {expanded ? (
        <View style={styles.details}>
          <Text style={styles.note}>필수 동의는 서비스 이용을 위해 유지되며, 선택 동의는 언제든 바꿀 수 있어요.</Text>
          {request.loading && !request.data ? <Text style={styles.note}>동의 내역을 불러오는 중이에요.</Text> : null}
          {message ? <Text style={styles.error}>{message}</Text> : null}
          {CONSENTS.map((item) => {
            const agreed = latest.get(item.type)?.agreed ?? false;
            return (
              <View key={item.type} style={styles.row}>
                <View style={styles.copy}>
                  <Text style={styles.title}>{item.title}{item.required ? " (필수)" : ""}</Text>
                  <Text style={styles.description}>{item.description}</Text>
                </View>
                {item.editable ? (
                  <Pressable
                    onPress={() => void toggle(item.type)}
                    disabled={busyType !== null}
                    accessibilityRole="switch"
                    accessibilityState={{ checked: agreed, disabled: busyType !== null }}
                    style={[styles.switch, agreed && styles.switchOn]}
                  >
                    <View style={[styles.knob, agreed && styles.knobOn]} />
                  </Pressable>
                ) : (
                  <Ionicons name={agreed ? "checkmark-circle" : "ellipse-outline"} size={22} color={agreed ? colors.primary : colors.mutedForeground} />
                )}
              </View>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  group: { backgroundColor: colors.card, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, marginBottom: spacing.lg, overflow: "hidden" },
  header: { minHeight: 72, paddingHorizontal: spacing.lg, paddingVertical: spacing.md, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  groupTitle: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.semibold, color: colors.foreground },
  details: { paddingHorizontal: spacing.xl, paddingBottom: spacing.xl },
  note: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 22, marginBottom: spacing.md },
  error: { fontSize: fontSize.body, color: colors.destructive, lineHeight: 22, marginBottom: spacing.md },
  row: { flexDirection: "row", alignItems: "flex-start", gap: spacing.md, paddingVertical: spacing.md, borderTopWidth: 1, borderTopColor: colors.border },
  copy: { flex: 1, gap: spacing.xs },
  title: { fontSize: fontSize.bodyLg, lineHeight: 23, fontWeight: fontWeight.semibold, color: colors.foreground },
  description: { fontSize: fontSize.body, color: colors.mutedForeground, lineHeight: 22 },
  switch: { width: 44, height: 26, borderRadius: 13, justifyContent: "center", backgroundColor: colors.switchBackground },
  switchOn: { backgroundColor: colors.primary },
  knob: { position: "absolute", left: 4, width: 18, height: 18, borderRadius: 9, backgroundColor: colors.white },
  knobOn: { left: 22 },
});
