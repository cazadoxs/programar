import { router } from "expo-router";
import { useState } from "react";
import { Alert, Platform, Switch } from "react-native";
import { CATALOG, findApp, SURFACE_LABELS, type BlockRule, type FocusSession, type SessionKind, type Surface } from "@foco/core";
import {
  getBlockStats,
  getStatus,
  isBlockerAvailable,
  openAccessibilitySettings,
  openOverlaySettings,
  openUsageAccessSettings,
} from "../../../modules/foco-blocker";
import { Body, Button, Card, Chip, Row, Screen, SectionTitle } from "../../components/ui";
import { canPauseNow, stopSession } from "../../lib/blocker";
import { dayKey, remaining } from "../../lib/format";
import { activeSessions, startSession } from "../../lib/sessions";
import { usePrefs } from "../../lib/settings";
import { store, useCollection } from "../../lib/store";
import { colors } from "../../lib/theme";
import { useNow } from "../../lib/useNow";

const KINDS: Array<{ kind: SessionKind; label: string }> = [
  { kind: "pomodoro", label: "🍅 Pomodoro" },
  { kind: "study", label: "📚 Estudio" },
  { kind: "manual", label: "🎯 Foco" },
];
const DURATIONS = [25, 50, 60, 90, 120, 180];

export default function FocusScreen() {
  const now = useNow(15_000);
  const rules = useCollection<BlockRule>("rules");
  useCollection<FocusSession>("sessions");
  const prefs = usePrefs();
  const [kind, setKind] = useState<SessionKind>("pomodoro");
  const [minutes, setMinutes] = useState(50);
  const [apps, setApps] = useState<string[]>(prefs.focusApps);
  const [surfaces, setSurfaces] = useState<Surface[]>(["app"]);
  const [strict, setStrict] = useState(false);
  const [, refresh] = useState(0);
  const sessions = activeSessions();
  const status = getStatus();

  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);

  const stop = (s: FocusSession) => {
    const pause = canPauseNow();
    if (!pause.allowed || s.strict) return Alert.alert("No se puede parar", "Hay un bloqueo estricto activo. ¡Tú puedes!");
    const delayMs = prefs.friction.unlockDelayMinutes * 60_000;
    if (delayMs > 0 && !s.stopRequestedAt) {
      store.put("sessions", s.id, { ...s, stopRequestedAt: new Date().toISOString() });
      return Alert.alert("Espera un poco", `Podrás parar dentro de ${prefs.friction.unlockDelayMinutes} min. Si para entonces se te ha pasado la tentación, mejor.`);
    }
    const readyAt = new Date(new Date(s.stopRequestedAt ?? 0).getTime() + delayMs);
    if (readyAt > new Date()) return Alert.alert("Todavía no", `Podrás parar en ${remaining(readyAt)}.`);
    const confirm = () => {
      const r = stopSession(s);
      if (!r.ok) Alert.alert("No se puede parar", r.reason);
    };
    if (Platform.OS === "web") return confirm();
    Alert.alert("¿Seguro?", prefs.friction.unlockPhrase ? `Repite para ti: «${prefs.friction.unlockPhrase}».` : "Vas a parar la sesión.", [
      { text: "Seguir concentrado", style: "cancel" },
      { text: "Parar", style: "destructive", onPress: confirm },
    ]);
  };

  const stats = getBlockStats();
  const week = Array.from({ length: 7 }, (_, i) => dayKey(new Date(now.getTime() - i * 86_400_000)));
  const weekBlocks = week.reduce((sum, d) => sum + Object.values(stats[d] ?? {}).reduce((a, b) => a + b, 0), 0);

  return (
    <Screen title="Bloqueo" subtitle="Elige qué te distrae y cuándo">
      <PermissionCard status={status} onChange={() => refresh((n) => n + 1)} />

      {sessions.map((s) => (
        <Card key={s.id} style={{ backgroundColor: colors.cardAlt }}>
          <Body style={{ fontWeight: "800" }}>{s.title ?? "Sesión"} · quedan {remaining(s.endsAt, now)}</Body>
          <Body muted>{s.targets.map((t) => findApp(t.appId)?.name ?? t.appId).join(", ")}</Body>
          {s.strict ? (
            <Body muted>🔒 Estricta: no se puede parar.</Body>
          ) : (
            <Button small kind="danger" label={s.stopRequestedAt ? "Parar (pedido)" : "Quiero parar"} onPress={() => stop(s)} />
          )}
        </Card>
      ))}

      <SectionTitle>Nueva sesión</SectionTitle>
      <Card>
        <Row>{KINDS.map((k) => <Chip key={k.kind} label={k.label} selected={kind === k.kind} onPress={() => setKind(k.kind)} />)}</Row>
        <Row>{DURATIONS.map((m) => <Chip key={m} label={m >= 60 ? `${m / 60} h` : `${m} min`} selected={minutes === m} onPress={() => setMinutes(m)} />)}</Row>
        <Body muted>Apps</Body>
        <Row>{CATALOG.map((a) => <Chip key={a.id} label={a.name} selected={apps.includes(a.id)} onPress={() => setApps(toggle(apps, a.id))} />)}</Row>
        <Body muted>Qué bloquear</Body>
        <Row>
          {(Object.keys(SURFACE_LABELS) as Surface[]).map((s) => (
            <Chip key={s} label={SURFACE_LABELS[s]} selected={surfaces.includes(s)} onPress={() => setSurfaces(s === "app" ? ["app"] : toggle(surfaces.filter((x) => x !== "app"), s))} />
          ))}
        </Row>
        <Row style={{ justifyContent: "space-between" }}>
          <Body>🔒 Modo estricto (no se puede parar)</Body>
          <Switch value={strict} onValueChange={setStrict} />
        </Row>
        <Button
          label="Empezar"
          disabled={apps.length === 0 || surfaces.length === 0}
          onPress={() => startSession({ kind, minutes, appIds: apps, surfaces, strict, title: KINDS.find((k) => k.kind === kind)!.label.slice(3) })}
        />
      </Card>

      <SectionTitle>Reglas y horarios</SectionTitle>
      {rules.map((r) => (
        <Card key={r.id}>
          <Row style={{ justifyContent: "space-between" }}>
            <Body style={{ fontWeight: "700", flex: 1 }} >{r.name}</Body>
            <Switch value={r.enabled} onValueChange={(enabled) => store.put("rules", r.id, { ...r, enabled })} />
          </Row>
          <Body muted>{describeRule(r)}</Body>
          <Button small kind="secondary" label="Editar" onPress={() => router.push({ pathname: "/rule", params: { id: r.id } })} />
        </Card>
      ))}
      <Button kind="secondary" label="＋ Nueva regla" onPress={() => router.push("/rule")} />

      <SectionTitle>Esta semana</SectionTitle>
      <Card>
        <Body>{weekBlocks} intentos bloqueados en 7 días</Body>
        <Body muted>≈ {weekBlocks * 4} minutos recuperados (estimado)</Body>
      </Card>
    </Screen>
  );
}

