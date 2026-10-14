# Pásalo

Transferencia de archivos de un dispositivo a otro, sin nube, entre **Android** y
**PC con Windows**. Además trae herramientas para comprimir, descomprimir,
cifrar y convertir un archivo en una imagen hecha de bits.

- **Android:** `Pasalo.apk`
- **Windows:** `Pasalo-Setup.exe` (instalador) o `Pasalo-portable.zip`

Las dos se generan solas en GitHub Actions (workflow *Pásalo (transfer-p2p)*):
abre la última ejecución en la pestaña **Actions** y descarga los artefactos
`Pasalo-Android-APK` y `Pasalo-Windows`.

## Cómo se usa

1. En el dispositivo que **recibe**: pestaña **Recibir** → elige cómo os
   conectáis. Aparece un **código de 6 cifras**, un **QR** y un botón
   **Copiar enlace**.
2. En el que **envía**: pestaña **Enviar** → **Añadir archivos** → conecta de
   una de estas formas:
   - **Código:** escribe las 6 cifras (busca al receptor en la red local).
   - **QR:** *Escanear QR* (desde Android).
   - **Enlace:** pega el enlace `pasalo://…`. En Android abrir el enlace abre la
     app; en Windows también si se instaló con el instalador.
   - **Bluetooth:** elige el móvil emparejado (solo Android ↔ Android).
3. El receptor ve quién le envía, cuántos archivos y cuánto ocupan, y pulsa
   **Aceptar**. Lo recibido va a `Descargas/Pasalo`.

## Formas de conexión: qué funciona en cada sitio

| Modo | Android ↔ Android | Android ↔ PC | PC ↔ PC | Velocidad |
|---|---|---|---|---|
| Misma Wi-Fi (TCP) | Sí | Sí | Sí | Alta |
| Wi-Fi Direct (sin router) | Sí | Sí (el PC se une a la red del móvil) | No | Alta |
| Bluetooth (RFCOMM) | Sí | No | No | Baja (~100–200 KB/s) |

- **Misma Wi-Fi:** el modo principal. Conexión directa entre los dos
  dispositivos dentro de la red local; nada pasa por Internet.
- **Wi-Fi Direct:** el móvil que recibe crea un grupo Wi-Fi Direct de verdad
  (`WifiP2pManager.createGroup`) y hace de punto de acceso. La app muestra un QR
  de Wi-Fi con la red y la contraseña: otro Android lo escanea desde *Ajustes →
  Wi-Fi*, y el PC se conecta a esa red como a cualquier Wi-Fi. Después se usa
  código, QR o enlace como siempre. Windows no tiene una API de Wi-Fi Direct
  usable desde Flutter, así que el PC solo puede **unirse** al grupo, no crearlo.
- **Bluetooth:** RFCOMM entre dos Android que ya estén emparejados en *Ajustes →
  Bluetooth*. En Windows no está disponible (no hay soporte de Bluetooth clásico
  en Flutter para escritorio). Es lento: piénsalo para archivos pequeños.

## Seguir aunque salgas de la app

- En **Android**, cuando una transferencia **ya ha empezado** (el emisor pulsó
  *Enviar* o el receptor pulsó *Aceptar*) se activa un servicio en primer plano
  con notificación de progreso. Puedes salir de la app, apagar la pantalla o
  quitarla de recientes: la descarga sigue hasta terminar.
- Lo que **no** se hace en segundo plano: aceptar transferencias nuevas. Si la
  app receptora no está en pantalla, cualquier conexión nueva se rechaza con el
  mensaje "Abre Pásalo en el receptor…", y una oferta pendiente se cancela.
- En **Windows**, minimizar no corta nada. Cerrar la ventana sí cierra la app y
  corta la transferencia.
- Si fuerzas el cierre (*Forzar detención*) o el sistema mata la app por falta
  de memoria, la transferencia se corta: el archivo a medias (`.part`) se borra.

## Herramientas

| Herramienta | Qué hace |
|---|---|
| Comprimir | 1 archivo → `.gz`; varios → `.zip`. En Windows también carpetas enteras. |
| Descomprimir | `.zip`, `.gz`, `.tar`, `.tar.gz`/`.tgz`. Protegido contra rutas maliciosas ("zip slip"). |
| Cifrar / Descifrar (AES) | **AES-256-GCM** con clave derivada por PBKDF2-HMAC-SHA256 (310.000 iteraciones). Detecta contraseña mala, archivos modificados y cortados. **Es la opción para datos sensibles.** |
| Cifrado Espejo | El cifrado casero: ver abajo. |
| Archivo → imagen / Imagen → archivo | Ver abajo. |

### Cifrado Espejo (casero)

Hace exactamente lo pedido:

