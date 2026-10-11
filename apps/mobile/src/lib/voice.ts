import { ExpoSpeechRecognitionModule } from "expo-speech-recognition";
import { Platform } from "react-native";

export { useSpeechRecognitionEvent } from "expo-speech-recognition";

/** On-device dictation (free, works offline on most phones). */
export async function startDictation(lang = "es-ES"): Promise<boolean> {
  if (Platform.OS === "web" && typeof window !== "undefined" && !("webkitSpeechRecognition" in window)) return false;
  if (!ExpoSpeechRecognitionModule.isRecognitionAvailable()) return false;
  const perm = await ExpoSpeechRecognitionModule.requestPermissionsAsync();
  if (!perm.granted) return false;
  ExpoSpeechRecognitionModule.start({
    lang,
    interimResults: true,
    continuous: false,
    addsPunctuation: true,
    requiresOnDeviceRecognition: ExpoSpeechRecognitionModule.supportsOnDeviceRecognition(),
  });
  return true;
}

export function stopDictation() {
  ExpoSpeechRecognitionModule.stop();
}
