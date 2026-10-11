import { router } from "expo-router";
import { useState } from "react";
import { Platform, Text, View } from "react-native";
import {
  CATALOG,
  checkFreeTargets,
  findApp,
  focusMinutesByDay,
  formatMinutes,
  FREE_LIMITS,
  granularAppIds,
  isScheduleActive,
  SURFACE_LABELS,
  type BlockRule,
  type FocusSession,
  type SessionKind,
  type Surface,
} from "@foco/core";
import { getBlockStats, getStatus, isBlockerAvailable, openAccessibilitySettings, openOverlaySettings, openUsageAccessSettings, requestNotificationPermission } from "../../../modules/foco-blocker";
import { confirm, notify } from "../../lib/dialog";
import { Icon, type IconName } from "../../components/Icon";
import {
  AppTile, Bars, Body, Button, Card, Chip, Divider, EmptyState, Field, Heading, IconBadge, Pill, ProBadge, Row, Screen, SectionTitle, Segmented, Toggle, ToggleRow,
} from "../../components/ui";
import { allSessions, canPauseNow, stopSession } from "../../lib/blocker";
import { clock, dayKey, shortWeekday } from "../../lib/format";
import { requirePro } from "../../lib/gate";
import { usePro } from "../../lib/pro";
import { activeSessions, startSession } from "../../lib/sessions";
import { usePrefs } from "../../lib/settings";
import { store, useCollection } from "../../lib/store";
import { colors, fonts, space } from "../../lib/theme";
import { useNow } from "../../lib/useNow";

const KINDS: Array<{ kind: SessionKind; label: string; icon: IconName }> = [
  { kind: "pomodoro", label: "Pomodoro", icon: "tomato" },
  { kind: "study", label: "Estudio", icon: "book" },
  { kind: "manual", label: "Foco", icon: "target" },
];
const DURATIONS = [25, 50, 90, 120, 180, 240];

