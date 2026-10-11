# Revisión de seguridad · Filtro 400

Revisión del código de la app Android y del servidor, hecha a mano sobre la versión 1.0.0. Estado de cada punto: ✅ resuelto en el código, ⚠️ riesgo aceptado y documentado, 🔧 requiere acción tuya.

## App Android

| # | Tema | Estado |
|---|---|---|
| 1 | **Servicio de filtrado** exportado, protegido con `android.permission.BIND_SCREENING_SERVICE`: solo el sistema puede enlazarse. | ✅ |
| 2 | **Mosaico de ajustes rápidos** exportado, protegido con `BIND_QUICK_SETTINGS_TILE`. | ✅ |
| 3 | **Receptor de notificación** `exported=false`; los `PendingIntent` son `FLAG_IMMUTABLE` y el número se revalida (`normalize`) antes de actuar. | ✅ |
| 4 | **Actividad principal** (única exportada con intent-filter de lanzador): el extra `destination` solo acepta una lista cerrada de rutas. | ✅ |
| 5 | **Permisos mínimos**: sin contactos, sin registro de llamadas, sin estado del teléfono. Solo `INTERNET` y `POST_NOTIFICATIONS`. | ✅ |
| 6 | **Red**: solo HTTPS (`cleartextTrafficPermitted=false`), sin redirecciones, tiempos límite, tamaño máximo de lista (8 MB) y lista validada entrada a entrada (solo E.164 reales). El servidor no es de fiar a ciegas. | ✅ |
| 7 | **Copias de seguridad**: `allowBackup=false` y reglas de extracción que excluyen todo. El historial de llamadas no sale del teléfono. | ✅ |
| 8 | **Notificaciones** con visibilidad privada: en pantalla bloqueada no se ve el número. | ✅ |
| 9 | **Sin registros con datos**: ningún `Log` de números ni de configuración. | ✅ |
| 10 | **Base de datos**: consultas parametrizadas; no hay SQL construido con texto del usuario. | ✅ |
| 11 | **Fallo seguro**: si el filtro falla o tarda más de 3,5 s, la llamada suena. Nunca se pierde una llamada por un error. | ✅ |
| 12 | **Ofuscación**: R8 + reducción de recursos en release. | ✅ |
| 13 | **Estado Premium en el cliente**: se guarda en local, así que alguien con el móvil modificado podría marcarse como Premium. Pero la lista solo se descarga con un token de compra que el **servidor verifica con Google**, así que no obtiene datos. Lo único que consigue es usar una lista ya guardada. | ⚠️ |
| 14 | **Identificador de instalación** aleatorio (24 bytes, `SecureRandom`), no ligado al hardware ni a la cuenta de Google. | ✅ |
| 15 | **Firma de release**: la clave no está en el repositorio (`keystore.properties` y `*.jks` en `.gitignore`). Se firma con variables de entorno en CI o fichero local. | 🔧 |

## Servidor (Cloudflare Worker)

| # | Tema | Estado |
|---|---|---|
| 1 | **Validación estricta** de denuncias: número E.164, 9 cifras para +34, categoría de una lista cerrada, identificador con formato fijo, cuerpo ≤ 1 KB. | ✅ |
| 2 | **SQL parametrizado** (`bind`) en todas las consultas. | ✅ |
| 3 | **Privacidad**: el identificador de instalación y el token de compra se guardan solo como hash con sal (`PEPPER`); la IP nunca se guarda. | ✅ |
| 4 | **Suscripción verificada en el servidor** con la API de Google Play (`subscriptionsv2`): estado activo o en periodo de gracia y producto correcto; se cachea como máximo 12 h. | ✅ |
| 5 | **Límites**: 30 denuncias/día por instalación y 120/día por red. | ✅ |
| 6 | **Envenenamiento de la lista**: un atacante podría intentar que un número legítimo (tu banco) entre en la lista. Mitigaciones: hacen falta ≥ 3 redes distintas (hash de IP por día); una denuncia por persona y número; tabla `excluded` para retirar números a mano; las entradas caducan a los 90 días; el usuario puede ponerlos en «Siempre permitidos». Un atacante con muchas IP y paciencia podría lograrlo. | ⚠️ |
| 7 | **Denegación de servicio**: la protección de red de Cloudflare absorbe lo básico; los límites por IP/instalación acotan el abuso de escritura. Activa **Rate Limiting rules** de Cloudflare en `/v1/*` si ves tráfico anómalo. | ⚠️ |
| 8 | **Secretos** (`PEPPER`, `GOOGLE_SERVICE_ACCOUNT`) solo como secretos de Wrangler, nunca en el repositorio. | 🔧 |
| 9 | **Cabeceras**: `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`. La API es solo JSON consumida por la app (sin CORS abierto). | ✅ |
| 10 | **Retención**: denuncias 180 días, contadores de IP 1 día, suscripciones hasta caducar. Limpieza diaria programada. | ✅ |

## Cadena de suministro

- Dependencias fijadas por versión en `gradle/libs.versions.toml` y `package-lock.json`.
- El wrapper de Gradle se valida en CI (`validate-wrappers`).
- Recomendado: activar **Dependabot** y **secret scanning** del repositorio (Ajustes › Código, seguridad y análisis).

## Cumplimiento

- **RGPD:** el tratamiento se limita a denuncias voluntarias y verificación de suscripción; base jurídica: ejecución del servicio solicitado (art. 6.1.b) y consentimiento al pulsar «Denunciar» (art. 6.1.a). Política de privacidad en `publicar/politica-privacidad.md`. Antes de publicar, rellena tu identidad como responsable.
- **Google Play:** sin permisos restringidos (`publicar/declaracion-permisos.md`).
- **Ley 10/2025 / rango 400:** la app solo informa y filtra; no hace afirmaciones legales fuera de la guía, que incluye aviso de «información orientativa».

## Lo que no he podido verificar

- La app **no se ha ejecutado en un teléfono ni en un emulador**: el entorno de trabajo no tiene Android SDK. Compila, pasa lint y pruebas unitarias del motor en GitHub Actions, pero hay que probarla en un móvil real antes de publicar: llamada entrante desde un número desconocido con el rol activo, silenciar, bloquear, notificación y mosaico.
- El servidor **no se ha desplegado** ni probado contra la API real de Google Play (hace falta tu cuenta de servicio). La lógica pura (validación, agregación, suscripciones) tiene pruebas unitarias.
