import React from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { counseling, guardian as guardianApi } from "@/api";
import type {
  CounselingAppointmentResponse,
  CounselingType,
  ElderSummaryResponse,
} from "@/api/types";
import { apiErrorMessage } from "@/api/errors";
import { useApi } from "@/hooks/useApi";
import { useApp } from "@/store/AppContext";
import { guardian, colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import {
  Button,
  Card,
  Caption,
  EmptyState,
  ErrorState,
  LoadingState,
  Screen,
  ScreenHeader,
} from "@/components/ui";

const TYPE_OPTIONS: { key: CounselingType; label: string }[] = [
  { key: "cognitive_screening", label: "인지 검사 상담" },
  { key: "neurology", label: "신경과" },
  { key: "counseling", label: "상담" },
  { key: "other", label: "기타" },
];

function defaultAppointmentInput() {
  const date = new Date(Date.now() + 86_400_000);
  date.setHours(10, 0, 0, 0);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(
    date.getMinutes(),
  ).padStart(2, "0")}`;
}

function parseAppointmentAt(value: string): string | null {
  const match = /^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})$/.exec(value.trim());
  if (!match) return null;
  const date = new Date(`${match[1]}T${match[2]}:00+09:00`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function appointmentLabel(appointment: CounselingAppointmentResponse) {
  return new Date(appointment.appointment_at).toLocaleString("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "numeric",
    minute: "2-digit",
  });
}

const STATUS_LABEL: Record<string, string> = {
  requested: "예약 신청",
  confirmed: "예약 확정",
  cancelled: "취소됨",
  completed: "진료 완료",
};

export default function GuardianAppointmentsScreen() {
  const { userId, selectedElderId, setSelectedElderId } = useApp();
  const [elderId, setElderId] = React.useState<string | null>(selectedElderId);
  const [centerId, setCenterId] = React.useState<string | null>(null);
  const [appointmentAt, setAppointmentAt] = React.useState(defaultAppointmentInput);
  const [consultationType, setConsultationType] = React.useState<CounselingType>("counseling");
  const [note, setNote] = React.useState("");
  const [privacyAgreed, setPrivacyAgreed] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);

  const elders = useApi(
    () => guardianApi.elders(userId as string, "active"),
    [userId],
    { enabled: !!userId },
  );
  const centers = useApi(() => counseling.centers("26"), [], { enabled: true });
  const appointments = useApi(() => counseling.appointments(), [], { enabled: !!userId });

  const elderItems: ElderSummaryResponse[] = elders.data?.elders ?? [];
  const selectedElder = elderItems.find((elder) => elder.elder_id === elderId) ?? null;
  const selectedCenter = centers.data?.centers.find((center) => center.center_id === centerId) ?? null;

  React.useEffect(() => {
    if (!elderId && selectedElderId) setElderId(selectedElderId);
    if (!elderId && elderItems[0]) {
      setElderId(elderItems[0].elder_id);
      setSelectedElderId(elderItems[0].elder_id);
    }
  }, [elderId, elderItems, selectedElderId, setSelectedElderId]);

  const create = async () => {
    const parsed = parseAppointmentAt(appointmentAt);
    if (!elderId || !centerId || !parsed) {
      setMessage("어르신, 기관, 날짜·시간을 모두 확인해 주세요. 예: 2026-08-20 10:00");
      return;
    }
    if (!privacyAgreed) {
      setMessage("개인정보 제공 및 예약 동의가 필요해요.");
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      await counseling.createAppointment({
        elder_id: elderId,
        center_id: centerId,
        appointment_at: parsed,
        consultation_type: consultationType,
        note: note.trim() || undefined,
        privacy_agreed: true,
      });
      setNote("");
      setPrivacyAgreed(false);
      appointments.reload();
      setMessage("예약 신청이 접수됐어요. 확정되면 알림으로 알려드릴게요.");
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async (appointment: CounselingAppointmentResponse) => {
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      await counseling.cancelAppointment(appointment.appointment_id);
      appointments.reload();
    } catch (cause) {
      setMessage(apiErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen
      header={
        <ScreenHeader
          color={guardian.blue}
          title="전문의 예약"
          subtitle="연결된 어르신의 상담·검사 예약을 신청해요."
        />
      }
    >
      {message ? <Text style={styles.message}>{message}</Text> : null}

      <Card>
        <Text style={styles.sectionTitle}>예약 신청</Text>
        {elders.error ? <ErrorState message={apiErrorMessage(elders.error)} onRetry={elders.reload} /> : null}
        {elderItems.length === 0 && !elders.loading ? (
          <EmptyState message="먼저 연결된 어르신을 등록해 주세요." icon="people-outline" />
        ) : (
          <>
            <Caption>어르신</Caption>
            <View style={styles.chipRow}>
              {elderItems.map((elder) => (
                <Pressable
                  key={elder.elder_id}
                  onPress={() => {
                    setElderId(elder.elder_id);
                    setSelectedElderId(elder.elder_id);
                  }}
                  style={[styles.chip, elder.elder_id === elderId && styles.chipSelected]}
                >
                  <Text style={[styles.chipText, elder.elder_id === elderId && styles.chipTextSelected]}>
                    {elder.elder_name}
                  </Text>
                </Pressable>
              ))}
            </View>

            <Caption style={styles.fieldCaption}>기관</Caption>
            {centers.loading && !centers.data ? <LoadingState label="기관을 불러오는 중이에요" /> : null}
            <View style={styles.chipRow}>
              {(centers.data?.centers ?? []).map((center) => (
                <Pressable
                  key={center.center_id}
                  onPress={() => setCenterId(center.center_id)}
                  style={[styles.chip, center.center_id === centerId && styles.chipSelected]}
                >
                  <Text style={[styles.chipText, center.center_id === centerId && styles.chipTextSelected]}>
                    {center.name}
                  </Text>
                </Pressable>
              ))}
            </View>
            {selectedCenter ? <Caption>{selectedCenter.address}</Caption> : null}

            <Caption style={styles.fieldCaption}>상담 유형</Caption>
            <View style={styles.chipRow}>
              {TYPE_OPTIONS.map((option) => (
                <Pressable
                  key={option.key}
                  onPress={() => setConsultationType(option.key)}
                  style={[styles.chip, option.key === consultationType && styles.chipSelected]}
                >
                  <Text style={[styles.chipText, option.key === consultationType && styles.chipTextSelected]}>
                    {option.label}
                  </Text>
                </Pressable>
              ))}
            </View>

            <Caption style={styles.fieldCaption}>희망 날짜·시간</Caption>
            <TextInput
              value={appointmentAt}
              onChangeText={setAppointmentAt}
              placeholder="2026-08-20 10:00"
              placeholderTextColor={colors.mutedForeground}
              style={styles.input}
              accessibilityLabel="희망 예약 날짜와 시간"
            />
            <Caption>한국 시간 기준으로 입력해 주세요.</Caption>

            <Caption style={styles.fieldCaption}>전달할 내용 (선택)</Caption>
            <TextInput
              value={note}
              onChangeText={setNote}
              placeholder="상담 전에 전달할 내용이 있나요?"
              placeholderTextColor={colors.mutedForeground}
              multiline
              style={[styles.input, styles.noteInput]}
              accessibilityLabel="예약 메모"
            />

            <Pressable
              onPress={() => setPrivacyAgreed((value) => !value)}
              style={styles.consentRow}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: privacyAgreed }}
            >
              <Ionicons
                name={privacyAgreed ? "checkbox" : "square-outline"}
                size={22}
                color={privacyAgreed ? guardian.blue : colors.mutedForeground}
              />
              <Text style={styles.consentText}>예약을 위해 선택한 기관에 일정·상담 정보를 제공하는 데 동의해요.</Text>
            </Pressable>
            <Button label={busy ? "처리 중이에요" : "예약 신청하기"} disabled={busy} onPress={() => void create()} />
          </>
        )}
      </Card>

      <View style={styles.historyHeader}>
        <Text style={styles.sectionTitle}>예약 내역</Text>
        <Caption>{appointments.data?.total ?? 0}건</Caption>
      </View>
      {appointments.error ? <ErrorState message={apiErrorMessage(appointments.error)} onRetry={appointments.reload} /> : null}
      {appointments.loading && !appointments.data ? <LoadingState label="예약 내역을 불러오는 중이에요" /> : null}
      {!appointments.loading && appointments.data?.appointments.length === 0 ? (
        <EmptyState message="아직 신청한 예약이 없어요." icon="calendar-outline" />
      ) : null}
      <View style={{ gap: spacing.md }}>
        {appointments.data?.appointments.map((appointment) => (
          <Card key={appointment.appointment_id}>
            <View style={styles.rowBetween}>
              <View style={{ flex: 1 }}>
                <Text style={styles.appointmentDate}>{appointmentLabel(appointment)}</Text>
                <Caption>{appointment.center_name ?? "상담 기관"}</Caption>
                <Caption>{selectedElder?.elder_name ?? "연결된 어르신"} · {appointment.consultation_type}</Caption>
              </View>
              <Text style={styles.status}>{STATUS_LABEL[appointment.status] ?? appointment.status}</Text>
            </View>
            {appointment.note ? <Caption style={{ marginTop: spacing.sm }}>{appointment.note}</Caption> : null}
            {appointment.status === "requested" || appointment.status === "confirmed" ? (
              <Pressable onPress={() => void cancel(appointment)} style={styles.cancelButton}>
                <Text style={styles.cancelLabel}>예약 취소</Text>
              </Pressable>
            ) : null}
          </Card>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  message: { color: guardian.blueDark, backgroundColor: guardian.blueLight, borderRadius: radius.md, padding: spacing.md, lineHeight: 20 },
  sectionTitle: { color: colors.foreground, fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold },
  fieldCaption: { marginTop: spacing.lg, marginBottom: 6 },
  chipRow: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm, marginTop: spacing.sm },
  chip: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.pill, paddingHorizontal: spacing.md, paddingVertical: 9, backgroundColor: colors.white },
  chipSelected: { borderColor: guardian.blue, backgroundColor: guardian.blueLight },
  chipText: { color: colors.mutedForeground, fontSize: fontSize.caption },
  chipTextSelected: { color: guardian.blueDark, fontWeight: fontWeight.bold },
  input: { marginTop: spacing.sm, minHeight: 50, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingHorizontal: spacing.md, color: colors.foreground, backgroundColor: colors.white, fontSize: fontSize.body },
  noteInput: { minHeight: 76, paddingTop: spacing.md, textAlignVertical: "top" },
  consentRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm, marginVertical: spacing.lg },
  consentText: { flex: 1, color: colors.foreground, fontSize: fontSize.caption, lineHeight: 20 },
  historyHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: spacing.xl },
  rowBetween: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: spacing.md },
  appointmentDate: { color: colors.foreground, fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, marginBottom: 4 },
  status: { color: guardian.blueDark, backgroundColor: guardian.blueLight, borderRadius: radius.pill, paddingHorizontal: spacing.sm, paddingVertical: 5, fontSize: fontSize.badge, fontWeight: fontWeight.bold },
  cancelButton: { alignSelf: "flex-end", marginTop: spacing.md, paddingVertical: 8, paddingHorizontal: spacing.md },
  cancelLabel: { color: colors.destructive, fontSize: fontSize.caption, fontWeight: fontWeight.semibold },
});
