# Foco

App para dejar de procrastinar: **bloquea** Instagram, TikTok, YouTube,
Snapchat, Facebook, X… (la app entera o solo Reels, Historias, Explorar,
Mensajes o el feed), junta **todos tus calendarios** en uno, tiene un
**asistente con IA** (Anthropic, OpenAI o Google, con tu propia clave) y una
**captura rápida** por gesto (doble pulsación de volumen en Android, Tocar
atrás / botón de Acción en iPhone). Una cuenta para el móvil y el PC.

- Plan de producto y técnico: [`docs/PLAN.md`](docs/PLAN.md)
- Qué hace falta para publicar: [`docs/PUBLICAR.md`](docs/PUBLICAR.md)

## Estructura

```
apps/mobile/      App Expo (Android, iOS y web) — pantallas en src/app
  modules/foco-blocker/   Módulo nativo de bloqueo (Kotlin + Swift)
apps/server/      API: cuenta, sincronización, IA, calendarios, transcripción
packages/core/    Lógica compartida: reglas, pomodoro, captura, calendario, sync
legacy/focusguard-android/   La app Android anterior (FocusGuard), de referencia
```

## Arrancar en local

Requisitos: Node 22 y pnpm 10 (`corepack enable`).

```bash
pnpm install
pnpm test                 # tests de core y servidor
pnpm dev:server           # API en http://localhost:8787 (base de datos local, sin instalar nada)
pnpm dev:mobile           # Expo: pulsa w para abrir la versión web
```

Para fijar las claves de desarrollo del servidor copia `apps/server/.env.example`
a `apps/server/.env` (si no, se generan claves temporales y las claves de IA
guardadas dejan de poder leerse al reiniciar).

### Probar en el móvil

El bloqueo y la captura por gesto usan código nativo, así que **no funcionan
en Expo Go**: hace falta una *development build*.

```bash
cd apps/mobile
npx eas-cli@latest build --profile development --platform android   # o: npx expo run:android
```

En el móvil, la app apunta al servidor de `EXPO_PUBLIC_API_URL` (por defecto
`http://localhost:8787`, que en un teléfono físico hay que cambiar por la IP
del PC o por el servidor desplegado).

## Desplegar el servidor

```bash
docker build -f apps/server/Dockerfile -t foco-server .
docker run -p 8787:8787 -e DATABASE_URL=postgres://… -e JWT_SECRET=… -e ENCRYPTION_KEY=… -e PUBLIC_URL=https://… foco-server
```

Cualquier hosting de contenedores con Postgres sirve (Railway, Fly.io, Render
+ Neon…).