1. **Invierte cada bit**: los 0 pasan a 1 y los 1 a 0.
2. **Rota los bits** dentro de cada byte una cantidad que depende de la
   posición y de la contraseña.
3. **Baraja los bytes** en bloques de 4 KB con una permutación sacada de la
   contraseña.

La contraseña es opcional. Es reversible y sirve para ocultar algo de un
vistazo, **pero no es seguro**: quien conozca el algoritmo lo deshace con poco
esfuerzo. Para cualquier cosa sensible usa **Cifrar (AES)**.

### Archivo → imagen

Interpretación de "compresión de archivo a imagen compuesta de bits": cualquier
archivo se guarda dentro de un PNG sin pérdidas, y se puede recuperar idéntico.
Hay dos modos:

- **Color (compacta):** cada píxel guarda 3 bytes del archivo en sus canales
  rojo, verde y azul. La imagen parece ruido de colores. Máximo 64 MB.
- **Bits en blanco y negro:** cada píxel es un bit (negro = 1, blanco = 0); ves
  literalmente el archivo como bits. Ocupa 8 píxeles por byte. Máximo 4 MB.

La imagen lleva nombre, tamaño y CRC32 del archivo original. **Tiene que viajar
como archivo PNG**: si la mandas como foto por WhatsApp o similar, se recomprime
y los bits se pierden. No reduce el tamaño (el PNG ya comprime lo que puede);
si quieres que ocupe menos, comprime antes.

## Seguridad de la transferencia

- Cada sesión de recepción genera un **token aleatorio de 128 bits** (va en el
  enlace y el QR) y un **código de 6 cifras**. Sin uno de los dos no se puede
  enviar nada. Tras 5 intentos fallidos el código cambia.
- El receptor siempre tiene que **aceptar** a mano.
- Cada archivo se comprueba con **SHA-256** al llegar; si no cuadra se borra.
- Los nombres de archivo recibidos se limpian (sin rutas ni nombres reservados
  de Windows) y nunca se sobrescribe nada: se añade "(1)", "(2)"…
- **El canal Wi-Fi no va cifrado.** Dentro de tu red de casa es razonable; en
  una Wi-Fi pública cifra antes con *Cifrar (AES)* y envía el `.aes`.

## Primer uso

- **Windows:** la primera vez que pulses *Recibir*, el Firewall de Windows
  preguntará si Pásalo puede usar la red. Marca **Redes privadas** y acepta. Si
  la red está marcada como "pública", el código de 6 cifras puede no encontrarse;
  usa el enlace.
- **Android:** pide permisos de notificaciones (para el progreso en segundo
  plano), cámara (QR), Bluetooth o dispositivos Wi-Fi cercanos según el modo.
  En Android 9–10 pide también almacenamiento para guardar en Descargas.
- El APK está firmado con la clave de depuración: sirve para instalar a mano
  (activa "Instalar apps de origen desconocido"), no para Google Play.

## Desarrollo

Flutter 3.47 (Dart 3.13). Estructura:

```
lib/core/       lógica pura en Dart, con tests (protocolo, emparejamiento,
                cifrados, compresor, archivo↔imagen)
lib/platform/   puente con el código nativo de Android
lib/app/        estado de la app (recibir, enviar, ciclo de vida)
lib/ui/         pantallas
android/app/src/main/kotlin/…  servicio en primer plano, Bluetooth RFCOMM,
                Wi-Fi Direct, motor de Flutter que sobrevive a la pantalla
windows/installer.iss          instalador de Inno Setup (registra pasalo://)
```

```bash
flutter pub get
flutter test             # 21 tests del núcleo
flutter run -d windows   # o con un Android conectado
flutter build apk --release
flutter build windows --release
```

Protocolo (TCP o RFCOMM): tramas `tipo (1 byte) | largo (u32) | datos`.
`HELLO` (JSON con token y lista de archivos) → `ACCEPT`/`REJECT` → por archivo
`FILE_START`, `DATA`… , `FILE_END` (SHA-256) → `DONE` y confirmación.
Descubrimiento por código: broadcast UDP al puerto 47821 (`PASALO?123456`); el
receptor responde con su puerto TCP (47820 si está libre).

## Limitaciones conocidas

- Un receptor atiende una transferencia a la vez.
- No se reanuda una transferencia cortada: hay que volver a empezar.
- Bluetooth y Wi-Fi Direct solo en Android (ver tabla).
- El código de 6 cifras necesita que la red deje pasar broadcast UDP; algunas
  redes de invitados o de empresa lo bloquean (el enlace/QR sí funcionan si hay
  conexión directa entre dispositivos; si la red aísla clientes, nada en red
  local funcionará y toca Wi-Fi Direct).
- En Windows, abrir un segundo enlace con la app ya abierta abre otra ventana.
