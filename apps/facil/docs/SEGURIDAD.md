# Revisión de seguridad — Fácil 1.0

Alcance: app Android, reglas de Firebase, flujo de emparejamiento y suscripción. Revisión manual del código completo + pruebas automáticas (`tools/test-local.sh`, `firebase/rules-test.js`).

## Modelo de amenazas
| Quién | Qué querría | Defensa |
|---|---|---|
| Desconocido con internet | Leer/escribir datos de otros | Reglas RTDB: cada ruta exige `auth.uid` dueño o miembro activo; no se puede listar `devices`, `codes` ni `users`. 39 pruebas en emulador. |
| Desconocido que adivina un código | Unirse a un móvil | Código de 10 caracteres (32¹⁰ ≈ 10¹⁵), caduca a los 15 min y, sobre todo, **la persona mayor debe aceptar** a cada familiar viendo su nombre. |
| Familiar aceptado (hostil) | Abusar | Solo ve batería/actividad/SOS (no ubicación, mensajes, llamadas). Puede cambiar botones: el contenido se **valida y recorta** en el móvil (tipos permitidos, paquetes con formato válido, teléfonos solo dígitos → sin USSD `*#`, textos sin control, fotos ≤60 KB y ≤1024 px). Aviso permanente en el móvil mayor; puede quitarlo cuando quiera o apagar el compartir. |
| Niño / curioso con el móvil mayor | Cambiar ajustes | Mantener 2 s + PIN (PBKDF2-SHA256 60 000 it., sal 16 B, comparación en tiempo constante, bloqueo 30 s → 15 min tras 5 fallos). |
| Otra app del móvil | Leer datos | `allowBackup=false`, exclusiones de extracción, SharedPreferences privadas, refresh token cifrado con Android Keystore (AES-GCM), solo `HomeActivity` exportada, receiver/servicio no exportados, PendingIntents `IMMUTABLE`. |
| Red hostil | Interceptar | Solo HTTPS (`usesCleartextTraffic=false`, network security config), sin redirecciones, límite de respuesta 2 MB, timeouts. |

## Hallazgos corregidos durante la revisión
- Rutas de la API con lista blanca de caracteres (evita inyectar `../` o parámetros).
- Comprobación de **dimensiones de imagen antes de decodificar** (un JPEG manipulado de 60 KB podía declarar 60 000 × 60 000 px y tumbar la app).
- "Compartir estado" es **solo local**: la familia no puede activarlo a distancia (evita vigilancia encubierta).
- Normalización de códigos sin sustituciones que los invalidaban.

## Riesgos asumidos
- El PIN viaja en la configuración sincronizada (solo hash+sal). Un familiar aceptado podría intentar fuerza bruta offline de un PIN de 4–8 dígitos; ya es alguien de confianza.
- La suscripción se comprueba en el dispositivo contra Google Play. Con root se puede saltar el candado de "Fácil Familia" en el propio móvil del usuario; no expone datos de terceros (las reglas no dependen de la suscripción). Mejora futura: validar el token en servidor.
- Clave de API de Firebase: no es secreta; se restringe en Google Cloud (ver `FIREBASE.md`).

## Permisos
`INTERNET`, `ACCESS_NETWORK_STATE`, `CALL_PHONE` (solo al llamar), `CAMERA` (solo para leer el QR; no guarda fotos), `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `BILLING`. **No** usa: AccessibilityService, ubicación, contactos (se usa el selector del sistema), SMS/llamadas leídas, `QUERY_ALL_PACKAGES`, alarmas exactas, `SYSTEM_ALERT_WINDOW`.

## Antes de escalar
1. Probar en dispositivos reales (Android 8, 12, 14, 15) y con TalkBack.
2. Activar Firebase App Check (Play Integrity).
3. Presupuesto y alertas en Google Cloud.
