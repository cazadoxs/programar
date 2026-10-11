# Publicar Foco en Google Play (cuenta personal nueva, 2026)

Guía paso a paso para Álvaro. Complementa a [`../PUBLICAR.md`](../PUBLICAR.md)
(servidor, OAuth de Google/Microsoft, compilación con EAS). Textos de la
ficha: [`ficha-es.md`](ficha-es.md) y [`listing-en.md`](listing-en.md).

**Calendario realista**: cuenta y verificación (1–7 días) → prueba interna
(1 día) → prueba cerrada con 12+ testers **14 días seguidos** → solicitud de
producción (Google responde en ~7 días, a veces más) → producción
escalonada. Cuenta con **4–5 semanas** desde que creas la cuenta hasta que
cualquiera puede descargarla. Empieza a reclutar testers **antes** de
tener la build.

> Las pantallas de Play Console cambian de nombre a menudo. Si algo no está
> donde dice esta guía, busca el nombre en la barra de búsqueda de Play
> Console. Nada de esto es asesoramiento legal o fiscal.

---

## 0. Antes de empezar (checklist rápido)

- [ ] Servidor desplegado con HTTPS (`PUBLIC_URL`) y la build apuntando a él.
- [ ] Correo solo para la app (p. ej. `hola.foco.app@gmail.com`): será el
      contacto público y el de soporte.
- [ ] Política de privacidad publicada en una URL (sección 3).
- [ ] Una **cuenta de prueba** en tu servidor para los revisores de Google
      (email + contraseña), con Pro activado y algún evento y tarea creados.
- [ ] Móvil Android físico para grabar los vídeos y para la verificación.
- [ ] DNI/NIE a mano (verificación de identidad) y una tarjeta para los 25 $.

---

## 1. Crear y verificar la cuenta de desarrollador

1. Entra en **play.google.com/console** con la cuenta de Google que vaya a
   ser dueña de la app (mejor una creada para Foco que tu Gmail personal;
   puedes darte acceso de administrador con la personal después).
2. Tipo de cuenta: **Personal** (eres una persona, no una empresa: no
   necesitas número D-U-N-S).
3. Paga la **cuota única de 25 $**.
4. **Verificación de identidad**: nombre legal y dirección exactamente como
   en tu DNI/NIE, y foto del documento. Tarda de horas a unos días.
5. **Verificación de contacto**: confirma el correo y el teléfono.
6. **Acceso a un dispositivo Android**: instala la app *Google Play Console*
   en tu móvil e inicia sesión; Play lo pide a las cuentas nuevas.
7. **Nombre de desarrollador** (público en la ficha): por ejemplo «Foco
   App» o «Álvaro [apellido]». Las cuentas personales muestran tu nombre
   legal y país a los usuarios que compran; tenlo en cuenta.

> **Verificación de desarrolladores de Android (2026)**: Google exige que
> las apps instaladas en móviles certificados vengan de desarrolladores
> verificados (empezó en septiembre de 2026 en algunos países y se amplía en
> 2027). Si publicas en Play y has hecho los pasos anteriores, ya cumples.
> Solo tendrías que registrar el nombre de paquete aparte si distribuyes el
> APK fuera de Play (plan B) — hazlo con la misma identidad.

### Perfil de pagos (para vender Foco Pro)

8. Play Console › **Configuración › Perfil de pagos** → crear perfil de
   comerciante (país: España), cuenta bancaria (IBAN) e información fiscal
   (NIF). Sin esto no puedes crear productos de pago.
9. Fiscalidad, en corto: en la UE Google cobra y liquida el **IVA** de las
   ventas de Play por ti (el precio que pones ya lo incluye). Google se
   queda una comisión del **15 %**. Lo que te ingresa es renta tuya: si las
   ventas se vuelven habituales, pregunta en una gestoría (o en el servicio
   de emprendimiento de la UPV, *IDEAS UPV*) si te toca darte de alta como
   autónomo; con ingresos pequeños y esporádicos lo normal es declararlo en
   la renta.

