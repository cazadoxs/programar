import { router } from "expo-router";
import * as Linking from "expo-linking";
import { useRef, useState } from "react";
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { ASSISTANT_PRESETS } from "@foco/core";
import { Icon } from "../../components/Icon";
import { Body, Button, Card, EmptyState, Glow, IconBadge, IconButton, Row, Segmented, styles as ui, tap } from "../../components/ui";
import { api, ApiError, useToken } from "../../lib/api";
import { requirePro } from "../../lib/gate";
import { usePro } from "../../lib/pro";
import { store } from "../../lib/store";
import { colors, fonts, radius, space } from "../../lib/theme";

const SUPPORT_EMAIL = process.env.EXPO_PUBLIC_SUPPORT_EMAIL ?? "soporte@foco.app";

interface Pending {
  id: string;
  tool: string;
  summary: string;
  status?: "done" | "rejected" | "error";
}

interface Turn {
  role: "user" | "assistant";
  content: string;
  actions?: string[];
  pending?: Pending[];
}

const ACTION_LABELS: Record<string, string> = {
  create_event: "Evento creado",
  move_event: "Evento movido",
  create_task: "Tarea creada",
  start_focus_session: "Sesión de foco iniciada",
};

export default function AssistantScreen() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const [mode, setMode] = useState<"chat" | "work">("chat");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scroll = useRef<ScrollView>(null);
  const loggedIn = !!useToken();
  const { pro } = usePro();

  const send = async (text: string) => {
    if (!text.trim() || loading) return;
    const next: Turn[] = [...turns, { role: "user", content: text.trim() }];
    setTurns(next);
    setInput("");
    setLoading(true);
    setError(null);
    try {
      await store.sync(); // the assistant reads the server copy
      const res = await api<{ text: string; actions: Array<{ tool: string; error?: string }>; pending?: Pending[] }>("/ai/chat", {
        body: {
          messages: next.map(({ role, content }) => ({ role, content })),
          mode,
          timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        },
      });
      const actions = res.actions.filter((a) => !a.error && ACTION_LABELS[a.tool]).map((a) => ACTION_LABELS[a.tool]!);
      setTurns([...next, { role: "assistant", content: res.text, actions, pending: res.pending ?? [] }]);
      if (actions.length) await store.sync();
    } catch (e) {
      if (e instanceof ApiError && e.status === 402) requirePro("workMode");
      setError(e instanceof Error ? e.message : String(e));
      setTurns(turns);
      setInput(text);
    } finally {
      setLoading(false);
      setTimeout(() => scroll.current?.scrollToEnd({ animated: true }), 50);
    }
  };

  const decide = async (turnIndex: number, p: Pending, ok: boolean) => {
    const mark = (status: Pending["status"]) =>
      setTurns((ts) => ts.map((t, i) => (i === turnIndex ? { ...t, pending: t.pending?.map((x) => (x.id === p.id ? { ...x, status } : x)) } : t)));
    try {
      await api(ok ? "/ai/confirm" : "/ai/reject", { body: { id: p.id } });
      mark(ok ? "done" : "rejected");
      if (ok) await store.sync();
    } catch (e) {
      mark("error");
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  if (!loggedIn) {
    return (
      <SafeAreaView style={ui.safe} edges={["top"]}>
        <Glow />
        <View style={[ui.scroll, { flex: 1, justifyContent: "center" }]}>
          <IconBadge name="sparkles" size={64} />
          <Text style={[ui.title, { marginTop: space(2) }]}>Tu asistente</Text>
          <Body muted style={{ marginVertical: space(1.5) }}>
            Planifica tu día, mueve eventos y reserva bloques de foco hablando normal. Usa tu propia cuenta de IA (Anthropic, OpenAI o Google) y trabaja sobre tu calendario y tus tareas.
          </Body>
          <Button label="Iniciar sesión" icon="user" onPress={() => router.push("/login")} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={ui.safe} edges={["top"]}>
      <Glow />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView ref={scroll} contentContainerStyle={[ui.scroll, { paddingBottom: space(4) }]} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
          <View style={ui.header}>
            <View style={{ flex: 1 }}>
              <Text style={ui.eyebrow}>Asistente</Text>
              <Text style={ui.title}>{mode === "work" ? "Modo Trabajo" : "¿En qué te ayudo?"}</Text>
            </View>
          </View>
          <Segmented
            options={[{ value: "chat", label: "Chat", icon: "sparkles" }, { value: "work", label: pro ? "Modo Trabajo" : "Modo Trabajo · Pro", icon: "briefcase" }]}
            value={mode}
            onChange={(m) => (m === "work" && !pro ? requirePro("workMode") : setMode(m))}
          />
          {mode === "work" && <Body muted>La IA planifica y ejecuta: crea eventos, reserva bloques de foco y reorganiza tu semana. Antes de borrar o cerrar nada, te pide permiso.</Body>}

          {turns.length === 0 && (
            <View style={{ gap: 10 }}>
              {ASSISTANT_PRESETS.map((p) => (
                <Card key={p.id} onPress={() => void send(p.prompt)} style={{ flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 14 }}>
                  <Icon name="bolt" size={18} color={colors.primary} />
                  <Text style={{ color: colors.text, fontFamily: fonts.bodySemi, fontSize: 15.5, flex: 1 }}>{p.label}</Text>
                  <Icon name="chevronRight" size={16} color={colors.faint} />
                </Card>
              ))}
            </View>
          )}

          {turns.map((t, i) =>
            t.role === "user" ? (
              <View key={i} style={{ alignSelf: "flex-end", maxWidth: "86%", backgroundColor: colors.primary, borderRadius: radius, borderBottomRightRadius: 6, paddingVertical: 10, paddingHorizontal: 14 }}>
                <Text style={{ color: colors.primaryText, fontFamily: fonts.bodyMedium, fontSize: 15.5, lineHeight: 22 }}>{t.content}</Text>
              </View>
            ) : (
              <View key={i} style={{ alignSelf: "flex-start", maxWidth: "94%", gap: 8 }}>
                <Row style={{ gap: 8, flexWrap: "nowrap", alignItems: "flex-start" }}>
                  <IconBadge name="sparkles" size={28} />
                  <View style={{ flex: 1, backgroundColor: colors.card, borderRadius: radius, borderTopLeftRadius: 6, padding: 14, borderWidth: 1, borderColor: colors.border, gap: 8 }}>
                    <Body>{t.content}</Body>
                    {t.actions?.map((a, j) => (
                      <Row key={j} style={{ gap: 6 }}>
                        <Icon name="check" size={14} color={colors.success} />
                        <Text style={{ color: colors.success, fontFamily: fonts.bodySemi, fontSize: 13 }}>{a}</Text>
                      </Row>
                    ))}
                    <Text
                      accessibilityRole="link"
                      onPress={() => void Linking.openURL(`mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent("Respuesta del asistente de Foco")}&body=${encodeURIComponent(`Quiero reportar esta respuesta:\n\n${t.content.slice(0, 1500)}\n\nMotivo:`)}`)}
                      style={{ color: colors.faint, fontFamily: fonts.body, fontSize: 12 }}
                    >
                      Respuesta generada por IA · Reportar
                    </Text>
                    {t.pending?.map((p) => (
                      <View key={p.id} style={{ gap: 8, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 8 }}>
                        <Text style={{ color: colors.text, fontFamily: fonts.bodySemi, fontSize: 14 }}>¿{p.summary}?</Text>
                        {p.status ? (
                          <Text style={{ color: p.status === "done" ? colors.success : colors.muted, fontFamily: fonts.bodyMedium, fontSize: 13 }}>
                            {p.status === "done" ? "Hecho" : p.status === "rejected" ? "Descartado" : "No se pudo hacer"}
                          </Text>
                        ) : (
                          <Row>
                            <Button small label="Sí, hazlo" onPress={() => void decide(i, p, true)} />
                            <Button small kind="ghost" label="No" onPress={() => void decide(i, p, false)} />
                          </Row>
                        )}
                      </View>
                    ))}
                  </View>
                </Row>
              </View>
            ),
          )}
          {loading && (
            <Row style={{ gap: 10 }}>
              <ActivityIndicator color={colors.primary} />
              <Body muted>Pensando…</Body>
            </Row>
          )}
          {error && (
            <Card tone="danger">
              <Body>{error}</Body>
              {/IA/.test(error) && <Button small kind="secondary" label="Conectar una IA en Ajustes" onPress={() => router.push("/settings")} />}
            </Card>
          )}
          {turns.length > 0 && !loading && (
            <Pressable onPress={() => { tap(); setTurns([]); setError(null); }} style={{ alignSelf: "center" }}>
              <Text style={{ color: colors.faint, fontFamily: fonts.bodySemi, fontSize: 13 }}>Nueva conversación</Text>
            </Pressable>
          )}
          {turns.length === 0 && !error && <EmptyState icon="key" title="Con tu propia clave" body="Tus conversaciones van directas al proveedor que elijas en Ajustes. Foco no las guarda." />}
        </ScrollView>

        <View style={{ flexDirection: "row", gap: 8, alignItems: "flex-end", paddingHorizontal: space(2), paddingVertical: space(1.25), borderTopWidth: 1, borderTopColor: colors.border, backgroundColor: colors.bg, width: "100%", maxWidth: 760, alignSelf: "center" }}>
          <TextInput
            value={input}
            onChangeText={setInput}
            placeholder={mode === "work" ? "Organízame la semana de exámenes…" : "Pregúntame lo que quieras…"}
            placeholderTextColor={colors.faint}
            selectionColor={colors.primary}
            multiline
            onSubmitEditing={() => void send(input)}
            style={[ui.field, { flex: 1, maxHeight: 120, borderRadius: 22, paddingVertical: 12 }]}
            accessibilityLabel="Mensaje para el asistente"
          />
          <IconButton icon="send" kind="primary" label="Enviar" onPress={() => void send(input)} />
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