export default function FocusScreen() {
  const now = useNow(1000);
  const rules = useCollection<BlockRule>("rules");
  const sessionsAll = useCollection<FocusSession>("sessions");
  const prefs = usePrefs();
  const { pro } = usePro();
  const [kind, setKind] = useState<SessionKind>("pomodoro");
  const [minutes, setMinutes] = useState(50);
  const [apps, setApps] = useState<string[]>(prefs.focusApps);
  const [surfaces, setSurfaces] = useState<Surface[]>(["app"]);
  const [strict, setStrict] = useState(false);
  const [, refresh] = useState(0);
  const sessions = activeSessions(now);
  const status = getStatus();

  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);

  const pickSurface = (s: Surface) => {
    if (s === "app") return setSurfaces(["app"]);
    const next = toggle(surfaces.filter((x) => x !== "app"), s);
    setSurfaces(next.length ? next : ["app"]);
  };

  const start = () => {
    const targets = apps.map((appId) => ({ appId, surfaces }));
    if (!pro) {
      const needs = checkFreeTargets(targets, granularAppIds(rules), { strict });
      if (needs && !requirePro(needs)) return;
    }
    startSession({ kind, minutes, appIds: apps, surfaces, strict, title: KINDS.find((k) => k.kind === kind)!.label });
  };

  const days = 7;
  const perDay = focusMinutesByDay(allSessions(), now, days);
  const labels = Array.from({ length: days }, (_, i) => shortWeekday(new Date(now.getTime() - (days - 1 - i) * 86_400_000)).slice(0, 2));
  const stats = getBlockStats();
  const weekBlocks = Array.from({ length: 7 }, (_, i) => dayKey(new Date(now.getTime() - i * 86_400_000))).reduce(
    (sum, d) => sum + Object.values(stats[d] ?? {}).reduce((a, b) => a + b, 0),
    0,
  );
  void sessionsAll;

  return (
    <Screen eyebrow="Bloqueo" title="Tu escudo" subtitle="Elige qué te distrae y cuándo.">
      <PermissionCard status={status} onChange={() => refresh((n) => n + 1)} />

      {sessions.map((s) => <ActiveSession key={s.id} session={s} now={now} />)}

      <SectionTitle>Nueva sesión</SectionTitle>
      <Card style={{ gap: space(2) }}>
        <Segmented options={KINDS.map((k) => ({ value: k.kind, label: k.label, icon: k.icon }))} value={kind} onChange={setKind} />
        <View style={{ gap: 8 }}>
          <Body muted>Duración</Body>
          <Row>{DURATIONS.map((m) => <Chip key={m} label={m >= 60 ? `${m / 60} h` : `${m} min`} selected={minutes === m} onPress={() => setMinutes(m)} />)}</Row>
        </View>
        <View style={{ gap: 10 }}>
          <Body muted>Apps</Body>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
            {CATALOG.map((a) => <AppTile key={a.id} appId={a.id} size={48} showName selected={apps.includes(a.id)} onPress={() => setApps(toggle(apps, a.id))} />)}
          </View>
        </View>
        <View style={{ gap: 8 }}>
          <Row style={{ justifyContent: "space-between" }}>
            <Body muted>Qué bloquear</Body>
            {!pro && <Text style={{ color: colors.faint, fontSize: 12, fontFamily: fonts.body }}>Por partes: {FREE_LIMITS.granularApps} apps gratis</Text>}
          </Row>
          <Row>
            {(Object.keys(SURFACE_LABELS) as Surface[]).map((s) => (
              <Chip key={s} label={SURFACE_LABELS[s]} selected={surfaces.includes(s)} onPress={() => pickSurface(s)} />
            ))}
          </Row>
        </View>
        <Divider />
        <ToggleRow
          icon="lock"
          title="Modo estricto"
          description="No podrás parar la sesión hasta que acabe."
          value={strict}
          locked={!pro}
          onChange={(v) => (v && !pro ? requirePro("strict") : setStrict(v))}
        />
        <Button label={`Empezar ${minutes >= 60 ? `${minutes / 60} h` : `${minutes} min`}`} icon="bolt" disabled={apps.length === 0} onPress={start} />
      </Card>

      <SectionTitle right={<Text onPress={() => router.push("/rule")} style={{ color: colors.primary, fontFamily: fonts.bodySemi, fontSize: 14 }}>+ Nuevo horario</Text>}>Horarios</SectionTitle>
      {rules.length === 0 && (
        <Card>
          <EmptyState icon="clock" title="Sin horarios" body="Bloquea automáticamente en clase, al estudiar o por la noche." action={<Button small kind="secondary" label="Crear horario" onPress={() => router.push("/rule")} />} />
        </Card>
      )}
      {rules.map((r) => {
        const lockedNow = !!r.strict && r.enabled && isScheduleActive(r.schedule, now);
        return (
          <Card key={r.id} onPress={() => (lockedNow ? notify("Horario estricto en curso", "No se puede editar hasta que termine. ¡Tú puedes!") : router.push({ pathname: "/rule", params: { id: r.id } }))}>
            <Row style={{ justifyContent: "space-between", flexWrap: "nowrap" }}>
              <View style={{ flex: 1, gap: 4 }}>
                <Row style={{ gap: 6 }}>
                  <Heading size={17}>{r.name}</Heading>
                  {r.strict && <Icon name="lock" size={14} color={colors.danger} />}
                  {r.enabled && isScheduleActive(r.schedule, now) && <Pill label="Activo" tint={colors.success} />}
                </Row>
                <ScheduleLine rule={r} />
              </View>
              <ToggleSwitch rule={r} locked={lockedNow} />
            </Row>
            <Row style={{ gap: 6 }}>
              {r.targets.slice(0, 8).map((t) => <AppTile key={t.appId} appId={t.appId} size={28} />)}
              <Body muted style={{ marginLeft: 4 }}>{[...new Set(r.targets.flatMap((t) => t.surfaces))].map((s) => SURFACE_LABELS[s].split(" ")[0]).join(", ")}</Body>
            </Row>
          </Card>
        );
      })}

      <SectionTitle right={!pro ? <ProBadge label="HISTORIAL PRO" /> : undefined}>Esta semana</SectionTitle>
      <Card style={{ gap: space(2) }}>
        <Row style={{ justifyContent: "space-between" }}>
          <View>
            <Heading size={28}>{formatMinutes(perDay.reduce((a, b) => a + b, 0))}</Heading>
            <Body muted>de foco en 7 días</Body>
          </View>
          {Platform.OS === "android" && (
            <View style={{ alignItems: "flex-end" }}>
              <Heading size={28} style={{ color: colors.success }}>{weekBlocks}</Heading>
              <Body muted>intentos frenados</Body>
            </View>
          )}
        </Row>
        <Bars values={perDay} labels={labels} highlight={days - 1} format={(v) => (v >= 60 ? `${Math.round(v / 6) / 10}h` : `${v}m`)} />
      </Card>
    </Screen>
  );
}