---

## 2. Crear la app

1. **Crear aplicación** → nombre «Foco: Bloquea Reels y Estudia», idioma
   predeterminado **Español (España) – es-ES**, tipo *Aplicación*,
   **Gratuita**, acepta las declaraciones.
2. El nombre del paquete queda fijado con el primer AAB que subas:
   `app.foco.mobile` (está en `apps/mobile/app.json`). **No se puede cambiar
   nunca**.
3. **Play App Signing**: acéptalo (EAS ya genera el AAB firmado con tu clave
   de subida).
4. Requisito técnico de 2026: las apps nuevas deben apuntar a **Android 16
   (API 36)**. Comprueba que la build de EAS sale con `targetSdkVersion 36`
   (lo pone el SDK de Expo; si Play Console se queja al subir, actualiza
   Expo o fija `android.targetSdkVersion` con `expo-build-properties`).

---

## 3. Publicar la política de privacidad (GitHub Pages)

1. Crea en GitHub un repositorio **público** llamado `foco` (solo para la
   web; el código de la app puede seguir siendo privado).
2. Sube a su raíz `docs/store/privacy-policy.html` después de rellenar los
   `[corchetes]` (apellidos, email, proveedor de hosting y región). Opcional:
   un `index.html` sencillo con el logo, el lema y el enlace a Play.
3. *Settings › Pages* → *Deploy from a branch* → rama `main`, carpeta
   `/ (root)`.
4. URL resultante: `https://[usuario].github.io/foco/privacy-policy.html`
   (la que ya ponen `ficha-es.md` y `listing-en.md`). Ábrela en el móvil
   para comprobar que carga.
5. La sección **«Borrar tu cuenta»** de la política tiene ancla
   `#borrar-cuenta`: esa URL es la que pide Play como *enlace para eliminar
   la cuenta*.

---

## 4. Contenido de la aplicación (Play Console › Política › Contenido de la app)

Rellena todo antes de enviar nada a revisión, incluso para la prueba cerrada.

### 4.1 Política de privacidad
La URL de la sección 3.

### 4.2 Anuncios
**No, mi app no contiene anuncios.** Comprueba además que el AAB no
declara `com.google.android.gms.permission.AD_ID` (Expo no lo añade por
defecto); en *ID de publicidad* responde que **no** lo usa.

### 4.3 Acceso a la aplicación
**Toda o parte de la funcionalidad está restringida** → añade
instrucciones:

```text
Foco requiere una cuenta. Usa: Email: revisor@[tu-dominio-o-gmail] · Contraseña: [contraseña]
La cuenta tiene Foco Pro activado. Para ver el bloqueo: Bloqueo › activa el servicio de accesibilidad cuando la app lo pida › crea una regla para Instagram › Reels › Siempre, y abre Reels.
El asistente de IA necesita una clave del usuario; la cuenta de prueba ya tiene una clave de prueba configurada.
```

(Pon una clave de API con límite de gasto bajo — 5 $ — en esa cuenta, y
revócala después del lanzamiento.)

### 4.4 Clasificación de contenido (cuestionario IARC)

- Correo de contacto: el de la app.
- Categoría: **Todas las demás tipos de apps** (*Utility, Productivity,
  Communication or Other*). No es juego.
- Respuestas:

| Pregunta | Respuesta |
|---|---|
| Violencia, miedo, sexualidad, lenguaje soez, drogas, alcohol, tabaco | **No** a todo |
| Apuestas o simulación de apuestas | **No** |
| ¿Los usuarios pueden interactuar o intercambiar contenido entre ellos? | **No** (no hay chat ni perfiles públicos) |
| ¿Comparte la ubicación del usuario con otros usuarios? | **No** |
| ¿Permite compras de productos digitales? | **Sí** (Foco Pro) |
| ¿Acceso sin restricciones a internet (navegador / buscador)? | **No** |
| ¿Contenido generado por IA? (si aparece) | **Sí**: asistente de texto para planificar el día; no genera imágenes |
| ¿Es una app de noticias / educativa para menores? | **No** |

