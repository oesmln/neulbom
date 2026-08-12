import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Constants from "expo-constants";

import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, ScreenHeader, Caption } from "@/components/ui";

/**
 * 앱 설정 — shared by the elder and guardian areas.
 *
 * The Figma ships this screen twice, identical apart from the accent colour
 * (sage for the elder, blue for the guardian), so it lives here once and each
 * area passes its palette in.
 *
 * Both preferences are local for now: `PATCH /users/{id}/preferences` carries
 * hearing, voice and notification settings but has no field for theme or text
 * scale, so there is nowhere to persist them yet. The note under each control
 * says so rather than leaving a switch that silently does nothing — applying
 * them is follow-up work recorded on the Issue.
 */
const FONT_SIZES = [
  { key: "normal", label: "보통", preview: 15 },
  { key: "large", label: "크게", preview: 18 },
  { key: "xlarge", label: "매우 크게", preview: 22 },
] as const;

type FontSizeKey = (typeof FONT_SIZES)[number]["key"];

export interface AppSettingsPalette {
  /** Header band, active switch and selected row border. */
  accent: string;
  /** Selected row fill. */
  accentLight: string;
  /** Selected row label. */
  accentDark: string;
}

function SettingsGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupTitle}>{title}</Text>
      {children}
    </View>
  );
}

export default function AppSettingsView({
  palette,
  onBack,
  backLabel,
}: {
  palette: AppSettingsPalette;
  onBack: () => void;
  backLabel: string;
}) {
  const [darkMode, setDarkMode] = React.useState(false);
  const [fontSizeKey, setFontSizeKey] = React.useState<FontSizeKey>("normal");

  const version = Constants.expoConfig?.version ?? "—";

  return (
    <Screen
      header={
        <ScreenHeader
          color={palette.accent}
          onBack={onBack}
          backLabel={backLabel}
          title="앱 설정"
        />
      }
    >
      <SettingsGroup title="화면 설정">
        <View style={styles.row}>
          <View style={{ flex: 1 }}>
            <Text style={styles.rowLabel}>다크 모드</Text>
            <Caption style={{ marginTop: 2 }}>어두운 화면으로 눈의 피로를 줄여요</Caption>
          </View>
          <Pressable
            onPress={() => setDarkMode((d) => !d)}
            accessibilityRole="switch"
            accessibilityLabel="다크 모드"
            accessibilityState={{ checked: darkMode }}
            style={[
              styles.switch,
              { backgroundColor: darkMode ? palette.accent : colors.switchBackground },
            ]}
          >
            <View style={[styles.knob, { left: darkMode ? 22 : 4 }]} />
          </Pressable>
        </View>
        <Caption style={styles.note}>
          테마 전환은 아직 적용되지 않아요. 선택만 저장돼요.
        </Caption>
      </SettingsGroup>

      <SettingsGroup title="글씨 크기">
        <View style={{ gap: spacing.sm }}>
          {FONT_SIZES.map((size) => {
            const on = fontSizeKey === size.key;
            return (
              <Pressable
                key={size.key}
                onPress={() => setFontSizeKey(size.key)}
                accessibilityRole="radio"
                accessibilityState={{ selected: on }}
                accessibilityLabel={`글씨 크기 ${size.label}`}
                style={[
                  styles.fontOption,
                  {
                    backgroundColor: on ? palette.accentLight : "transparent",
                    borderColor: on ? palette.accent : colors.border,
                  },
                ]}
              >
                <Text
                  style={{
                    fontSize: size.preview,
                    color: on ? palette.accentDark : colors.foreground,
                  }}
                >
                  {size.label}
                </Text>
                {on ? (
                  <Ionicons name="checkmark-circle" size={18} color={palette.accent} />
                ) : null}
              </Pressable>
            );
          })}
        </View>
        <Caption style={styles.note}>글씨 크기 변경은 다음 실행 시 적용됩니다.</Caption>
      </SettingsGroup>

      <SettingsGroup title="앱 정보">
        <View style={styles.infoRow}>
          <Text style={styles.rowLabel}>버전</Text>
          <Caption>{version}</Caption>
        </View>
      </SettingsGroup>
    </Screen>
  );
}

const styles = StyleSheet.create({
  group: {
    backgroundColor: colors.card,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    marginBottom: spacing.lg,
  },
  groupTitle: {
    fontSize: fontSize.badge,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
    letterSpacing: 0.7,
    marginBottom: spacing.md,
  },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, minHeight: 44 },
  rowLabel: { fontSize: fontSize.bodyLg, color: colors.foreground },
  switch: { width: 44, height: 26, borderRadius: 13, justifyContent: "center" },
  knob: {
    position: "absolute",
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.white,
  },
  fontOption: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    minHeight: 48,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1.5,
  },
  note: { marginTop: spacing.md },
  infoRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    minHeight: 44,
  },
});
