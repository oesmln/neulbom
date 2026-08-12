/**
 * Shared UI primitives for MemoCare.
 *
 * Shapes follow the Figma Make screens: every screen opens with a solid sage
 * header band, content sits on white, and cards are hairline-bordered rather
 * than heavily shadowed.
 */
import React from "react";
import {
  View,
  Text,
  Pressable,
  ScrollView,
  StyleSheet,
  ViewStyle,
  TextStyle,
  StyleProp,
  DimensionValue,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { colors, radius, spacing, fontSize, fontWeight, sizes, shadow } from "@/theme";

/* ----------------------------------------------------------------- Screen */

type ScreenProps = {
  children: React.ReactNode;
  /** Rendered edge-to-edge above the scroll area — pass a `ScreenHeader`. */
  header?: React.ReactNode;
  scroll?: boolean;
  background?: string;
  style?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
  edges?: ("top" | "bottom" | "left" | "right")[];
};

export function Screen({
  children,
  header,
  scroll = true,
  background = colors.background,
  style,
  contentStyle,
  edges = ["top"],
}: ScreenProps) {
  const body = scroll ? (
    <ScrollView
      contentContainerStyle={[styles.screenContent, contentStyle]}
      showsVerticalScrollIndicator={false}
    >
      {children}
    </ScrollView>
  ) : (
    <View style={[{ flex: 1 }, styles.screenContent, contentStyle]}>{children}</View>
  );

  return (
    <SafeAreaView style={[{ flex: 1, backgroundColor: background }, style]} edges={edges}>
      {header}
      {body}
    </SafeAreaView>
  );
}

/* ------------------------------------------------------------ ScreenHeader */

/**
 * The sage band at the top of every screen.
 *
 * `backLabel` renders the inline "← 홈" style link the Figma uses on pushed
 * screens; `onBack` alone renders the round chevron button used inside flows.
 */
export function ScreenHeader({
  title,
  subtitle,
  onBack,
  backLabel,
  right,
  color = colors.primary,
  children,
}: {
  title?: string;
  subtitle?: string;
  onBack?: () => void;
  backLabel?: string;
  right?: React.ReactNode;
  color?: string;
  children?: React.ReactNode;
}) {
  return (
    <View style={[styles.header, { backgroundColor: color }]}>
      {onBack && backLabel ? (
        <Pressable
          onPress={onBack}
          accessibilityRole="button"
          accessibilityLabel={`${backLabel}(으)로 돌아가기`}
          hitSlop={10}
          style={styles.headerBackLink}
        >
          <Ionicons name="chevron-back" size={16} color="rgba(255,255,255,0.65)" />
          <Text style={styles.headerBackLabel}>{backLabel}</Text>
        </Pressable>
      ) : null}

      <View style={styles.headerRow}>
        {onBack && !backLabel ? (
          <Pressable
            onPress={onBack}
            accessibilityRole="button"
            accessibilityLabel="뒤로 가기"
            hitSlop={10}
            style={styles.headerBackButton}
          >
            <Ionicons name="chevron-back" size={18} color={colors.white} />
          </Pressable>
        ) : null}

        <View style={{ flex: 1 }}>
          {title ? <Text style={styles.headerTitle}>{title}</Text> : null}
          {subtitle ? <Text style={styles.headerSubtitle}>{subtitle}</Text> : null}
        </View>

        {right}
      </View>

      {children}
    </View>
  );
}

/* ------------------------------------------------------------------ Button */

type ButtonVariant = "primary" | "secondary" | "outline" | "ghost" | "danger" | "onDark";

export function Button({
  label,
  onPress,
  variant = "primary",
  icon,
  disabled,
  style,
  size = "md",
}: {
  label: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  icon?: keyof typeof Ionicons.glyphMap;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  size?: "sm" | "md" | "lg";
}) {
  const palette: Record<ButtonVariant, { bg: string; fg: string; border?: string }> = {
    primary: { bg: colors.primary, fg: colors.primaryForeground },
    secondary: { bg: colors.secondary, fg: colors.primaryDark },
    outline: { bg: "transparent", fg: colors.primary, border: colors.primary },
    ghost: { bg: "transparent", fg: colors.mutedForeground },
    danger: { bg: colors.destructive, fg: colors.destructiveForeground },
    // Outlined button sitting on the dark character stage (Splash).
    onDark: { bg: "transparent", fg: "rgba(255,255,255,0.55)", border: "rgba(255,255,255,0.18)" },
  };
  const p = palette[variant];
  // Disabled primary actions go flat-muted in the Figma rather than translucent.
  const flattens = disabled && (variant === "primary" || variant === "danger");
  const height =
    size === "sm" ? sizes.buttonHeightSm : size === "lg" ? sizes.buttonHeightLg : sizes.buttonHeight;

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !!disabled }}
      style={({ pressed }) => [
        styles.button,
        {
          height,
          backgroundColor: flattens ? colors.muted : p.bg,
          borderColor: p.border ?? "transparent",
          borderWidth: p.border ? 1.5 : 0,
          opacity: pressed ? 0.85 : 1,
        },
        style,
      ]}
    >
      {icon ? (
        <Ionicons
          name={icon}
          size={18}
          color={flattens ? colors.mutedForeground : p.fg}
          style={{ marginRight: 6 }}
        />
      ) : null}
      <Text
        style={[
          styles.buttonText,
          {
            color: flattens ? colors.mutedForeground : p.fg,
            fontSize: size === "sm" ? fontSize.body : fontSize.bodyLg,
          },
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

/* -------------------------------------------------------------------- Card */

export function Card({
  children,
  style,
  color = colors.card,
  onPress,
  accessibilityLabel,
}: {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  color?: string;
  onPress?: () => void;
  accessibilityLabel?: string;
}) {
  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        style={({ pressed }) => [styles.card, { backgroundColor: color, opacity: pressed ? 0.9 : 1 }, style]}
      >
        {children}
      </Pressable>
    );
  }
  return <View style={[styles.card, { backgroundColor: color }, style]}>{children}</View>;
}

