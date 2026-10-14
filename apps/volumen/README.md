# Volumen

Volumen fino por debajo del mínimo de Android y volumen por app, sin root.

## Qué hace

- **Volumen fino de todo el móvil**: aplica una ganancia negativa (hasta silencio) con
  `DynamicsProcessing` sobre la mezcla global (sesión 0). Así puedes dejar el volumen del
  sistema en un paso donde los cascos no se desconectan y bajar desde ahí.
- **Volumen por app**: escucha los avisos `OPEN_AUDIO_EFFECT_CONTROL_SESSION` que mandan las
  apps de música y aplica a cada una su propio nivel. Solo funciona con las apps que mandan ese
  aviso (Spotify, YouTube Music, Poweramp, VLC…). YouTube, Chrome y la mayoría de juegos no lo
  mandan, y Android no permite ajustarlas por separado sin root.
- **Paso del sistema** con botones − / + y acceso directo a Opciones de desarrollador para
  "Inhabilitar volumen absoluto" (la causa típica de que los cascos Bluetooth se corten al bajar).

## Límites honestos

- El efecto global sobre la sesión 0 está obsoleto en Android pero sigue funcionando en la
  mayoría de móviles. Si un fabricante lo bloquea, la app lo avisa y el modo por app sigue.
- Necesita una notificación fija mientras está activa (servicio en primer plano).

## Compilar

GitHub Actions (`.github/workflows/volumen-android.yml`) genera el APK de depuración como
artefacto `volumen-apk`. En local: `./gradlew :app:assembleDebug`.
