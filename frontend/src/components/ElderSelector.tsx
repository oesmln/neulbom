import React from "react";
import { Pressable, ScrollView, StyleSheet } from "react-native";
import { useIsFocused } from "@react-navigation/native";

import { useApp } from "@/store/AppContext";
import { guardian as guardianApi } from "@/api";
import { useApi } from "@/hooks/useApi";
import type { ElderSummaryResponse } from "@/api/types";
import { colors, guardian, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Caption, Card, SentenceText as Text } from "@/components/ui";

/**
 * 보호자 화면 공용 "확인할 어르신" 선택 칩.
 *
 * 선택은 전역 `selectedElderId`에 반영되므로 어느 화면에서 바꿔도 홈·추이·기록이
 * 함께 따라간다. 연결된 어르신이 1명 이하면 선택할 것이 없으므로 그리지 않는다.
 * 칩이 화면 폭을 넘어가면 좌우로 스크롤한다.
 *
 * `elders`를 넘기면 그 목록을 쓰고(이미 목록을 불러온 화면), 넘기지 않으면
 * 컴포넌트가 직접 연결 목록을 불러온다.
 */
export default function ElderSelector({ elders }: { elders?: ElderSummaryResponse[] }) {
  const { userId, selectedElderId, setSelectedElderId } = useApp();
  const isFocused = useIsFocused();
  const fetched = useApi(
    () => guardianApi.elders(userId as string, "active"),
    [userId, isFocused],
    { enabled: !!userId && isFocused && elders == null },
  );
  const items = elders ?? fetched.data?.elders ?? [];
  if (items.length <= 1) return null;
  const currentId = selectedElderId ?? items[0]?.elder_id ?? null;

  return (
    <Card style={styles.selector}>
      <Caption>확인할 어르신</Caption>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.options}
      >
        {items.map((elder) => {
          const selected = elder.elder_id === currentId;
          return (
            <Pressable
              key={elder.elder_id}
              onPress={() => setSelectedElderId(elder.elder_id)}
              accessibilityRole="button"
              accessibilityLabel={`${elder.elder_name} 어르신 선택`}
              accessibilityState={{ selected }}
              style={[styles.option, selected && styles.optionSelected]}
            >
              <Text style={[styles.optionLabel, selected && { color: guardian.blueDark }]}>
                {elder.elder_name}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </Card>
  );
}

const styles = StyleSheet.create({
  selector: { marginBottom: spacing.lg, gap: spacing.sm },
  options: { flexDirection: "row", gap: spacing.sm, paddingRight: spacing.sm },
  option: {
    minHeight: 44,
    justifyContent: "center",
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
    paddingHorizontal: spacing.lg,
  },
  optionSelected: { borderColor: guardian.blue, backgroundColor: guardian.blueLight },
  optionLabel: {
    fontSize: fontSize.caption,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
  },
});
