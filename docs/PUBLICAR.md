# Publicar Foco: lo que hace falta

Todo lo de esta lista necesita tus manos (cuentas, pagos, verificaciones).
Lo demás ya está preparado en el repositorio.

## 1. Cuentas (en este orden)

1. **Expo** (gratis) — expo.dev. Sirve para compilar en la nube sin Android
   Studio ni Xcode. Luego: `cd apps/mobile && npx eas-cli@latest login && npx eas-cli@latest init`.
2. **Google Play Console** (25 $, pago único) — play.google.com/console.
3. **Apple Developer Program** (99 $/año) — developer.apple.com/programs.
   Hace falta para iPhone y Mac.
4. **Hosting del servidor** con Postgres — por ejemplo Railway o Fly.io +
   Neon. Variables: `DATABASE_URL`, `JWT_SECRET`, `ENCRYPTION_KEY`
   (`openssl rand -base64 32`), `PUBLIC_URL`.

## 2. Pedir ya el permiso de Apple para bloquear apps

Sin él no se puede publicar el bloqueo en iPhone y Apple puede tardar.
Formulario: developer.apple.com/contact/request/family-controls-distribution

Texto propuesto (en inglés, como lo piden):

> Foco is a personal productivity and digital-wellbeing app. Users choose the
> apps that distract them (e.g. social networks) and Foco shields them during
> study/work sessions, Pomodoro timers and schedules the user sets for
> themselves. We use FamilyControls with `.individual` authorization (the user
> manages their own device), ManagedSettings to apply shields and
> DeviceActivity for schedules. No data about app usage leaves the device.

## 3. Google Play: declaración de Accesibilidad

El bloqueo granular (Reels, Historias…) usa el servicio de accesibilidad.
Play lo permite si se declara bien:

- En Play Console › Contenido de la app › **Accesibilidad**: marcar que la
  app usa la API de Accesibilidad, no como herramienta de accesibilidad,
  para "bloquear contenido/apps elegidos por el usuario para su bienestar
  digital". Adjuntar un vídeo corto mostrando el aviso y el bloqueo.
- La app ya muestra un aviso destacado antes de mandar a los ajustes
  (pantalla Bloqueo) y explica que todo se procesa en el teléfono.
- Rellenar "Seguridad de los datos": cuenta (email), contenido creado por el
  usuario (tareas/eventos), nada de uso de apps sale del dispositivo.

## 4. Conectar Google Calendar y Outlook (opcional para el lanzamiento)

**Google**: console.cloud.google.com → nuevo proyecto → habilitar *Google
Calendar API* → Pantalla de consentimiento OAuth (externa) → Credenciales →
ID de cliente OAuth tipo *Aplicación web* con URI de redirección
`https://TU-SERVIDOR/calendar/oauth/google/callback`. Copiar ID y secreto a
`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`. Mientras Google verifica la app
(el permiso de calendario lo exige) se puede usar con hasta 100 usuarios de
prueba.

**Microsoft**: entra.microsoft.com → Registros de aplicaciones → Nuevo
registro (cuentas personales y de organización) → URI de redirección web
`https://TU-SERVIDOR/calendar/oauth/microsoft/callback` → Certificados y
secretos → nuevo secreto. Copiar a `MICROSOFT_CLIENT_ID` /
`MICROSOFT_CLIENT_SECRET`.

Samsung, Xiaomi e iCloud no necesitan nada: se leen desde el calendario del
propio móvil (botón "Importar calendarios del móvil").

## 5. Compilar y subir

```bash
cd apps/mobile
EXPO_PUBLIC_API_URL=https://TU-SERVIDOR npx eas-cli@latest build --profile production --platform android
npx eas-cli@latest submit --platform android
# iPhone (cuando Apple apruebe el permiso):
EXPO_PUBLIC_API_URL=https://TU-SERVIDOR npx eas-cli@latest build --profile production --platform ios
npx eas-cli@latest submit --platform ios
```

Web: `npx expo export --platform web` genera `dist/`, que se sube a cualquier
hosting estático (Vercel, Netlify, Cloudflare Pages).

## 6. Textos legales

Las dos tiendas piden una URL de **política de privacidad**. Puntos clave que
debe decir: qué se guarda en la cuenta (email, tareas, eventos, reglas), que
las claves de IA se guardan cifradas y solo se usan para llamar al proveedor
que el usuario eligió, y que la detección de apps/pantallas ocurre solo en el
teléfono.