const DAY_LETTERS = ["D", "L", "M", "X", "J", "V", "S"];

function ScheduleLine({ rule: r }: { rule: BlockRule }) {
  if (r.schedule.kind === "always") return <Body muted>Siempre, todos los días</Body>;
  const schedule = r.schedule;
  return (
    <Row style={{ gap: 4 }}>
      {[1, 2, 3, 4, 5, 6, 0].map((d) => (
        <Text key={d} style={{ width: 16, textAlign: "center", fontSize: 12, fontFamily: fonts.bodySemi, color: schedule.days.includes(d) ? colors.primary : colors.faint }}>{DAY_LETTERS[d]}</Text>
      ))}
      <Body muted style={{ marginLeft: 6 }}>{schedule.start}–{schedule.end}</Body>
    </Row>
  );
}

function ToggleSwitch({ rule, locked }: { rule: BlockRule; locked: boolean }) {
  const { pro } = usePro();
  const rules = useCollection<BlockRule>("rules");
  return (
    <Toggle
        label={`Activar ${rule.name}`}
        value={rule.enabled}
        onChange={(enabled) => {
          if (locked) return notify("Horario estricto en curso", "Podrás desactivarlo cuando termine.");
          if (enabled && !pro && rules.filter((r) => r.enabled && r.id !== rule.id).length >= FREE_LIMITS.rules && !requirePro("rules")) return;
          store.put("rules", rule.id, { ...rule, enabled });
        }}
      />
  );
}

