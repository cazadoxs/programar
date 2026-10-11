# Publicar Foco: lo que hace falta

Todo lo de esta lista necesita tus manos (cuentas, pagos, verificaciones).
Lo demás ya está preparado en el repositorio.

## 1. Cuentas (en este orden)

1. **Expo** (gratis) — expo.dev. Sirve para compilar en la nube sin Android
   Studio. Luego: `cd apps/mobile && npx eas-cli@latest login && npx eas-cli@latest init`.
2. **Google Play Console** (25 $, pago único) — play.google.com/console.
3. **Hosting del servidor** con Postgres — por ejemplo Railway o Fly.io +
   Neon. Variables: `DATABASE_URL`, `JWT_SECRET`, `ENCRYPTION_KEY`
   (`openssl rand -base64 32`), `PUBLIC_URL` (URL del servidor), `WEB_URL`
   (URL de la web de Foco) y `TRUST_PROXY=1` si va detrás de un proxy.
   Nunca actives `BILLING_DEV_ACCEPT_ALL` en producción (solo para pruebas).

## 1b. Foco Pro (compra única)

Modelo: gratis con límites + **Foco Pro** (pago único, sin suscripción ni
anuncios). Precio sugerido 9,99 € (lanzamiento 6,99 €).

1. Play Console › Monetizar › **Productos integrados en la aplicación** →
   crear el producto **`foco_pro_lifetime`** (no consumible) y activarlo.
2. Play Console › Configuración › Acceso a la API → vincular un proyecto de
   Google Cloud, crear una **cuenta de servicio** con permiso de lectura de
   pedidos y gestión de compras, y descargar su JSON.
3. En el servidor: `GOOGLE_PLAY_PACKAGE` (el id del paquete) y
   `GOOGLE_PLAY_SERVICE_ACCOUNT` (el JSON de la cuenta de servicio).
   El servidor verifica y confirma cada compra; un token queda ligado a una
   sola cuenta.

Límites gratis: 2 apps con bloqueo por partes, 3 reglas activas, 1 calendario
en la nube, estadísticas de 7 días. Pro lo quita y añade modo estricto y
Modo Trabajo de la IA.

## 2. Google Play: declaración de Accesibilidad

El bloqueo granular (Reels, Historias…) usa el servicio de accesibilidad.
Play lo permite si se declara bien:

- En Play Console › Contenido de la app › **Accesibilidad**: marcar que la
  app usa la API de Accesibilidad, no como herramienta de accesibilidad,
  para "bloquear contenido/apps elegidos por el usuario para su bienestar
  digital". Adjuntar un vídeo corto mostrando el aviso y el bloqueo.
- La app ya muestra un aviso destacado antes de mandar a los ajustes
  (pantalla Bloqueo) y explica que todo se procesa en el teléfono. La política lista para publicar está en `docs/store/privacy-policy.html`
(alójala, por ejemplo, en GitHub Pages) y rellena los marcadores `[apellidos]`,
`[email]`, `[usuario]`, hosting y región. Toda la ficha, capturas y la guía de
vídeos están en `docs/store/` y `docs/marketing/`.
- Declarar también: servicio en primer plano de tipo *specialUse* (motor de
  respaldo), acceso a datos de uso, y superposición (`SYSTEM_ALERT_WINDOW`),
  todos para bloquear lo que el usuario eligió. Android 17 con Protección
  Avanzada desactiva Accesibilidad para apps que no son de accesibilidad: el
  motor de respaldo sigue bloqueando apps enteras.
- Cuentas personales nuevas: prueba cerrada con **12 testers durante 14
  días** antes de producción.
- Borrado de cuenta: está en la app (Ajustes) y hay que dar también una URL
  web para pedirlo.
- Rellenar "Seguridad de los datos": cuenta (email), contenido creado por el
  usuario (tareas/eventos), nada de uso de apps sale del dispositivo.

## 3. Conectar Google Calendar y Outlook (opcional para el lanzamiento)

**Google**: console.cloud.google.com → nuevo proyecto → habilitar *Google
Calendar API* → Pantalla de consentimiento OAuth (externa) → Credenciales →
ID de cliente OAuth tipo *Aplicación web* con URI de redirección
`https://TU-SERVIDOR/calendar/oauth/google/callback`. Copiar ID y secreto a
`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`. Foco solo pide lectura
(`calendar.readonly`). Mientras Google verifica la app (el permiso de
calendario lo exige) se puede usar con hasta 100 usuarios de prueba.

**Microsoft**: entra.microsoft.com → Registros de aplicaciones → Nuevo
registro (cuentas personales y de organización) → URI de redirección web
`https://TU-SERVIDOR/calendar/oauth/microsoft/callback` → Certificados y
secretos → nuevo secreto. Copiar a `MICROSOFT_CLIENT_ID` /
`MICROSOFT_CLIENT_SECRET`. Permisos delegados: `Calendars.Read` y
`offline_access` (solo lectura).

Tras el inicio de sesión, el servidor devuelve el control a la app
(`foco://oauth`) o a la web (`WEB_URL/oauth`), que termina de conectar la
cuenta con su propia sesión.

Samsung y Xiaomi no necesitan nada: se leen desde el calendario del propio
móvil (botón "Importar calendarios del móvil"). iCloud se añade con su enlace
`.ics`.

## 4. Compilar y subir

```bash
cd apps/mobile
EXPO_PUBLIC_API_URL=https://TU-SERVIDOR npx eas-cli@latest build --profile production --platform android
npx eas-cli@latest submit --platform android
```

Web: `npx expo export --platform web` genera `dist/`, que se sube a cualquier
hosting estático (Vercel, Netlify, Cloudflare Pages).

## 5. Textos legales

Las dos tiendas piden una URL de **política de privacidad**. Puntos clave que
debe decir: qué se guarda en la cuenta (email, tareas, eventos, reglas), que
las claves de IA se guardan cifradas y solo se usan para llamar al proveedor
que el usuario eligió, y que la detección de apps/pantallas ocurre solo en el
teléfono. La política lista para publicar está en `docs/store/privacy-policy.html`
(alójala, por ejemplo, en GitHub Pages) y rellena los marcadores `[apellidos]`,
`[email]`, `[usuario]`, hosting y región. Toda la ficha, capturas y la guía de
vídeos están en `docs/store/` y `docs/marketing/`.
