import { router } from "expo-router";
import { useState } from "react";
import { Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { CATALOG } from "@foco/core";
import { Icon } from "../components/Icon";
import { LogoMark } from "../components/Logo";
import { AppTile, Button, Glow, IconBadge, Pill, Ring, Row } from "../components/ui";
import { getPrefs, updatePrefs } from "../lib/settings";
import { colors, fonts, space } from "../lib/theme";

const STEPS = [
  {
    eyebrow: "Bienvenido a Foco",
    title: "Lo importante,\na la luz.",
    body: "Foco apaga el ruido del móvil para que hagas lo que de verdad querías hacer hoy.",
  },
  {
    eyebrow: "Bloqueo sin trampas",
    title: "Quita los Reels.\nDeja los mensajes.",
    body: "Bloquea la app entera o solo la parte que te atrapa: Reels, Shorts, Historias, Explorar o el feed infinito.",
  },
  {
    eyebrow: "Un solo calendario",
    title: "Todo tu día\nen un sitio.",
    body: "Google, Outlook, Samsung, Xiaomi o iCloud juntos. Y una IA con tu propia clave que te organiza la semana.",
  },
  {
    eyebrow: "Apunta en un segundo",
    title: "Doble toque.\nApuntado.",
    body: "Pulsa dos veces bajar volumen y dicta: «mañana a las 5 dentista». Foco lo convierte en evento o tarea.",
  },
];

export default function Onboarding() {
  const [step, setStep] = useState(0);
  const [apps, setApps] = useState<string[]>(getPrefs().focusApps);
  const last = step === STEPS.length;

  const finish = () => {
    updatePrefs({ onboarded: true, focusApps: apps.length ? apps : getPrefs().focusApps });
    router.replace("/");
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.bg }}>
      <Glow height={520} opacity={0.28} />
      <View style={{ flex: 1, padding: space(3), maxWidth: 560, width: "100%", alignSelf: "center" }}>
        <Row style={{ justifyContent: "space-between" }}>
          <Row style={{ gap: 6 }}>
            {[...STEPS, null].map((_, i) => (
              <View key={i} style={{ height: 4, width: i === step ? 28 : 12, borderRadius: 2, backgroundColor: i <= step ? colors.primary : colors.border }} />
            ))}
          </Row>
          {!last && <Text onPress={() => setStep(STEPS.length)} style={{ color: colors.muted, fontFamily: fonts.bodySemi }}>Saltar</Text>}
        </Row>

        <View style={{ flex: 1, justifyContent: "center", gap: space(3) }}>
          <View style={{ alignItems: "center", minHeight: 220, justifyContent: "center" }}>{last ? <PickVisual apps={apps} onToggle={(id) => setApps(apps.includes(id) ? apps.filter((x) => x !== id) : [...apps, id])} /> : <Visual step={step} />}</View>
          <View style={{ gap: 12 }}>
            <Text style={{ color: colors.primary, fontFamily: fonts.bodySemi, letterSpacing: 1.6, fontSize: 12, textTransform: "uppercase" }}>
              {last ? "Último paso" : STEPS[step]!.eyebrow}
            </Text>
            <Text style={{ color: colors.text, fontFamily: fonts.display, fontSize: 38, lineHeight: 42, letterSpacing: -1 }}>
              {last ? "¿Qué te roba\nel tiempo?" : STEPS[step]!.title}
            </Text>
            <Text style={{ color: colors.muted, fontFamily: fonts.body, fontSize: 17, lineHeight: 25 }}>
              {last ? "Elige las apps que Foco bloqueará cuando empieces una sesión. Puedes cambiarlo cuando quieras." : STEPS[step]!.body}
            </Text>
          </View>
        </View>

        <View style={{ gap: 10 }}>
          <Button label={last ? "Empezar a concentrarme" : "Siguiente"} icon={last ? "bolt" : undefined} onPress={() => (last ? finish() : setStep(step + 1))} />
          {step > 0 && <Button kind="ghost" label="Atrás" onPress={() => setStep(step - 1)} />}
        </View>
      </View>
    </SafeAreaView>
  );
}

function Visual({ step }: { step: number }) {
  if (step === 0) return <LogoMark size={200} />;
  if (step === 1) {
    return (
      <View style={{ alignItems: "center", gap: 14 }}>
        <Row style={{ gap: 12, justifyContent: "center" }}>
          {["instagram", "tiktok", "youtube", "snapchat"].map((id) => <AppTile key={id} appId={id} size={58} />)}
        </Row>
        <Row style={{ justifyContent: "center" }}>
          <Pill label="Reels" icon="eyeOff" tint={colors.danger} />
          <Pill label="Shorts" icon="eyeOff" tint={colors.danger} />
          <Pill label="Mensajes" icon="check" tint={colors.success} />
        </Row>
      </View>
    );
  }
  if (step === 2) {
    const rows: Array<[string, string, string]> = [
      ["09:00", "Clase de Cálculo", colors.info],
      ["11:30", "Bloque de foco · TFG", colors.primary],
      ["17:00", "Dentista", colors.success],
    ];
    return (
      <View style={{ width: "100%", gap: 10 }}>
        {rows.map(([h, t, c]) => (
          <View key={h} style={{ flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: colors.card, borderRadius: 16, padding: 14, borderWidth: 1, borderColor: colors.border }}>
            <Text style={{ color: colors.muted, fontFamily: fonts.bodySemi, width: 48 }}>{h}</Text>
            <View style={{ width: 4, height: 28, borderRadius: 2, backgroundColor: c }} />
            <Text style={{ color: colors.text, fontFamily: fonts.bodySemi, fontSize: 16 }}>{t}</Text>
          </View>
        ))}
      </View>
    );
  }
  return (
    <Ring progress={0.66} size={200}>
      <IconBadge name="mic" size={72} />
      <Text style={{ color: colors.muted, fontFamily: fonts.bodyMedium, marginTop: 10 }}>«mañana a las 5…»</Text>
    </Ring>
  );
}

function PickVisual({ apps, onToggle }: { apps: string[]; onToggle: (id: string) => void }) {
  return (
    <View style={{ flexDirection: "row", flexWrap: "wrap", justifyContent: "center", gap: 14 }}>
      {CATALOG.map((a) => <AppTile key={a.id} appId={a.id} size={56} showName selected={apps.includes(a.id)} onPress={() => onToggle(a.id)} />)}
      <View style={{ width: "100%", alignItems: "center", marginTop: 4 }}>
        <Row style={{ gap: 6 }}>
          <Icon name="lock" size={14} color={colors.faint} />
          <Text style={{ color: colors.faint, fontFamily: fonts.body, fontSize: 13 }}>Todo se procesa en tu móvil.</Text>
        </Row>
      </View>
    </View>
  );
}