Resultado esperado: **PEGI 3 / IARC 3+** (o «Todos» en EE. UU.), con
la mención *Compras en la aplicación*.

> **Política de contenido generado por IA**: Play exige que las apps con
> IA generativa permitan **reportar respuestas ofensivas** desde la app.
> Asegúrate de que el asistente tiene un botón o menú «Reportar respuesta»
> (aunque solo mande un email); si no lo tiene, pídelo antes de enviar a
> producción.

### 4.5 Público objetivo y contenido

- **Grupos de edad**: marca **16–17** y **18 o más**.
- **Por qué no niños (menos de 13)**: marcar cualquier grupo infantil mete
  la app en la *Política de Familias* (SDK certificados, sin servicio de
  accesibilidad para bloquear otras apps, consentimiento parental, COPPA),
  que choca con lo que hace Foco: una cuenta con email, un servicio de
  accesibilidad que mira otras apps y un chat con IA de terceros.
- **Por qué no 13–15**: en España la edad para consentir el tratamiento de
  datos es **14 años** (LOPDGDD art. 7) y en algunos países de la UE es 16.
  La política de privacidad fija 14 años como mínimo (o la de tu país si es
  mayor). Marcar 13–15 te obliga a cumplir requisitos extra para menores y
  sería incoherente con ese mínimo. Los estudiantes de ESO de 14–15 pueden
  descargarla igual: el público objetivo no bloquea descargas, solo dice a
  quién va dirigida.
- ¿La app podría atraer a niños? **No** (ni dibujos, ni personajes, ni
  lenguaje infantil).

### 4.6 Seguridad de los datos (Data safety)

**Preguntas generales**

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte alguno de los tipos de datos requeridos? | **Sí** |
| ¿Todos los datos se cifran en tránsito? | **Sí** (HTTPS) |
| ¿Ofreces una forma de solicitar la eliminación de datos? | **Sí**: en la app (*Ajustes › Borrar cuenta*) y en la URL `…/privacy-policy.html#borrar-cuenta` |
| ¿Cuenta? ¿Los usuarios pueden crear una cuenta? | **Sí**, con email y contraseña. Enlace para eliminar la cuenta: la URL de arriba |

**Tipos de datos** (*recogido* = sale del móvil hacia tu servidor;
*compartido* = va a un tercero que no actúa por cuenta tuya. Los
proveedores de servicio — hosting, la API de IA que el usuario elige con su
propia clave, Google/Microsoft para sus propios calendarios — **no cuentan
como compartir** porque los datos se envían por petición expresa del usuario
o a un encargado del tratamiento):

| Categoría › tipo | ¿Recogido? | ¿Compartido? | ¿Opcional? | Finalidad |
|---|---|---|---|---|
| Info personal › **Dirección de correo** | Sí | No | Obligatorio | Funcionalidad de la app, gestión de la cuenta |
| Info personal › **ID de usuario** | Sí | No | Obligatorio | Gestión de la cuenta |
| Info personal › Nombre | Sí, si lo escribe | No | Opcional | Funcionalidad, gestión de la cuenta |
| Info financiera › **Historial de compras** | Sí (el servidor guarda que tienes Pro) | No | Opcional | Funcionalidad de la app |
| Calendario › **Eventos del calendario** | Sí | No | Opcional | Funcionalidad de la app |
| Mensajes / Contenido del usuario › **Otro contenido generado por el usuario** (tareas, notas, capturas, reglas de bloqueo, chats con el asistente) | Sí | No | Opcional | Funcionalidad de la app |
| Audio › **Grabaciones de voz** | Sí, **procesamiento efímero** (solo si el usuario elige transcribir con Whisper/Gemini) | No | Opcional | Funcionalidad de la app |
| Actividad en apps › Interacciones, apps instaladas, historial de búsqueda | **No** — la detección de apps y pantallas se hace solo en el móvil | — | — | — |
| Ubicación, contactos, fotos, salud, diagnósticos, ID de publicidad | **No** | — | — | — |

