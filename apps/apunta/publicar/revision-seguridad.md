# Revisión de seguridad · Apunta v1.0.0

Revisión manual del código propio (Kotlin y JNI) el 11-oct-2026. No sustituye una auditoría externa ni un escáner automático (ver «Pendiente»).

## Superficie de ataque
| Entrada | Quién puede usarla | Qué hace la app | Riesgo | Mitigación |
|---|---|---|---|---|
| Compartir / «Abrir con» audio o vídeo (`SEND`, `SEND_MULTIPLE`, `VIEW`) | Cualquier app instalada | Copia el fichero a almacenamiento privado y lo decodifica | Fichero enorme, nombre malicioso (`../`), esquema `file://` para leer ficheros privados, fichero corrupto que tumbe el decodificador | Límite 2 GB con contador mientras copia; nombre generado por la app (`imp_<tiempo>.<ext>`, extensión validada con regex `[a-z0-9]{2,5}`); `file://` rechazado; decodificación con `MediaCodec` (proceso aislado del sistema) y excepciones capturadas |
| Intents `app.apunta.action.RECORD/SEARCH/OPEN` | Cualquier app | Solo navegan a una pantalla | Una app maliciosa abre la pantalla de grabar | Grabar exige que el usuario pulse el botón; `OPEN` solo lleva un `Long` |
| `RecorderService`, `TranscriptionService` | Solo la propia app (`exported=false`) | — | — | No exportados |
| `RecordTileService` | Sistema (`BIND_QUICK_SETTINGS_TILE`) | Abre la app para grabar | — | Permiso de sistema; no inicia el micrófono sin la actividad visible |
| `FileProvider` | Solo la app que reciba el URI al compartir | Expone únicamente `cache/exports/` | Fuga de otros ficheros | `cache-path` limitado a `exports/`, `exported=false`, permiso temporal de lectura; carpeta se vacía al compartir de nuevo y al arrancar |
| Red: descarga de modelos | Servidor Hugging Face | Descarga un `.bin` | Modelo manipulado / MITM | HTTPS obligatorio (`cleartextTrafficPermitted=false`, solo CAs del sistema); comprobación SHA-1 contra la huella publicada por whisper.cpp antes de usarlo; verificación de la cabecera «ggml» en modelos propios |
| Red: resumen con IA | Proveedor elegido por el usuario | Envía **solo texto** | Fuga de la clave o del texto | Consentimiento explícito antes del primer envío; dominios fijos (api.anthropic.com, api.openai.com, generativelanguage.googleapis.com); clave en cabecera, no en URL; el id de modelo se valida (`[A-Za-z0-9._-]{1,64}`) para no inyectar rutas; errores no incluyen la clave |
| Compra Pro | Google Play | Concede Pro | Compra falsificada / parcheo | Firma RSA de la compra verificada con la clave de licencia; en *release* sin clave configurada nunca se concede Pro; se reconsulta en cada arranque y se confirma (`acknowledge`) |

## Datos en el dispositivo
- Audio y texto: `filesDir` y Room (almacenamiento privado, inaccesible a otras apps sin root). **No se cifran en reposo** más allá del cifrado de disco de Android; es una decisión de diseño (rendimiento y búsqueda) y se documenta en la política.
- Clave de API: AES-256-GCM con clave en el **Android Keystore**; el fichero de preferencias está excluido de copias de seguridad y de transferencia entre dispositivos.
- `allowBackup=true` pero con reglas que **excluyen todo** de la nube (`data_extraction_rules.xml`, `backup_rules.xml`), coherente con la promesa «tu audio no sale del móvil».
- Sin analíticas, sin identificadores de publicidad, sin registros con contenido del usuario (el JNI solo registra avisos genéricos).

## Código nativo (whisper.cpp / ggml, C/C++)
- Versión fija 1.8.5 incluida en el repositorio (sin descargar nada en la compilación → compilación reproducible).
- El puente JNI (`apunta_jni.cpp`) valida punteros nulos, el tamaño del buffer (`n_samples ≤ longitud del array`), evita `NewStringUTF` con datos no controlados (el texto vuelve como bytes UTF-8 y se decodifica en Kotlin), comprueba excepciones Java tras cada llamada de retorno y libera referencias locales. Probado en PC con un modelo de prueba: transcripción, cancelación, tamaño inválido (−2) y ruta de modelo inexistente (0).
- Flags de compilación: `-fstack-protector-strong`, `-fvisibility=hidden`, `-Wl,-z,max-page-size=16384` (compatible con páginas de 16 KB exigidas por Google Play).
- Riesgo residual: un modelo `.bin` malicioso importado a mano podría explotar un fallo de whisper.cpp al parsearlo. Por eso solo se carga lo que el usuario importa **voluntariamente**; los modelos descargados van verificados.

## Concurrencia y robustez
- Un único trabajo de transcripción a la vez; cancelación cooperativa y manejo del tiempo máximo de servicios en primer plano de Android 15 (`onTimeout`).
- La transcripción guarda progreso por trozo de 5 min: un cierre forzado no pierde trabajo.
- WAV con cabecera actualizada cada 5 s: si el móvil se apaga grabando, la grabación sigue siendo válida.
- Disco lleno: la grabación guarda lo capturado; la descarga de modelos exige 2× el tamaño libre.

## Hallazgos y estado
| # | Hallazgo | Gravedad | Estado |
|---|---|---|---|
| 1 | Los transcritos exportados quedaban en caché hasta la siguiente exportación | Baja | **Corregido** (se borran al arrancar la app y al exportar de nuevo) |
| 2 | Servicio `dataSync` sin gestión del límite de 6 h de Android 15 | Media | **Corregido** (`onTimeout`) |
| 3 | `file://` en el importador podría usarse para leer ficheros de la app | Alta (potencial) | **Prevenido**: rechazado siempre |
| 4 | Compra Pro editable en `SharedPreferences` en un móvil con root | Baja (aceptada) | Aceptada: Play se reconsulta en cada arranque y quita Pro si no hay compra; un usuario con root solo se engaña a sí mismo |
| 5 | Cuota mensual gratuita editable con root | Baja (aceptada) | Aceptada |
| 6 | SHA-1 es débil como huella | Baja | Aceptada: es la huella publicada por el proyecto y el transporte es HTTPS; una mejora futura es fijar SHA-256 calculado a mano |
| 7 | Sin cifrado de audio/texto en reposo | Informativa | Documentada; posible mejora (Pro): cifrar con clave del Keystore y bloqueo biométrico |

## Pendiente (recomendado antes de producción)
1. Ejecutar sobre el APK de *release*: `MobSF` (análisis estático gratuito) y revisar el informe de **Lint** del CI.
2. Activar el análisis **Play Pre-launch report** al subir a prueba cerrada (detecta fallos y problemas de accesibilidad en dispositivos reales).
3. Probar en móviles reales: Android 8, 10, 13, 14 y 15; una gama baja de 3-4 GB de RAM.
4. Dependabot para dependencias (Billing, Room, Compose).