/* ------------------------------------------------------------------- Badge */

export function Badge({
  label,
  color = colors.primaryDark,
  background = colors.secondary,
}: {
  label: string;
  color?: string;
  background?: string;
}) {
  return (
    <View style={[styles.badge, { backgroundColor: background }]}>
      <Text style={{ fontSize: fontSize.badge, fontWeight: fontWeight.bold, color }}>{label}</Text>
    </View>
  );
}

/* ------------------------------------------------------------ SpeechBubble */

/**
 * White bubble with a tail pointing back at the character.
 *
 * The tail is a CSS-triangle equivalent: a zero-size view with three
 * transparent borders and one coloured one, offset by exactly its own size so
 * it reads as an extension of the bubble body rather than a detached arrow.
 */
export function SpeechBubble({
  text,
  side = "below",
  style,
}: {
  text: string;
  side?: "below" | "left" | "right";
  style?: StyleProp<ViewStyle>;
}) {
  const S = 8;
  const tail: ViewStyle =
    side === "below"
      ? {
          top: -S,
          left: "50%",
          marginLeft: -S,
          borderLeftWidth: S,
          borderRightWidth: S,
          borderBottomWidth: S,
          borderLeftColor: "transparent",
          borderRightColor: "transparent",
          borderBottomColor: colors.white,
        }
      : side === "left"
        ? {
            bottom: 14,
            right: -S,
            borderTopWidth: S,
            borderBottomWidth: S,
            borderLeftWidth: S,
            borderTopColor: "transparent",
            borderBottomColor: "transparent",
            borderLeftColor: colors.white,
          }
        : {
            bottom: 14,
            left: -S,
            borderTopWidth: S,
            borderBottomWidth: S,
            borderRightWidth: S,
            borderTopColor: "transparent",
            borderBottomColor: "transparent",
            borderRightColor: colors.white,
          };

  return (
    <View style={[styles.bubble, style]}>
      <View style={[styles.bubbleTail, tail]} />
      <Text style={styles.bubbleText}>{text}</Text>
    </View>
  );
}

/* -------------------------------------------------------------- Typography */

export function Title({ children, style }: { children: React.ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.title, style]}>{children}</Text>;
}

export function Subtitle({ children, style }: { children: React.ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.subtitle, style]}>{children}</Text>;
}

export function Body({ children, style }: { children: React.ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.body, style]}>{children}</Text>;
}

export function Caption({ children, style }: { children: React.ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.caption, style]}>{children}</Text>;
}

export function SectionTitle({ children, style }: { children: React.ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.sectionTitle, style]}>{children}</Text>;
}

/* -------------------------------------------------------------------- Pill */

export function Pill({
  label,
  color = colors.secondary,
  textColor = colors.primaryDark,
  icon,
  style,
}: {
  label: string;
  color?: string;
  textColor?: string;
  icon?: keyof typeof Ionicons.glyphMap;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <View style={[styles.pill, { backgroundColor: color }, style]}>
      {icon ? <Ionicons name={icon} size={14} color={textColor} style={{ marginRight: 6 }} /> : null}
      <Text style={{ color: textColor, fontSize: fontSize.caption, fontWeight: fontWeight.semibold }}>
        {label}
      </Text>
    </View>
  );
}

/* -------------------------------------------------------------- ProgressBar */