- **Claves de API de IA**: no son un tipo de dato de la lista; se guardan
  cifradas (AES-256) en el servidor y no se comparten. Menciónalo en la
  política (ya está).
- **¿Se venden datos?** No. **Anuncios o marketing**: no.
- Si un día añades Crashlytics/Sentry, estadísticas sincronizadas o
  analítica, **actualiza** este formulario antes de publicar esa versión.

### 4.7 API de Accesibilidad (*Permisos y APIs sensibles*)

Play Console › Contenido de la app › **API de Accesibilidad**.

- ¿Es tu app una herramienta de accesibilidad (`isAccessibilityTool`)?
  **No.** (No la marques como tal: Google lo comprueba y es motivo de
  retirada.)
- Funcionalidades que usan la API: marca *Otra* / *Bloqueo de apps y
  bienestar digital* y pega:

```text
Foco es una app de bienestar digital. Usa la API de Accesibilidad (AccessibilityService) solo con el consentimiento explícito del usuario y solo para:
1) Detectar qué app está en primer plano y, dentro de las apps que el propio usuario ha elegido bloquear (Instagram, TikTok, YouTube, Snapchat, Facebook, X, Reddit, WhatsApp), qué sección está abierta (Reels/Shorts, Historias, Explorar, Mensajes o feed), para mostrar la pantalla de bloqueo o volver atrás durante las sesiones, horarios o límites que el usuario ha configurado.
2) Detectar la doble pulsación del botón de bajar volumen (flagRequestFilterKeyEvents) para abrir la captura rápida de notas.
Todo el análisis se hace en el dispositivo. Foco no recoge, guarda, transmite ni comparte el contenido de la pantalla, lo que el usuario escribe ni su historial de uso. No realiza acciones en nombre del usuario fuera de volver atrás o mostrar la pantalla de bloqueo, y no cambia ajustes del sistema.
Antes de abrir los ajustes de accesibilidad, la app muestra un aviso destacado que explica qué datos se usan y para qué, con botones Aceptar / Ahora no. Si el usuario no acepta, Foco sigue funcionando con bloqueo de apps completas mediante acceso a uso.
```

**Vídeo de demostración** (sube a YouTube como *Oculto*, 1–2 min, graba la
pantalla del móvil con el grabador de Android o `scrcpy --record`). Debe
verse, en este orden y sin cortes engañosos:

1. Abrir Foco desde cero y entrar en *Bloqueo*.
2. El **aviso destacado** completo (que se lea el texto) y pulsar
   **Aceptar** — enseña también que **«Ahora no»** existe.
3. Los ajustes de Android › Accesibilidad › Foco y activar el servicio
   (con el diálogo del sistema).
4. Crear una regla: Instagram › Reels, *Siempre*.
5. Abrir Instagram, ir a Mensajes (funciona) y luego a **Reels → aparece la
   pantalla de bloqueo** de Foco.
6. La doble pulsación de bajar volumen abriendo la captura rápida.
7. Desactivar el servicio (para mostrar que el usuario lo controla).

> **Android 17 – Protección Avanzada**: con el *Modo de Protección
> Avanzada* activado, Android 17 impide usar la API de Accesibilidad a las
> apps que no son herramientas de accesibilidad. Foco lo detecta y pasa a
> **detección por acceso a uso + pantalla superpuesta** (bloquea apps
> enteras, no secciones). Dilo en el formulario si hay un campo de
> comentarios y en la ficha (ya está: «Si no activas la accesibilidad…»).
> No intentes saltártelo declarando `isAccessibilityTool="true"`.

### 4.8 Servicio en primer plano (*Foreground service permissions*)

