# Declaraciones de permisos y datos para Google Play

## Resumen: no usamos permisos restringidos

Comprobado en la documentación oficial de `CallScreeningService` y `RoleManager.ROLE_CALL_SCREENING` (developer.android.com):

- La app que tiene el rol de filtrado de llamadas recibe cada llamada entrante **antes de que suene**, con el número en `Call.Details.getHandle()`. **No hace falta `READ_CALL_LOG`**.
- Solo se pasan al servicio las llamadas de números **que no están en los contactos**, salvo que se conceda `READ_CONTACTS`. Filtro 400 **no pide `READ_CONTACTS`**: es justo lo que queremos y se convierte en argumento de venta.
- Debe responder en **5 segundos** (`respondToCall`); el servicio decide en memoria y limita su espera a 3,5 s. Ante cualquier error la llamada suena.
- Los números ocultos, restringidos o de teléfonos públicos no se entregan al servicio.

Por eso **no hay que rellenar la declaración de «Permisos de registro de llamadas y SMS»** en Play Console. Si Play te la pidiera por error, responde que la app no solicita `READ_CALL_LOG`, `WRITE_CALL_LOG`, `PROCESS_OUTGOING_CALLS`, `READ_SMS` ni `SEND_SMS`.

El historial propio de la app solo contiene llamadas que han pasado por el filtro, guardadas en la base de datos privada de la app.

## Permisos declarados en el manifiesto

| Permiso | Para qué | Restringido |
|---|---|---|
| `INTERNET` | Descargar la lista de la comunidad (Premium), enviar denuncias anónimas, Google Play Billing | No |
| `POST_NOTIFICATIONS` | Aviso silencioso «3 llamadas filtradas hoy» y recordatorio de la Lista Robinson (se pide en el onboarding, es opcional) | No |
| `BIND_SCREENING_SERVICE` (en el servicio, no `uses-permission`) | Solo el sistema puede enlazar el servicio de filtrado | No |
| `BIND_QUICK_SETTINGS_TILE` (en el mosaico) | Interruptor en ajustes rápidos | No |

## Rol del sistema (texto para la revisión si lo piden)

> Filtro 400 solicita el rol `ROLE_CALL_SCREENING` mediante `RoleManager.createRequestRoleIntent`. Es la API oficial para apps de bloqueo de llamadas. El usuario lo concede en un diálogo del sistema y puede revocarlo en Ajustes › Aplicaciones › Apps predeterminadas. La app usa el servicio solo para decidir si una llamada entrante de un número no guardado suena, se silencia o se rechaza, según reglas configuradas por el usuario. No lee contactos, no lee el registro de llamadas y no transmite los números entrantes a ningún servidor, salvo que el usuario pulse «Denunciar como spam» sobre una llamada concreta.

## Formulario «Seguridad de los datos»

**¿Recopila o comparte datos del usuario?** Sí (solo en la función de denuncias y en la suscripción).

| Tipo de dato | ¿Se recopila? | ¿Se comparte? | Tratamiento | Obligatorio | Finalidad |
|---|---|---|---|---|---|
| Registros de llamadas / otros datos de actividad | **Sí, solo el número denunciado**, y solo cuando el usuario pulsa «Denunciar» | No | Cifrado en tránsito (HTTPS) | Opcional | Funcionalidad de la app (lista de la comunidad) |
| ID de dispositivo u otros ID | **Sí**, identificador aleatorio de la instalación (no es el ID de publicidad ni de hardware) | No | Cifrado en tránsito; en el servidor solo se guarda un hash con sal | Opcional | Prevención de fraude y abuso (no contar dos veces una denuncia) |
| Información de compra | **Sí**, token de compra de Google Play | No | Cifrado en tránsito; en el servidor solo se guarda un hash | Opcional (solo Premium) | Verificar la suscripción |
| Contactos | **No** | — | — | — | — |
| Ubicación, nombre, correo, teléfono del usuario | **No** | — | — | — | — |

Preguntas generales:
- ¿Los datos se cifran en tránsito? **Sí** (HTTPS obligatorio; `cleartextTrafficPermitted=false`).
- ¿Pueden los usuarios solicitar que se eliminen? **Sí**: los datos de denuncias son anónimos y se borran del servidor a los 180 días; para borrado anticipado, escribir al correo de contacto con el número denunciado.
- Cumple la política de familias: no está dirigida a menores.

## Declaraciones adicionales de la consola

- **Anuncios:** No contiene anuncios.
- **App de noticias / gubernamental / financiera / salud:** No.
- **Acceso a la app:** todas las funciones son accesibles sin credenciales (no hay cuenta).
- **ID de publicidad:** No se usa (declarar «No»).
- **Público objetivo:** mayores de 18 años (recomendado para evitar requisitos de familias).
- **Facturación:** suscripciones con Google Play Billing (producto `comunidad`).
- **Tipo de app:** la función principal NO es identificación de llamadas con base de datos de números, así que no aplica la política específica de «apps de identificador de llamadas»; si Play la aplicara, explicar que la lista se consulta en local.
