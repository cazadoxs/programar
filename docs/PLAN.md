# Foco — plan de producto y técnico

> Nombre de trabajo: **Foco**. Se puede cambiar en cualquier momento (solo
> aparece en `app.json`, `package.json` y textos).

Foco es una app para dejar de procrastinar que junta en un solo sitio cuatro
cosas que hoy viven separadas:

1. **Bloqueador** de apps que roban tiempo (Instagram, TikTok, Snapchat,
   Facebook, YouTube, X…), con niveles: la app entera, o solo partes (Reels,
   Historias, Explorar, Mensajes, feed infinito).
2. **Súper calendario** que une Google, Microsoft/Outlook, Apple/iCloud,
   Samsung, Xiaomi y cualquier calendario del móvil o enlace ICS.
3. **Asistente con IA** (Anthropic, OpenAI o Google, con la clave o cuenta
   del propio usuario) que planifica el día, reorganiza tareas y puede
   activar sesiones de foco.
4. **Captura rápida**: un gesto (doble pulsación) abre una pestañita flotante
   para apuntar algo escribiendo o por voz, con transcripción, aunque la app
   esté en segundo plano.

Todo ligado a **una cuenta** que se sincroniza entre móvil y PC.

---

## 1. Funcionalidades

### 1.1 Bloqueo

| Modo | Qué hace |
|---|---|
| **Siempre** | Bloquea las apps/partes elegidas todo el día. |
| **Horarios** | Reglas por días y franjas (ej. L–V 9:00–14:00). Varias reglas a la vez. |
| **Pomodoro** | Ciclos foco/descanso (25/5 por defecto, configurable). Bloquea solo durante el foco. |
| **Estudio / Trabajo** | Sesión manual de N minutos con objetivo ("Estudiar tema 4"). |
| **Desde el calendario** | Los eventos marcados como "foco" activan el bloqueo automáticamente. |
| **Límite diario** | X minutos al día por app; al agotarlos se bloquea. |

**Niveles de bloqueo (por app):**
`app completa` · `reels/shorts` · `historias` · `explorar/buscar` ·
`mensajes` · `feed infinito`. Ejemplo: en Instagram bloquear Reels y Explorar
pero dejar Mensajes.

**Anti-trampas (fricción configurable):**
- Espera antes de desactivar (ej. 15 min) y/o PIN.
- "Modo estricto": no se puede parar una sesión empezada.
- Frase a escribir para saltarse el bloqueo ("Estoy eligiendo distraerme").
- Pantalla de bloqueo con la tarea en la que deberías estar y cuánto falta.

**Estadísticas:** intentos bloqueados, tiempo ahorrado estimado, rachas,
gráfico semanal (la app FocusGuard ya tiene esto en Android; se reutiliza).

### 1.2 Súper calendario

- Vista Día / Semana / Mes / Agenda con todos los calendarios mezclados,
  cada fuente con su color y se puede ocultar.
- **Deduplicación**: el mismo evento invitado en Google y en Outlook aparece
  una sola vez.
- Crear/editar eventos eligiendo en qué calendario se guardan.
- **Tareas** (con o sin fecha) junto a los eventos; agenda del día y agenda
  general (bandeja de entrada).
- Bloques de foco: arrastrar una tarea al calendario crea un bloque que
  activa el bloqueador.

### 1.3 Asistente IA

- El usuario conecta **su** proveedor: clave de API de Anthropic, OpenAI o
  Google (Gemini). Se elige modelo. (Iniciar sesión con la cuenta de ChatGPT
  / Claude / Gemini no es posible hoy para apps de terceros: esos servicios
  no ofrecen OAuth para usar la suscripción del usuario desde otra app. Si
  aparece, se añade.)
- **Chat** con acceso a herramientas: ver calendario, crear eventos y
  tareas, buscar huecos libres, empezar/parar sesiones de foco, ver
  estadísticas de bloqueo.
- **Tareas predefinidas** (un toque): "Planifica mi día", "Reorganiza lo que
  no hice", "Resumen de la semana", "Prepárame para mañana", "Divide esta
  tarea en pasos".
- **Modo Trabajo**: la IA propone el plan del día con bloques de foco y, si
  el usuario acepta, los crea y programa el bloqueo.
- Interpretar capturas rápidas ("el jueves a las 5 dentista" → evento).
- Todo lo que la IA cambia se puede deshacer y pide confirmación para
  borrar.

### 1.4 Captura rápida

| Plataforma | Gesto / acceso | Notas |
|---|---|---|
| Android | **Doble pulsación de bajar volumen** con la pantalla encendida | Lo detecta el servicio de accesibilidad (`flagRequestFilterKeyEvents`), funciona aunque la app esté cerrada. Configurable (subir volumen, o desactivado). |
| Android | Mosaico de Ajustes rápidos, widget, acceso directo, Quick Tap (Pixel: doble toque en la parte trasera) | Alternativas sin accesibilidad. |
| PC | Atajo global (ej. `Ctrl+Shift+Espacio`) | App de escritorio. |

