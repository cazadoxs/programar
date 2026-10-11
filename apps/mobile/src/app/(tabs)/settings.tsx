import Constants from "expo-constants";
import { router } from "expo-router";
import * as WebBrowser from "expo-web-browser";
import { useEffect, useState } from "react";
import { Platform, Text, View } from "react-native";
import { CATALOG, findApp } from "@foco/core";
import { LogoMark } from "../../components/Logo";
import { AppTile, Body, Button, Card, Chip, Divider, Field, Heading, ListRow, Pill, Row, Screen, SectionTitle, Segmented } from "../../components/ui";
import { changePassword, deleteAccount, signOut } from "../../lib/account";
import { api, API_URL, useToken } from "../../lib/api";
import { confirm, notify } from "../../lib/dialog";
import { usePro } from "../../lib/pro";
import { updatePrefs, usePrefs } from "../../lib/settings";
import { store } from "../../lib/store";
import { colors, fonts, space } from "../../lib/theme";

type Provider = "anthropic" | "openai" | "google";
interface AiSettings {
  provider: Provider | null;
  providers: Array<{ provider: Provider; hasKey: boolean; model: string }>;
}

const PROVIDER_LABELS: Record<Provider, string> = { anthropic: "Claude", openai: "ChatGPT", google: "Gemini" };
const KEY_HELP: Record<Provider, string> = {
  anthropic: "Anthropic: créala en console.anthropic.com → API Keys",
  openai: "OpenAI: créala en platform.openai.com → API keys",
  google: "Google: créala gratis en aistudio.google.com → Get API key",
};

export const PRIVACY_URL = process.env.EXPO_PUBLIC_PRIVACY_URL ?? "https://cazadoxs.github.io/programar/store/privacy-policy.html";

