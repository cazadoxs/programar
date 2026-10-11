# Revisión de seguridad

Alcance: todo el código de `core/` y `app/`, manifiesto, recursos XML y CI. Revisión manual línea a línea + pruebas automáticas
+ lint de Android en CI. Fecha: 11 oct 2026. Modelo de amenaza: la app **no tiene backend ni cuentas**; la superficie es
(a) datos que descarga de terceros, (b) otras apps del mismo móvil, (c) el propio dispositivo/APK, (d) la cadena de compra/anuncios.

## Resumen
| Área | Riesgo | Resultado |
|---|---|---|
| Tráfico de red | Cleartext, MITM, hosts inesperados, redirecciones | ✅ HTTPS obligatorio (`network_security_config`, sin tráfico claro, sin CAs de usuario). Cliente propio con lista blanca de hosts aplicada **también en cada redirección** |
| Datos descargados (feeds) | ZIP bomb, CSV malicioso, memoria | ✅ Límites: descarga ≤ 200 MB, expandido ≤ 900 MB, ≤ 6 M filas, respuestas pequeñas ≤ 4 MB / 256 KB (tiempos), parser CSV con límites, blobs con lectura acotada (un blob corrupto lanza excepción, no lee fuera de rango) |
| XML de tiempos de EMT | **XXE / entidades externas** | ✅ DocumentBuilder con DOCTYPE prohibido y entidades externas desactivadas; prueba unitaria `busRealtimeRejectsXxe` |
| JSON (Renfe, Valenbisi, avisos) | Inyección, tamaños | ✅ `kotlinx.serialization` tolerante; textos solo se pintan como texto plano (sin HTML/WebView); enlaces solo `https://` |
| SQL | Inyección | ✅ Room con parámetros enlazados; sin SQL dinámico. Los comodines `%`/`_` del buscador solo afectan al propio usuario |
| Componentes exportados | Intents hostiles de otras apps | ✅ Solo exportados: actividad lanzadora, configuración de widgets (obligatorio) y receptores de widgets (estándar). La clave de parada que llega por intent se valida con `splitKey` y se consulta en BD; una clave falsa simplemente no devuelve datos. `PendingIntent` con `FLAG_IMMUTABLE` |
| Permisos | Excesivos | ✅ Solo `INTERNET`, `ACCESS_NETWORK_STATE`, ubicación aproximada/precisa (**opcional, solo en primer plano**), `POST_NOTIFICATIONS` (opcional), `BILLING`. Sin ubicación en segundo plano, sin almacenamiento, sin contactos |
| Privacidad | Fuga de ubicación | ✅ La ubicación nunca sale del móvil: solo se usa para consultar la base de datos local |
| Copias de seguridad | Restaurar Pro gratis / filtrar datos | ✅ Solo se respalda `user.db` (favoritas). El ajuste que cachea Pro **no** se respalda y Play lo revalida en cada arranque |
| Secretos | Claves en el repo | ✅ Cero secretos versionados. AdMob/JCDecaux/firma por `keystore.properties` o variables de entorno (en `.gitignore`). Escaneo de secretos de GitHub activable |
| Ofuscación | Ingeniería inversa | ✅ R8 + reducción de recursos en release; `Log.v/d/i` eliminados |
| Compras | Fraude / bypass | ⚠️ El derecho Pro se valida con Play Billing en cliente. Un dispositivo con root puede falsificarlo; es el compromiso estándar para un pago único de 2–3 €. Si hubiera abuso real, añadir verificación del token en servidor |
| Anuncios | Datos de menores / consentimiento | ✅ UMP antes de pedir anuncios en EEE/RU/CH, contenido máx. G, no dirigido a niños, AdMob desactivado para Pro |
| Dependencias | CVE conocidas | ✅ Versiones recientes de AndroidX/Compose/OkHttp/MapLibre/Play. Activar Dependabot (ver abajo) |
| CI | Secretos filtrados / workflows | ✅ El workflow no usa secretos; `permissions` por defecto; solo `actions/*` y `gradle/actions` oficiales |

## Hallazgos corregidos durante la revisión
1. **Backup exponía el flag Pro** (`data_extraction_rules` respaldaba DataStore): corregido, ya no se respalda.
2. **Redirecciones** podían saltarse la lista de hosts: el guard se aplica como interceptor de aplicación **y de red**.
3. **Bomba de descompresión**: añadido tope de tamaño expandido y de filas antes de compilar.
4. **XXE en el XML de EMT**: parser endurecido y con prueba.
5. **Feed GTFS caducado de Metrovalencia** producía horarios vacíos (fallo funcional con riesgo de confianza): calendario "día equivalente" marcado como *aproximado* en la UI.

## Riesgos aceptados / pendientes (necesitan decisión de Álvaro)
- **Tiempos de EMT**: usa un servicio web público no documentado. Riesgos: cambio sin aviso, bloqueo, o términos que lo prohíban. Mitigación implementada: si falla, horarios y se avisa ("Según horario · sin tiempo real ahora"). Acción: pedir autorización a EMT (ver `fuentes-de-datos.md`).
- **Valenbisi**: endpoint CKAN no verificable desde el entorno; mitigado con último dato guardado.
- **Pro solo en cliente** (arriba).

## Recomendado activar en GitHub (1 min)
- Dependabot (`.github/dependabot.yml`, ecosistemas `gradle` y `github-actions`), *secret scanning* y *push protection*.
- Protección de rama `main`: PR + CI verde.

## Cómo repetir la revisión
```bash
CORE_ONLY=1 ./gradlew :core:test          # incluye pruebas de XXE, blobs corruptos y límites
./gradlew :app:lintRelease                # lint de seguridad de Android
```
