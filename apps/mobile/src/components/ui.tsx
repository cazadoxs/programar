import * as Haptics from "expo-haptics";
import type { ReactNode } from "react";
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type TextStyle,
  type ViewStyle,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import Svg, { Circle, Defs, RadialGradient, Rect, Stop } from "react-native-svg";
import { findApp } from "@foco/core";
import { APP_TINTS, colors, fonts, radius, radiusSm, space } from "../lib/theme";
import { Icon, type IconName } from "./Icon";

export const tap = () => {
  if (Platform.OS !== "web") void Haptics.selectionAsync().catch(() => undefined);
};

/** The warm halo behind every screen header: Foco's "spotlight". */
export function Glow({ height = 320, opacity = 0.22, color = colors.primary }: { height?: number; opacity?: number; color?: string }) {
  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { height }]}>
      <Svg width="100%" height="100%">
        <Defs>
          <RadialGradient id="glow" cx="78%" cy="0%" rx="85%" ry="100%">
            <Stop offset="0" stopColor={color} stopOpacity={opacity} />
            <Stop offset="1" stopColor={color} stopOpacity={0} />
          </RadialGradient>
        </Defs>
        <Rect x="0" y="0" width="100%" height="100%" fill="url(#glow)" />
      </Svg>
    </View>
  );
}

export function Screen({
  title, eyebrow, subtitle, children, right, footer, glow = true, scroll = true,
}: {
  title: string;
  eyebrow?: string;
  subtitle?: string;
  children: ReactNode;
  right?: ReactNode;
  footer?: ReactNode;
  glow?: boolean;
  scroll?: boolean;
}) {
  const header = (
    <View style={styles.header}>
      <View style={{ flex: 1 }}>
        {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
        <Text style={styles.title} accessibilityRole="header">{title}</Text>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      {right}
    </View>
  );
  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      {glow && <Glow />}
      {scroll ? (
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
          {header}
          {children}
        </ScrollView>
      ) : (
        <View style={[styles.scroll, { flex: 1, paddingBottom: 0 }]}>
          {header}
          {children}
        </View>
      )}
      {footer}
    </SafeAreaView>
  );
}

export function Card({ children, style, tone = "default", onPress }: { children: ReactNode; style?: StyleProp<ViewStyle>; tone?: "default" | "lit" | "danger" | "success" | "info" | "flat"; onPress?: () => void }) {
  const toneStyle = TONES[tone];
  if (onPress) {
    return (
      <Pressable onPress={() => { tap(); onPress(); }} style={({ pressed }) => [styles.card, toneStyle, style, pressed && { opacity: 0.85 }]}>
        {children}
      </Pressable>
    );
  }
  return <View style={[styles.card, toneStyle, style]}>{children}</View>;
}

const TONES: Record<string, ViewStyle> = {
  default: {},
  flat: { backgroundColor: "transparent", borderColor: colors.border },
  lit: { backgroundColor: "#1C1710", borderColor: "rgba(255,178,63,0.45)" },
  danger: { backgroundColor: "#1F1213", borderColor: "rgba(255,91,73,0.45)" },
  success: { backgroundColor: "#0F1C18", borderColor: "rgba(67,217,163,0.4)" },
  info: { backgroundColor: "#121827", borderColor: "rgba(122,167,255,0.4)" },
};

export function SectionTitle({ children, right }: { children: ReactNode; right?: ReactNode }) {
  return (
    <View style={styles.sectionRow}>
      <Text style={styles.section}>{children}</Text>
      {right}
    </View>
  );
}

export function Body({ children, muted, style, numberOfLines }: { children: ReactNode; muted?: boolean; style?: StyleProp<TextStyle>; numberOfLines?: number }) {
  return <Text numberOfLines={numberOfLines} style={[styles.body, muted && { color: colors.muted, fontSize: 14, lineHeight: 20 }, style]}>{children}</Text>;
}

export function Heading({ children, style, size = 20 }: { children: ReactNode; style?: StyleProp<TextStyle>; size?: number }) {
  return <Text style={[styles.heading, { fontSize: size, lineHeight: Math.round(size * 1.2) }, style]}>{children}</Text>;
}