Foco usa un servicio en primer plano para detectar qué app está delante
cuando el servicio de accesibilidad **no está disponible** (no concedido o
bloqueado por Protección Avanzada). En el manifiesto, el servicio de
respaldo (`FallbackBlockerService`) debe declarar:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<service android:name="app.foco.blocker.FallbackBlockerService"
    android:foregroundServiceType="specialUse" android:exported="false">
  <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
      android:value="Bloqueo de apps elegidas por el usuario durante sesiones de foco: detecta la app en primer plano mediante UsageStatsManager cuando el servicio de accesibilidad no está disponible." />
</service>
```

En Play Console › Contenido de la app › **Permisos de servicios en primer
plano** → tipo **Uso especial (specialUse)** → descripción:

```text
Foco es un bloqueador de distracciones. Mientras el usuario tiene una sesión de foco, un horario o un límite diario activo y el servicio de accesibilidad no está disponible (no concedido o restringido por el Modo de Protección Avanzada de Android), Foco ejecuta un servicio en primer plano con una notificación visible ("Foco está protegiendo tu sesión") que consulta UsageStatsManager para saber qué app está en primer plano y, si es una de las que el usuario eligió bloquear, muestra la pantalla de bloqueo superpuesta. El servicio empieza solo cuando el usuario inicia una sesión o empieza un horario, se detiene en cuanto termina, y la detección se hace solo en el dispositivo. Ningún otro tipo de servicio en primer plano encaja: no es reproducción multimedia, ubicación, sincronización de datos ni salud.
Impacto si se retrasa o interrumpe: el bloqueo dejaría de funcionar en mitad de la sesión que el usuario ha pedido explícitamente.
```

Vídeo (puede ser el mismo enlace con un segundo tramo): con la
accesibilidad **desactivada**, iniciar un Pomodoro → se ve la notificación
del servicio → abrir TikTok → aparece la pantalla de bloqueo → terminar la
sesión → desaparece la notificación.

### 4.9 Otras declaraciones

- **Acceso a uso (`PACKAGE_USAGE_STATS`)** y **mostrar sobre otras apps
  (`SYSTEM_ALERT_WINDOW`)**: no tienen formulario propio, pero el usuario los
  concede a mano; explícalos en la app antes de mandar a Ajustes.
- **`QUERY_ALL_PACKAGES`**: no la uses. Para saber si Instagram, TikTok, etc.
  están instalados basta con declarar esos paquetes en `<queries>`. Si algún
  día la necesitas, hay que rellenar otra declaración.
- **Calendario (`READ_CALENDAR`)** y **micrófono (`RECORD_AUDIO`)**: permisos
  normales en tiempo de ejecución; no hay formulario.
- **Apps de salud / finanzas / gobierno / noticias**: **No** a todo.
- **Funciones relacionadas con la seguridad infantil (CSAE)**: no aplica
  (sin contenido entre usuarios), pero si Play lo pide, pon el email de la
  app como contacto.

---

## 5. Producto de pago: Foco Pro

Play Console › **Monetizar con Play › Productos › Productos únicos**
(*One-time products*) › Crear:

| Campo | Valor |
|---|---|
| ID del producto | `foco_pro_lifetime` (no se puede cambiar ni reutilizar) |
| Nombre | Foco Pro (de por vida) |
| Descripción | Pago único: apps, reglas y calendarios ilimitados, modo estricto, estadísticas completas, modo trabajo con IA y temas. Sin suscripción. |
| Tipo / opción de compra | **Compra** (*Buy*), **no consumible** |
| Precio base | **9,99 €** (Play convierte al resto de países; revisa EE. UU. 9,99 $, México, Argentina…) |
| Oferta de lanzamiento | **6,99 €** — crea una *oferta* de descuento con fecha de fin (p. ej. 30 días tras el lanzamiento). Si tu consola no muestra ofertas para productos únicos, pon 6,99 € de precio y súbelo a 9,99 € el día que acabe |

- Activa el producto (estado **Activo**).
- **Pruebas de compra**: Configuración › **Pruebas de licencias** → añade
  tu Gmail y el de 2–3 testers; compran con tarjetas de prueba sin cobro.
- El servidor verifica la compra (ya hay `billing.ts` con
  `PRO_PRODUCT_ID`). Necesita una **cuenta de servicio** de Google Cloud
  con acceso a la *Google Play Developer API* (Play Console › Configuración
  › Acceso a la API) para validar el token de compra.
- Recuerda: Foco Pro es **pago único**, sin suscripción y sin anuncios. Que
  todo (ficha, app, web, vídeos) diga lo mismo.

---

## 6. Las pistas: interna → cerrada → producción

### 6.1 Prueba interna (el mismo día)

1. Compila: `cd apps/mobile && EXPO_PUBLIC_API_URL=https://TU-SERVIDOR npx eas-cli@latest build --profile production --platform android`.
2. Play Console › Pruebas › **Prueba interna** › Crear versión › sube el
   `.aab` (o `npx eas-cli@latest submit --platform android`).
