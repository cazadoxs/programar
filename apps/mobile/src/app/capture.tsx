import { router } from "expo-router";
import { useAudioRecorder, RecordingPresets, requestRecordingPermissionsAsync, setAudioModeAsync } from "expo-audio";
import { useMemo, useState } from "react";
import { Alert, Platform, View } from "react-native";
import { parseCapture, type CalendarEvent, type Task } from "@foco/core";
import { Body, Button, Card, Field, Row, Screen } from "../components/ui";
import { api, useToken } from "../lib/api";
import { hhmm, longDate } from "../lib/format";
import { usePrefs } from "../lib/settings";
import { newId, store } from "../lib/store";
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
    if (!ok) Alert.alert("Dictado no disponible", "Este dispositivo no permite reconocimiento de voz. Escríbelo o elige Whisper en Ajustes.");
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
      Alert.alert("No se pudo transcribir", e instanceof Error ? e.message : String(e));
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
      Alert.alert("La IA no pudo guardarlo", e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const close = () => (router.canGoBack() ? router.back() : router.replace("/"));

  return (
    <Screen title="Apuntar" subtitle="Escribe o dicta: «mañana a las 5 dentista»">
      <Field autoFocus multiline value={text} onChangeText={setText} placeholder="¿Qué quieres apuntar?" style={{ minHeight: 90 }} />
      <Row>
        <Button kind={listening ? "danger" : "secondary"} label={listening ? "⏹ Parar" : "🎙️ Dictar"} onPress={toggleVoice} disabled={busy} />
      </Row>
      {parsed && (
        <Card>
          <Body style={{ fontWeight: "700" }}>{parsed.kind === "event" ? "📅 Evento" : parsed.kind === "note" ? "📝 Nota" : "✅ Tarea"}: {parsed.title}</Body>
          {parsed.start && <Body muted>{longDate(parsed.start)} · {hhmm(parsed.start)}{parsed.end ? ` – ${hhmm(parsed.end)}` : ""}</Body>}
          {parsed.dueDate && <Body muted>Para el {longDate(parsed.dueDate)}</Body>}
          {!parsed.start && !parsed.dueDate && parsed.kind === "task" && <Body muted>Sin fecha: va a la bandeja</Body>}
          {parsed.confidence < 0.7 && <Body muted>No estoy seguro de haberlo entendido bien.</Body>}
        </Card>
      )}
      <View style={{ gap: 8 }}>
        <Button label="Guardar" disabled={!parsed || busy} onPress={save} />
        {token && parsed && <Button kind="secondary" label="✨ Que lo organice la IA" disabled={busy} onPress={askAi} />}
      </View>
    </Screen>
  );
}

const isoDay = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