type ButtonKind = "primary" | "secondary" | "ghost" | "danger";

export function Button({
  label, onPress, kind = "primary", disabled, small, icon, style, accessibilityHint,
}: { label: string; onPress: () => void; kind?: ButtonKind; disabled?: boolean; small?: boolean; icon?: IconName; style?: StyleProp<ViewStyle>; accessibilityHint?: string }) {
  const palette = {
    primary: { bg: colors.primary, fg: colors.primaryText, border: colors.primary },
    secondary: { bg: colors.cardAlt, fg: colors.text, border: colors.border },
    ghost: { bg: "transparent", fg: colors.text, border: colors.border },
    danger: { bg: colors.dangerSoft, fg: colors.danger, border: "rgba(255,91,73,0.5)" },
  }[kind];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: !!disabled }}
      onPress={() => { tap(); onPress(); }}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        small && styles.buttonSmall,
        { backgroundColor: palette.bg, borderColor: palette.border, opacity: disabled ? 0.4 : 1, transform: [{ scale: pressed ? 0.98 : 1 }] },
        style,
      ]}
    >
      {icon && <Icon name={icon} size={small ? 16 : 18} color={palette.fg} strokeWidth={2.2} />}
      <Text style={[styles.buttonText, { color: palette.fg }, small && { fontSize: 14 }]}>{label}</Text>
    </Pressable>
  );
}

export function IconButton({ icon, onPress, label, kind = "secondary", size = 44 }: { icon: IconName; onPress: () => void; label: string; kind?: "primary" | "secondary"; size?: number }) {
  const primary = kind === "primary";
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={() => { tap(); onPress(); }}
      style={({ pressed }) => ({
        width: size, height: size, borderRadius: size / 2, alignItems: "center", justifyContent: "center",
        backgroundColor: primary ? colors.primary : colors.cardAlt, borderWidth: primary ? 0 : 1, borderColor: colors.border,
        transform: [{ scale: pressed ? 0.94 : 1 }],
      })}
    >
      <Icon name={icon} size={size * 0.46} color={primary ? colors.primaryText : colors.text} strokeWidth={2.4} />
    </Pressable>
  );
}

export function Chip({ label, selected, onPress, icon, locked }: { label: string; selected?: boolean; onPress?: () => void; icon?: IconName; locked?: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: !!selected }}
      onPress={() => { tap(); onPress?.(); }}
      style={({ pressed }) => [styles.chip, selected && styles.chipSelected, pressed && { opacity: 0.8 }]}
    >
      {icon && <Icon name={icon} size={15} color={selected ? colors.primaryText : colors.muted} />}
      <Text style={[styles.chipText, selected && { color: colors.primaryText, fontFamily: fonts.bodySemi }]}>{label}</Text>
      {locked && <Icon name="lock" size={13} color={selected ? colors.primaryText : colors.faint} />}
    </Pressable>
  );
}

