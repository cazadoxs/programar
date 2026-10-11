# Fácil — el móvil, sin complicaciones

Launcher de Android para personas mayores. **Gratis y sin anuncios** para quien lo usa;
una suscripción familiar barata (**Fácil Familia**, ~12 €/año) permite a un familiar
configurarlo y recibir avisos desde su propio móvil.

## Qué hace

**Persona mayor (siempre gratis)**
- Pantalla de inicio con reloj enorme, saludo y hasta 8 botones grandes: llamar a alguien (con su foto), WhatsApp, SMS, cámara, fotos, linterna, cualquier app, "¿qué hora es?" (lo dice en voz alta).
- Botón rojo de **AYUDA** (mantener pulsado): cuenta atrás de 5 s con botón enorme de cancelar y llamada automática al contacto de emergencia; 112 siempre a un toque.
- Recordatorios (pastillas, citas) con aviso y botón "Hecho".
- Tres temas (crema, noche, alto contraste negro/amarillo) y tres tamaños de letra; lectura en voz alta opcional.
- Ajustes **protegidos**: hay que mantener pulsado 2 s y escribir un PIN (PBKDF2, bloqueo progresivo tras fallos). Imposible desconfigurarlo sin querer.

**Familiar (Fácil Familia)**
- Empareja con QR o código de 10 letras; la persona mayor debe **aceptar** a cada familiar.
- Edita botones, contactos, fotos, recordatorios y ajustes a distancia (los cambios llegan en minutos).
- Alertas: batería baja, móvil sin usar X horas, pastilla sin marcar, sin conexión, botón de ayuda pulsado.

**Privacidad y cumplimiento (Play)**: sin AccessibilityService, sin ubicación, sin leer mensajes/llamadas. La persona mayor ve un aviso permanente de qué ve su familia, puede apagar el compartir estado y borrar todo.

## Estructura

```
app/                 código Kotlin (vistas nativas, sin AndroidX ni Compose)
  core/              lógica pura y testeada: modelo, PIN, alertas, horarios, códigos
  remote/            Firebase por REST (Auth anónima + Realtime Database)
  ui/ system/ billing/ data/
firebase/            reglas de seguridad de la base de datos
docs/                SEGURIDAD.md, FIREBASE.md (configuración)
publicar/            ficha de Play, privacidad, marketing, logo
tools/               build-local.sh / test-local.sh (sin Gradle, para entornos sin Google Maven)
```

## Compilar

Con Android Studio / Gradle: abre `apps/facil` y `./gradlew assembleDebug` (compileSdk 35, minSdk 26).
Los valores de Firebase van en `local.properties` (no se sube):

```
facil.firebaseApiKey=AIza...
facil.firebaseDbUrl=https://TU-PROYECTO-default-rtdb.europe-west1.firebasedatabase.app
```

Sin ellos la app funciona entera en local; solo la gestión remota queda desactivada.

Release firmado: crea `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) y `./gradlew bundleRelease`.

Pasos de Firebase y Play Console: `docs/FIREBASE.md` y `publicar/PUBLICAR.md`.

## Estado de verificación

- ✅ Lógica (`core`) con 8 tests unitarios: `tools/test-local.sh`.
- ✅ Todo el código Kotlin compila contra Android 15 y empaqueta en un APK firmado válido (`tools/build-local.sh`).
- ⚠️ No se ha podido ejecutar en un dispositivo/emulador ni compilar con Gradle desde el entorno de desarrollo (sin acceso a Google Maven/SDK). **Antes de publicar**: probar en un móvil real el recorrido completo (ver lista en `publicar/PUBLICAR.md`).
