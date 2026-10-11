import { router } from "expo-router";
import { useEffect, useState } from "react";
import { Platform, Pressable, ScrollView, Text, View } from "react-native";
import {
  addDays,
  eventsBetween,
  FREE_LIMITS,
  mergeEvents,
  startOfDay,
  type CalendarEvent,
  type CalendarSource,
  type Task,
} from "@foco/core";
import { Icon } from "../../components/Icon";
import { Body, Button, Card, Divider, EmptyState, Field, IconBadge, ListRow, Pill, ProBadge, Row, Screen, SectionTitle, Toggle, tap } from "../../components/ui";
import { api, ApiError, useToken } from "../../lib/api";
import { confirm, notify } from "../../lib/dialog";
import { importDeviceCalendars } from "../../lib/deviceCalendars";
import { dayKey, hhmm, longDate, shortWeekday } from "../../lib/format";
import { requirePro } from "../../lib/gate";
import { connectCalendar } from "../../lib/oauth";
import { usePro } from "../../lib/pro";
import { store, useCollection } from "../../lib/store";
import { colors, fonts, radiusSm, space } from "../../lib/theme";
import { useNow } from "../../lib/useNow";

interface Accounts {
  accounts: Array<{ id: string; provider: string; label: string; lastSyncedAt: string | null; lastError: string | null }>;
  available: { google: boolean; microsoft: boolean; ics: boolean };
}

