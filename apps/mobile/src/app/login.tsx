import { router } from "expo-router";
import { useState } from "react";
import { Body, Button, Card, Chip, Field, Row, Screen } from "../components/ui";
import { api, tokenStorage } from "../lib/api";
import { store } from "../lib/store";

export default function LoginScreen() {
  const [mode, setMode] = useState<"login" | "register">("register");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const { token } = await api<{ token: string }>(`/auth/${mode}`, {
        body: mode === "register" ? { email, password, name: name || undefined } : { email, password },
      });
      await tokenStorage.set(token);
      // Upload what was created on this device before having an account.
      store.markAllPending();
      await store.sync();
      router.back();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title={mode === "register" ? "Crea tu cuenta" : "Entrar"} subtitle="Una cuenta para el móvil y el PC">
      <Row>
        <Chip label="Crear cuenta" selected={mode === "register"} onPress={() => setMode("register")} />
        <Chip label="Ya tengo cuenta" selected={mode === "login"} onPress={() => setMode("login")} />
      </Row>
      <Card>
        {mode === "register" && <Field placeholder="Nombre" value={name} onChangeText={setName} />}
        <Field placeholder="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" autoComplete="email" />
        <Field placeholder="Contraseña (mín. 8)" value={password} onChangeText={setPassword} secureTextEntry autoComplete="password" />
        {error && <Body>⚠️ {error}</Body>}
        <Button label={mode === "register" ? "Crear cuenta" : "Entrar"} disabled={busy || !email || password.length < 8} onPress={submit} />
      </Card>
    </Screen>
  );
}
