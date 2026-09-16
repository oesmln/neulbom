import React from "react";
import { View, StyleSheet, Pressable } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { SafeAreaView } from "react-native-safe-area-context";

import { RootNav } from "@/navigation/types";
import { useApp } from "@/store/AppContext";
import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Button, SentenceText as Text } from "@/components/ui";
import Memoi3D from "@/components/Memoi3D";
import { DEFAULT_MEMOI, MEMOI_MOUTH_SHAPES } from "@/components/memoiCharacters";
import { destinationForAuth } from "./authRouting";

/**
 * The only screen that uses the dark character stage. Memoi is lit from the
 * front and reads best against it, and the sage primary button needs a surface
 * it can sit on without disappearing.
 */
export default function SplashScreen() {
  const navigation = useNavigation<RootNav>();
  const { ready, role, onboardingStep, baselineCompleted } = useApp();

  React.useEffect(() => {
    if (!ready || !role) return;
    navigation.reset({
      index: 0,
      routes: [
        {
          name: destinationForAuth({ role, onboardingStep, baselineCompleted }),
        },
      ],
    });
  }, [baselineCompleted, navigation, onboardingStep, ready, role]);

  // Dev-only viewer for the mouth-shape models. They exist for the Phase 2
  // TTS/viseme work and nothing drives them yet, so this is the one place they
  // can be looked at on a device. Tapping steps 기본 → 으 → 오 → 에 → ...
  const [shapeIndex, setShapeIndex] = React.useState(-1);
  const character = shapeIndex < 0 ? DEFAULT_MEMOI : MEMOI_MOUTH_SHAPES[shapeIndex];
  const cycleShape = () =>
    setShapeIndex((i) => (i + 1 >= MEMOI_MOUTH_SHAPES.length ? -1 : i + 1));

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <View style={styles.hero}>
        <Pressable
          onPress={__DEV__ ? cycleShape : undefined}
          accessibilityRole="image"
          accessibilityLabel="늘봄 캐릭터"
        >
          <Memoi3D character={character} height={200} style={{ width: 200 }} />
        </Pressable>

        <View style={styles.brandBlock}>
          <Text style={styles.brand}>늘봄</Text>
          {__DEV__ && shapeIndex >= 0 ? (
            <Text style={styles.shapeLabel}>{character.name}</Text>
          ) : null}
        </View>

        <Text style={styles.tagline}>
          <Text style={styles.taglineStrong}>늘 곁에서 오늘을 살펴봅니다</Text>{"\n"}
          {"\n"}
          매일 나누는 작은 대화가{"\n"}
          오늘의 안부가 되고, 내일의 안심이 됩니다.
        </Text>

        <View style={styles.bubble}>
          <Text style={styles.bubbleText}>
            "반가워요. 오늘 하루는 어떠셨어요?{"\n"}
            저와 천천히 이야기 나눠요."
          </Text>
        </View>
      </View>

      <View style={styles.footer}>
        <Button
          label={ready ? "시작하기" : "로그인 정보를 확인하는 중이에요"}
          size="lg"
          disabled={!ready}
          onPress={() => navigation.navigate("Login", { mode: "signup" })}
        />
        <Button
          label="이미 계정이 있어요"
          variant="onDark"
          onPress={() => navigation.navigate("Login", { mode: "login" })}
          style={{ marginTop: spacing.md }}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.characterStage },
  hero: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.xxl,
    gap: spacing.xl,
  },
  brandBlock: { alignItems: "center" },
  brand: { fontSize: fontSize.display, fontWeight: fontWeight.bold, color: colors.white },
  shapeLabel: { fontSize: fontSize.caption, color: colors.accent, marginTop: 2 },
  tagline: {
    fontSize: fontSize.bodyLg - 1,
    color: "rgba(255,255,255,0.75)",
    textAlign: "center",
    lineHeight: 26,
  },
  taglineStrong: { color: "rgba(255,255,255,0.9)", fontWeight: fontWeight.bold },
  bubble: {
    alignSelf: "stretch",
    backgroundColor: "rgba(255,255,255,0.08)",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.14)",
    borderRadius: radius.lg,
    paddingVertical: spacing.lg,
    paddingHorizontal: spacing.md,
  },
  bubbleText: {
    fontSize: fontSize.body,
    color: "rgba(255,255,255,0.75)",
    textAlign: "center",
    lineHeight: 24,
  },
  footer: { paddingHorizontal: spacing.xxl - 4, paddingBottom: spacing.xxl + 4 },
});
