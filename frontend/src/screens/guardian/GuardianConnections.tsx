import React from "react";
import {
  Alert,
  Pressable,
  StyleSheet,
  TextInput,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import * as Clipboard from "expo-clipboard";
import { useNavigation } from "@react-navigation/native";

import { guardian as guardianApi } from "@/api";
import { apiErrorMessage } from "@/api/errors";
import type {
  ElderSummaryResponse,
  InvitationCreateResponse,
  Uuid,
} from "@/api/types";
import { useApi } from "@/hooks/useApi";
import { useApp } from "@/store/AppContext";
import type { GuardianNav } from "@/navigation/types";
import { colors, fontSize, fontWeight, guardian, radius, spacing } from "@/theme";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  Screen,
  ScreenHeader,
  SentenceText as Text,
} from "@/components/ui";

const SCOPES = [
  { value: "screening", label: "검사 결과" },
  { value: "summary", label: "대화 요약" },
  { value: "diary", label: "일기" },
  { value: "activity", label: "활동·게임" },
] as const;

function remainingTimeLabel(expiresAt: string, now: number): string {
  const expiresAtMs = new Date(expiresAt).getTime();
  if (Number.isNaN(expiresAtMs)) return "남은 시간을 확인할 수 없어요";
  const remainingSeconds = Math.max(0, Math.ceil((expiresAtMs - now) / 1000));
  if (remainingSeconds === 0) return "초대 코드가 만료됐어요";
  const minutes = Math.floor(remainingSeconds / 60);
  const seconds = remainingSeconds % 60;
  return `남은 시간 ${minutes}분 ${String(seconds).padStart(2, "0")}초`;
}

