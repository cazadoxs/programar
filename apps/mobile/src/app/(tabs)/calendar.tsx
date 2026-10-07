import * as WebBrowser from "expo-web-browser";
import { useEffect, useState } from "react";
import { Alert, Platform, Pressable, Switch, Text, View } from "react-native";
import {
  addDays,
  eventsBetween,
  mergeEvents,
  startOfDay,
  type CalendarEvent,
  type CalendarSource,
  type Task,
} from "@foco/core";
import { Body, Button, Card, Field, Row, Screen, SectionTitle } from "../../components/ui";
import { api, useToken } from "../../lib/api";
import { importDeviceCalendars } from "../../lib/deviceCalendars";
import { dayKey, hhmm, longDate, shortWeekday } from "../../lib/format";
import { store, useCollection } from "../../lib/store";
import { colors } from "../../lib/theme";

interface Accounts {
  accounts: Array<{ id: string; provider: string; label: string; lastSyncedAt: string | null; lastError: string | null }>;
  available: { google: boolean; microsoft: boolean; ics: boolean };
}

export default function CalendarScreen() {
  const events = useCollection<CalendarEvent>("events");
  const sources = useCollection<CalendarSource>("sources");
  const tasks = useCollection<Task>("tasks");
  const [day, setDay] = useState(() => startOfDay(new Date()));
  const [accounts, setAccounts] = useState<Accounts | null>(null);
  const [icsUrl, setIcsUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const loggedIn = !!useToken();

  const loadAccounts = () => {
    if (loggedIn) api<Accounts>("/calendar/accounts").then(setAccounts).catch(() => setAccounts(null));
  };
  useEffect(loadAccounts, [loggedIn]);

  const week = Array.from({ length: 7 }, (_, i) => addDays(startOfDay(new Date()), i - 1));
  const dayEvents = eventsBetween(mergeEvents(events, sources), day, addDays(day, 1));
  const dayTasks = tasks.filter((t) => !t.done && t.due?.slice(0, 10) === dayKey(day));

  const run = async (fn: () => Promise<string | void>) => {
    setBusy(true);
    try {
      const msg = await fn();
      if (msg) Alert.alert("Listo", msg);
    } catch (e) {
      Alert.alert("Error", e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
      loadAccounts();
    }
  };

  const connect = (provider: "google" | "microsoft") =>
    run(async () => {
      const { url } = await api<{ url: string }>(`/calendar/oauth/${provider}/start`);
      await WebBrowser.openBrowserAsync(url);
      await store.sync();
    });

  return (
    <Screen title="Calendario" subtitle="Todos tus calendarios en uno">
      <Row style={{ justifyContent: "space-between", flexWrap: "nowrap" }}>
        {week.map((d) => {
          const selected = dayKey(d) === dayKey(day);
          return (
            <Pressable key={dayKey(d)} onPress={() => setDay(d)} style={{ alignItems: "center", padding: 8, borderRadius: 12, backgroundColor: selected ? colors.primary : "transparent", minWidth: 42 }}>
              <Text style={{ color: selected ? colors.primaryText : colors.muted, fontSize: 12 }}>{shortWeekday(d)}</Text>
              <Text style={{ color: selected ? colors.primaryText : colors.text, fontSize: 18, fontWeight: "700" }}>{d.getDate()}</Text>
            </Pressable>
          );
        })}
      </Row>

      <SectionTitle>{longDate(day)}</SectionTitle>
      <Card>
        {dayEvents.length === 0 && dayTasks.length === 0 && <Body muted>Día libre.</Body>}
        {dayEvents.map((e) => (
          <View key={e.id} style={{ flexDirection: "row", gap: 12, paddingVertical: 4 }}>
            <Text style={{ color: colors.muted, width: 48 }}>{e.allDay ? "día" : hhmm(e.start)}</Text>
            <View style={{ width: 4, borderRadius: 2, backgroundColor: sources.find((s) => s.id === e.sourceId)?.color ?? colors.primary }} />
            <View style={{ flex: 1 }}>
              <Body>{e.focus ? "🎯 " : ""}{e.title}</Body>
              <Body muted>
                {e.allDay ? "Todo el día" : `hasta ${hhmm(e.end)}`} · {sources.find((s) => s.id === e.sourceId)?.name ?? "Foco"}
              </Body>
            </View>
            {e.sourceId === "foco" && (
              <Pressable onPress={() => store.put("events", e.id, { ...e, focus: !e.focus, updatedAt: new Date().toISOString() })}>
                <Text style={{ color: colors.muted }}>{e.focus ? "Quitar foco" : "Hacer foco"}</Text>
              </Pressable>
            )}
          </View>
        ))}
        {dayTasks.map((t) => <Body key={t.id}>☐ {t.title}</Body>)}
      </Card>

      <SectionTitle>Calendarios</SectionTitle>
      <Card>
        {sources.length === 0 && <Body muted>Aún no hay calendarios conectados.</Body>}
        {sources.map((s) => (
          <Row key={s.id} style={{ justifyContent: "space-between", flexWrap: "nowrap" }}>
            <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: s.color }} />
            <Body style={{ flex: 1 }}>{s.name}</Body>
            <Switch value={s.visible} onValueChange={(visible) => store.put("sources", s.id, { ...s, visible })} />
          </Row>
        ))}
      </Card>

      <Card>
        <Body style={{ fontWeight: "700" }}>Añadir calendarios</Body>
        {Platform.OS !== "web" && (
          <>
            <Body muted>
              Calendarios del móvil: Samsung, Xiaomi, {Platform.OS === "ios" ? "iCloud, " : ""}y cualquier cuenta añadida al teléfono.
            </Body>
            <Button
              disabled={busy}
              label="📱 Importar calendarios del móvil"
              onPress={() => run(async () => {
                const r = await importDeviceCalendars();
                return `${r.calendars} calendarios, ${r.events} eventos.`;
              })}
            />
          </>
        )}
        {!loggedIn && <Body muted>Inicia sesión en Ajustes para conectar Google, Outlook o un enlace ICS y verlos en todos tus dispositivos.</Body>}
        {loggedIn && accounts?.available.google && <Button disabled={busy} kind="secondary" label="Conectar Google Calendar" onPress={() => connect("google")} />}
        {loggedIn && accounts?.available.microsoft && <Button disabled={busy} kind="secondary" label="Conectar Outlook / Microsoft 365" onPress={() => connect("microsoft")} />}
        {loggedIn && (
          <>
            <Field placeholder="Enlace .ics (iCloud público, universidad, deportes…)" value={icsUrl} onChangeText={setIcsUrl} autoCapitalize="none" />
            <Button
              disabled={busy || !icsUrl}
              kind="secondary"
              label="Suscribirse al enlace"
              onPress={() => run(async () => {
                await api("/calendar/ics", { body: { url: icsUrl.trim(), label: "Suscripción" } });
                setIcsUrl("");
                await store.sync();
              })}
            />
          </>
        )}
        {accounts?.accounts.map((a) => (
          <Row key={a.id} style={{ justifyContent: "space-between" }}>
            <Body style={{ flex: 1 }}>
              {a.label}
              {a.lastError ? ` ⚠️ ${a.lastError}` : ""}
            </Body>
            <Button small kind="secondary" label="Quitar" onPress={() => run(async () => { await api(`/calendar/accounts/${a.id}`, { method: "DELETE" }); await store.sync(); })} />
          </Row>
        ))}
        {loggedIn && (
          <Button disabled={busy} small kind="secondary" label="↻ Actualizar ahora" onPress={() => run(async () => { await api("/calendar/sync", { method: "POST" }); await store.sync(); })} />
        )}
      </Card>
    </Screen>
  );
}
