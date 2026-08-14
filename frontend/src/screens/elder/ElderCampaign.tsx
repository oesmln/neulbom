import React from "react";
import { View, StyleSheet } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { ElderNav } from "@/navigation/types";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, ScreenHeader, Card, Button, Body, Caption, Pill, SentenceText as Text } from "@/components/ui";

const GAMES = [
  { icon: "grid", title: "카드 짝 맞추기", desc: "같은 그림을 찾아요", points: 50 },
  { icon: "text", title: "낱말 잇기", desc: "끝말잇기로 어휘력 UP", points: 40 },
  { icon: "calculator", title: "숫자 계산", desc: "간단한 암산 게임", points: 30 },
];

const EVENTS = [
  { title: "마을회관 기억력 교실", when: "매주 화 · 오전 10시", place: "행복경로당" },
  { title: "치매 예방 걷기 대회", when: "7월 20일 · 오전 9시", place: "중앙공원" },
];

export default function ElderCampaignScreen() {
  const navigation = useNavigation<ElderNav>();

  return (
    <Screen
      header={
        <ScreenHeader
          title="지역 캠페인"
          subtitle="우리 동네 인지건강 소식을 확인하세요"
          onBack={() => navigation.goBack()}
          backLabel="홈"
        />
      }
    >
      <Card color={colors.accent}>
        <Pill label="이번 주 이벤트" color="rgba(255,255,255,0.2)" textColor={colors.white} icon="sparkles" />
        <Text style={styles.heroTitle}>기억력 게임하고{"\n"}마을 포인트 받기 🎁</Text>
        <Body style={{ color: colors.white, marginTop: spacing.xs }}>내 포인트: 320P</Body>
      </Card>

      <Text style={styles.section}>두뇌 게임</Text>
      <View style={{ gap: spacing.md }}>
        {GAMES.map((g) => (
          <Card key={g.title}>
            <View style={styles.row}>
              <View style={styles.iconWrap}>
                <Ionicons name={g.icon as keyof typeof Ionicons.glyphMap} size={24} color={colors.primary} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.gameTitle}>{g.title}</Text>
                <Caption style={{ marginTop: 2 }}>{g.desc}</Caption>
              </View>
              <Pill label={`+${g.points}P`} color={colors.secondary} textColor={colors.secondaryForeground} />
            </View>
            {/* Campaigns are Phase 2 (api-spec 11); until `GET /campaigns` exists
                this sends the user to the game hub rather than doing nothing. */}
            <Button
              label="게임 시작"
              variant="secondary"
              size="sm"
              style={{ marginTop: spacing.md }}
              onPress={() => navigation.navigate("ElderTabs", { screen: "ElderGameHub" })}
            />
          </Card>
        ))}
      </View>

      <Text style={styles.section}>우리 동네 소식</Text>
      <View style={{ gap: spacing.md }}>
        {EVENTS.map((e) => (
          <Card key={e.title}>
            <Text style={styles.gameTitle}>{e.title}</Text>
            <View style={[styles.metaRow, { marginTop: spacing.sm }]}>
              <Ionicons name="time-outline" size={16} color={colors.mutedForeground} />
              <Caption style={{ marginLeft: 6 }}>{e.when}</Caption>
            </View>
            <View style={styles.metaRow}>
              <Ionicons name="location-outline" size={16} color={colors.mutedForeground} />
              <Caption style={{ marginLeft: 6 }}>{e.place}</Caption>
            </View>
          </Card>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  heroTitle: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.white, marginTop: spacing.md, lineHeight: 30 },
  section: { fontSize: fontSize.bodyLg, fontWeight: fontWeight.bold, color: colors.foreground, marginTop: spacing.xl, marginBottom: spacing.md },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  iconWrap: { width: 48, height: 48, borderRadius: radius.md, backgroundColor: colors.secondary, alignItems: "center", justifyContent: "center" },
  gameTitle: { fontSize: fontSize.body, fontWeight: fontWeight.bold, color: colors.foreground },
  metaRow: { flexDirection: "row", alignItems: "center", marginTop: 4 },
});
