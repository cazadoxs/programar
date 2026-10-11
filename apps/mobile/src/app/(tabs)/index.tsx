import { router } from "expo-router";
import { Pressable, Text, View } from "react-native";
import {
  addDays,
  eventsBetween,
  findApp,
  focusMinutesByDay,
  focusStreak,
  formatMinutes,
  mergeEvents,
  pomodoroStateAt,
  SURFACE_LABELS,
  startOfDay,
  type CalendarEvent,
  type CalendarSource,
  type FocusSession,
  type Task,
} from "@foco/core";
import { getBlockStats } from "../../../modules/foco-blocker";
import { Icon } from "../../components/Icon";
import { AppTile, Body, Card, EmptyState, Heading, IconButton, Pill, Ring, Row, Screen, SectionTitle, Stat, hexA, tap } from "../../components/ui";
import { allSessions, currentBlocks } from "../../lib/blocker";
import { clock, dayKey, greeting, hhmm, longDate } from "../../lib/format";
import { activeSessions, startSession } from "../../lib/sessions";
import { getPrefs } from "../../lib/settings";
import { store, useCollection } from "../../lib/store";
import { colors, fonts, radius, space } from "../../lib/theme";
import { useNow } from "../../lib/useNow";

const QUICK = [
  { kind: "pomodoro" as const, minutes: 0, title: "Pomodoro", label: "Pomodoro", detail: "4 rondas", icon: "tomato" as const },
  { kind: "study" as const, minutes: 60, title: "Estudio", label: "Estudiar", detail: "1 hora", icon: "book" as const },
  { kind: "manual" as const, minutes: 120, title: "Trabajo profundo", label: "Trabajar", detail: "2 horas", icon: "briefcase" as const },
];