export function Segmented<T extends string>({ options, value, onChange }: { options: Array<{ value: T; label: string; icon?: IconName }>; value: T; onChange: (v: T) => void }) {
  return (
    <View style={styles.segmented} accessibilityRole="tablist">
      {options.map((o) => {
        const active = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="tab"
            accessibilityState={{ selected: active }}
            onPress={() => { tap(); onChange(o.value); }}
            style={[styles.segment, active && { backgroundColor: colors.cardAlt, borderColor: colors.border }]}
          >
            {o.icon && <Icon name={o.icon} size={16} color={active ? colors.primary : colors.muted} />}
            <Text style={[styles.segmentText, active && { color: colors.text }]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Row({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[{ flexDirection: "row", alignItems: "center", gap: space(1), flexWrap: "wrap" }, style]}>{children}</View>;
}

export function Field(props: TextInputProps & { label?: string }) {
  const { label, style, ...rest } = props;
  const input = <TextInput placeholderTextColor={colors.faint} selectionColor={colors.primary} {...rest} style={[styles.field, style]} />;
  if (!label) return input;
  return (
    <View style={{ gap: 6 }}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {input}
    </View>
  );
}

export function ToggleRow({ title, description, value, onChange, icon, locked }: { title: string; description?: string; value: boolean; onChange: (v: boolean) => void; icon?: IconName; locked?: boolean }) {
  return (
    <View style={styles.toggleRow}>
      {icon && <IconBadge name={icon} />}
      <View style={{ flex: 1, gap: 2 }}>
        <Row style={{ gap: 6 }}>
          <Text style={styles.rowTitle}>{title}</Text>
          {locked && <ProBadge />}
        </Row>
        {description ? <Text style={styles.rowDescription}>{description}</Text> : null}
      </View>
      <Switch
        accessibilityLabel={title}
        value={value}
        onValueChange={(v) => { tap(); onChange(v); }}
        trackColor={{ false: colors.border, true: colors.primary }}
        thumbColor={Platform.OS === "android" ? (value ? colors.text : colors.muted) : undefined}
        ios_backgroundColor={colors.border}
      />
    </View>
  );
}

export function Toggle({ value, onChange, label }: { value: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <Switch
      accessibilityLabel={label}
      value={value}
      onValueChange={(v) => { tap(); onChange(v); }}
      trackColor={{ false: colors.border, true: colors.primary }}
      thumbColor={Platform.OS === "android" ? (value ? colors.text : colors.muted) : undefined}
      ios_backgroundColor={colors.border}
    />
  );
}

export function ListRow({ title, description, icon, onPress, right, tint }: { title: string; description?: string; icon?: IconName; onPress?: () => void; right?: ReactNode; tint?: string }) {
  const content = (
    <>
      {icon && <IconBadge name={icon} tint={tint} />}
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={styles.rowTitle} numberOfLines={2}>{title}</Text>
        {description ? <Text style={styles.rowDescription} numberOfLines={3}>{description}</Text> : null}
      </View>
      {right ?? (onPress ? <Icon name="chevronRight" size={18} color={colors.faint} /> : null)}
    </>
  );
  if (!onPress) return <View style={styles.toggleRow}>{content}</View>;
  return (
    <Pressable accessibilityRole="button" onPress={() => { tap(); onPress(); }} style={({ pressed }) => [styles.toggleRow, pressed && { opacity: 0.7 }]}>
      {content}
    </Pressable>
  );
}

export function IconBadge({ name, tint = colors.primary, size = 36 }: { name: IconName; tint?: string; size?: number }) {
  return (
    <View style={{ width: size, height: size, borderRadius: size * 0.32, backgroundColor: hexA(tint, 0.14), alignItems: "center", justifyContent: "center" }}>
      <Icon name={name} size={size * 0.52} color={tint} />
    </View>
  );
}

export function Divider() {
  return <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: colors.border, marginVertical: 2 }} />;
}

export function ProBadge({ label = "PRO" }: { label?: string }) {
  return (
    <View style={styles.proBadge}>
      <Text style={styles.proBadgeText}>{label}</Text>
    </View>
  );
}

export function Pill({ label, tint = colors.primary, icon }: { label: string; tint?: string; icon?: IconName }) {
  return (
    <View style={[styles.pill, { backgroundColor: hexA(tint, 0.14) }]}>
      {icon && <Icon name={icon} size={13} color={tint} strokeWidth={2.4} />}
      <Text style={[styles.pillText, { color: tint }]}>{label}</Text>
    </View>
  );
}

/** Rounded tile with the app's initial, tinted with an accent colour (no third-party logos). */
export function AppTile({ appId, size = 44, selected, onPress, showName }: { appId: string; size?: number; selected?: boolean; onPress?: () => void; showName?: boolean }) {
  const app = findApp(appId);
  const tint = APP_TINTS[appId] ?? colors.primary;
  const tile = (
    <View
      style={{
        width: size, height: size, borderRadius: size * 0.3, alignItems: "center", justifyContent: "center",
        backgroundColor: selected === false ? colors.cardAlt : hexA(tint, 0.16),
        borderWidth: 1.5, borderColor: selected ? colors.primary : selected === false ? colors.border : hexA(tint, 0.35),
      }}
    >
      <Text style={{ color: selected === false ? colors.faint : tint, fontFamily: fonts.display, fontSize: size * 0.42 }}>
        {(app?.name ?? appId).slice(0, 1)}
      </Text>
      {selected && (
        <View style={styles.tileCheck}>
          <Icon name="check" size={11} color={colors.primaryText} strokeWidth={3.2} />
        </View>
      )}
    </View>
  );
  const body = (
    <View style={{ alignItems: "center", gap: 6, width: showName ? size + 24 : undefined }}>
      {tile}
      {showName && <Text style={{ color: selected === false ? colors.faint : colors.muted, fontSize: 12, fontFamily: fonts.bodyMedium }} numberOfLines={1}>{app?.name ?? appId}</Text>}
    </View>
  );
  if (!onPress) return body;
  return (
    <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: !!selected }} accessibilityLabel={app?.name ?? appId} onPress={() => { tap(); onPress(); }}>
      {body}
    </Pressable>
  );
}

/** Circular progress ring with free content in the middle. */
export function Ring({ progress, size = 220, stroke = 14, color = colors.primary, track = colors.cardAlt, children }: { progress: number; size?: number; stroke?: number; color?: string; track?: string; children?: ReactNode }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const p = Math.max(0, Math.min(1, progress));
  return (
    <View style={{ width: size, height: size, alignItems: "center", justifyContent: "center" }}>
      <Svg width={size} height={size} style={StyleSheet.absoluteFill}>
        <Circle cx={size / 2} cy={size / 2} r={r} stroke={track} strokeWidth={stroke} fill="none" />
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={color}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={`${c} ${c}`}
          strokeDashoffset={c * (1 - p)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </Svg>
      {children}
    </View>
  );
}

export function Stat({ value, label, tint = colors.text, icon }: { value: string; label: string; tint?: string; icon?: IconName }) {
  return (
    <View style={styles.stat}>
      {icon && <Icon name={icon} size={16} color={tint} />}
      <Text style={[styles.statValue, { color: tint }]} numberOfLines={1} adjustsFontSizeToFit>{value}</Text>
      <Text style={styles.statLabel} numberOfLines={2}>{label}</Text>
    </View>
  );
}

/** Minimal vertical bar chart (one bar per label). */
export function Bars({ values, labels, highlight, height = 96, format }: { values: number[]; labels: string[]; highlight?: number; height?: number; format?: (v: number) => string }) {
  const max = Math.max(1, ...values);
  return (
    <View style={{ flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between", height: height + 40, gap: 6 }} accessibilityRole="image" accessibilityLabel={labels.map((l, i) => `${l}: ${format ? format(values[i] ?? 0) : values[i]}`).join(", ")}>
      {values.map((v, i) => (
        <View key={i} style={{ flex: 1, alignItems: "center", gap: 6 }}>
          <Text style={{ color: i === highlight ? colors.primary : colors.faint, fontSize: 10, fontFamily: fonts.bodyMedium }} numberOfLines={1}>
            {v > 0 && format ? format(v) : ""}
          </Text>
          <View
            style={{
              width: "100%", maxWidth: 28, height: Math.max(4, (v / max) * height), borderRadius: 8,
              backgroundColor: i === highlight ? colors.primary : v > 0 ? hexA(colors.primary, 0.35) : colors.cardAlt,
            }}
          />
          <Text style={{ color: i === highlight ? colors.text : colors.muted, fontSize: 12, fontFamily: fonts.bodySemi }}>{labels[i]}</Text>
        </View>
      ))}
    </View>
  );
}

export function EmptyState({ icon, title, body, action }: { icon: IconName; title: string; body?: string; action?: ReactNode }) {
  return (
    <View style={{ alignItems: "center", paddingVertical: space(2), gap: 8 }}>
      <IconBadge name={icon} tint={colors.muted} size={44} />
      <Text style={[styles.rowTitle, { textAlign: "center" }]}>{title}</Text>
      {body ? <Text style={[styles.rowDescription, { textAlign: "center", maxWidth: 300 }]}>{body}</Text> : null}
      {action}
    </View>
  );
}

export function hexA(hex: string, alpha: number): string {
  if (!hex.startsWith("#") || hex.length !== 7) return hex;
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
}

export const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: space(2.5), paddingBottom: space(14), gap: space(2), width: "100%", maxWidth: 760, alignSelf: "center" },
  header: { flexDirection: "row", alignItems: "flex-end", marginTop: space(1), marginBottom: space(1), gap: space(2) },
  eyebrow: { color: colors.primary, fontSize: 12, fontFamily: fonts.bodySemi, letterSpacing: 1.6, textTransform: "uppercase", marginBottom: 6 },
  title: { color: colors.text, fontSize: 34, lineHeight: 40, fontFamily: fonts.display, letterSpacing: -0.8 },
  subtitle: { color: colors.muted, fontSize: 15, lineHeight: 21, marginTop: 6, fontFamily: fonts.body },
  card: { backgroundColor: colors.card, borderRadius: radius, padding: space(2), gap: space(1.25), borderWidth: 1, borderColor: colors.border },
  sectionRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: space(1.5) },
  section: { color: colors.muted, fontSize: 12, fontFamily: fonts.bodySemi, textTransform: "uppercase", letterSpacing: 1.4 },
  heading: { color: colors.text, fontFamily: fonts.display, letterSpacing: -0.3 },
  body: { color: colors.text, fontSize: 16, lineHeight: 23, fontFamily: fonts.body },
  button: { flexDirection: "row", gap: 8, borderRadius: 999, borderWidth: 1, paddingVertical: 15, paddingHorizontal: 22, alignItems: "center", justifyContent: "center" },
  buttonSmall: { paddingVertical: 9, paddingHorizontal: 14 },
  buttonText: { fontSize: 16, fontFamily: fonts.bodySemi },
  chip: { flexDirection: "row", alignItems: "center", gap: 6, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card, borderRadius: 999, paddingVertical: 8, paddingHorizontal: 14 },
  chipSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.text, fontSize: 14, fontFamily: fonts.bodyMedium },
  segmented: { flexDirection: "row", backgroundColor: colors.card, borderRadius: 999, padding: 4, borderWidth: 1, borderColor: colors.border },
  segment: { flex: 1, flexDirection: "row", gap: 6, alignItems: "center", justifyContent: "center", paddingVertical: 9, borderRadius: 999, borderWidth: 1, borderColor: "transparent" },
  segmentText: { color: colors.muted, fontSize: 14, fontFamily: fonts.bodySemi },
  field: { backgroundColor: colors.bg, color: colors.text, borderRadius: radiusSm, borderWidth: 1, borderColor: colors.border, paddingVertical: 14, paddingHorizontal: 16, fontSize: 16, fontFamily: fonts.body },
  fieldLabel: { color: colors.muted, fontSize: 13, fontFamily: fonts.bodySemi },
  toggleRow: { flexDirection: "row", alignItems: "center", gap: space(1.5), paddingVertical: 6 },
  rowTitle: { color: colors.text, fontSize: 16, fontFamily: fonts.bodySemi },
  rowDescription: { color: colors.muted, fontSize: 13.5, lineHeight: 19, fontFamily: fonts.body },
  proBadge: { backgroundColor: colors.primary, borderRadius: 6, paddingHorizontal: 6, paddingVertical: 2 },
  proBadgeText: { color: colors.primaryText, fontSize: 10, fontFamily: fonts.bodyBold, letterSpacing: 1 },
  pill: { flexDirection: "row", alignItems: "center", gap: 5, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4, alignSelf: "flex-start" },
  pillText: { fontSize: 12, fontFamily: fonts.bodySemi },
  tileCheck: { position: "absolute", right: -5, top: -5, width: 18, height: 18, borderRadius: 9, backgroundColor: colors.primary, alignItems: "center", justifyContent: "center", borderWidth: 2, borderColor: colors.bg },
  stat: { flex: 1, gap: 4, backgroundColor: colors.card, borderRadius: radiusSm, borderWidth: 1, borderColor: colors.border, padding: space(1.5) },
  statValue: { fontFamily: fonts.display, fontSize: 26, letterSpacing: -0.5 },
  statLabel: { color: colors.muted, fontSize: 12.5, lineHeight: 17, fontFamily: fonts.bodyMedium },
});
