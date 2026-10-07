import { router } from "expo-router";
import { Pressable, Text, View } from "react-native";
import {
  addDays,
  eventsBetween,
  findApp,
  mergeEvents,
  pomodoroStateAt,
  SURFACE_LABELS,
  startOfDay,
  type CalendarEvent,
  type CalendarSource,
  type FocusSession,
  type Task,
} from "@foco/core";
import { Body, Button, Card, Row, Screen, SectionTitle } from "../../components/ui";
import { currentBlocks } from "../../lib/blocker";
import { dayKey, greeting, hhmm, longDate, remaining } from "../../lib/format";
import { activeSessions, startSession } from "../../lib/sessions";
import { store, useCollection } from "../../lib/store";
import { colors } from "../../lib/theme";
import { useNow } from "../../lib/useNow";

export default function TodayScreen() {
  const now = useNow(15_000);
  const events = useCollection<CalendarEvent>("events");
  const sources = useCollection<CalendarSource>("sources");
  const tasks = useCollection<Task>("tasks");
  useCollection<FocusSession>("sessions"); // re-render on session changes

  const today = startOfDay(now);
  const agenda = eventsBetween(mergeEvents(events, sources), today, addDays(today, 1));
  const todayKey = dayKey(now);
  const todayTasks = tasks.filter((t) => !t.done && t.due && t.due.slice(0, 10) <= todayKey);
  const inbox = tasks.filter((t) => !t.done && !t.due);
  const blocks = currentBlocks();
  const sessions = activeSessions();

  return (
    <Screen title={greeting(now)} subtitle={longDate(now)} right={<Button small label="＋ Apuntar" onPress={() => router.push("/capture")} />}>
      {sessions.map((s) => {
        const pomo = s.kind === "pomodoro" ? pomodoroStateAt(new Date(s.startedAt), now, s.pomodoro) : null;
        return (
          <Card key={s.id} style={{ backgroundColor: colors.cardAlt }}>
            <Body style={{ fontWeight: "800", fontSize: 20 }}>
              {pomo ? (pomo.phase === "focus" ? `🍅 Foco · ronda ${pomo.cycle}` : "☕ Descanso") : `🎯 ${s.title ?? "Sesión de foco"}`}
            </Body>
            <Body muted>
              {pomo ? `Esta fase acaba en ${remaining(pomo.phaseEndsAt, now)} · ` : ""}La sesión termina en {remaining(s.endsAt, now)}
            </Body>
            <Button kind="secondary" small label="Ver sesión" onPress={() => router.push("/focus")} />
          </Card>
        );
      })}

      <Card>
        <Body style={{ fontWeight: "700" }}>{blocks.length ? "🛡️ Bloqueando ahora" : "Sin bloqueos activos"}</Body>
        {blocks.map((b) => (
          <Body key={b.appId} muted>
            {findApp(b.appId)?.name ?? b.appId}: {b.surfaces.map((s) => SURFACE_LABELS[s]).join(", ")} · {b.reasons.join(", ")}
          </Body>
        ))}
        {sessions.length === 0 && (
          <Row>
            <Button small label="🍅 Pomodoro" onPress={() => startSession({ kind: "pomodoro", minutes: 120, title: "Pomodoro" })} />
            <Button small kind="secondary" label="📚 Estudiar 1 h" onPress={() => startSession({ kind: "study", minutes: 60, title: "Estudio" })} />
            <Button small kind="secondary" label="💼 Trabajar 2 h" onPress={() => startSession({ kind: "manual", minutes: 120, title: "Trabajo" })} />
          </Row>
        )}
      </Card>

      <SectionTitle>Agenda de hoy</SectionTitle>
      <Card>
        {agenda.length === 0 && <Body muted>Nada en el calendario hoy.</Body>}
        {agenda.map((e) => (
          <View key={e.id} style={{ flexDirection: "row", gap: 12, paddingVertical: 4 }}>
            <View style={{ width: 4, borderRadius: 2, backgroundColor: sources.find((s) => s.id === e.sourceId)?.color ?? colors.primary }} />
            <View style={{ flex: 1 }}>
              <Body>{e.focus ? "🎯 " : ""}{e.title}</Body>
              <Body muted>{e.allDay ? "Todo el día" : `${hhmm(e.start)} – ${hhmm(e.end)}`}{e.location ? ` · ${e.location}` : ""}</Body>
            </View>
          </View>
        ))}
      </Card>

      <SectionTitle>Tareas de hoy</SectionTitle>
      <Card>
        {todayTasks.length === 0 && <Body muted>No hay tareas para hoy.</Body>}
        {todayTasks.map((t) => <TaskRow key={t.id} task={t} />)}
      </Card>

      {inbox.length > 0 && (
        <>
          <SectionTitle>Bandeja ({inbox.length})</SectionTitle>
          <Card>{inbox.slice(0, 8).map((t) => <TaskRow key={t.id} task={t} />)}</Card>
        </>
      )}
    </Screen>
  );
}

function TaskRow({ task }: { task: Task }) {
  return (
    <Pressable
      onPress={() => store.put("tasks", task.id, { ...task, done: !task.done, updatedAt: new Date().toISOString() })}
      style={{ flexDirection: "row", gap: 12, paddingVertical: 6, alignItems: "center" }}
    >
      <Text style={{ fontSize: 18, color: colors.primary }}>{task.done ? "☑︎" : "☐"}</Text>
      <Body style={{ flex: 1 }}>{task.title}</Body>
      {task.due && task.due.length > 10 ? <Body muted>{hhmm(task.due)}</Body> : null}
    </Pressable>
  );
}
