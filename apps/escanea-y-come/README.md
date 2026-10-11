# Escanea y come

Contador de calorías para Android con **escáner de códigos de barras gratis para siempre**. Kotlin + Jetpack Compose.

- **Núcleo (`core/`)**: Kotlin puro y probado con JUnit — validación de códigos EAN/UPC, cálculo de objetivos (Mifflin-St Jeor con límites seguros), lectura defensiva de Open Food Facts, estadísticas, CSV a prueba de inyección de fórmulas.
- **App (`app/`)**: Compose Material 3, Room, escáner de Google ML Kit (sin permiso de cámara), Play Billing.
- **Datos:** [Open Food Facts](https://world.openfoodfacts.org) (ODbL). La atribución está en cada ficha de producto y en Ajustes.
- **Monetización:** escanear y anotar siempre gratis. "Plus" = pago único (`plus_para_siempre`) por estadísticas largas, exportar CSV y colores.
- **Privacidad:** sin cuenta ni servidor; solo el código de barras sale hacia Open Food Facts.
- **Modo tranquilo** pensado para personas con conductas alimentarias delicadas: oculta números, rachas y peso.

## Compilar
```
./gradlew :core:test            # pruebas del núcleo (no necesita Android SDK)
./gradlew :app:assembleDebug    # APK de depuración
./gradlew :app:bundleRelease    # .aab para Play (firma: ver publicar/guia-publicar.md)
```
Requiere JDK 17 y Android SDK 36 (Android Studio lo instala).

## Publicar
Todo el material está en [`publicar/`](publicar): ficha, logo, política de privacidad, guía paso a paso, capturas, vídeos y publicidad. Revisión de seguridad en [`docs/SEGURIDAD.md`](docs/SEGURIDAD.md).