La pestañita permite escribir o mantener pulsado para dictar. La
transcripción usa el reconocimiento de voz del propio teléfono (gratis,
funciona sin internet en la mayoría de móviles) y, si el usuario quiere más
calidad, Whisper con su clave de OpenAI. El texto se interpreta (fecha, hora,
duración, ¿es tarea o evento?) primero en el dispositivo con un parser y,
si hay IA conectada y el parser duda, con la IA.

### 1.5 Cuenta y sincronización

- Registro con email + contraseña (más adelante: Iniciar sesión con
  Google).
- Sincronización de reglas de bloqueo, tareas, capturas, ajustes y eventos
  propios entre todos los dispositivos. Funciona offline y sincroniza al
  volver la conexión (última escritura gana, por registro).
- Las claves de IA se guardan cifradas en el servidor (o solo en el
  dispositivo, si el usuario lo prefiere).

---

## 2. Lo que permite (y no) cada plataforma

Esta es la parte que decide el diseño. Resumen honesto:

### Android — se puede casi todo

- **Bloqueo granular (Reels, Historias, Mensajes…)**: con un
  `AccessibilityService` que lee el árbol de la pantalla de la app objetivo y
  saca al usuario de la sección bloqueada. Es lo que ya hace FocusGuard
  (`legacy/focusguard-android`) para Reels/Shorts/TikTok y se porta al
  módulo nativo nuevo. Es heurístico: cuando Instagram cambia su interfaz
  hay que actualizar los patrones → los patrones se descargan del servidor
  (actualización sin publicar versión nueva).
- **Bloqueo de app completa**: el mismo servicio detecta qué app está en
  primer plano y muestra una pantalla de bloqueo encima
  (`SYSTEM_ALERT_WINDOW`) o vuelve al inicio. `UsageStatsManager` para
  límites de tiempo diarios.
- **Google Play**: usar Accesibilidad para algo que no es ayuda a la
  discapacidad está permitido si se rellena la **declaración de
  Accesibilidad** en Play Console, se muestra un aviso destacado y se pide
  consentimiento explícito. Apps de bienestar digital lo hacen (es el caso
  de uso típico). Riesgo: revisión más lenta. Plan B: distribución por APK /
  F-Droid / Galaxy Store además de Play.

### iPhone: fuera del alcance

Decisión de Álvaro (7 oct 2026): Foco **no** se hace para iPhone. Se publica
en Android y en el PC (web / escritorio). El calendario sigue pudiendo leer
iCloud por CalDAV o enlace `.ics`.

### PC (Windows / macOS / Linux)

- App de escritorio (Tauri) con el calendario, IA y captura rápida.
- Bloqueo en el PC: **extensión de navegador** (Chrome, Edge, Firefox,
  Safari) que bloquea webs y partes de webs (instagram.com/reels,
  youtube.com/shorts, etc.) con las mismas reglas sincronizadas. Bloqueo de
  programas de escritorio en una fase posterior.

### Calendarios

| Proveedor | Cómo se conecta |
|---|---|
| Google Calendar | OAuth 2.0 + Google Calendar API (servidor). Requiere verificación de la app por Google para el scope de calendario. |
| Microsoft (Outlook, Office 365, Hotmail) | OAuth 2.0 + Microsoft Graph (servidor). |
| Apple iCloud | CalDAV con contraseña específica de app, o enlace `.ics` público. |
| **Samsung Calendar** | No tiene API pública en la nube. Sus eventos están en el **proveedor de calendario de Android** (`CalendarContract`) del móvil Samsung → la app los lee en el teléfono y los sube a la cuenta Foco (con permiso). |
| **Xiaomi / HyperOS Calendar** | Igual que Samsung: vía calendario del dispositivo Android. |
| Cualquier otro | Calendarios del dispositivo Android, CalDAV genérico o suscripción a URL `.ics`. |

Así "todo aparece" aunque una marca no tenga API: el móvil hace de puente.

### IA

- Llamadas desde el **servidor** con la clave del usuario (así la misma
  clave sirve en móvil y PC, y no se expone en el cliente). Adaptadores para
  Anthropic (SDK oficial), OpenAI y Google con una interfaz común de
  herramientas.
- Modelo por defecto de Anthropic: `claude-opus-5-5` (el más capaz); el
  usuario puede elegir uno más barato (`claude-sonnet-5-5`,
  `claude-haiku-5-5`).

---

## 3. Arquitectura

