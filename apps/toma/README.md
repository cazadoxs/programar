# Toma

Recordatorio de medicación gratuito de verdad: medicamentos ilimitados, alarma insistente, perfiles familiares, modo cuidador, informe PDF y todo en el móvil, sin cuenta ni anuncios.

- **Stack**: Kotlin, Jetpack Compose (Material 3), Room, AlarmManager, WorkManager, Play Billing.
- **Módulos**: `core` (lógica pura de horarios, cumplimiento, cifrado de copias; con tests JVM) y `app` (Android).
- **Android**: minSdk 26, targetSdk 36. Español e inglés.

## Funciones
Horarios (diario, días de la semana, cada N días, cada X horas, a demanda) · alarma a pantalla completa que se repite hasta confirmar · perfiles · modo cuidador por ntfy · existencias y aviso de reposición · historial y racha · PDF para el médico · CSV · copia cifrada (AES-256-GCM) · Toma Plus (pago único: temas, widget, copia automática).

## Desarrollo
```
cd apps/toma
./gradlew :core:test            # lógica (no necesita Android SDK)
./gradlew :app:assembleDebug    # APK de depuración
./gradlew :app:lintDebug :app:testDebugUnitTest
```
Sin acceso a `dl.google.com` (el plugin de Android no se puede descargar), `TOMA_CORE_ONLY=1 ./gradlew :core:test` ejecuta solo los tests de la lógica.

## Publicación y marketing
Todo en [`publicar/`](publicar/): ficha de Play Store, prompt de logo e iconos, política de privacidad, revisión de seguridad, guía de vídeos y publicidad, y pasos para publicar.

Aviso: Toma es un recordatorio; no da consejo médico ni de dosis.