export default function GuardianConnectionsScreen() {
  const navigation = useNavigation<GuardianNav>();
  const { userId, selectedElderId, setSelectedElderId } = useApp();
  const [relation, setRelation] = React.useState("보호자");
  const [scopes, setScopes] = React.useState<string[]>(SCOPES.map((scope) => scope.value));
  const [invitation, setInvitation] = React.useState<InvitationCreateResponse | null>(null);
  const [creating, setCreating] = React.useState(false);
  const [inviteError, setInviteError] = React.useState<string | null>(null);
  const [now, setNow] = React.useState(Date.now());
  const [copied, setCopied] = React.useState(false);

  React.useEffect(() => {
    if (!invitation) return;
    setNow(Date.now());
    const intervalId = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(intervalId);
  }, [invitation]);

  React.useEffect(() => {
    if (!copied) return;
    const timeoutId = setTimeout(() => setCopied(false), 2000);
    return () => clearTimeout(timeoutId);
  }, [copied]);

  const links = useApi(() => guardianApi.elders(userId as string), [userId], {
    enabled: !!userId,
  });

  React.useEffect(() => {
    if (!links.data) return;
    const selectableElders = links.data.elders.filter(
      (elder) => elder.status === "active" && elder.consent_status === "agreed",
    );
    const selectionIsValid = selectableElders.some(
      (elder) => elder.elder_id === selectedElderId,
    );
    if (!selectionIsValid) {
      setSelectedElderId(selectableElders[0]?.elder_id ?? null);
    }
  }, [links.data, selectedElderId, setSelectedElderId]);

  const createInvitation = async () => {
    if (creating || scopes.length === 0) return;
    setCreating(true);
    setInviteError(null);
    try {
      const created = await guardianApi.createInvitation({
        relation: relation.trim() || undefined,
        access_scope: scopes,
        expires_in: 600,
      });
      setInvitation(created);
      setCopied(false);
      setNow(Date.now());
    } catch (cause) {
      setInviteError(apiErrorMessage(cause));
    } finally {
      setCreating(false);
    }
  };

  const expiresAtMs = invitation ? new Date(invitation.expires_at).getTime() : 0;
  const invitationExpired = !!invitation && (!Number.isFinite(expiresAtMs) || expiresAtMs <= now);

  const copyInvitation = async () => {
    if (!invitation || invitationExpired) return;
    try {
      await Clipboard.setStringAsync(invitation.invite_code);
      setCopied(true);
    } catch (cause) {
      setInviteError(apiErrorMessage(cause));
    }
  };

  const afterRevoke = (elderId: Uuid) => {
    if (selectedElderId === elderId) setSelectedElderId(null);
    links.reload();
  };

  const selectElder = (elder: ElderSummaryResponse) => {
    if (elder.status !== "active" || elder.consent_status !== "agreed") return;
    setSelectedElderId(elder.elder_id);
    navigation.navigate("GuardianTabs", { screen: "GuardianDashboard" });
  };

  return (
    <Screen
      header={
        <ScreenHeader
          title="보호자 연결 관리"
          subtitle="초대하고 열람 범위를 관리하세요"
          color={guardian.blue}
          onBack={() => navigation.goBack()}
        />
      }
      contentStyle={{ gap: spacing.lg }}
    >
      <Card style={styles.section}>
        <View style={styles.sectionHeading}>
          <View style={styles.sectionIcon}>
            <Ionicons name="person-add-outline" size={20} color={guardian.blue} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.sectionTitle}>어르신 초대하기</Text>
            <Text style={styles.sectionDescription}>코드는 한 번만 표시되고 10분 뒤 만료돼요.</Text>
          </View>
        </View>

        <Text style={styles.fieldLabel}>관계</Text>
        <TextInput
          value={relation}
          onChangeText={setRelation}
          placeholder="예: 딸, 아들, 사회복지사"
          maxLength={100}
          accessibilityLabel="어르신과의 관계"
          style={styles.input}
        />

        <Text style={styles.fieldLabel}>열람 범위</Text>
        <ScopeSelector selected={scopes} onChange={setScopes} />

        {inviteError ? <Text style={styles.errorText}>{inviteError}</Text> : null}

        {invitation ? (
          <View style={styles.invitationBox}>
            <Text style={styles.invitationGuide}>어르신께 아래 코드를 알려 주세요</Text>
            <Pressable
              onPress={() => void copyInvitation()}
              disabled={invitationExpired}
              accessibilityRole="button"
              accessibilityLabel={`초대 코드 ${invitation.invite_code} 복사`}
            >
              <Text selectable style={[styles.inviteCode, invitationExpired && styles.expiredCode]}>
                {invitation.invite_code}
              </Text>
            </Pressable>
            <Text style={[styles.expiration, invitationExpired && styles.expiredText]}>
              {remainingTimeLabel(invitation.expires_at, now)}
            </Text>
            <Button
              label={copied ? "복사됐어요" : "초대 코드 복사"}
              icon={copied ? "checkmark-outline" : "copy-outline"}
              disabled={invitationExpired}
              onPress={() => void copyInvitation()}
              style={{ backgroundColor: guardian.blue }}
            />
          </View>
        ) : (
          <Button
            label={creating ? "초대 코드 생성 중" : "초대 코드 생성"}
            disabled={creating || scopes.length === 0}
            onPress={() => void createInvitation()}
            style={{ backgroundColor: guardian.blue }}
          />
        )}

        {invitation ? (
          <Button
            label={creating ? "새 초대 코드 생성 중" : "새 초대 코드 생성"}
            variant="outline"
            disabled={creating || scopes.length === 0}
            onPress={() => void createInvitation()}
          />
        ) : null}
      </Card>

      <View style={styles.listHeading}>
        <View style={{ flex: 1 }}>
          <Text style={styles.listTitle}>연결된 어르신</Text>
          <Text style={styles.sectionDescription}>
            현재 보고 있는 어르신을 확인하고 다른 어르신으로 전환할 수 있어요.
          </Text>
        </View>
        <Pressable
          onPress={() => void links.reload()}
          accessibilityRole="button"
          accessibilityLabel="연결된 어르신 목록 새로고침"
          style={styles.refreshButton}
        >
          <Ionicons name="refresh-outline" size={17} color={guardian.blue} />
          <Text style={styles.refreshLabel}>새로고침</Text>
        </Pressable>
      </View>

      {links.loading && !links.data ? <LoadingState label="연결 목록을 불러오는 중이에요" /> : null}
      {links.error && !links.data ? (
        <ErrorState message={apiErrorMessage(links.error)} onRetry={links.reload} />
      ) : null}
      {links.data?.elders.length === 0 ? (
        <EmptyState message="아직 연결된 어르신이 없어요" icon="people-outline" />
      ) : null}
      {links.data?.elders.map((elder) => (
        <ConnectionCard
          key={elder.link_id ?? elder.elder_id}
          elder={elder}
          selected={elder.elder_id === selectedElderId}
          onSelect={() => selectElder(elder)}
          onChanged={links.reload}
          onRevoked={() => afterRevoke(elder.elder_id)}
        />
      ))}
    </Screen>
  );
}

