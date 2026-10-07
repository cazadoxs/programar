import { Tabs } from "expo-router/js-tabs";
import { Text } from "react-native";
import { colors } from "../../lib/theme";

const icon = (glyph: string) => ({ focused }: { focused: boolean }) => <Text style={{ fontSize: 20, opacity: focused ? 1 : 0.6 }}>{glyph}</Text>;

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.border },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.muted,
      }}
    >
      <Tabs.Screen name="index" options={{ title: "Hoy", tabBarIcon: icon("☀️") }} />
      <Tabs.Screen name="focus" options={{ title: "Bloqueo", tabBarIcon: icon("🛡️") }} />
      <Tabs.Screen name="calendar" options={{ title: "Calendario", tabBarIcon: icon("📅") }} />
      <Tabs.Screen name="assistant" options={{ title: "Asistente", tabBarIcon: icon("✨") }} />
      <Tabs.Screen name="settings" options={{ title: "Ajustes", tabBarIcon: icon("⚙️") }} />
    </Tabs>
  );
}