export function ProgressBar({
  value,
  color = colors.primary,
  track = colors.muted,
  height = 6,
}: {
  value: number;
  color?: string;
  track?: string;
  height?: number;
}) {
  return (
    <View style={[styles.progressTrack, { backgroundColor: track, height }]}>
      <View
        style={{
          height: "100%",
          borderRadius: radius.pill,
          width: `${Math.max(0, Math.min(100, value))}%` as DimensionValue,
          backgroundColor: color,
        }}
      />
    </View>
  );
}

/* ------------------------------------------------------------------ Avatar */

/** 2D stand-in for the character. Only used where GL cannot run. */
export function Avatar({ size = 96, emoji = "🫘" }: { size?: number; emoji?: string }) {
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        backgroundColor: colors.secondary,
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Text style={{ fontSize: size * 0.5 }}>{emoji}</Text>
    </View>
  );
}

/* ----------------------------------------------------------------- Divider */

/* ------------------------------------------------------------ async states */

/**
 * The three states every API-backed screen has to render.
 *
 * The frontend checklist treats "로딩 / 빈 상태 / 오류" as part of a screen being
 * done, and the elder screens have to say what happened in plain words rather
 * than showing an empty card.
 */
export function LoadingState({ label = "불러오는 중이에요" }: { label?: string }) {
  return (
    <View style={styles.stateBox} accessibilityRole="progressbar" accessibilityLabel={label}>
      <ActivityIndicator size="large" color={colors.primary} />
      <Text style={styles.stateLabel}>{label}</Text>
    </View>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <View style={styles.stateBox}>
      <Ionicons name="cloud-offline-outline" size={40} color={colors.mutedForeground} />
      <Text style={styles.stateLabel}>{message}</Text>
      {onRetry ? (
        <Button label="다시 시도" variant="outline" onPress={onRetry} style={styles.stateAction} />
      ) : null}
    </View>
  );
}

export function EmptyState({
  message,
  icon = "document-text-outline",
}: {
  message: string;
  icon?: keyof typeof Ionicons.glyphMap;
}) {
  return (
    <View style={styles.stateBox}>
      <Ionicons name={icon} size={40} color={colors.muted} />
      <Text style={styles.stateLabel}>{message}</Text>
    </View>
  );
}

export function Divider({ style }: { style?: StyleProp<ViewStyle> }) {
  return <View style={[styles.divider, style]} />;
}

const styles = StyleSheet.create({
  screenContent: { paddingHorizontal: spacing.xl, paddingVertical: spacing.xl, paddingBottom: spacing.xxl },

  stateBox: { alignItems: "center", justifyContent: "center", paddingVertical: 56, gap: spacing.md },
  stateLabel: {
    fontSize: fontSize.body,
    color: colors.mutedForeground,
    textAlign: "center",
    lineHeight: 24,
  },
  stateAction: { alignSelf: "stretch", marginTop: spacing.xs },

  header: { paddingHorizontal: spacing.xl, paddingTop: spacing.xxl - 4, paddingBottom: spacing.xl },
  headerRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  headerBackButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.2)",
  },
  headerBackLink: { flexDirection: "row", alignItems: "center", marginBottom: spacing.lg },
  headerBackLabel: { fontSize: fontSize.caption, color: "rgba(255,255,255,0.65)", marginLeft: 2 },
  headerTitle: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.white },
  headerSubtitle: { fontSize: fontSize.caption, color: "rgba(255,255,255,0.6)", marginTop: 6 },

  button: {
    borderRadius: radius.xl,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.lg,
  },
  buttonText: { fontWeight: fontWeight.bold },

  card: {
    borderRadius: radius.xl,
    padding: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadow.card,
  },

  badge: {
    alignSelf: "flex-start",
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
    borderRadius: radius.pill,
  },

  bubble: {
    backgroundColor: colors.white,
    borderRadius: 18,
    paddingHorizontal: 18,
    paddingVertical: 14,
    maxWidth: 260,
    borderWidth: 1,
    borderColor: "rgba(0,0,0,0.06)",
    ...shadow.floating,
  },
  bubbleTail: { position: "absolute", width: 0, height: 0 },
  bubbleText: { fontSize: fontSize.body, lineHeight: 22, color: colors.foreground, textAlign: "center" },

  title: { fontSize: fontSize.title, fontWeight: fontWeight.bold, color: colors.foreground },
  subtitle: { fontSize: fontSize.subtitle, fontWeight: fontWeight.bold, color: colors.foreground },
  body: { fontSize: fontSize.body, color: colors.foreground, lineHeight: 22 },
  caption: { fontSize: fontSize.caption, color: colors.mutedForeground },
  sectionTitle: {
    fontSize: fontSize.cardTitle,
    fontWeight: fontWeight.bold,
    color: colors.foreground,
    marginBottom: spacing.md,
  },

  pill: {
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.md,
  },

  progressTrack: { borderRadius: radius.pill, overflow: "hidden" },

  divider: { height: 1, backgroundColor: colors.border, marginVertical: spacing.md },
});