function ActiveSession({ session: s, now }: { session: FocusSession; now: Date }) {
  const prefs = usePrefs();
  const [phrase, setPhrase] = useState("");
  const delayMs = prefs.friction.unlockDelayMinutes * 60_000;
  const readyAt = s.stopRequestedAt ? new Date(new Date(s.stopRequestedAt).getTime() + delayMs) : null;
  const waiting = readyAt && readyAt > now;
  const unlockPhrase = prefs.friction.unlockPhrase?.trim() ?? "";
  const needsPhrase = unlockPhrase.length > 0;
  const phraseOk = !needsPhrase || phrase.trim().toLowerCase() === unlockPhrase.toLowerCase();

  const request = () => {
    const pause = canPauseNow();
    if (!pause.allowed || s.strict) return notify("No se puede parar", "Hay un bloqueo estricto activo. ¡Tú puedes!");
    store.put("sessions", s.id, { ...s, stopRequestedAt: new Date().toISOString() });
  };
  const stop = () => {
    const r = stopSession(s);
    if (!r.ok) notify("No se puede parar", r.reason);
  };

  return (
    <Card tone="lit">
      <Row style={{ justifyContent: "space-between", flexWrap: "nowrap" }}>
        <View style={{ flex: 1 }}>
          <Pill label="En curso" icon="target" />
          <Heading size={22} style={{ marginTop: 8 }}>{s.title ?? "Sesión"}</Heading>
        </View>
        <Text style={{ color: colors.primary, fontFamily: fonts.display, fontSize: 30, fontVariant: ["tabular-nums"] }}>{clock(s.endsAt, now)}</Text>
      </Row>
      <Row style={{ gap: 6 }}>{s.targets.map((t) => <AppTile key={t.appId} appId={t.appId} size={30} />)}</Row>
      {s.strict ? (
        <Row style={{ gap: 8 }}>
          <IconBadge name="lock" tint={colors.danger} size={30} />
          <Body muted style={{ flex: 1 }}>Sesión estricta: no se puede parar. Termina a las {new Date(s.endsAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}.</Body>
        </Row>
      ) : !s.stopRequestedAt && delayMs > 0 ? (
        <Button small kind="ghost" label="Quiero parar" onPress={request} />
      ) : waiting ? (
        <Body muted>Respira. Podrás parar en {clock(readyAt!, now)}. Si para entonces se te ha pasado la tentación, mejor.</Body>
      ) : (
        <View style={{ gap: 8 }}>
          {needsPhrase && (
            <>
              <Body muted>Para parar, escribe: «{prefs.friction.unlockPhrase}»</Body>
              <Field value={phrase} onChangeText={setPhrase} placeholder={prefs.friction.unlockPhrase} autoCapitalize="none" autoCorrect={false} />
            </>
          )}
          <Row>
            <Button small kind="danger" label="Parar sesión" disabled={!phraseOk} onPress={stop} />
            <Button small kind="secondary" label="Seguir concentrado" onPress={() => store.put("sessions", s.id, { ...s, stopRequestedAt: undefined })} />
          </Row>
        </View>
      )}
    </Card>
  );
}

type Status = ReturnType<typeof getStatus>;

const ACCESSIBILITY_DISCLOSURE =
  "Foco usa el servicio de accesibilidad de Android para saber qué app y qué pantalla tienes delante (por ejemplo, los Reels de Instagram) y cerrarla si la has bloqueado, y para detectar la doble pulsación de volumen de la captura rápida.\n\nNo lee tus mensajes ni lo que escribes, no guarda capturas y nada de esto sale de tu móvil. Puedes desactivarlo cuando quieras en Ajustes de Android.";
const USAGE_DISCLOSURE =
  "Foco usa el acceso a uso para saber qué app está abierta y cuánto tiempo la usas hoy, para aplicar tus bloqueos y límites diarios. Esta información se queda en tu móvil.";

function PermissionCard({ status, onChange }: { status: Status; onChange: () => void }) {
  if (!isBlockerAvailable || status.platform === "unsupported") {
    return (
      <Card tone="info">
        <Row style={{ flexWrap: "nowrap", gap: 12 }}>
          <IconBadge name="phone" tint={colors.info} />
          <Body style={{ flex: 1 }}>El bloqueo funciona en la app de Android. Lo que crees aquí se sincroniza con tu móvil.</Body>
        </Row>
      </Card>
    );
  }
  const withDisclosure = (title: string, text: string, open: () => void) => async () => {
    if (await confirm(title, text, "Aceptar y continuar")) {
      open();
      setTimeout(onChange, 1500);
    }
  };
  const items = [
    { ok: status.accessibilityEnabled, label: "Detección de Reels e Historias", detail: "Bloqueo por partes y captura con el volumen. Recomendado.", open: withDisclosure("Detección de Foco", ACCESSIBILITY_DISCLOSURE, openAccessibilitySettings) },
    { ok: status.usageAccess, label: "Acceso a uso", detail: "Límites diarios y bloqueo básico sin accesibilidad.", open: withDisclosure("Acceso a uso", USAGE_DISCLOSURE, openUsageAccessSettings) },
    { ok: status.overlayPermission, label: "Mostrar sobre otras apps", detail: "Para enseñar la pantalla de bloqueo.", open: () => { openOverlaySettings(); setTimeout(onChange, 1500); } },
    { ok: status.notificationsPermission, label: "Notificaciones", detail: "Aviso discreto mientras Foco protege tu foco.", open: () => { requestNotificationPermission(); setTimeout(onChange, 1500); } },
  ];
  const engine = status.engine;
  const missing = items.filter((i) => !i.ok);
  if (engine === "accessibility" && missing.length === 0) return null;
  const tone = engine === "none" ? "danger" : engine === "usage" ? "default" : "default";
  return (
    <Card tone={tone}>
      <Row style={{ gap: 8 }}>
        <Pill
          label={engine === "accessibility" ? "Protección completa" : engine === "usage" ? "Protección básica" : "Bloqueo desactivado"}
          icon="shield"
          tint={engine === "accessibility" ? colors.success : engine === "usage" ? colors.primary : colors.danger}
        />
      </Row>
      <Body muted>
        {engine === "none"
          ? "Activa al menos el acceso a uso para que Foco pueda bloquear."
          : engine === "usage"
            ? "Sin accesibilidad, Foco bloquea apps enteras y pone una pausa antes de los Reels. Activa la detección para bloquear solo partes."
            : "Todo se procesa en tu móvil. Nada sale de él."}
      </Body>
      {missing.map((i) => (
        <Row key={i.label} style={{ flexWrap: "nowrap", gap: 12 }}>
          <IconBadge name="info" tint={colors.primary} size={32} />
          <View style={{ flex: 1 }}>
            <Body style={{ fontFamily: fonts.bodySemi }}>{i.label}</Body>
            <Body muted>{i.detail}</Body>
          </View>
          <Button small kind="secondary" label="Activar" onPress={() => void i.open()} />
        </Row>
      ))}
    </Card>
  );
}
