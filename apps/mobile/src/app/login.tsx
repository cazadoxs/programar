import { router } from "expo-router";
import { useState } from "react";
import { View } from "react-native";
import { Wordmark } from "../components/Logo";
import { Body, Button, Card, Field, Screen, Segmented } from "../components/ui";
import { signIn } from "../lib/account";
import { colors } from "../lib/theme";

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
      await signIn(mode, mode === "register" ? { email: email.trim(), password, name: name.trim() || undefined } : { email: email.trim(), password });
      router.back();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen title={mode === "register" ? "Crea tu cuenta" : "Bienvenido de nuevo"} subtitle="Una cuenta para el móvil y el PC. Tus tareas, calendarios y horarios, siempre al día.">
      <View style={{ alignItems: "flex-start" }}>
        <Wordmark size={26} />
      </View>
      <Segmented
        options={[{ value: "register", label: "Crear cuenta" }, { value: "login", label: "Ya tengo cuenta" }]}
        value={mode}
        onChange={(m) => {
          setMode(m);
          setError(null);
        }}
      />
      <Card>
        {mode === "register" && <Field label="Nombre" placeholder="¿Cómo te llamas?" value={name} onChangeText={setName} autoComplete="name" />}
        <Field label="Email" placeholder="tu@email.com" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" autoComplete="email" />
        <Field
          label="Contraseña"
          placeholder="Mínimo 8 caracteres"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoComplete={mode === "register" ? "new-password" : "current-password"}
          onSubmitEditing={() => !busy && email && password.length >= 8 && void submit()}
        />
        {error && <Body style={{ color: colors.danger }}>{error}</Body>}
        <Button label={mode === "register" ? "Crear cuenta" : "Entrar"} disabled={busy || !email || password.length < 8} onPress={submit} />
      </Card>
      <Body muted style={{ textAlign: "center" }}>Al crear la cuenta aceptas la política de privacidad. Puedes borrar tu cuenta y todos tus datos desde Ajustes.</Body>
    </Screen>
  );
}
