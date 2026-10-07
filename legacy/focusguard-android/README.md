# FocusGuard

FocusGuard es una app nativa para Android que bloquea **Shorts de YouTube**,
**Reels de Instagram** y el **feed de scroll infinito de TikTok**, sin tocar
historias, chats/DMs, ni los videos largos normales.

No usa root, no tiene permiso de Internet y no envía datos a ningún sitio:
todo el procesamiento y las estadísticas viven únicamente en el teléfono.

## Cómo funciona

Android no da a las apps de terceros ninguna API para saber "esta pantalla es
un Short". La única forma de detectarlo sin root es el **servicio de
accesibilidad**: cuando YouTube, Instagram o TikTok están en primer plano,
FocusGuard inspecciona (solo para esas tres apps) el árbol de accesibilidad
en busca de patrones propios del reproductor de Shorts/Reels/feed —
identificadores de vista, pestañas seleccionadas, nombres de actividad— y en
cuanto los reconoce, saca al usuario de esa pantalla (`GLOBAL_ACTION_BACK`,
con un mecanismo de seguridad que usa `GLOBAL_ACTION_HOME` si detecta que
está en un bucle).

Como esa detección se basa en patrones internos de cada app y no en una API
oficial, **puede dejar de funcionar puntualmente cuando YouTube, Instagram o
TikTok cambian su interfaz**, hasta que se actualicen los patrones en
`ShortsDetector.java`. Esto se explica también dentro de la app, en
"Acerca de".

## Funcionalidades

- **Bloqueo por plataforma**, activable/desactivable de forma independiente
  para YouTube, Instagram y TikTok.
- **Panel principal** con: estado de protección, bloqueos de hoy, tiempo
  estimado ahorrado, racha de días activos y un gráfico de la última semana
  (dibujado a mano con `Canvas`, sin librerías de gráficos).
- **Estadísticas** de 7/30 días y desglose por plataforma, guardadas en
  SQLite local.
- **Aviso al bloquear** (Toast) opcional, para reforzar el hábito.
- **Protección contra impulsos**: un PIN opcional + una espera activa de 15
  minutos antes de poder ir a apagar el servicio de accesibilidad. FocusGuard
  no puede desactivar el permiso por sí sola (ninguna app puede hacerlo);
  esta fricción es lo único que puede controlar dentro de su propia UI.
- **Resumen diario** opcional por notificación.
- Onboarding claro para conceder el permiso de accesibilidad y (opcional)
  notificaciones.

## Diseño pensado para el consumo de batería

- **Cero polling**: es un servicio 100% dirigido por eventos de
  accesibilidad, sin temporizadores en bucle, sin `WorkManager`, sin
  `WakeLock`.
- El manifiesto del servicio de accesibilidad filtra los eventos por
  `android:packageNames`, así que el sistema ni siquiera entrega eventos de
  otras apps — `onAccessibilityEvent` no se ejecuta para nada que no sea
  YouTube/Instagram/TikTok.
- Los eventos en ráfaga (muy comunes al hacer scroll) se **debouncean**
  (~220 ms) antes de recorrer el árbol de accesibilidad.
- El recorrido del árbol está acotado (máximo de nodos e hijos visitados),
  y termina en cuanto encuentra una coincidencia.
- Sin permiso de Internet: no hay sincronización, telemetría ni SDKs de
  analítica corriendo en segundo plano.
- Sin dependencias externas (ni AndroidX): menos código ejecutándose, APK
  más pequeño (~80 KB).

## Privacidad

FocusGuard no declara `android.permission.INTERNET` en el manifiesto, así
que el sistema operativo le impide literalmente hacer peticiones de red.
Todo lo que guarda (ajustes, PIN con hash SHA-256, historial de bloqueos)
vive en `SharedPreferences` y una base SQLite locales.

## Instalar el APK

1. Copia `release/FocusGuard.apk` al teléfono.
2. Ábrelo y permite la instalación desde "orígenes desconocidos" cuando
   Android lo pida.
3. Al abrir la app, sigue el onboarding y activa el **servicio de
   accesibilidad** (imprescindible) y, si quieres, las notificaciones.

## Estructura del proyecto

```
app/src/main/java/com/focusguard/blocker/
  data/      Platform, Prefs, DbHelper, StatsRepository
  service/   BlockerAccessibilityService, ShortsDetector
  receiver/  BootReceiver, DailySummaryReceiver
  util/      PermissionUtils, DailySummaryScheduler
  ui/        Splash/Onboarding/Main/Settings/Stats/About/PinActivity, WeekChartView
app/src/main/res/        layouts, drawables vectoriales, temas claro/oscuro, strings es/en
```

## Compilar

### Con Android Studio (recomendado)

Es un proyecto Gradle estándar, sin dependencias externas. Solo hace falta:

```
./gradlew assembleDebug
```

(el `gradlew` incluido usa Gradle 8.7 + Android Gradle Plugin 8.5.0; se
descargan la primera vez que lo ejecutes, así que necesitas conexión a
Internet normal — algo que este sandbox de desarrollo no tenía).

### Sin Android Studio / sin acceso al Maven de Google

El entorno en el que se generó este proyecto no tenía acceso a
`dl.google.com` ni `maven.google.com` (donde viven el Android Gradle Plugin
y el SDK oficial), así que el APK en `release/FocusGuard.apk` se generó con
un pipeline manual equivalente, en `tools/build-apk.sh`, usando:

- `aapt` (v1, del paquete `aapt` de Ubuntu) para compilar recursos/manifiesto.
- Un stub completo de la API de Android para `javac` — se usó
  `org.robolectric:android-all` (Maven Central), que expone las mismas
  clases que un `android.jar` real.
- `d8` (dentro de `r8.jar`, publicado por Google en Maven Central /
  `storage.googleapis.com/r8-releases`) para generar el `.dex`.
- `zipalign` + `apksigner` (paquetes de Ubuntu) para alinear y firmar.

Ver los comentarios al principio de `tools/build-apk.sh` para las variables
de entorno (`ANDROID_STUB_JAR`, `R8_JAR`) y de dónde salieron esos dos jars.
Ninguno de los dos se incluye en el repositorio (son grandes y no son código
del proyecto).

## Limitaciones honestas

- La detección es heurística: se basa en nombres de identificadores de vista
  y en pestañas seleccionadas dentro de apps de terceros que FocusGuard no
  controla. Actualizaciones de YouTube/Instagram/TikTok pueden requerir
  ajustar `ShortsDetector.java`.
- FocusGuard **no puede activar ni desactivar el servicio de accesibilidad
  por sí misma**: ninguna app puede hacerlo por diseño de Android. Solo
  puede llevarte a la pantalla de ajustes del sistema y, con el PIN activado,
  poner fricción antes de hacerlo.
- No es una alternativa a root/DNS/firewall: no bloquea tráfico de red ni
  filtra contenido a nivel de red, solo interviene en pantalla dentro de las
  tres apps soportadas.
