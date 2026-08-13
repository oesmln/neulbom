import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import Constants from "expo-constants";

import { colors, spacing, radius, fontSize, fontWeight } from "@/theme";
import { Screen, ScreenHeader, Caption } from "@/components/ui";
import {
  currentDisplaySettings,
  saveDisplaySettings,
  type FontScaleKey,
} from "@/store/settings";

/**
 * 앱 설정 — shared by the elder and guardian areas.
 *
 * The Figma ships this screen twice, identical apart from the accent colour
 * (sage for the elder, blue for the guardian), so it lives here once and each
 * area passes its palette in.
 *
 * Choices persist to the device (`@/store/settings`) and are applied at boot,
 * because every screen bakes the theme tokens into its `StyleSheet` at import
 * time — hence the "다시 시작하면 적용" note on both controls. The server has
 * no field for either (`PATCH /users/{id}/preferences` carries hearing, voice
 * and notification settings only), so these stay device-local.
 */
const FONT_SIZES: { key: FontScaleKey; label: string; preview: number }[] = [
  { key: "normal", label: "보통", preview: 15 },
  { key: "large", label: "크게", preview: 18 },
  { key: "xlarge", label: "매우 크게", preview: 22 },
];

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
  headerRight,
}: {
  palette: AppSettingsPalette;
  onBack?: () => void;
  backLabel?: string;
  headerRight?: React.ReactNode;
}) {
  // Boot already loaded the stored settings, so the sync read is populated.
  const stored = currentDisplaySettings();
  const [darkMode, setDarkMode] = React.useState(stored.darkMode);
  const [fontSizeKey, setFontSizeKey] = React.useState<FontScaleKey>(stored.fontScale);

  const toggleDarkMode = () => {
    setDarkMode((prev) => {
      void saveDisplaySettings({ darkMode: !prev, fontScale: fontSizeKey });
      return !prev;
    });
  };

  const pickFontScale = (key: FontScaleKey) => {
    setFontSizeKey(key);
    void saveDisplaySettings({ darkMode, fontScale: key });
  };

  const version = Constants.expoConfig?.version ?? "—";

  return (
    <Screen
      header={
        <ScreenHeader
          color={palette.accent}
          onBack={onBack}
          backLabel={backLabel}
          title="앱 설정"
          right={headerRight}
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
            onPress={toggleDarkMode}
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
        <Caption style={styles.note}>앱을 다시 시작하면 적용돼요.</Caption>
      </SettingsGroup>

      <SettingsGroup title="글씨 크기">
        <View style={{ gap: spacing.sm }}>
          {FONT_SIZES.map((size) => {
            const on = fontSizeKey === size.key;
            return (
              <Pressable
                key={size.key}
                onPress={() => pickFontScale(size.key)}
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
        <Caption style={styles.note}>앱을 다시 시작하면 적용돼요.</Caption>
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
