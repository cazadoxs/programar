# Revisión de seguridad — Escanea y come v1.0.0

Revisión del código completo antes de publicar. Estado: **sin hallazgos abiertos de gravedad alta o media**; riesgos residuales aceptados al final.

## Superficie de ataque
| Área | Estado | Cómo se controla |
|---|---|---|
| Componentes exportados | OK | Solo `MainActivity` (launcher). El `FileProvider` no está exportado y solo sirve `cache/exports/`. Sin receivers, servicios ni deep links. |
| Permisos | OK | `INTERNET` y `ACCESS_NETWORK_STATE`. Sin cámara (escáner de Google), sin almacenamiento, sin ubicación. |
| Red | OK | HTTPS obligatorio (`network_security_config`, cleartext desactivado, solo CA del sistema). Redirecciones desactivadas en el cliente OFF. Tiempos de espera, tope de 3 MB por respuesta, `User-Agent` propio. |
| Entrada externa (códigos) | OK | Todo código pasa por `Barcode.normalize`: solo dígitos 8–14 con dígito de control válido. Es lo único que se concatena en una ruta de URL; el texto libre se codifica con `URLEncoder`. |
| Respuestas de Open Food Facts | OK | `OffParser` no lanza con JSON malformado; recorta longitudes, elimina caracteres de control, descarta números no finitos/negativos y fija máximos físicos (≤100 g/100 g, ≤950 kcal). Imágenes solo `https` y host `images/static.openfoodfacts.org` sin credenciales. Etiquetas filtradas a letras/dígitos. |
| Base de datos | OK | Room con consultas parametrizadas; el `LIKE` de búsqueda escapa `%`, `_` y `\`. Sin SQL dinámico. |
| Exportación CSV | OK | Escapado RFC 4180 y prefijo `'` en celdas que empiezan por `= + - @ tab CR` (inyección de fórmulas en Excel/Sheets). Archivo en caché, borrado en la siguiente exportación. |
| Intents salientes | OK | Solo `ACTION_VIEW` a `https://world.openfoodfacts.org/product/<código validado>` y a la política de privacidad; `ACTION_SEND` del CSV con permiso de lectura temporal. |
| Copias de seguridad | OK | `allowBackup` limitado a la BD y ajustes; en Android ≤11 exige cifrado del cliente; en 12+ `disableIfNoEncryptionCapabilities`. |
| Compilación release | OK | R8 + reducción de recursos activos; logs `v/d/i` eliminados. Clave de subida fuera del repositorio (`.gitignore`, `keystore.properties`). |
| Secretos | OK | La app no contiene claves de API ni tokens. Open Food Facts no los necesita. |
| Dependencias | OK | Versiones fijadas en `gradle/libs.versions.toml`; todas de Google/JetBrains/Maven Central. Revisa Dependabot o `./gradlew dependencies` antes de cada versión. |

## Compras (Play Billing)
- El derecho "Plus" se guarda en preferencias para funcionar sin conexión y **se revalida contra Google Play en cada arranque**; un reembolso lo retira.
- Se reconoce la compra (`acknowledge`) para que Google no la reembolse a los 3 días.
- Estado `PENDING` no concede Plus hasta que pasa a `PURCHASED`.

## Privacidad y salud
- Sin analíticas, anuncios ni identificadores. El único dato que sale es el código/consulta hacia Open Food Facts (visible su IP para ese servidor).
- Modo tranquilo, objetivos con suelo (nunca por debajo del metabolismo basal ni de 1200/1500 kcal en déficit), déficit máximo del 15 %, menores de 18 solo mantenimiento, objetivo manual limitado a 1000–6000 kcal. Sin lenguaje de culpa: pasarse de objetivo se muestra en tono neutro, no rojo.

## Riesgos residuales (aceptados)
1. **Plus manipulable en móviles con root:** el indicador está en preferencias locales. Coste/beneficio: un servidor de validación costaría más que lo que protege; los extras no son contenido sensible. 
2. **Coil y redirecciones de imagen:** la URL se valida en el parseo, pero si el servidor de OFF respondiera con una redirección, Coil la seguiría. Impacto: solo una imagen distinta, sin ejecución de código.
3. **Calidad de datos colaborativos:** un producto de OFF puede tener valores erróneos. Mitigación: límites físicos al leer, enlace "Corregir datos" y alimentos propios con prioridad.
4. **Disponibilidad de OFF:** si cae, la app muestra un error amable y sigue funcionando con la caché y alimentos propios.

## Pruebas automáticas relacionadas
`core/src/test`: validación de códigos (inyección de rutas, dígito de control), parser (JSON basura, imágenes ajenas, valores extremos), CSV (fórmulas) y objetivos (suelos y menores).
