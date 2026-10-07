import { router } from "expo-router";
import { useEffect, useState } from "react";
import { Alert, Platform } from "react-native";
import { CATALOG, findApp } from "@foco/core";
import { Body, Button, Card, Chip, Field, Row, Screen, SectionTitle } from "../../components/ui";
import { api, API_URL, tokenStorage, useToken } from "../../lib/api";
import { updatePrefs, usePrefs } from "../../lib/settings";
import { store } from "../../lib/store";

type Provider = "anthropic" | "openai" | "google";
interface AiSettings {
  provider: Provider | null;
  providers: Array<{ provider: Provider; hasKey: boolean; model: string }>;
}

const PROVIDER_LABELS: Record<Provider, string> = { anthropic: "Anthropic (Claude)", openai: "OpenAI (ChatGPT)", google: "Google (Gemini)" };
const KEY_HELP: Record<Provider, string> = {
  anthropic: "Créala en console.anthropic.com → API Keys",
  openai: "Créala en platform.openai.com → API keys",
  google: "Créala en aistudio.google.com → Get API key",
};

export default function SettingsScreen() {
  const token = useToken();
  const prefs = usePrefs();
  const [ai, setAi] = useState<AiSettings | null>(null);
  const [provider, setProvider] = useState<Provider>("anthropic");
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState("");
  const [me, setMe] = useState<{ email: string } | null>(null);

  const loadAi = () => {
    if (!token) return;
    api<AiSettings>("/ai/settings").then((s) => {
      setAi(s);
      if (s.provider) setProvider(s.provider);
    }).catch(() => undefined);
    api<{ email: string }>("/me").then(setMe).catch(() => undefined);
  };
  useEffect(loadAi, [token]);
  useEffect(() => setModel(ai?.providers.find((p) => p.provider === provider)?.model ?? ""), [ai, provider]);

  const saveKey = async () => {
    try {
      await api(`/ai/keys/${provider}`, { method: "PUT", body: { apiKey: apiKey.trim(), model: model.trim() || undefined } });
      await api("/ai/settings", { method: "PUT", body: { provider } });
      setApiKey("");
      loadAi();
      Alert.alert("Guardado", `${PROVIDER_LABELS[provider]} conectado. La clave se guarda cifrada en tu cuenta.`);
    } catch (e) {
      Alert.alert("Error", e instanceof Error ? e.message : String(e));
    }
  };

  const toggleFocusApp = (id: string) =>
    updatePrefs({ focusApps: prefs.focusApps.includes(id) ? prefs.focusApps.filter((x) => x !== id) : [...prefs.focusApps, id] });

  const setLimit = (appId: string, minutes: number | null) =>
    updatePrefs({ limits: [...prefs.limits.filter((l) => l.appId !== appId), ...(minutes ? [{ appId, minutesPerDay: minutes }] : [])] });

  return (
    <Screen title="Ajustes">
      <SectionTitle>Cuenta</SectionTitle>
      <Card>
        {token ? (
          <>
            <Body>Conectado{me ? ` como ${me.email}` : ""}. Tus datos se sincronizan entre el móvil y el PC.</Body>
            {store.lastSyncError && <Body muted>⚠️ Última sincronización: {store.lastSyncError}</Body>}
            <Row>
              <Button small kind="secondary" label="↻ Sincronizar" onPress={() => void store.sync()} />
              <Button small kind="secondary" label="Cerrar sesión" onPress={() => void tokenStorage.set(null)} />
            </Row>
          </>
        ) : (
          <>
            <Body>Sin cuenta, todo se guarda solo en este dispositivo. Crea una para usarlo en el PC y el móvil a la vez.</Body>
            <Button label="Entrar o crear cuenta" onPress={() => router.push("/login")} />
          </>
        )}
      </Card>

      <SectionTitle>Inteligencia artificial</SectionTitle>
      <Card>
        {!token && <Body muted>Inicia sesión para conectar una IA.</Body>}
        {token && (
          <>
            <Row>
              {(Object.keys(PROVIDER_LABELS) as Provider[]).map((p) => (
                <Chip key={p} label={`${ai?.providers.find((x) => x.provider === p)?.hasKey ? "✅ " : ""}${PROVIDER_LABELS[p]}`} selected={provider === p} onPress={() => setProvider(p)} />
              ))}
            </Row>
            <Body muted>{KEY_HELP[provider]}</Body>
            <Field placeholder="Clave de API" value={apiKey} onChangeText={setApiKey} autoCapitalize="none" secureTextEntry />
            <Field placeholder="Modelo" value={model} onChangeText={setModel} autoCapitalize="none" />
            <Row>
              <Button label="Guardar" disabled={apiKey.trim().length < 10} onPress={saveKey} />
              {ai?.providers.find((x) => x.provider === provider)?.hasKey && (
                <Button kind="secondary" label="Usar esta" onPress={() => void api("/ai/settings", { method: "PUT", body: { provider } }).then(loadAi)} />
              )}
            </Row>
            {ai?.provider && <Body muted>IA activa: {PROVIDER_LABELS[ai.provider]}</Body>}
          </>
        )}
      </Card>

      <SectionTitle>Captura rápida</SectionTitle>
      <Card>
        {Platform.OS === "android" && (
          <>
            <Body muted>Gesto con la pantalla encendida (necesita el servicio de accesibilidad):</Body>
            <Row>
              <Chip label="2× bajar volumen" selected={prefs.captureGesture === "volume_down_double"} onPress={() => updatePrefs({ captureGesture: "volume_down_double" })} />
              <Chip label="2× subir volumen" selected={prefs.captureGesture === "volume_up_double"} onPress={() => updatePrefs({ captureGesture: "volume_up_double" })} />
              <Chip label="Desactivado" selected={prefs.captureGesture === "off"} onPress={() => updatePrefs({ captureGesture: "off" })} />
            </Row>
          </>
        )}
        <Body muted>Dictado:</Body>
        <Row>
          <Chip label="Voz del móvil (gratis)" selected={prefs.voice === "device"} onPress={() => updatePrefs({ voice: "device" })} />
          <Chip label="Whisper / Gemini (con tu clave)" selected={prefs.voice === "server"} onPress={() => updatePrefs({ voice: "server" })} />
        </Row>
      </Card>

      <SectionTitle>Bloqueo</SectionTitle>
      <Card>
        <Body muted>Apps que bloquean las sesiones rápidas y los bloques de foco del calendario:</Body>
        <Row>{CATALOG.map((a) => <Chip key={a.id} label={a.name} selected={prefs.focusApps.includes(a.id)} onPress={() => toggleFocusApp(a.id)} />)}</Row>
        <Body muted>Espera antes de poder parar una sesión:</Body>
        <Row>
          {[0, 5, 15, 30].map((m) => (
            <Chip key={m} label={m ? `${m} min` : "Sin espera"} selected={prefs.friction.unlockDelayMinutes === m} onPress={() => updatePrefs({ friction: { ...prefs.friction, unlockDelayMinutes: m } })} />
          ))}
        </Row>
        <Body muted>Pomodoro (foco / descanso):</Body>
        <Row>
          {[[25, 5], [50, 10], [90, 15]].map(([f, b]) => (
            <Chip
              key={f}
              label={`${f} / ${b}`}
              selected={prefs.pomodoro.focusMinutes === f}
              onPress={() => updatePrefs({ pomodoro: { ...prefs.pomodoro, focusMinutes: f!, shortBreakMinutes: b!, longBreakMinutes: b! * 3 } })}
            />
          ))}
        </Row>
        {Platform.OS === "android" && (
          <>
            <Body muted>Límite diario por app:</Body>
            {["instagram", "tiktok", "youtube"].map((id) => {
              const current = prefs.limits.find((l) => l.appId === id)?.minutesPerDay ?? null;
              return (
                <Row key={id}>
                  <Body style={{ width: 90 }}>{findApp(id)?.name}</Body>
                  {[null, 15, 30, 60].map((m) => <Chip key={String(m)} label={m ? `${m} min` : "Sin límite"} selected={current === m} onPress={() => setLimit(id, m)} />)}
                </Row>
              );
            })}
          </>
        )}
      </Card>

      <Body muted style={{ textAlign: "center" }}>Servidor: {API_URL}</Body>
    </Screen>
  );
}
