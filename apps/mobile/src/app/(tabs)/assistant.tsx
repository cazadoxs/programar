import { router } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, View } from "react-native";
import { ASSISTANT_PRESETS } from "@foco/core";
import { Body, Button, Card, Chip, Field, Row, Screen } from "../../components/ui";
import { api, useToken } from "../../lib/api";
import { store } from "../../lib/store";
import { colors } from "../../lib/theme";

interface Turn {
  role: "user" | "assistant";
  content: string;
  actions?: string[];
}

const ACTION_LABELS: Record<string, string> = {
  create_event: "📅 Evento creado",
  move_event: "📅 Evento movido",
  delete_event: "🗑️ Evento borrado",
  create_task: "✅ Tarea creada",
  complete_task: "✅ Tarea hecha",
  start_focus_session: "🛡️ Sesión de foco iniciada",
  stop_focus_session: "⏹️ Sesión parada",
};

export default function AssistantScreen() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const [mode, setMode] = useState<"chat" | "work">("chat");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loggedIn = !!useToken();

  const send = async (text: string) => {
    if (!text.trim() || loading) return;
    const next: Turn[] = [...turns, { role: "user", content: text.trim() }];
    setTurns(next);
    setInput("");
    setLoading(true);
    setError(null);
    try {
      await store.sync(); // the assistant reads the server copy
      const res = await api<{ text: string; actions: Array<{ tool: string; error?: string }> }>("/ai/chat", {
        body: {
          messages: next.map(({ role, content }) => ({ role, content })),
          mode,
          timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        },
      });
      const actions = res.actions.filter((a) => !a.error && ACTION_LABELS[a.tool]).map((a) => ACTION_LABELS[a.tool]!);
      setTurns([...next, { role: "assistant", content: res.text, actions }]);
      if (actions.length) await store.sync();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setTurns(turns);
      setInput(text);
    } finally {
      setLoading(false);
    }
  };

  if (!loggedIn) {
    return (
      <Screen title="Asistente">
        <Card>
          <Body>El asistente usa tu propia cuenta de IA (Anthropic, OpenAI o Google) y trabaja sobre tu calendario y tus tareas.</Body>
          <Button label="Iniciar sesión" onPress={() => router.push("/login")} />
        </Card>
      </Screen>
    );
  }

  return (
    <Screen title="Asistente" subtitle={mode === "work" ? "Modo Trabajo: planifica y ejecuta" : "Pregunta o pide lo que necesites"}>
      <Row>
        <Chip label="💬 Chat" selected={mode === "chat"} onPress={() => setMode("chat")} />
        <Chip label="💼 Modo Trabajo" selected={mode === "work"} onPress={() => setMode("work")} />
      </Row>
      {turns.length === 0 && (
        <Row>{ASSISTANT_PRESETS.map((p) => <Chip key={p.id} label={p.label} onPress={() => send(p.prompt)} />)}</Row>
      )}
      {turns.map((t, i) => (
        <Card key={i} style={t.role === "user" ? { backgroundColor: colors.cardAlt, alignSelf: "flex-end", maxWidth: "90%" } : undefined}>
          <Body>{t.content}</Body>
          {t.actions?.map((a, j) => <Body key={j} muted>{a}</Body>)}
        </Card>
      ))}
      {loading && <ActivityIndicator color={colors.primary} />}
      {error && (
        <Card>
          <Body>⚠️ {error}</Body>
          {/IA/.test(error) && <Button small kind="secondary" label="Ir a Ajustes" onPress={() => router.push("/settings")} />}
        </Card>
      )}
      <View style={{ gap: 8 }}>
        <Field placeholder="Escribe aquí…" value={input} onChangeText={setInput} multiline onSubmitEditing={() => send(input)} />
        <Button label="Enviar" disabled={!input.trim() || loading} onPress={() => send(input)} />
      </View>
    </Screen>
  );
}
