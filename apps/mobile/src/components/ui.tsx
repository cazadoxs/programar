import type { ReactNode } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View, type TextInputProps, type ViewStyle } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors, radius, space } from "../lib/theme";

export function Screen({ title, subtitle, children, right }: { title: string; subtitle?: string; children: ReactNode; right?: ReactNode }) {
  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <View style={styles.header}>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>{title}</Text>
            {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
          </View>
          {right}
        </View>
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return <Text style={styles.section}>{children}</Text>;
}

export function Body({ children, muted, style }: { children: ReactNode; muted?: boolean; style?: object }) {
  return <Text style={[styles.body, muted && { color: colors.muted }, style]}>{children}</Text>;
}

export function Button({
  label, onPress, kind = "primary", disabled, small,
}: { label: string; onPress: () => void; kind?: "primary" | "secondary" | "danger"; disabled?: boolean; small?: boolean }) {
  const bg = kind === "primary" ? colors.primary : kind === "danger" ? colors.danger : colors.cardAlt;
  const fg = kind === "secondary" ? colors.text : colors.primaryText;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [styles.button, small && styles.buttonSmall, { backgroundColor: bg, opacity: disabled ? 0.4 : pressed ? 0.8 : 1 }]}
    >
      <Text style={[styles.buttonText, { color: fg }, small && { fontSize: 14 }]}>{label}</Text>
    </Pressable>
  );
}

export function Chip({ label, selected, onPress }: { label: string; selected?: boolean; onPress?: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, selected && { backgroundColor: colors.primary, borderColor: colors.primary }]}>
      <Text style={[styles.chipText, selected && { color: colors.primaryText }]}>{label}</Text>
    </Pressable>
  );
}

export function Row({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[{ flexDirection: "row", alignItems: "center", gap: space(1), flexWrap: "wrap" }, style]}>{children}</View>;
}

export function Field(props: TextInputProps) {
  return <TextInput placeholderTextColor={colors.muted} {...props} style={[styles.field, props.style]} />;
}

export const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { padding: space(2), paddingBottom: space(6), gap: space(2) },
  header: { flexDirection: "row", alignItems: "center", marginBottom: space(1) },
  title: { color: colors.text, fontSize: 30, fontWeight: "800" },
  subtitle: { color: colors.muted, fontSize: 15, marginTop: 4 },
  card: { backgroundColor: colors.card, borderRadius: radius, padding: space(2), gap: space(1) },
  section: { color: colors.muted, fontSize: 13, fontWeight: "700", textTransform: "uppercase", letterSpacing: 1, marginTop: space(1) },
  body: { color: colors.text, fontSize: 16, lineHeight: 22 },
  button: { borderRadius: 12, paddingVertical: 14, paddingHorizontal: 18, alignItems: "center" },
  buttonSmall: { paddingVertical: 8, paddingHorizontal: 12 },
  buttonText: { fontSize: 16, fontWeight: "700" },
  chip: { borderWidth: 1, borderColor: colors.border, borderRadius: 999, paddingVertical: 6, paddingHorizontal: 12 },
  chipText: { color: colors.text, fontSize: 14 },
  field: { backgroundColor: colors.cardAlt, color: colors.text, borderRadius: 12, padding: 14, fontSize: 16 },
});
