import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { ActivityIndicator, AppState, View } from "react-native";
import { tokenStorage } from "../lib/api";
import { startBlockingSync } from "../lib/blocker";
import { store } from "../lib/store";
import { colors } from "../lib/theme";

export default function RootLayout() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let stopBlocking: (() => void) | undefined;
    (async () => {
      await Promise.all([store.load(), tokenStorage.load()]);
      stopBlocking = startBlockingSync();
      setReady(true);
      void store.sync();
    })();
    const sub = AppState.addEventListener("change", (s) => s === "active" && void store.sync());
    const timer = setInterval(() => void store.sync(), 60_000);
    return () => {
      stopBlocking?.();
      sub.remove();
      clearInterval(timer);
    };
  }, []);

  if (!ready) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, alignItems: "center", justifyContent: "center" }}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTintColor: colors.text,
          contentStyle: { backgroundColor: colors.bg },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen
          name="capture"
          options={{ title: "Captura rápida", presentation: "formSheet", sheetAllowedDetents: [0.6, 1], sheetGrabberVisible: true }}
        />
        <Stack.Screen name="login" options={{ title: "Tu cuenta", presentation: "modal" }} />
        <Stack.Screen name="rule" options={{ title: "Regla de bloqueo" }} />
      </Stack>
    </>
  );
}