**Todo en TypeScript, un solo monorepo.** Una lengua y un núcleo compartido
para móvil, web, escritorio y servidor; solo el bloqueo (que tiene que ser
nativo sí o sí) va en Kotlin.

```
apps/
  mobile/        Expo (React Native) → Android y Web
    modules/foco-blocker/   módulo nativo: Kotlin (Accesibilidad)
  server/        API (Hono + Postgres/Drizzle): cuenta, sync, IA, calendarios, voz
  desktop/       (fase 2) Tauri envolviendo la web + atajo global
  extension/     (fase 2) extensión de navegador para bloquear webs
packages/
  core/          lógica compartida: reglas de bloqueo, pomodoro, calendario,
                 parser de captura rápida, herramientas de la IA, sync
legacy/
  focusguard-android/   la app Android existente (referencia y APK actual)
```

- **Expo**: un código para Android + web, builds y publicación en
  tiendas con EAS, módulos nativos propios con Expo Modules.
- **Hono + Drizzle + Postgres**: servidor ligero, portable (Node, Docker,
  Fly.io, Railway, Cloudflare). En desarrollo y tests usa PGlite (Postgres
  en memoria), sin instalar nada.
- **Sync**: cada registro (tarea, regla, captura, evento propio, ajuste)
  lleva `updatedAt` + `deletedAt`; el cliente hace `push` de cambios y
  `pull` desde su último cursor; gana la escritura más reciente. Simple y
  suficiente para un usuario con varios dispositivos.
- **Reglas de bloqueo**: se evalúan en el dispositivo (`packages/core`), y el
  módulo nativo recibe el resultado ("ahora mismo bloquear: Instagram·reels,
  TikTok·app") cada vez que algo cambia, además de un horario precalculado
  para que funcione con la app cerrada.

---

## 4. Hoja de ruta

### Fase 0 — Cimientos (este PR)
- [x] Plan (este documento).
- [x] Monorepo, núcleo compartido con tests: reglas, pomodoro, parser de
  captura, fusión de calendarios, huecos libres, herramientas de IA, sync.
- [x] Servidor: cuenta, sync, claves de IA cifradas, chat con IA y
  herramientas, transcripción, OAuth de Google/Microsoft, ICS.
- [x] App Expo con pestañas Hoy · Bloqueo · Calendario · Asistente ·
  Ajustes y hoja de Captura rápida.
- [x] Módulo nativo `foco-blocker`: Android (Accesibilidad con el detector
  de FocusGuard + bloqueo de app completa + doble pulsación de volumen).

### Fase 1 — MVP Android + Web (primer lanzamiento)
- Probar en móviles reales (Samsung, Xiaomi, Pixel), ajustar patrones de
  Instagram/TikTok/Snapchat/Facebook.
- Lectura de calendarios del dispositivo y subida a la cuenta.
- Voz: reconocimiento en el dispositivo + Whisper opcional.
- Despliegue del servidor y publicación en Google Play (prueba cerrada →
  producción) y web.

### Fase 2 — PC
- App de escritorio (Tauri) + extensión de navegador.
- Iniciar sesión con Google.

### Fase 3 — Crecimiento
- Suscripción (RevenueCat) con plan gratis generoso; IA incluida sin clave
  propia en el plan de pago.
- Modo amigos/accountability, retos, widgets, Wear OS.

---

## 5. Publicación: qué hace falta (y quién)

| Qué | Para qué | Quién |
|---|---|---|
| Cuenta Google Play Console (25 $ una vez) | Publicar en Android | **Álvaro** |
| Proyecto en Google Cloud + pantalla de consentimiento OAuth | Conectar Google Calendar | Álvaro crea el proyecto, yo dejo la config |
| Registro de app en Microsoft Entra (Azure) | Conectar Outlook | Igual |
| Cuenta Expo (gratis) | Builds en la nube (EAS) | Álvaro |
| Hosting del servidor + Postgres | Cuenta y sync | Recomendado: Railway o Fly.io + Neon (hay planes gratis) |
| Política de privacidad y web | Obligatorio en las tiendas | Yo la redacto |
| Claves de IA | Probar el asistente | Cada usuario pone la suya |

---

## 6. Riesgos

- **Cambios de interfaz de Instagram/TikTok** rompen la detección granular
  en Android → patrones actualizables remotamente + tests con capturas de
  árboles de accesibilidad.
- **Revisión de Google Play por Accesibilidad** → declaración bien hecha,
  vídeo de demostración, aviso destacado; plan B fuera de Play.
- **Verificación de Google para el scope de Calendar** (puede tardar
  semanas) → empezar con usuarios de prueba (hasta 100) mientras tanto.
- **Batería**: todo el bloqueo es por eventos, sin sondeos (el diseño de
  FocusGuard ya lo cumple).
