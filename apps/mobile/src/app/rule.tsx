import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { Switch } from "react-native";
import { CATALOG, parseHHMM, SURFACE_LABELS, type BlockRule, type Surface } from "@foco/core";
import { Body, Button, Card, Chip, Field, Row, Screen } from "../components/ui";
import { newId, store } from "../lib/store";

const DAYS = [
  { d: 1, l: "L" }, { d: 2, l: "M" }, { d: 3, l: "X" }, { d: 4, l: "J" }, { d: 5, l: "V" }, { d: 6, l: "S" }, { d: 0, l: "D" },
];

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
  const validTime = (v: string) => {
    try {
      parseHHMM(v);
      return true;
    } catch {
      return false;
    }
  };
  const valid = name.trim() && apps.length && surfaces.length && (always || (days.length && validTime(start) && validTime(end)));

  const save = () => {
    const rule: BlockRule = {
      id: existing?.id ?? newId(),
      name: name.trim(),
      enabled: existing?.enabled ?? true,
      targets: apps.map((appId) => ({ appId, surfaces })),
      schedule: always ? { kind: "always" } : { kind: "weekly", days, start, end },
      strict,
    };
    store.put("rules", rule.id, rule);
    router.back();
  };

  return (
    <Screen title={existing ? "Editar regla" : "Nueva regla"}>
      <Card>
        <Field placeholder="Nombre" value={name} onChangeText={setName} />
        <Body muted>Apps</Body>
        <Row>{CATALOG.map((a) => <Chip key={a.id} label={a.name} selected={apps.includes(a.id)} onPress={() => setApps(toggle(apps, a.id))} />)}</Row>
        <Body muted>Qué bloquear</Body>
        <Row>
          {(Object.keys(SURFACE_LABELS) as Surface[]).map((s) => (
            <Chip key={s} label={SURFACE_LABELS[s]} selected={surfaces.includes(s)} onPress={() => setSurfaces(s === "app" ? ["app"] : toggle(surfaces.filter((x) => x !== "app"), s))} />
          ))}
        </Row>
      </Card>
      <Card>
        <Row style={{ justifyContent: "space-between" }}>
          <Body>Todo el día, todos los días</Body>
          <Switch value={always} onValueChange={setAlways} />
        </Row>
        {!always && (
          <>
            <Row>{DAYS.map(({ d, l }) => <Chip key={d} label={l} selected={days.includes(d)} onPress={() => setDays(toggle(days, d))} />)}</Row>
            <Row>
              <Field value={start} onChangeText={setStart} placeholder="09:00" style={{ width: 100 }} />
              <Body>a</Body>
              <Field value={end} onChangeText={setEnd} placeholder="14:00" style={{ width: 100 }} />
            </Row>
            <Body muted>Si la hora de fin es menor que la de inicio, la regla cruza la medianoche (p. ej. 23:00 a 07:00).</Body>
          </>
        )}
        <Row style={{ justifyContent: "space-between" }}>
          <Body>🔒 Estricta (no se puede pausar)</Body>
          <Switch value={strict} onValueChange={setStrict} />
        </Row>
      </Card>
      <Button label="Guardar" disabled={!valid} onPress={save} />
      {existing && <Button kind="danger" label="Borrar regla" onPress={() => { store.remove("rules", existing.id); router.back(); }} />}
    </Screen>
  );
}
