import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { Text, View } from "react-native";
import { CATALOG, checkFreeTargets, FREE_LIMITS, granularAppIds, isScheduleActive, parseHHMM, SURFACE_LABELS, type BlockRule, type Surface } from "@foco/core";
import { AppTile, Body, Button, Card, Chip, Field, Row, Screen, ToggleRow } from "../components/ui";
import { confirm } from "../lib/dialog";
import { requirePro } from "../lib/gate";
import { isPro } from "../lib/pro";
import { newId, store } from "../lib/store";
import { colors, fonts } from "../lib/theme";

const DAYS = [
  { d: 1, l: "L" }, { d: 2, l: "M" }, { d: 3, l: "X" }, { d: 4, l: "J" }, { d: 5, l: "V" }, { d: 6, l: "S" }, { d: 0, l: "D" },
];
const PRESETS: Array<{ label: string; name: string; days: number[]; start: string; end: string }> = [
  { label: "Clase", name: "Horario de clase", days: [1, 2, 3, 4, 5], start: "09:00", end: "14:00" },
  { label: "Estudio", name: "Tarde de estudio", days: [1, 2, 3, 4, 5], start: "16:00", end: "20:00" },
  { label: "Noche", name: "Dormir sin móvil", days: [0, 1, 2, 3, 4, 5, 6], start: "23:00", end: "07:00" },
];

const validTime = (v: string) => {
  try {
    parseHHMM(v);
    return true;
  } catch {
    return false;
  }
};

export default function RuleScreen() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const existing = id ? store.get<BlockRule>("rules", id) : undefined;
  const [name, setName] = useState(existing?.name ?? "Horario de estudio");
  const [apps, setApps] = useState<string[]>(existing?.targets.map((t) => t.appId) ?? ["instagram", "tiktok"]);
  const [surfaces, setSurfaces] = useState<Surface[]>(existing?.targets[0]?.surfaces ?? ["app"]);
  const [always, setAlways] = useState(existing?.schedule.kind === "always");
  const weekly = existing?.schedule.kind === "weekly" ? existing.schedule : undefined;
  const [days, setDays] = useState<number[]>(weekly?.days ?? [1, 2, 3, 4, 5]);
  const [start, setStart] = useState(weekly?.start ?? "09:00");
  const [end, setEnd] = useState(weekly?.end ?? "14:00");
  const [strict, setStrict] = useState(existing?.strict ?? false);

  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);
  const pickSurface = (s: Surface) => {
    if (s === "app") return setSurfaces(["app"]);
    const next = toggle(surfaces.filter((x) => x !== "app"), s);
    setSurfaces(next.length ? next : ["app"]);
  };
  const valid = !!name.trim() && apps.length > 0 && surfaces.length > 0 && (always || (days.length > 0 && validTime(start) && validTime(end)));

  const save = () => {
    const rules = store.list<BlockRule>("rules");
    const targets = apps.map((appId) => ({ appId, surfaces }));
    if (!isPro()) {
      const others = rules.filter((r) => r.id !== existing?.id);
      const needs = others.filter((r) => r.enabled).length >= FREE_LIMITS.rules && !existing ? "rules" : checkFreeTargets(targets, granularAppIds(others), { strict });
      if (needs && !requirePro(needs)) return;
    }
    const rule: BlockRule = {
      id: existing?.id ?? newId(),
      name: name.trim(),
      enabled: existing?.enabled ?? true,
      targets,
      schedule: always ? { kind: "always" } : { kind: "weekly", days, start, end },
      strict,
    };
    store.put("rules", rule.id, rule);
    router.back();
  };

  const remove = async () => {
    if (existing && (await confirm("Borrar horario", `¿Borrar «${existing.name}»?`, "Borrar", true))) {
      store.remove("rules", existing.id);
      router.back();
    }
  };

  if (existing?.strict && existing.enabled && isScheduleActive(existing.schedule, new Date())) {
    return (
      <Screen eyebrow="Horario" title={existing.name}>
        <Card tone="danger">
          <Body>Este horario es estricto y está activo ahora, así que no se puede editar ni borrar hasta que termine. ¡Tú puedes!</Body>
          <Button kind="secondary" label="Volver" onPress={() => router.back()} />
        </Card>
      </Screen>
    );
  }

  return (
    <Screen eyebrow="Horario" title={existing ? "Editar horario" : "Nuevo horario"} subtitle="Foco bloquea solo, sin que tengas que acordarte.">
      {!existing && (
        <Row>
          {PRESETS.map((p) => (
            <Chip key={p.label} label={p.label} onPress={() => { setName(p.name); setDays(p.days); setStart(p.start); setEnd(p.end); setAlways(false); }} />
          ))}
        </Row>
      )}
      <Card>
        <Field label="Nombre" value={name} onChangeText={setName} maxLength={60} />
        <Body muted>Apps</Body>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
          {CATALOG.map((a) => <AppTile key={a.id} appId={a.id} size={46} showName selected={apps.includes(a.id)} onPress={() => setApps(toggle(apps, a.id))} />)}
        </View>
        <Body muted>Qué bloquear</Body>
        <Row>{(Object.keys(SURFACE_LABELS) as Surface[]).map((s) => <Chip key={s} label={SURFACE_LABELS[s]} selected={surfaces.includes(s)} onPress={() => pickSurface(s)} />)}</Row>
      </Card>
      <Card>
        <ToggleRow icon="clock" title="Todo el día, todos los días" value={always} onChange={setAlways} />
        {!always && (
          <>
            <Row style={{ justifyContent: "space-between", flexWrap: "nowrap" }}>
              {DAYS.map(({ d, l }) => (
                <Chip key={d} label={l} selected={days.includes(d)} onPress={() => setDays(toggle(days, d))} />
              ))}
            </Row>
            <Row style={{ flexWrap: "nowrap", gap: 12 }}>
              <View style={{ flex: 1 }}><Field label="Desde" value={start} onChangeText={setStart} placeholder="09:00" keyboardType="numbers-and-punctuation" maxLength={5} /></View>
              <View style={{ flex: 1 }}><Field label="Hasta" value={end} onChangeText={setEnd} placeholder="14:00" keyboardType="numbers-and-punctuation" maxLength={5} /></View>
            </Row>
            {(!validTime(start) || !validTime(end)) && <Text style={{ color: colors.danger, fontFamily: fonts.body, fontSize: 13 }}>Usa el formato 24 h: 09:00</Text>}
            <Body muted>Si «hasta» es menor que «desde», cruza la medianoche (p. ej. de 23:00 a 07:00).</Body>
          </>
        )}
        <ToggleRow
          icon="lock"
          title="Estricto"
          description="No se puede pausar ni editar mientras está activo."
          value={strict}
          locked={!isPro()}
          onChange={(v) => (v && !isPro() ? requirePro("strict") : setStrict(v))}
        />
      </Card>
      <Button label="Guardar horario" disabled={!valid} onPress={save} />
      {existing && <Button kind="danger" label="Borrar horario" onPress={() => void remove()} />}
    </Screen>
  );
}
