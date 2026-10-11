import { Text, View } from "react-native";
import Svg, { Circle, Defs, Path, RadialGradient, Stop } from "react-native-svg";
import { colors, fonts } from "../lib/theme";

/** Foco's mark (docs/brand/logo.svg): an open ring with an off-centre spot of light. */
export function LogoMark({ size = 48, halo = true }: { size?: number; halo?: boolean }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 512 512">
      <Defs>
        <RadialGradient id="foco-halo" cx="298" cy="214" r="200" gradientUnits="userSpaceOnUse">
          <Stop offset="0" stopColor={colors.primary} stopOpacity={0.55} />
          <Stop offset="0.42" stopColor={colors.primary} stopOpacity={0.16} />
          <Stop offset="1" stopColor={colors.primary} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      {halo && <Circle cx={298} cy={214} r={200} fill="url(#foco-halo)" />}
      <Path d="M439.2 223.7 A186 186 0 1 1 288.3 72.8" fill="none" stroke={colors.primary} strokeWidth={52} strokeLinecap="round" />
      <Circle cx={298} cy={214} r={80} fill={colors.primary} />
    </Svg>
  );
}

export function Wordmark({ size = 28 }: { size?: number }) {
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: size * 0.3 }} accessibilityLabel="Foco">
      <LogoMark size={size * 1.15} />
      <Text style={{ color: colors.text, fontFamily: fonts.display, fontSize: size, letterSpacing: -0.5 }}>foco</Text>
    </View>
  );
}