const DAY_LETTERS = ["D", "L", "M", "X", "J", "V", "S"];

function describeRule(r: BlockRule) {
  const when = r.schedule.kind === "always" ? "Siempre" : `${[1, 2, 3, 4, 5, 6, 0].filter((d) => r.schedule.kind === "weekly" && r.schedule.days.includes(d)).map((d) => DAY_LETTERS[d]).join(" ")} · ${r.schedule.start}–${r.schedule.end}`;
  const what = r.targets.map((t) => `${findApp(t.appId)?.name ?? t.appId} (${t.surfaces.map((s) => SURFACE_LABELS[s].split(" ")[0]).join(", ")})`).join(", ");
  return `${when}\n${what}`;
}

function PermissionCard({ status, onChange }: { status: ReturnType<typeof getStatus>; onChange: () => void }) {
  if (!isBlockerAvailable || status.platform === "unsupported") {
    return (
      <Card>
        <Body>ℹ️ El bloqueo funciona en la app de Android. Aquí puedes crear reglas y sesiones: se sincronizan con tu teléfono.</Body>
      </Card>
    );
  }
  const items = [
    { ok: status.accessibilityEnabled, label: "Servicio de accesibilidad (imprescindible)", open: openAccessibilitySettings },
    { ok: status.overlayPermission, label: "Mostrar sobre otras apps (pantalla de bloqueo)", open: openOverlaySettings },
    { ok: status.usageAccess, label: "Acceso a uso (límites diarios)", open: openUsageAccessSettings },
  ];
  if (items.every((i) => i.ok)) return null;
  return (
    <Card>
      <Body style={{ fontWeight: "700" }}>Activa los permisos</Body>
      <Body muted>Foco solo mira las apps que eliges bloquear y todo se procesa en tu teléfono.</Body>
      {items.map((i) => (
        <Row key={i.label} style={{ justifyContent: "space-between" }}>
          <Body style={{ flex: 1 }}>{i.ok ? "✅" : "⚠️"} {i.label}</Body>
          {!i.ok && <Button small kind="secondary" label="Activar" onPress={() => { i.open(); setTimeout(onChange, 1500); }} />}
        </Row>
      ))}
    </Card>
  );
}
