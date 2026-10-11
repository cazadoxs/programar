import { Inter_400Regular, Inter_500Medium, Inter_600SemiBold, Inter_700Bold } from "@expo-google-fonts/inter";
import { SpaceGrotesk_500Medium, SpaceGrotesk_700Bold } from "@expo-google-fonts/space-grotesk";
import { useFonts } from "expo-font";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { AppState, View } from "react-native";
import { LogoMark } from "../components/Logo";
import { tokenStorage } from "../lib/api";
import { startBlockingSync } from "../lib/blocker";
import { refreshPro } from "../lib/pro";
import { store } from "../lib/store";
import { colors, fonts } from "../lib/theme";

export default function RootLayout() {
  const [ready, setReady] = useState(false);
  const [fontsLoaded, fontError] = useFonts({
    SpaceGrotesk_500Medium,
    SpaceGrotesk_700Bold,
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    Inter_700Bold,
  });

  useEffect(() => {
    let stopBlocking: (() => void) | undefined;
    (async () => {
      await Promise.all([store.load(), tokenStorage.load()]);
      stopBlocking = startBlockingSync();
      setReady(true);
      void store.sync();
      void refreshPro();
    })();
    const sub = AppState.addEventListener("change", (s) => s === "active" && void store.sync());
    const timer = setInterval(() => void store.sync(), 60_000);
    return () => {
      stopBlocking?.();
      sub.remove();
      clearInterval(timer);
    };
  }, []);

  if (!ready || (!fontsLoaded && !fontError)) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, alignItems: "center", justifyContent: "center" }}>
        <LogoMark size={96} />
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
          headerTitleStyle: { fontFamily: fonts.bodySemi },
          headerShadowVisible: false,
          contentStyle: { backgroundColor: colors.bg },
          animation: "fade_from_bottom",
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="onboarding" options={{ headerShown: false, animation: "fade" }} />
        <Stack.Screen
          name="capture"
          options={{ title: "", presentation: "formSheet", sheetAllowedDetents: [0.65, 1], sheetGrabberVisible: true, sheetCornerRadius: 28 }}
        />
        <Stack.Screen name="login" options={{ title: "", presentation: "modal" }} />
        <Stack.Screen name="pro" options={{ title: "", presentation: "modal" }} />
        <Stack.Screen name="rule" options={{ title: "" }} />
        <Stack.Screen name="oauth" options={{ title: "", presentation: "modal" }} />
      </Stack>
    </>
  );
}
