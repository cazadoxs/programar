# Filtro 400

Antispam de llamadas para España, pensado para el rango 400 de la Ley 10/2025 (obligatorio para llamadas comerciales desde el 17 de octubre de 2026). Android, Kotlin y Jetpack Compose. Sin anuncios.

- **Filtrado 100 % local y gratis:** bloquea, silencia o deja sonar el 400, tarificación adicional, números extranjeros y prefijos propios.
- **Sin permisos restringidos:** usa `CallScreeningService` con el rol del sistema `ROLE_CALL_SCREENING`. No pide contactos ni registro de llamadas.
- **Llamadas esperadas** con caducidad, horario de silencio, historial privado, mosaico de ajustes rápidos, guía de Lista Robinson.
- **Premium (0,99 €/mes o 7,99 €/año):** lista anónima de la comunidad, descargada y consultada en local.

## Estructura

```
apps/filtro-400/
├── app/        App Android (Compose, DataStore, SQLite, Play Billing, WorkManager)
├── core/       Motor de reglas en Kotlin puro (sin Android) + pruebas
├── servidor/   API de la comunidad: Cloudflare Worker + D1 (TypeScript) + pruebas
├── publicar/   Ficha de Play, permisos, privacidad, logo, guía de marketing
└── SEGURIDAD.md
```

## Compilar

Requiere JDK 17 y el SDK de Android (o Android Studio).

```bash
cd apps/filtro-400
./gradlew :core:test                 # pruebas del motor
./gradlew :app:assembleDebug         # APK de depuración
./gradlew :app:bundleRelease         # AAB para Play (firmado si hay clave, ver abajo)
```

GitHub Actions (`.github/workflows/filtro-400.yml`) hace pruebas, lint y compila el APK de depuración en cada cambio; lo descargas desde la pestaña Actions › artefactos.

URL de la API: `./gradlew ... -Pfiltro400.api=https://filtro400-api.TU-SUBDOMINIO.workers.dev` (o variable `FILTRO400_API`). Sin ella, la app funciona entera en local y oculta las funciones de comunidad.

## Lo que necesito de ti (lo único que requiere tus manos)

1. **Cuenta de desarrollador de Google Play** (pago único de 25 USD). Si es una cuenta personal nueva, Google exige una prueba cerrada con 12 o más testers durante 14 días antes de publicar en producción.
2. **Clave de firma:** `keytool -genkeypair -v -keystore filtro400.jks -alias filtro400 -keyalg RSA -keysize 4096 -validity 10000`. Guarda el `.jks` y las contraseñas fuera del repositorio (con Play App Signing, esta es solo la clave de subida). Crea `keystore.properties` con `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
3. **Crear la app y la suscripción** en Play Console con los datos de `publicar/ficha-play-store.md` (producto `comunidad`, planes `mensual` y `anual`).
4. **Servidor** (gratis en Cloudflare para este volumen): ver `servidor/LEEME.md`.
5. **Política de privacidad pública:** publica `publicar/politica-privacidad.md` en una URL (GitHub Pages sirve) y rellena tu nombre y correo.
6. **Probar en un móvil real** (ver «Lo que no he podido verificar» en `SEGURIDAD.md`).