3. Testers: lista de emails (tú + 2–3 amigos). Comparte el enlace de
   participación. Está disponible en minutos, sin revisión larga.
4. Prueba la compra de Pro, el bloqueo y el borrado de cuenta en al menos
   un Samsung, un Xiaomi y un Pixel (o el que tengáis).

### 6.2 Prueba cerrada (14 días, ≥ 12 testers)

Requisito de las cuentas personales nuevas: **al menos 12 testers que se
hayan apuntado (opt-in) y sigan apuntados durante 14 días seguidos** antes
de poder pedir producción.

1. Crea un **Grupo de Google** (`foco-testers@googlegroups.com`) — es más
   cómodo que la lista de emails: la gente se une sola.
2. Pruebas › **Prueba cerrada** › pista *Alpha* › Testers: añade el grupo.
   Países: España (y los que quieras).
3. Sube la versión, rellena las notas y **envíala a revisión** (la prueba
   cerrada sí se revisa: 1–3 días, más con la declaración de Accesibilidad).
4. Comparte el **enlace de participación** (web y Android).
5. **Recluta 20–25**, no 12: siempre hay quien se da de baja o desinstala.
   Los 14 días cuentan solo con 12 o más apuntados a la vez.

**Dónde conseguir testers** (mensaje de ejemplo abajo):

- Grupos de clase de la UPV (WhatsApp/Telegram de tu grado, delegación de
  alumnos, *Casa del Alumno*), compañeros de piso, familia.
- Telegram: grupos de estudio y oposiciones (busca «oposiciones», «estudiar
  juntos», «study with me» en español).
- Reddit: r/androiddev tiene hilos de intercambio de testers; r/AndroidClosedTesting
  y r/TestersCommunity existen para exactamente esto (tú pruebas sus apps,
  ellos la tuya).
- Comunidades tipo *Testers Community* o intercambio en Discord: válido,
  pero prioriza gente real que use la app; en la solicitud de producción
  Google pregunta por el feedback recibido.

```text
¡Hola! Estoy sacando Foco, una app Android para dejar de procrastinar: bloquea Reels/TikTok/Shorts mientras estudias (Pomodoro, horarios) y junta tus calendarios. Google me pide 12 personas que la prueben 14 días antes de publicarla.
¿Me echas un cable? Solo es:
1) Unirte a este grupo con tu Gmail: [enlace grupo]
2) Instalarla desde aquí: [enlace participación]
3) No desinstalarla en 14 días (y si me dices qué mejorarías, te invito a un café ☕).
Te regalo Foco Pro de por vida cuando salga. ¡Gracias!
```

**Durante los 14 días**: sube **1–2 actualizaciones** con mejoras reales
(Google valora que la app evolucione), pide feedback con un formulario corto
(Google Forms: ¿qué te ha bloqueado bien/mal?, ¿en qué móvil?, ¿pagarías
Pro?) y guarda capturas de lo que cambias: te lo preguntarán.

### 6.3 Solicitar acceso a producción

Panel › **Solicitar acceso a producción**. Preguntas típicas y qué decir:

- *¿Cómo reclutaste testers?* — Compañeros de la UPV, grupos de estudio de
  Telegram y amigos; N testers activos.
- *¿Qué feedback recibiste y qué cambiaste?* — Ejemplos concretos
  («detección de Reels fallaba en Xiaomi → corregido en 1.0.2», «el aviso de
  accesibilidad no se entendía → reescrito»).
- *¿Para quién es la app y qué valor aporta?* — Estudiantes y teletrabajadores
  que quieren bloquear solo las partes adictivas de las redes, con
  calendario y planificación con IA.
- *¿Está lista para producción?* — Sí, con los cambios del feedback.

Respuesta en ~7 días. Si la deniegan, suele ser por poco uso durante la
prueba: alarga la prueba con testers activos y vuelve a pedirlo.

### 6.4 Producción

1. Producción › Crear versión con el mismo AAB (o uno nuevo con
   `versionCode` mayor).
2. **Lanzamiento escalonado**: 20 % → 50 % (a los 2–3 días si no hay
   fallos en *Android vitals*) → 100 %.
3. Países: empieza por **España + Latinoamérica + EE. UU./Reino Unido**
   (tienes ficha en es-ES y en-US). Puedes añadir *es-419* copiando la
   ficha española más adelante.
4. Activa la **Publicación gestionada** (*Managed publishing*) para decidir
   tú el momento exacto de salir (útil para coordinarlo con los vídeos y
   Product Hunt).

---

## 7. Checklist de lanzamiento

**Cuenta y legal**
- [ ] Cuenta verificada (identidad, teléfono, dispositivo).
- [ ] Perfil de pagos con IBAN y NIF.
- [ ] Política de privacidad publicada, con apellidos y email reales.
- [ ] Enlace de borrado de cuenta (`#borrar-cuenta`) funcionando.

**App**
- [ ] `targetSdkVersion 36`, AAB firmado, `versionCode` subido.
- [ ] Aviso destacado antes de la accesibilidad, con «Ahora no».
- [ ] Respaldo sin accesibilidad (acceso a uso + superposición) probado.
- [ ] *Ajustes › Borrar cuenta* funciona y borra en el servidor.
- [ ] Botón «Reportar respuesta» en el asistente de IA.
- [ ] Compra de `foco_pro_lifetime` probada con testers de licencia;
      *Restaurar compra* funciona tras reinstalar.
- [ ] Iconos nuevos (`apps/mobile/assets/`) y color de fondo del icono
      adaptativo `#0B0B12` en `app.json`.
- [ ] Probado en Samsung (One UI), Xiaomi (HyperOS: permitir *inicio
      automático* y sin restricciones de batería) y Pixel.

**Contenido de la app en Play Console**
- [ ] Anuncios: no · ID de publicidad: no.
- [ ] Acceso a la app con cuenta de prueba.
- [ ] Clasificación de contenido enviada.
- [ ] Público objetivo 16–17 y 18+.
- [ ] Seguridad de los datos.
- [ ] Declaración de Accesibilidad + vídeo.
- [ ] Declaración de servicio en primer plano *specialUse* + vídeo.

**Ficha**
- [ ] Textos es-ES y en-US (`check_listing.py` en verde).
- [ ] Icono 512, feature graphic ES y EN.
- [ ] 4–8 capturas por idioma (ver `ficha-es.md`).
- [ ] Vídeo promocional de YouTube (opcional, el mejor de TikTok en 16:9 o
      vertical).
- [ ] Email de contacto y web.

**Día del lanzamiento**
- [ ] Publicación gestionada → publicar a primera hora (9:00).
- [ ] Vídeo de lanzamiento en TikTok/Reels/Shorts.
- [ ] Mensaje a los testers: «ya está, déjame una reseña sincera».
- [ ] Product Hunt / Menéame / Reddit según
      `docs/marketing/GUIA-VIDEOS-Y-PUBLICIDAD.md`.
- [ ] Responder **todas** las reseñas durante el primer mes.
