import { router } from "expo-router";
import { useAudioRecorder, RecordingPresets, requestRecordingPermissionsAsync, setAudioModeAsync } from "expo-audio";
import { useMemo, useState } from "react";
import { ActivityIndicator, Platform, View } from "react-native";
import { parseCapture, type CalendarEvent, type Task } from "@foco/core";
import { Body, Button, Card, Field, Heading, Pill, Row, Screen } from "../components/ui";
import { api, useToken } from "../lib/api";
import { notify } from "../lib/dialog";
import { hhmm, longDate } from "../lib/format";
import { usePrefs } from "../lib/settings";
import { newId, store } from "../lib/store";
import { colors } from "../lib/theme";
import { startDictation, stopDictation, useSpeechRecognitionEvent } from "../lib/voice";

/**
 * Quick capture: opened from the "+" button, the Android volume double-press
 * (foco://capture), or a global shortcut on PC.
 */
export default function CaptureScreen() {
  const [text, setText] = useState("");
  const [listening, setListening] = useState(false);
  const [busy, setBusy] = useState(false);
  const token = useToken();
  const prefs = usePrefs();
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const parsed = useMemo(() => (text.trim() ? parseCapture(text) : null), [text]);

  useSpeechRecognitionEvent("result", (e) => {
    const transcript = e.results[0]?.transcript;
    if (transcript) setText(transcript);
  });
  useSpeechRecognitionEvent("end", () => setListening(false));
  useSpeechRecognitionEvent("error", () => setListening(false));

  const toggleVoice = async () => {
    if (prefs.voice === "server" && token && Platform.OS !== "web") return toggleServerRecording();
    if (listening) {
      stopDictation();
      return;
    }
    const ok = await startDictation(Intl.DateTimeFormat().resolvedOptions().locale || "es-ES");
    if (!ok) notify("Dictado no disponible", "Este dispositivo no permite reconocimiento de voz. Escríbelo o elige Whisper en Ajustes.");
    setListening(ok);
  };

  const toggleServerRecording = async () => {
    if (!listening) {
      const perm = await requestRecordingPermissionsAsync();
      if (!perm.granted) return;
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      recorder.record();
      setListening(true);
      return;
    }
    await recorder.stop();
    setListening(false);
    if (!recorder.uri) return;
    setBusy(true);
    try {
      const form = new FormData();
      form.append("file", { uri: recorder.uri, name: "nota.m4a", type: "audio/m4a" } as unknown as Blob);
      form.append("language", "es");
      const { text: transcript } = await api<{ text: string }>("/ai/transcribe", { form });
      setText(transcript);
    } catch (e) {
      notify("No se pudo transcribir", e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const save = () => {
    if (!parsed) return;
    const now = new Date().toISOString();
    if (parsed.kind === "event" && parsed.start && parsed.end) {
      const event: CalendarEvent = { id: newId(), sourceId: "foco", title: parsed.title, start: parsed.start.toISOString(), end: parsed.end.toISOString(), allDay: false, updatedAt: now };
      store.put("events", event.id, event);
    } else if (parsed.kind === "note") {
      const id = newId();
      store.put("captures", id, { id, text: parsed.title, createdAt: now });
    } else {
      const due = parsed.start?.toISOString() ?? (parsed.dueDate ? isoDay(parsed.dueDate) : undefined);
      const task: Task = { id: newId(), title: parsed.title, due, estimateMinutes: parsed.durationMinutes, done: false, createdAt: now, updatedAt: now };
      store.put("tasks", task.id, task);
    }
    close();
  };

  const askAi = async () => {
    setBusy(true);
    try {
      await api("/ai/capture", { body: { text, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone } });
      await store.sync();
      close();
    } catch (e) {
      notify("La IA no pudo guardarlo", e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const close = () => (router.canGoBack() ? router.back() : router.replace("/"));

  const kindMeta = parsed?.kind === "event" ? { label: "Evento", icon: "calendar" as const, tint: colors.info } : parsed?.kind === "note" ? { label: "Nota", icon: "book" as const, tint: colors.muted } : { label: "Tarea", icon: "check" as const, tint: colors.success };

  return (
    <Screen title="Apuntar" subtitle="Escribe o dicta: «mañana a las 5 dentista»" glow={false}>
      <Field autoFocus multiline value={text} onChangeText={setText} placeholder="¿Qué quieres apuntar?" style={{ minHeight: 96, fontSize: 18 }} accessibilityLabel="Texto a apuntar" />
      <Row>
        <Button kind={listening ? "danger" : "secondary"} icon={listening ? "stop" : "mic"} label={listening ? "Parar" : "Dictar"} onPress={toggleVoice} disabled={busy} />
        {busy && <ActivityIndicator color={colors.primary} />}
      </Row>
      {parsed && (
        <Card tone="flat" style={{ gap: 8 }}>
          <Row style={{ justifyContent: "space-between" }}>
            <Pill label={kindMeta.label} icon={kindMeta.icon} tint={kindMeta.tint} />
            {parsed.confidence < 0.7 && <Pill label="No estoy seguro" tint={colors.primary} />}
          </Row>
          <Heading size={20}>{parsed.title}</Heading>
          {parsed.start && <Body muted>{longDate(parsed.start)} · {hhmm(parsed.start)}{parsed.end ? ` – ${hhmm(parsed.end)}` : ""}</Body>}
          {parsed.dueDate && <Body muted>Para el {longDate(parsed.dueDate)}</Body>}
          {!parsed.start && !parsed.dueDate && parsed.kind === "task" && <Body muted>Sin fecha: va a la bandeja.</Body>}
        </Card>
      )}
      <View style={{ gap: 8 }}>
        <Button label="Guardar" icon="check" disabled={!parsed || busy} onPress={save} />
        {token && parsed && <Button kind="secondary" icon="sparkles" label="Que lo organice la IA" disabled={busy} onPress={askAi} />}
      </View>
    </Screen>
  );
}

const isoDay = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
