# Revisión de seguridad de A medias

Revisión del código y de las reglas del backend. Lo que se probó, lo que se
corrigió y lo que queda por decidir. Última revisión: octubre de 2026.

## Cómo se protege la información

- **Sin cuentas**: sesión anónima de Firebase por dispositivo. No hay contraseñas que robar.
- **Grupos locales**: solo en el almacenamiento privado de la app. `allowBackup=false`, así que no se copian a la nube de Android.
- **Grupos compartidos**: Firestore, región UE. Leer o escribir exige ser miembro (`groups/{g}/members/{uid}`); hacerse miembro exige un **secreto de invitación** (24 caracteres base62, ~143 bits, generado con `Random.secure()`) que el servidor comprueba.
- **El secreto va en el fragmento de la URL (`#`)**: el navegador no lo envía en la petición HTTP, así que no queda en registros del servidor de hosting ni en cabeceras `Referer` (además `Referrer-Policy: no-referrer`).
- **Revocación**: «Cambiar el enlace» borra la invitación vieja y crea otra; quien ya está dentro sigue dentro.
- **Tráfico**: solo HTTPS (`usesCleartextTraffic=false`, red de confianza solo del sistema, HSTS en hosting).

## Reglas de Firestore (`firebase/firestore.rules`)

Probadas con el emulador: **36 pruebas** (`firebase/rules-test`). Comprueban, entre otras:

- Un extraño o un usuario sin sesión no lee grupos, gastos, miembros ni invitaciones; no puede listar colecciones.
- Unirse con un secreto falso, inexistente o ya rotado falla; solo se puede crear el propio documento de miembro; no admite campos extra.
- Los gastos solo admiten los campos esperados, con tipos, rangos y longitudes (importe entero > 0 y ≤ 10⁹ unidades, tipo de cambio 0 < r ≤ 10⁵, ≤ 50 personas, textos acotados, id = ruta).
- Nadie puede quedarse la propiedad de un grupo, borrar un grupo ni sacar a otro miembro; cada uno puede salir.
- Cualquier otra colección está cerrada.

## Hallazgos y qué se hizo

| # | Riesgo | Estado |
|---|---|---|
| 1 | **Inyección de fórmulas en CSV** (`=HYPERLINK(...)` en el concepto de un gasto se ejecutaría en Excel) | Corregido: se prefija con `'` cualquier celda que empiece por `= + - @`, tab o retorno (prueba incluida). |
| 2 | **Datos mal formados de un cliente modificado** que rompan la pantalla del grupo (tipos incorrectos, importes enormes) | Corregido: las reglas lo rechazan y el cliente ignora los documentos inválidos en lugar de fallar. |
| 3 | **Desbordamiento / pérdida de precisión** con importes × tipo de cambio gigantes | Corregido: límites en cliente y reglas para que quepa en 53 bits exactos (web) y la suma sea exacta. |
| 4 | **Errores de redondeo** al repartir | Corregido por diseño: todo en enteros (céntimos) y reparto por mayor resto; la suma de partes siempre es exactamente el total (pruebas). |
| 5 | **Enlace de invitación malicioso** (ruta o ids con caracteres raros) | Corregido: se valida con una lista blanca `[A-Za-z0-9]{8,40}`; cualquier otra cosa se rechaza (pruebas). |
| 6 | **Caracteres de control** en nombres/notas | Corregido: se sustituyen y se acota la longitud al guardar y al leer. |
| 7 | **Fotos de tiques** que se quedan en el móvil | Corregido: el OCR es local (ML Kit) y la foto se borra al leerla. |
| 8 | **Tráfico sin cifrar / certificados** | Corregido: HTTPS obligatorio. |
| 9 | **Copia de seguridad de Android** exponiendo datos locales | Corregido: desactivada. |
| 10 | **Clave de firma / claves de Firebase** en el repositorio | No hay secretos en git: `key.properties` y `*.jks` en `.gitignore`; las claves de Firebase se inyectan con `--dart-define-from-file`. (Las claves de API de Firebase para cliente no son secretas, pero se restringen por paquete y con App Check.) |

## Riesgos aceptados y recomendaciones

1. **Quien tiene el enlace tiene acceso.** Es el modelo «sin cuenta». Mitigación: botón de cambiar enlace y aviso al compartir. Si más adelante hay grupos sensibles, añadir PIN opcional por grupo.
2. **No se puede expulsar a un miembro** (no hay identidad persistente de las personas). Para echar a alguien: cambiar el enlace y crear un grupo nuevo. Mejora futura: roles con propietario.
3. **Cualquier miembro puede editar y borrar gastos.** Es lo esperable en un grupo de confianza (hay «deshacer»). Mejora futura: historial de cambios.
4. **Abuso de sesiones anónimas / cuota de Firestore.** Activar **App Check** (Play Integrity + reCAPTCHA) y la alerta de presupuesto (ver `PUBLICAR.md` §1). Las reglas ya impiden crear contenido fuera de los grupos propios, pero no limitan la tasa de escrituras: si hubiera abuso, añadir límites con Cloud Functions.
5. **La compra de Plus se valida en el cliente.** Un móvil con root podría activar Plus sin pagar. El riesgo económico es bajo; si crece, validar el token de compra con la API de Google Play Developer desde una Cloud Function.
6. **Tipos de cambio** vienen de un servicio público de terceros; el usuario siempre los ve y puede editarlos antes de guardar.
7. **Dependencias**: ejecutar `flutter pub outdated` y `npm audit` (reglas) antes de cada release y activar Dependabot en el repositorio.

## RGPD

- **Datos de terceros**: se guardan nombres de pila que una persona escribe de otras. Base: interés legítimo de llevar cuentas en común (uso doméstico). Mitigación: política clara, nombres sin apellidos en el texto de ayuda, derecho de supresión por correo, y borrado de un grupo compartido a petición (desde la consola de Firebase; no hay borrado automático al salir todos).
- **Encargado**: Google (Firebase), región UE. Firmar los términos de tratamiento de datos de Firebase en la consola.
- **Sin analítica ni publicidad**, por tanto no hace falta banner de cookies (la web solo usa almacenamiento técnico).
- **Pagos**: nunca se tocan; la app solo copia un importe para que el usuario lo pague por su banco.

## Lo que falta verificar con un dispositivo

- Comportamiento del enlace verificado (App Links) con la huella real de firma.
- Play Integrity / App Check con la app firmada por Google Play.
- Flujo de compra con una cuenta de pruebas de licencias.
