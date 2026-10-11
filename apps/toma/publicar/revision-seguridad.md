# Revisión de seguridad de Toma

Revisión del código de la versión 1.0.0 (octubre de 2026). Repasa cada superficie de ataque y lo que se hizo.

| # | Área | Riesgo | Medida aplicada | Dónde |
|---|---|---|---|---|
| 1 | Datos de salud en la nube | Fuga por copia de Google | `allowBackup` con reglas que excluyen todo de la copia en la nube; solo se permite la transferencia directa entre dispositivos | `res/xml/data_extraction_rules.xml`, `backup_rules.xml` |
| 2 | Componentes expuestos | Otra app lanza actividades o receptores | Solo `MainActivity` y los receptores del sistema (arranque, hora, widget) están exportados; alarma, acciones y proveedor de archivos no | `AndroidManifest.xml` |
| 3 | PendingIntents | Manipulación por otras apps | Todos `FLAG_IMMUTABLE` con acción explícita | `AlarmScheduler`, `Notifications` |
| 4 | Cifrado de copias | Contraseña débil, archivo manipulado | PBKDF2-HMAC-SHA256 (310 000 iteraciones), sal e IV aleatorios, AES-256-GCM con la cabecera autenticada; mínimo 8 caracteres; los tests cubren contraseña errónea, manipulación y cabecera alterada | `core/BackupCrypto.kt`, `BackupTest` |
| 5 | Archivo de copia malicioso | Denegación de servicio o datos corruptos | Límite de 64 MB; iteraciones acotadas (un archivo no puede fijar un coste enorme); el contenido se valida (tamaños, horarios, referencias) antes de tocar la base de datos; la restauración es una única transacción | `BackupCrypto`, `BackupCodec.decode`, `Repository.replaceAll` |
| 6 | Inyección de fórmulas en CSV | Un nombre como `=HYPERLINK(...)` ejecuta algo en Excel | Se antepone una comilla a los valores que empiezan por `= + - @` | `core/Csv` |
| 7 | Contraseña de la copia automática | Lectura desde una copia de los archivos | Se guarda cifrada con una clave AES-256 del Android Keystore (no exportable) | `settings/SecretStore.kt` |
| 8 | Modo cuidador: red | Texto claro, redirecciones, SSRF | Solo `https`, sin credenciales en la URL, sin redirecciones, tiempos de espera, tema aleatorio de 20 caracteres (~100 bits) validado con expresión regular; `cleartextTrafficPermitted=false` | `CaregiverAlerts.kt`, `network_security_config.xml` |
| 9 | Modo cuidador: privacidad | Fuga de nombres de medicamentos | Por defecto solo van nombre del perfil y hora; los medicamentos son opt-in; aviso en pantalla de que el tema es secreto | `CaregiverScreen` |
| 10 | Compras falsas | Desbloqueo de Plus en un móvil manipulado | Verificación de la firma de Google Play con la clave de licencia de la app (`toma.playLicenseKey`); solo se acepta `PURCHASED`; un fallo de red nunca quita Plus ya comprado | `billing/PurchaseVerifier.kt`, `BillingManager.kt` |
| 11 | Notificaciones en pantalla bloqueada | Mostrar medicación a un curioso | Visibilidad privada + versión pública genérica («es hora de una toma») | `Notifications.ring` |
| 12 | Informe PDF | Archivos que se quedan en el móvil | Se crea en caché, se borra el anterior, se comparte con `FileProvider` (solo esa carpeta, permiso temporal de lectura) | `PdfReport`, `file_paths.xml` |
| 13 | Registro | Datos de salud en logs | No hay llamadas a `Log` con datos del usuario | todo el código |
| 14 | Inyección SQL | Consultas construidas con texto | Solo consultas Room con parámetros | `data/Daos.kt` |
| 15 | Ofuscación | Ingeniería inversa fácil | R8 con reducción y ofuscación en release; reglas para kotlinx.serialization | `proguard-rules.pro` |
| 16 | Firma | Pérdida o filtrado de la clave | `keystore.properties` y `*.jks` en `.gitignore`; se recomienda Play App Signing | `.gitignore`, `PUBLICAR.md` |
| 17 | Permisos | Exceso de permisos | Solo los necesarios; **no** se usa `USE_EXACT_ALARM` (reservado por Google para apps de alarma/calendario) sino `SCHEDULE_EXACT_ALARM` con comprobación de `canScheduleExactAlarms()` y alternativa inexacta si se revoca | `AlarmScheduler`, `Permissions` |

## Limitaciones conocidas (honestas)
- ntfy.sh es un servicio público: quien conozca el tema puede leer los avisos. El tema es largo y aleatorio, y se puede regenerar; para más control se puede usar un servidor propio.
- La base de datos local no está cifrada con contraseña: depende del cifrado de disco de Android y del bloqueo del móvil. Un teléfono con root puede leerla.
- La verificación de compras se hace en el cliente. Para una protección total habría que validar el token en un servidor; no se hace para no tener servidores ni cuentas.
- No se ha podido compilar ni probar en un dispositivo desde el entorno de desarrollo; la compilación y las pruebas corren en GitHub Actions (`.github/workflows/toma-android.yml`). Haz una prueba manual de alarmas en un móvil real antes de publicar (ver `PUBLICAR.md`).
