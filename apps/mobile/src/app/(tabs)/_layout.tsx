import { Redirect } from "expo-router";
import { Tabs } from "expo-router/js-tabs";
import { Platform, type ColorValue } from "react-native";
import { Icon, type IconName } from "../../components/Icon";
import { usePrefs } from "../../lib/settings";
import { colors, fonts } from "../../lib/theme";

const icon = (name: IconName) => ({ color, focused }: { color: ColorValue; focused: boolean }) => (
  <Icon name={name} size={23} color={String(color)} strokeWidth={focused ? 2.4 : 1.9} />
);

export default function TabsLayout() {
  const prefs = usePrefs();
  if (!prefs.onboarded) return <Redirect href="/onboarding" />;
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: { backgroundColor: colors.bg, borderTopColor: colors.border, height: Platform.OS === "web" ? 64 : undefined, paddingTop: 6 },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.faint,
        tabBarLabelStyle: { fontFamily: fonts.bodySemi, fontSize: 11 },
      }}
    >
      <Tabs.Screen name="index" options={{ title: "Hoy", tabBarIcon: icon("sun") }} />
      <Tabs.Screen name="focus" options={{ title: "Bloqueo", tabBarIcon: icon("shield") }} />
      <Tabs.Screen name="calendar" options={{ title: "Calendario", tabBarIcon: icon("calendar") }} />
      <Tabs.Screen name="assistant" options={{ title: "Asistente", tabBarIcon: icon("sparkles") }} />
      <Tabs.Screen name="settings" options={{ title: "Ajustes", tabBarIcon: icon("settings") }} />
    </Tabs>
  );
}