export default function SettingsScreen() {
  const token = useToken();
  const prefs = usePrefs();
  const { pro } = usePro();
  const [ai, setAi] = useState<AiSettings | null>(null);
  const [provider, setProvider] = useState<Provider>("anthropic");
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState("");
  const [me, setMe] = useState<{ email: string; name: string | null } | null>(null);
  const [security, setSecurity] = useState<"none" | "password" | "delete">("none");
  const [pw, setPw] = useState({ current: "", next: "" });

  const loadAi = () => {
    if (!token) return;
    api<AiSettings>("/ai/settings").then((s) => {
      setAi(s);
      if (s.provider) setProvider(s.provider);
    }).catch(() => undefined);
    api<{ email: string; name: string | null }>("/me").then(setMe).catch(() => undefined);
  };
  useEffect(loadAi, [token]);
  useEffect(() => setModel(ai?.providers.find((p) => p.provider === provider)?.model ?? ""), [ai, provider]);

  const saveKey = async () => {
    try {
      await api(`/ai/keys/${provider}`, { method: "PUT", body: { apiKey: apiKey.trim(), model: model.trim() || undefined } });
      await api("/ai/settings", { method: "PUT", body: { provider } });
      setApiKey("");
      loadAi();
      notify("Guardado", `${PROVIDER_LABELS[provider]} conectado. La clave se guarda cifrada y solo se usa para hablar con ${PROVIDER_LABELS[provider]}.`);
    } catch (e) {
      notify("Error", e instanceof Error ? e.message : String(e));
    }
  };

  const logout = async (everywhere: boolean) => {
    const ok = await confirm(
      everywhere ? "Cerrar sesión en todos los dispositivos" : "Cerrar sesión",
      "Los datos de tu cuenta se quitan de este dispositivo. Siguen guardados en tu cuenta y vuelven al iniciar sesión.",
      "Cerrar sesión",
    );
    if (ok) await signOut(everywhere);
  };

  const submitSecurity = async () => {
    try {
      if (security === "password") {
        await changePassword(pw.current, pw.next);
        notify("Contraseña cambiada", "Se ha cerrado la sesión en tus otros dispositivos.");
      } else if (security === "delete") {
        const ok = await confirm("Borrar cuenta", "Se borrarán para siempre tu cuenta, tareas, eventos, horarios, claves de IA y calendarios conectados. No se puede deshacer.", "Borrar todo", true);
        if (!ok) return;
        await deleteAccount(pw.current);
        notify("Cuenta borrada", "Tus datos se han eliminado.");
      }
      setSecurity("none");
      setPw({ current: "", next: "" });
    } catch (e) {
      notify("No se pudo", e instanceof Error ? e.message : String(e));
    }
  };

  const toggleFocusApp = (id: string) =>
    updatePrefs({ focusApps: prefs.focusApps.includes(id) ? prefs.focusApps.filter((x) => x !== id) : [...prefs.focusApps, id] });

  const setLimit = (appId: string, minutes: number | null) =>
    updatePrefs({ limits: [...prefs.limits.filter((l) => l.appId !== appId), ...(minutes ? [{ appId, minutesPerDay: minutes }] : [])] });

  const providerInfo = ai?.providers.find((x) => x.provider === provider);

  return (
    <Screen eyebrow="Ajustes" title={me?.name ? `Hola, ${me.name.split(" ")[0]}` : "Tu Foco"}>
      <Card tone={pro ? "success" : "lit"} onPress={() => router.push("/pro")} style={{ flexDirection: "row", alignItems: "center", gap: space(2) }}>
        <LogoMark size={52} />
        <View style={{ flex: 1, gap: 2 }}>
          <Heading size={19}>{pro ? "Tienes Foco Pro" : "Hazte Foco Pro"}</Heading>
          <Body muted>{pro ? "Gracias por apoyar una app sin anuncios." : "Pago único: modo estricto, horarios y calendarios sin límite."}</Body>
        </View>
      </Card>

      <SectionTitle>Cuenta</SectionTitle>
      <Card>
        {token ? (
          <>
            <ListRow icon="user" title={me?.email ?? "Conectado"} description={store.lastSyncError ? `Última sincronización: ${store.lastSyncError}` : "Sincronizado entre el móvil y el PC."} />
            <Row>
              <Button small kind="secondary" icon="refresh" label="Sincronizar" onPress={() => void store.sync()} />
              <Button small kind="ghost" label="Cerrar sesión" onPress={() => void logout(false)} />
            </Row>
            <Divider />
            <Segmented
              options={[{ value: "none", label: "Seguridad" }, { value: "password", label: "Contraseña" }, { value: "delete", label: "Borrar cuenta" }]}
              value={security}
              onChange={(v) => { setSecurity(v); setPw({ current: "", next: "" }); }}
            />
            {security === "none" && <Button small kind="ghost" label="Cerrar sesión en todos los dispositivos" onPress={() => void logout(true)} />}
            {security !== "none" && (
              <View style={{ gap: 8 }}>
                <Field placeholder="Contraseña actual" secureTextEntry value={pw.current} onChangeText={(current) => setPw({ ...pw, current })} autoComplete="current-password" />
                {security === "password" && <Field placeholder="Nueva contraseña (mín. 8)" secureTextEntry value={pw.next} onChangeText={(next) => setPw({ ...pw, next })} autoComplete="new-password" />}
                <Button
                  small
                  kind={security === "delete" ? "danger" : "primary"}
                  label={security === "delete" ? "Borrar mi cuenta y mis datos" : "Cambiar contraseña"}
                  disabled={!pw.current || (security === "password" && pw.next.length < 8)}
                  onPress={() => void submitSecurity()}
                />
              </View>
            )}
          </>
        ) : (
          <>
            <Body>Sin cuenta, todo se guarda solo en este dispositivo. Crea una para usar Foco en el PC y en el móvil a la vez.</Body>
            <Button label="Entrar o crear cuenta" icon="user" onPress={() => router.push("/login")} />
          </>
        )}
      </Card>

      <SectionTitle>Inteligencia artificial</SectionTitle>
      <Card>
        {!token ? (
          <Body muted>Inicia sesión para conectar tu IA.</Body>
        ) : (
          <>
            <Segmented
              options={(Object.keys(PROVIDER_LABELS) as Provider[]).map((p) => ({ value: p, label: `${PROVIDER_LABELS[p]}${ai?.providers.find((x) => x.provider === p)?.hasKey ? " ✓" : ""}` }))}
              value={provider}
              onChange={setProvider}
            />
            <Body muted>{KEY_HELP[provider]}</Body>
            <Field placeholder={providerInfo?.hasKey ? "Clave guardada · pega otra para cambiarla" : "Clave de API"} value={apiKey} onChangeText={setApiKey} autoCapitalize="none" autoCorrect={false} secureTextEntry />
            <Field placeholder="Modelo" value={model} onChangeText={setModel} autoCapitalize="none" autoCorrect={false} />
            <Row>
              <Button small label="Guardar clave" disabled={apiKey.trim().length < 10} onPress={() => void saveKey()} />
              {providerInfo?.hasKey && ai?.provider !== provider && (
                <Button small kind="secondary" label="Usar esta" onPress={() => void api("/ai/settings", { method: "PUT", body: { provider } }).then(loadAi)} />
              )}
              {providerInfo?.hasKey && (
                <Button small kind="ghost" label="Quitar" onPress={() => void api(`/ai/keys/${provider}`, { method: "DELETE" }).then(loadAi)} />
              )}
            </Row>
            {ai?.provider && <Pill label={`Activa: ${PROVIDER_LABELS[ai.provider]}`} icon="sparkles" />}
          </>
        )}
      </Card>

      <SectionTitle>Captura rápida</SectionTitle>
      <Card>
        {Platform.OS === "android" && (
          <>
            <Body muted>Con la pantalla encendida, en cualquier app (necesita la detección de Foco activada):</Body>
            <Row>
              <Chip label="2× bajar volumen" selected={prefs.captureGesture === "volume_down_double"} onPress={() => updatePrefs({ captureGesture: "volume_down_double" })} />
              <Chip label="2× subir volumen" selected={prefs.captureGesture === "volume_up_double"} onPress={() => updatePrefs({ captureGesture: "volume_up_double" })} />
              <Chip label="Desactivado" selected={prefs.captureGesture === "off"} onPress={() => updatePrefs({ captureGesture: "off" })} />
            </Row>
          </>
        )}
        <Body muted>Dictado</Body>
        <Row>
          <Chip label="Voz del móvil (gratis)" selected={prefs.voice === "device"} onPress={() => updatePrefs({ voice: "device" })} />
          <Chip label="Whisper / Gemini (tu clave)" selected={prefs.voice === "server"} onPress={() => updatePrefs({ voice: "server" })} />
        </Row>
      </Card>

      <SectionTitle>Bloqueo</SectionTitle>
      <Card style={{ gap: space(1.5) }}>
        <Body muted>Apps de las sesiones rápidas y los bloques de foco del calendario</Body>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
          {CATALOG.map((a) => <AppTile key={a.id} appId={a.id} size={44} showName selected={prefs.focusApps.includes(a.id)} onPress={() => toggleFocusApp(a.id)} />)}
        </View>
        <Divider />
        <Body muted>Espera antes de poder parar una sesión</Body>
        <Row>
          {[0, 1, 5, 15, 30].map((m) => (
            <Chip key={m} label={m ? `${m} min` : "Sin espera"} selected={prefs.friction.unlockDelayMinutes === m} onPress={() => updatePrefs({ friction: { ...prefs.friction, unlockDelayMinutes: m } })} />
          ))}
        </Row>
        <Field
          label="Frase para parar (escríbela para confirmar)"
          value={prefs.friction.unlockPhrase ?? ""}
          onChangeText={(unlockPhrase) => updatePrefs({ friction: { ...prefs.friction, unlockPhrase: unlockPhrase.slice(0, 80) } })}
          placeholder="Vacío = sin frase"
        />
        <Divider />
        <Body muted>Pomodoro (foco / descanso)</Body>
        <Row>
          {[[25, 5], [50, 10], [90, 15]].map(([f, b]) => (
            <Chip
              key={f}
              label={`${f} / ${b} min`}
              selected={prefs.pomodoro.focusMinutes === f}
              onPress={() => updatePrefs({ pomodoro: { ...prefs.pomodoro, focusMinutes: f!, shortBreakMinutes: b!, longBreakMinutes: b! * 3 } })}
            />
          ))}
        </Row>
        {Platform.OS === "android" && (
          <>
            <Divider />
            <Body muted>Límite diario por app</Body>
            {["instagram", "tiktok", "youtube"].map((id) => {
              const current = prefs.limits.find((l) => l.appId === id)?.minutesPerDay ?? null;
              return (
                <Row key={id} style={{ flexWrap: "nowrap", gap: 10 }}>
                  <AppTile appId={id} size={32} />
                  <Row style={{ flex: 1 }}>
                    {[null, 15, 30, 60].map((m) => <Chip key={String(m)} label={m ? `${m} min` : "Sin límite"} selected={current === m} onPress={() => setLimit(id, m)} />)}
                  </Row>
                </Row>
              );
            })}
            <Body muted>{findApp("instagram")?.name} y compañía se bloquean al llegar al límite hasta mañana.</Body>
          </>
        )}
      </Card>

      <SectionTitle>Sobre Foco</SectionTitle>
      <Card>
        <ListRow icon="info" title="Política de privacidad" description="Qué guardamos y por qué. Spoiler: lo mínimo." onPress={() => void WebBrowser.openBrowserAsync(PRIVACY_URL)} />
        <ListRow icon="star" title="Valora Foco en Google Play" description="Es lo que más nos ayuda a crecer." onPress={() => void WebBrowser.openBrowserAsync("https://play.google.com/store/apps/details?id=app.foco.mobile")} />
        <ListRow icon="refresh" title="Ver la bienvenida otra vez" onPress={() => { updatePrefs({ onboarded: false }); router.replace("/onboarding"); }} />
      </Card>
      <Text style={{ color: colors.faint, textAlign: "center", fontFamily: fonts.body, fontSize: 12 }}>
        Foco {Constants.expoConfig?.version ?? ""} · {API_URL.replace(/^https?:\/\//, "")}
      </Text>
    </Screen>
  );
}
