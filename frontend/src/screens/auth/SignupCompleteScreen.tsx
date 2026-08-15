import React from "react";
import { Share, StyleSheet, View } from "react-native";
import { useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";

import { Button, Card, ScreenHeader, SentenceText as Text } from "@/components/ui";
import type { RootNav, RootStackParamList } from "@/navigation/types";
import { colors, fontSize, fontWeight, spacing } from "@/theme";

/**
 * The account is already created and signed in when this screen is shown.
 * Keeping this acknowledgement separate makes the elder and guardian flows
 * explicit instead of dropping a new account straight into a dashboard.
 */
export default function SignupCompleteScreen() {
  const navigation = useNavigation<RootNav>();
  const route = useRoute<RouteProp<RootStackParamList, "SignupComplete">>();
  const { role, inviteCode, invitationError } = route.params;
  const isGuardian = role === "guardian";

  const shareInvitation = async () => {
    if (!inviteCode) return;
    try {
      await Share.share({
        title: "늘봄 보호자 연결 코드",
        message: `늘봄 보호자 연결 코드: ${inviteCode}\n앱의 초대 코드 입력란에 입력해 주세요.`,
      });
    } catch {
      // Sharing is optional; the code remains selectable for manual copying.
    }
  };

  const continueToApp = () => {
    navigation.reset({
      index: 0,
      routes: [
        isGuardian
          ? { name: "Guardian" }
          : { name: "ElderProfile", params: { inviteCode } },
      ],
    });
  };

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScreenHeader
        title="가입이 완료됐어요"
        subtitle={isGuardian ? "보호자 계정이 준비됐어요." : "이제 늘봄과 첫 만남을 시작해요."}
      />

      <View style={styles.body}>
        <View style={styles.hero}>
          <View style={styles.successCircle}>
            <Ionicons name="checkmark" size={38} color={colors.white} />
          </View>
          <Text style={styles.title}>{isGuardian ? "늘봄 보호자로 시작해요" : "늘봄에 오신 걸 환영해요"}</Text>
          <Text style={styles.description}>
            {isGuardian
              ? "어르신을 연결하면 활동 기록과 리포트를 확인할 수 있어요."
              : "기본정보와 고령자 기능 동의를 확인한 뒤 메모이와 첫 만남을 시작해요."}
          </Text>
        </View>

        {isGuardian ? (
          <Card style={styles.invitationCard}>
            <Text style={styles.cardEyebrow}>보호자 초대코드</Text>
            {inviteCode ? (
              <>
                <Text selectable accessibilityLabel="보호자 초대코드" style={styles.inviteCode}>
                  {inviteCode}
                </Text>
                <Text style={styles.cardHint}>코드를 선택해 복사하거나 바로 공유해 주세요.</Text>
                <Button
                  label="초대코드 공유하기"
                  icon="share-social-outline"
                  variant="outline"
                  onPress={() => void shareInvitation()}
                />
              </>
            ) : (
              <Text style={styles.errorText}>
                {invitationError ?? "초대코드를 발급하지 못했어요."}
                {"\n"}보호자 화면의 연결 관리에서 다시 발급할 수 있어요.
              </Text>
            )}
          </Card>
        ) : (
          <Card style={styles.infoCard}>
            <Text style={styles.cardEyebrow}>다음 단계</Text>
            <Text style={styles.cardText}>
              기본정보를 입력하고 고령자 기능에 필요한 동의를 확인한 뒤, 캐릭터 인사와 초기 인지 활동 확인을 차례대로 진행해요.
            </Text>
          </Card>
        )}

        <View style={styles.footer}>
          <Button
            label={isGuardian ? "보호자 화면으로 이동" : "기본정보 입력하기"}
            onPress={continueToApp}
            size="lg"
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: { flex: 1, paddingHorizontal: spacing.xl, paddingVertical: spacing.xxl, gap: spacing.xl },
  hero: { alignItems: "center", gap: spacing.md, paddingTop: spacing.xl },
  successCircle: {
    width: 82,
    height: 82,
    borderRadius: 41,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primary,
  },
  title: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.foreground, textAlign: "center" },
  description: { fontSize: fontSize.body, lineHeight: 23, color: colors.mutedForeground, textAlign: "center" },
  invitationCard: { gap: spacing.md, alignItems: "center" },
  infoCard: { gap: spacing.sm },
  cardEyebrow: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.primary },
  cardText: { fontSize: fontSize.body, lineHeight: 23, color: colors.foreground },
  inviteCode: {
    fontSize: 34,
    lineHeight: 42,
    letterSpacing: 8,
    fontWeight: fontWeight.bold,
    color: colors.primary,
  },
  cardHint: { fontSize: fontSize.caption, color: colors.mutedForeground, textAlign: "center" },
  errorText: { fontSize: fontSize.caption, lineHeight: 20, color: colors.destructive, textAlign: "center" },
  footer: { marginTop: "auto" },
});
