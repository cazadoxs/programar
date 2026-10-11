import * as WebBrowser from "expo-web-browser";
import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { Platform } from "react-native";
import { Body, Button, Card, Screen } from "../components/ui";
import { finishOAuth } from "../lib/oauth";
import { colors } from "../lib/theme";

// On web this page runs inside the sign-in popup: hand the URL back to the opener.
if (Platform.OS === "web") WebBrowser.maybeCompleteAuthSession();

/** Landing page for the calendar OAuth redirect when it arrives as a deep link. */
export default function OAuthScreen() {
  const params = useLocalSearchParams<{ provider?: string; code?: string; state?: string; error?: string }>();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    finishOAuth(params)
      .then(setMessage)
      .catch((e: unknown) => {
        setError(true);
        setMessage(e instanceof Error ? e.message : String(e));
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.state]);

  return (
    <Screen title="Calendario">
      <Card tone={error ? "danger" : message ? "success" : "default"}>
        <Body style={error ? { color: colors.danger } : undefined}>{message ?? "Conectando tu calendario…"}</Body>
        <Button label="Volver a Foco" onPress={() => (router.canGoBack() ? router.back() : router.replace("/calendar"))} />
      </Card>
    </Screen>
  );
}