export default function TodayScreen() {
  const sessions = activeSessions();
  const now = useNow(sessions.length ? 1000 : 30_000);
  const events = useCollection<CalendarEvent>("events");
  const sources = useCollection<CalendarSource>("sources");
  const tasks = useCollection<Task>("tasks");
  useCollection<FocusSession>("sessions"); // re-render on session changes

  const today = startOfDay(now);
  const agenda = eventsBetween(mergeEvents(events, sources), today, addDays(today, 1));
  const todayKey = dayKey(now);
  const todayTasks = tasks.filter((t) => !t.done && t.due && t.due.slice(0, 10) <= todayKey);
  const inbox = tasks.filter((t) => !t.done && !t.due);
  const blocks = currentBlocks(now);
  const all = allSessions();
  const minutesToday = focusMinutesByDay(all, now, 1)[0] ?? 0;
  const streak = focusStreak(all, now);
  const blockedToday = Object.values(getBlockStats()[todayKey] ?? {}).reduce((a, b) => a + b, 0);

  return (
    <Screen eyebrow={longDate(now)} title={greeting(now)} right={<IconButton icon="plus" kind="primary" label="Apuntar algo" onPress={() => router.push("/capture")} />}>
      {sessions.length > 0 ? sessions.map((s) => <SessionHero key={s.id} session={s} now={now} />) : <IdleHero minutesToday={minutesToday} />}

      <Row style={{ flexWrap: "nowrap", alignItems: "stretch" }}>
        <Stat icon="clock" value={formatMinutes(minutesToday)} label="de foco hoy" tint={colors.primary} />
        <Stat icon="flame" value={`${streak} ${streak === 1 ? "día" : "días"}`} label="de racha" tint={streak ? colors.danger : colors.muted} />
        <Stat icon="shield" value={String(blockedToday)} label="frenadas" tint={colors.success} />
      </Row>

      {blocks.length > 0 && (
        <Card tone="flat">
          <Row style={{ justifyContent: "space-between" }}>
            <Pill label="Bloqueando ahora" icon="shield" tint={colors.success} />
            <Text onPress={() => router.push("/focus")} style={{ color: colors.muted, fontFamily: fonts.bodySemi, fontSize: 13 }}>Ver todo</Text>
          </Row>
          {blocks.slice(0, 4).map((b) => (
            <Row key={b.appId} style={{ flexWrap: "nowrap", gap: 12 }}>
              <AppTile appId={b.appId} size={36} />
              <View style={{ flex: 1 }}>
                <Body style={{ fontFamily: fonts.bodySemi }}>{findApp(b.appId)?.name ?? b.appId}</Body>
                <Body muted numberOfLines={1}>{b.surfaces.map((s) => SURFACE_LABELS[s]).join(" · ")}</Body>
              </View>
              {b.strict && <Icon name="lock" size={16} color={colors.danger} />}
            </Row>
          ))}
        </Card>
      )}

      <SectionTitle right={<Text onPress={() => router.push("/calendar")} style={{ color: colors.muted, fontFamily: fonts.bodySemi, fontSize: 13 }}>Calendario</Text>}>Agenda de hoy</SectionTitle>
      <Card>
        {agenda.length === 0 ? (
          <EmptyState icon="calendar" title="Día despejado" body="Nada en tus calendarios hoy. Buen momento para un bloque de foco." />
        ) : (
          agenda.map((e, i) => {
            const past = new Date(e.end) < now;
            const live = !e.allDay && new Date(e.start) <= now && new Date(e.end) > now;
            const tint = sources.find((s) => s.id === e.sourceId)?.color ?? colors.primary;
            return (
              <View key={e.id} style={{ flexDirection: "row", gap: 12, opacity: past ? 0.45 : 1 }}>
                <View style={{ width: 46, alignItems: "flex-end", paddingTop: 2 }}>
                  <Text style={{ color: live ? colors.primary : colors.muted, fontFamily: fonts.bodySemi, fontSize: 13 }}>{e.allDay ? "Día" : hhmm(e.start)}</Text>
                </View>
                <View style={{ alignItems: "center" }}>
                  <View style={{ width: 10, height: 10, borderRadius: 5, marginTop: 5, backgroundColor: tint, borderWidth: live ? 2 : 0, borderColor: colors.text }} />
                  {i < agenda.length - 1 && <View style={{ flex: 1, width: 2, backgroundColor: colors.border, marginTop: 4 }} />}
                </View>
                <View style={{ flex: 1, paddingBottom: space(1.5) }}>
                  <Row style={{ gap: 6 }}>
                    {e.focus && <Icon name="target" size={15} color={colors.primary} />}
                    <Body style={{ fontFamily: fonts.bodySemi, flexShrink: 1 }}>{e.title}</Body>
                  </Row>
                  <Body muted>
                    {e.allDay ? "Todo el día" : `${hhmm(e.start)} – ${hhmm(e.end)}`}
                    {e.location ? ` · ${e.location}` : ""}
                  </Body>
                </View>
              </View>
            );
          })
        )}
      </Card>

      <SectionTitle>Tareas de hoy</SectionTitle>
      <Card>
        {todayTasks.length === 0 ? (
          <EmptyState icon="check" title="Nada pendiente para hoy" body="Apunta algo con el botón + o pulsando dos veces bajar volumen." />
        ) : (
          todayTasks.map((t) => <TaskRow key={t.id} task={t} />)
        )}
      </Card>

      {inbox.length > 0 && (
        <>
          <SectionTitle>Bandeja · {inbox.length}</SectionTitle>
          <Card>{inbox.slice(0, 8).map((t) => <TaskRow key={t.id} task={t} />)}</Card>
        </>
      )}
    </Screen>
  );
}

