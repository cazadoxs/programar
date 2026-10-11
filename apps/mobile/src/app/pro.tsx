import { router, useLocalSearchParams } from "expo-router";
import { Platform, Text, View } from "react-native";
import { PRO_FEATURES, type ProFeature } from "@foco/core";
import { notify } from "../lib/dialog";
import { type IconName } from "../components/Icon";
import { LogoMark } from "../components/Logo";
import { Body, Button, Card, IconBadge, Pill, Screen } from "../components/ui";
import { buyPro, restorePro, usePro } from "../lib/pro";
import { colors, fonts, space } from "../lib/theme";

const ICONS: Record<ProFeature, IconName> = {
  granular: "eyeOff",
  strict: "lock",
  rules: "clock",
  calendars: "calendar",
  stats: "flame",
  workMode: "sparkles",
};

export default function ProScreen() {
  const { feature } = useLocalSearchParams<{ feature?: ProFeature }>();
  const { pro, price, busy } = usePro();
  const highlighted = PRO_FEATURES.find((f) => f.id === feature);

  const buy = async () => {
    const r = await buyPro();
    if (r.ok) {
      notify("¡Gracias!", "Foco Pro está activado. Ahora ve a por ello.");
      router.back();
    } else if (r.message) notify("Foco Pro", r.message);
  };
  const restore = async () => {
    const ok = await restorePro();
    notify("Foco Pro", ok ? "Compra restaurada. ¡Bienvenido de vuelta!" : "No encontramos ninguna compra de Foco Pro en esta cuenta de Google Play.");
    if (ok) router.back();
  };

  return (
    <Screen title="" glow={false}>
      <View style={{ alignItems: "center", gap: space(1.5), marginTop: -space(2) }}>
        <LogoMark size={120} />
        <Pill label="Pago único · sin suscripción" icon="star" />
        <Text style={{ color: colors.text, fontFamily: fonts.display, fontSize: 40, letterSpacing: -1, textAlign: "center" }}>Foco Pro</Text>
        <Body muted style={{ textAlign: "center", maxWidth: 340 }}>
          {highlighted ? `${highlighted.title} es de Foco Pro. ` : ""}Paga una vez y es tuyo para siempre, en el móvil y en el PC. Sin anuncios, sin cuotas.
        </Body>
      </View>

      <Card style={{ gap: space(1.5) }}>
        {PRO_FEATURES.map((f) => (
          <View key={f.id} style={{ flexDirection: "row", gap: 12, alignItems: "center", opacity: highlighted && highlighted.id !== f.id ? 0.75 : 1 }}>
            <IconBadge name={ICONS[f.id]} tint={f.id === feature ? colors.primary : colors.muted} />
            <View style={{ flex: 1 }}>
              <Text style={{ color: colors.text, fontFamily: fonts.bodySemi, fontSize: 15.5 }}>{f.title}</Text>
              <Text style={{ color: colors.muted, fontFamily: fonts.body, fontSize: 13.5, lineHeight: 19 }}>{f.description}</Text>
            </View>
          </View>
        ))}
      </Card>

      {pro ? (
        <Card tone="success">
          <Body style={{ fontFamily: fonts.bodySemi }}>Ya tienes Foco Pro. ¡Gracias por apoyar una app sin anuncios!</Body>
        </Card>
      ) : (
        <View style={{ gap: 10 }}>
          <Button label={Platform.OS === "android" ? `Desbloquear para siempre${price ? ` · ${price}` : ""}` : "Cómpralo desde la app de Android"} icon="bolt" disabled={busy || Platform.OS !== "android"} onPress={buy} />
          {Platform.OS === "android" && <Button kind="ghost" label="Restaurar compra" disabled={busy} onPress={restore} />}
          <Body muted style={{ textAlign: "center" }}>
            {Platform.OS === "android"
              ? "El pago lo gestiona Google Play. Si inicias sesión, Pro también se activa en el PC."
              : "Compra Foco Pro en Android con Google Play e inicia sesión con la misma cuenta: se activa aquí automáticamente."}
          </Body>
        </View>
      )}
    </Screen>
  );
}