export default function CalendarScreen() {
  const now = useNow(60_000);
  const events = useCollection<CalendarEvent>("events");
  const sources = useCollection<CalendarSource>("sources");
  const tasks = useCollection<Task>("tasks");
  const [day, setDay] = useState(() => startOfDay(new Date()));
  const [accounts, setAccounts] = useState<Accounts | null>(null);
  const [icsUrl, setIcsUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const loggedIn = !!useToken();
  const { pro } = usePro();

  const loadAccounts = () => {
    if (loggedIn) api<Accounts>("/calendar/accounts").then(setAccounts).catch(() => setAccounts(null));
  };
  useEffect(loadAccounts, [loggedIn]);

  const today = startOfDay(now);
  const strip = Array.from({ length: 14 }, (_, i) => addDays(today, i - 2));
  const merged = mergeEvents(events, sources);
  const dayEvents = eventsBetween(merged, day, addDays(day, 1));
  const allDay = dayEvents.filter((e) => e.allDay);
  const timed = dayEvents.filter((e) => !e.allDay);
  const dayTasks = tasks.filter((t) => !t.done && t.due?.slice(0, 10) === dayKey(day));
  const cloudCount = accounts?.accounts.length ?? 0;
  const atLimit = !pro && cloudCount >= FREE_LIMITS.cloudCalendars;

  const run = async (fn: () => Promise<string | void | undefined>) => {
    setBusy(true);
    try {
      const msg = await fn();
      if (msg) notify("Calendario", msg);
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) requirePro("calendars");
      else notify("No se pudo", e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
      loadAccounts();
    }
  };

  const removeAccount = async (a: { id: string; label: string }) => {
    if (await confirm("Quitar calendario", `¿Quitar «${a.label}» de Foco? No se borra nada en el calendario original.`, "Quitar", true)) {
      await run(async () => {
        await api(`/calendar/accounts/${a.id}`, { method: "DELETE" });
        await store.sync();
      });
    }
  };

  const addCloud = (fn: () => Promise<string | void | undefined>) => {
    if (atLimit && !requirePro("calendars")) return;
    void run(fn);
  };

  return (
    <Screen eyebrow="Calendario" title={sameDay(day, today) ? "Hoy" : longDate(day).split(" ")[0]!.replace(/^./, (c) => c.toUpperCase())} subtitle={longDate(day)}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingRight: 8 }} style={{ marginHorizontal: -4 }}>
        {strip.map((d) => {
          const selected = dayKey(d) === dayKey(day);
          const isToday = dayKey(d) === dayKey(today);
          const has = eventsBetween(merged, d, addDays(d, 1)).length > 0;
          return (
            <Pressable
              key={dayKey(d)}
              accessibilityRole="button"
              accessibilityState={{ selected }}
              accessibilityLabel={longDate(d)}
              onPress={() => { tap(); setDay(d); }}
              style={{
                alignItems: "center", gap: 4, paddingVertical: 10, width: 50, borderRadius: radiusSm, borderWidth: 1,
                backgroundColor: selected ? colors.primary : colors.card, borderColor: selected ? colors.primary : isToday ? colors.primary : colors.border,
              }}
            >
              <Text style={{ color: selected ? colors.primaryText : colors.muted, fontSize: 12, fontFamily: fonts.bodySemi, textTransform: "capitalize" }}>{shortWeekday(d)}</Text>
              <Text style={{ color: selected ? colors.primaryText : colors.text, fontSize: 19, fontFamily: fonts.display }}>{d.getDate()}</Text>
              <View style={{ width: 5, height: 5, borderRadius: 3, backgroundColor: has ? (selected ? colors.primaryText : colors.primary) : "transparent" }} />
            </Pressable>
          );
        })}
      </ScrollView>

      <Card>
        {allDay.map((e) => (
          <Row key={e.id} style={{ flexWrap: "nowrap" }}>
            <Pill label="Todo el día" tint={sources.find((s) => s.id === e.sourceId)?.color ?? colors.info} />
            <Body style={{ flex: 1, fontFamily: fonts.bodySemi }} numberOfLines={1}>{e.title}</Body>
          </Row>
        ))}
        {allDay.length > 0 && timed.length > 0 && <Divider />}
        {timed.length === 0 && allDay.length === 0 && dayTasks.length === 0 && <EmptyState icon="sun" title="Día libre" body="Nada en el calendario. Puedes reservar un bloque de foco desde el asistente." />}
        {timed.map((e) => {
          const tint = sources.find((s) => s.id === e.sourceId)?.color ?? colors.primary;
          const live = new Date(e.start) <= now && new Date(e.end) > now;
          return (
            <View key={e.id} style={{ flexDirection: "row", gap: 12, alignItems: "stretch" }}>
              <View style={{ width: 46 }}>
                <Text style={{ color: live ? colors.primary : colors.text, fontFamily: fonts.bodySemi, fontSize: 14 }}>{hhmm(e.start)}</Text>
                <Text style={{ color: colors.faint, fontFamily: fonts.body, fontSize: 12 }}>{hhmm(e.end)}</Text>
              </View>
              <View style={{ flex: 1, borderRadius: 12, padding: 12, backgroundColor: hexTint(tint), borderLeftWidth: 3, borderLeftColor: tint, gap: 2 }}>
                <Row style={{ gap: 6, flexWrap: "nowrap" }}>
                  {e.focus && <Icon name="target" size={14} color={colors.primary} />}
                  <Body style={{ fontFamily: fonts.bodySemi, flex: 1 }} numberOfLines={2}>{e.title}</Body>
                </Row>
                <Body muted numberOfLines={1}>{[sources.find((s) => s.id === e.sourceId)?.name ?? "Foco", e.location].filter(Boolean).join(" · ")}</Body>
                {e.sourceId === "foco" && (
                  <Text
                    onPress={() => store.put("events", e.id, { ...e, focus: !e.focus, updatedAt: new Date().toISOString() })}
                    style={{ color: e.focus ? colors.primary : colors.muted, fontFamily: fonts.bodySemi, fontSize: 13, marginTop: 4 }}
                  >
                    {e.focus ? "◉ Bloque de foco (bloquea tus apps)" : "○ Convertir en bloque de foco"}
                  </Text>
                )}
              </View>
            </View>
          );
        })}
        {dayTasks.length > 0 && <Divider />}
        {dayTasks.map((t) => (
          <Row key={t.id} style={{ flexWrap: "nowrap" }}>
            <Icon name="check" size={16} color={colors.muted} />
            <Body style={{ flex: 1 }}>{t.title}</Body>
          </Row>
        ))}
      </Card>

      <SectionTitle>Tus calendarios</SectionTitle>
      <Card>
        {sources.length === 0 && <Body muted>Aún no hay calendarios. Añade el del móvil o conecta Google y Outlook.</Body>}
        {sources.map((s) => (
          <Row key={s.id} style={{ justifyContent: "space-between", flexWrap: "nowrap", gap: 12 }}>
            <View style={{ width: 14, height: 14, borderRadius: 5, backgroundColor: s.color }} />
            <Body style={{ flex: 1 }} numberOfLines={1}>{s.name}</Body>
            <Toggle label={`Mostrar ${s.name}`} value={s.visible} onChange={(visible) => store.put("sources", s.id, { ...s, visible })} />
          </Row>
        ))}
      </Card>

      <SectionTitle right={!pro ? <Text style={{ color: colors.faint, fontSize: 12, fontFamily: fonts.body }}>{cloudCount}/{FREE_LIMITS.cloudCalendars} en la nube gratis</Text> : undefined}>Añadir</SectionTitle>
      <Card style={{ gap: space(0.5) }}>
        {Platform.OS !== "web" && (
          <ListRow
            icon="phone"
            title="Calendarios del móvil"
            description="Samsung, Xiaomi y cualquier cuenta añadida al teléfono. Gratis y sin límite."
            onPress={() => !busy && void run(async () => {
              const r = await importDeviceCalendars();
              return `${r.calendars} calendarios y ${r.events} eventos importados.`;
            })}
          />
        )}
        {!loggedIn ? (
          <ListRow icon="user" title="Conecta Google, Outlook o iCloud" description="Inicia sesión para verlos aquí y en el PC." onPress={() => router.push("/login")} />
        ) : (
          <>
            {accounts?.available.google && (
              <ListRow icon="calendar" tint={colors.info} title="Google Calendar" description="Solo lectura. Tus eventos de Google, siempre al día." right={atLimit ? <ProBadge /> : undefined} onPress={() => !busy && addCloud(() => connectCalendar("google"))} />
            )}
            {accounts?.available.microsoft && (
              <ListRow icon="calendar" tint={colors.info} title="Outlook / Microsoft 365" description="Trabajo o universidad, solo lectura." right={atLimit ? <ProBadge /> : undefined} onPress={() => !busy && addCloud(() => connectCalendar("microsoft"))} />
            )}
            <Divider />
            <View style={{ gap: 8, paddingTop: 6 }}>
              <Row style={{ gap: 8 }}>
                <IconBadge name="link" tint={colors.success} size={30} />
                <Body style={{ fontFamily: fonts.bodySemi }}>Enlace .ics</Body>
                {atLimit && <ProBadge />}
              </Row>
              <Body muted>iCloud (calendario público), la universidad, tu equipo… Pega el enlace https o webcal.</Body>
              <Field placeholder="https://… o webcal://…" value={icsUrl} onChangeText={setIcsUrl} autoCapitalize="none" autoCorrect={false} keyboardType="url" />
              <Button
                small
                kind="secondary"
                label="Suscribirme"
                disabled={busy || !icsUrl.trim()}
                onPress={() => addCloud(async () => {
                  const r = await api<{ error?: string }>("/calendar/ics", { body: { url: icsUrl.trim(), label: labelFor(icsUrl) } });
                  setIcsUrl("");
                  await store.sync();
                  return r.error ? `Guardado, pero no se pudo leer todavía: ${r.error}` : "Suscripción añadida.";
                })}
              />
            </View>
          </>
        )}
      </Card>

      {loggedIn && (accounts?.accounts.length ?? 0) > 0 && (
        <>
          <SectionTitle right={<Text onPress={() => !busy && void run(async () => { await api("/calendar/sync", { method: "POST" }); await store.sync(); })} style={{ color: colors.primary, fontFamily: fonts.bodySemi, fontSize: 14 }}>Actualizar</Text>}>Conectados</SectionTitle>
          <Card>
            {accounts!.accounts.map((a) => (
              <Row key={a.id} style={{ flexWrap: "nowrap", gap: 12 }}>
                <IconBadge name={a.provider === "ics" ? "link" : "calendar"} tint={a.lastError ? colors.danger : colors.info} size={32} />
                <View style={{ flex: 1 }}>
                  <Body style={{ fontFamily: fonts.bodySemi }} numberOfLines={1}>{a.label}</Body>
                  <Body muted numberOfLines={2}>{a.lastError ? `No se pudo actualizar: ${a.lastError}` : a.lastSyncedAt ? `Actualizado ${hhmm(a.lastSyncedAt)}` : "Pendiente"}</Body>
                </View>
                <Button
                  small
                  kind="ghost"
                  label="Quitar"
                  onPress={() => void removeAccount(a)}
                />
              </Row>
            ))}
          </Card>
        </>
      )}
    </Screen>
  );
}

const sameDay = (a: Date, b: Date) => dayKey(a) === dayKey(b);
const hexTint = (hex: string) => (hex.startsWith("#") && hex.length === 7 ? `${hex}1F` : colors.cardAlt);

function labelFor(url: string) {
  try {
    const host = new URL(url.trim().replace(/^webcal:/i, "https:")).hostname.replace(/^www\./, "");
    return host.includes("icloud") ? "iCloud" : host.slice(0, 60);
  } catch {
    return "Suscripción";
  }
}