function SessionHero({ session: s, now }: { session: FocusSession; now: Date }) {
  const pomo = s.kind === "pomodoro" ? pomodoroStateAt(new Date(s.startedAt), now, s.pomodoro) : null;
  const from = pomo ? pomo.phaseStartedAt : new Date(s.startedAt);
  const to = pomo ? pomo.phaseEndsAt : new Date(s.endsAt);
  const progress = (now.getTime() - from.getTime()) / Math.max(1, to.getTime() - from.getTime());
  const onBreak = pomo && pomo.phase !== "focus";
  const tint = onBreak ? colors.success : colors.primary;
  return (
    <Card tone={onBreak ? "success" : "lit"} onPress={() => router.push("/focus")} style={{ alignItems: "center", paddingVertical: space(3) }}>
      <Pill label={onBreak ? "Descanso" : pomo ? `Foco · ronda ${pomo.cycle}` : (s.title ?? "Sesión de foco")} icon={onBreak ? "sun" : "target"} tint={tint} />
      <Ring progress={progress} size={220} color={tint} track={hexA(tint, 0.14)}>
        <Text style={{ color: colors.text, fontFamily: fonts.display, fontSize: 52, letterSpacing: -2, fontVariant: ["tabular-nums"] }}>{clock(to, now)}</Text>
        <Text style={{ color: colors.muted, fontFamily: fonts.bodyMedium, fontSize: 14 }}>{onBreak ? "para volver al foco" : pomo ? "para el descanso" : "para terminar"}</Text>
      </Ring>
      <Row style={{ justifyContent: "center" }}>
        {s.targets.slice(0, 6).map((t) => <AppTile key={t.appId} appId={t.appId} size={30} />)}
        {s.strict && <Pill label="Estricta" icon="lock" tint={colors.danger} />}
      </Row>
      {pomo && <Body muted>La sesión termina a las {hhmm(s.endsAt)}</Body>}
    </Card>
  );
}

function IdleHero({ minutesToday }: { minutesToday: number }) {
  return (
    <Card style={{ gap: space(2), padding: space(2.5) }}>
      <View style={{ gap: 4 }}>
        <Heading size={24}>{minutesToday > 0 ? "¿Otra ronda?" : "¿Empezamos?"}</Heading>
        <Body muted>Elige un modo y Foco bloquea lo que te distrae hasta que acabes.</Body>
      </View>
      <View style={{ flexDirection: "row", gap: space(1) }}>
        {QUICK.map((q, i) => (
          <Pressable
            key={q.kind}
            accessibilityRole="button"
            accessibilityLabel={`${q.label}, ${q.detail}`}
            onPress={() => {
              tap();
              const p = getPrefs().pomodoro;
              // Pomodoro: four focus rounds with their short breaks in between.
              const minutes = q.kind === "pomodoro" ? p.focusMinutes * p.cyclesBeforeLongBreak + p.shortBreakMinutes * (p.cyclesBeforeLongBreak - 1) : q.minutes;
              startSession({ kind: q.kind, minutes, title: q.title });
            }}
            style={({ pressed }) => ({
              flex: 1, gap: 10, padding: space(1.5), borderRadius: radius - 4, borderWidth: 1,
              backgroundColor: i === 0 ? colors.primary : colors.cardAlt, borderColor: i === 0 ? colors.primary : colors.border,
              transform: [{ scale: pressed ? 0.97 : 1 }],
            })}
          >
            <Icon name={q.icon} size={24} color={i === 0 ? colors.primaryText : colors.primary} />
            <View>
              <Text style={{ color: i === 0 ? colors.primaryText : colors.text, fontFamily: fonts.display, fontSize: 17 }}>{q.label}</Text>
              <Text style={{ color: i === 0 ? hexA("#1A1206", 0.7) : colors.muted, fontFamily: fonts.bodyMedium, fontSize: 12.5 }}>{q.detail}</Text>
            </View>
          </Pressable>
        ))}
      </View>
    </Card>
  );
}

function TaskRow({ task }: { task: Task }) {
  const overdue = task.due && task.due.slice(0, 10) < dayKey(new Date());
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: !!task.done }}
      onPress={() => {
        tap();
        store.put("tasks", task.id, { ...task, done: !task.done, updatedAt: new Date().toISOString() });
      }}
      style={{ flexDirection: "row", gap: 12, paddingVertical: 6, alignItems: "center" }}
    >
      <View style={{ width: 24, height: 24, borderRadius: 8, borderWidth: 2, borderColor: task.done ? colors.success : colors.faint, backgroundColor: task.done ? colors.success : "transparent", alignItems: "center", justifyContent: "center" }}>
        {task.done && <Icon name="check" size={14} color={colors.bg} strokeWidth={3} />}
      </View>
      <Body style={{ flex: 1, textDecorationLine: task.done ? "line-through" : "none" }}>{task.title}</Body>
      {task.due && task.due.length > 10 ? <Pill label={hhmm(task.due)} tint={colors.info} /> : overdue ? <Pill label="Atrasada" tint={colors.danger} /> : null}
    </Pressable>
  );
}