function ScopeSelector({
  selected,
  onChange,
  disabled = false,
}: {
  selected: string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
}) {
  const toggle = (scope: string) => {
    if (disabled) return;
    onChange(
      selected.includes(scope)
        ? selected.filter((item) => item !== scope)
        : [...selected, scope],
    );
  };

  return (
    <View style={styles.scopeGrid}>
      {SCOPES.map((scope) => {
        const checked = selected.includes(scope.value);
        return (
          <Pressable
            key={scope.value}
            onPress={() => toggle(scope.value)}
            disabled={disabled}
            accessibilityRole="checkbox"
            accessibilityLabel={`${scope.label} 열람`}
            accessibilityState={{ checked, disabled }}
            style={[
              styles.scopeChip,
              checked && styles.scopeChipSelected,
              disabled && { opacity: 0.6 },
            ]}
          >
            <Ionicons
              name={checked ? "checkmark-circle" : "ellipse-outline"}
              size={18}
              color={checked ? guardian.blue : colors.mutedForeground}
            />
            <Text style={[styles.scopeLabel, checked && { color: guardian.blueDark }]}>
              {scope.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function ConnectionCard({
  elder,
  selected,
  onSelect,
  onChanged,
  onRevoked,
}: {
  elder: ElderSummaryResponse;
  selected: boolean;
  onSelect: () => void;
  onChanged: () => void;
  onRevoked: () => void;
}) {
  const [scopes, setScopes] = React.useState<string[]>(elder.access_scope ?? []);
  const [relation, setRelation] = React.useState("");
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const active = elder.status === "active";
  const revoked = elder.status === "revoked";
  const selectable = active && elder.consent_status === "agreed";

  React.useEffect(() => setScopes(elder.access_scope ?? []), [elder.access_scope]);

  const save = async () => {
    if (!elder.link_id || !active || scopes.length === 0 || saving) return;
    setSaving(true);
    setError(null);
    try {
      await guardianApi.updateLink(elder.link_id, {
        access_scope: scopes,
        relation: relation.trim() || undefined,
      });
      setRelation("");
      onChanged();
    } catch (cause) {
      setError(apiErrorMessage(cause));
    } finally {
      setSaving(false);
    }
  };

  const revoke = () => {
    if (!elder.link_id || revoked) return;
    Alert.alert(
      "보호자 연결을 해제할까요?",
      `${elder.elder_name} 어르신의 검사, 일기와 활동 정보를 더 이상 볼 수 없어요.`,
      [
        { text: "취소", style: "cancel" },
        {
          text: "연결 해제",
          style: "destructive",
          onPress: () => {
            setSaving(true);
            setError(null);
            void guardianApi
              .revokeLink(elder.link_id as Uuid)
              .then(onRevoked)
              .catch((cause) => setError(apiErrorMessage(cause)))
              .finally(() => setSaving(false));
          },
        },
      ],
    );
  };

  const status = active
    ? { label: "연결됨", color: guardian.blueDark, background: guardian.blueLight }
    : revoked
      ? { label: "해제됨", color: colors.mutedForeground, background: colors.muted }
      : { label: "동의 대기", color: colors.warning, background: colors.warningLight };

  return (
    <Card style={styles.section}>
      <View style={styles.connectionHeading}>
        <View style={{ flex: 1 }}>
          <Text style={styles.elderName}>{elder.elder_name} 어르신</Text>
          <Text style={styles.sectionDescription}>
            {elder.consent_status === "required"
              ? "어르신의 보호자 접근 동의를 기다리고 있어요."
              : "허용할 정보를 필요에 맞게 변경할 수 있어요."}
          </Text>
        </View>
        <View style={styles.statusBadges}>
          {selected && selectable ? (
            <Badge label="현재 어르신" color={colors.white} background={guardian.blue} />
          ) : null}
          <Badge label={status.label} color={status.color} background={status.background} />
        </View>
      </View>

      {selectable ? (
        <Button
          label={selected ? "현재 보고 있는 어르신" : `${elder.elder_name} 어르신으로 전환`}
          icon={selected ? "checkmark-circle-outline" : "swap-horizontal-outline"}
          disabled={selected || saving}
          onPress={onSelect}
          style={{ backgroundColor: guardian.blue }}
        />
      ) : null}

      <ScopeSelector selected={scopes} onChange={setScopes} disabled={!active || saving} />

      {active ? (
        <>
          <TextInput
            value={relation}
            onChangeText={setRelation}
            placeholder="관계를 바꾸려면 새 관계 입력"
            maxLength={100}
            accessibilityLabel={`${elder.elder_name} 어르신과의 새 관계`}
            style={styles.input}
          />
          <Button
            label={saving ? "변경 저장 중" : "변경 저장"}
            disabled={saving || scopes.length === 0}
            onPress={() => void save()}
            style={{ backgroundColor: guardian.blue }}
          />
        </>
      ) : null}

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      {!revoked ? (
        <Button
          label="연결 해제"
          variant="danger"
          disabled={saving}
          onPress={revoke}
        />
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  section: { gap: spacing.lg },
  sectionHeading: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  sectionIcon: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: guardian.blueLight,
  },
  sectionTitle: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },
  listHeading: { flexDirection: "row", alignItems: "flex-start", gap: spacing.md },
  listTitle: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.foreground },
  refreshButton: {
    minHeight: 38,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs,
    borderWidth: 1,
    borderColor: guardian.blue,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.md,
  },
  refreshLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: guardian.blue },
  sectionDescription: { marginTop: 3, fontSize: fontSize.caption, color: colors.mutedForeground, lineHeight: 20 },
  fieldLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.foreground },
  input: {
    minHeight: 50,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.inputBackground,
    paddingHorizontal: spacing.lg,
    fontSize: fontSize.body,
    color: colors.foreground,
  },
  scopeGrid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  scopeChip: {
    width: "48%",
    minHeight: 44,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    paddingHorizontal: spacing.md,
  },
  scopeChipSelected: { borderColor: guardian.blue, backgroundColor: guardian.blueLight },
  scopeLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.mutedForeground },
  invitationBox: {
    alignItems: "center",
    gap: spacing.sm,
    borderRadius: radius.xl,
    backgroundColor: guardian.blueLight,
    padding: spacing.lg,
  },
  invitationGuide: { fontSize: fontSize.caption, color: guardian.blueDark },
  inviteCode: {
    fontSize: 34,
    fontWeight: fontWeight.bold,
    color: guardian.blueDark,
    letterSpacing: 7,
  },
  expiration: { marginBottom: spacing.sm, fontSize: fontSize.caption, color: colors.mutedForeground },
  expiredCode: { color: colors.mutedForeground },
  expiredText: { color: colors.destructive },
  connectionHeading: { flexDirection: "row", alignItems: "flex-start", gap: spacing.md },
  statusBadges: { alignItems: "flex-end", gap: spacing.xs },
  elderName: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground },
  errorText: { fontSize: fontSize.caption, color: colors.destructive, textAlign: "center" },
});
