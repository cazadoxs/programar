# Fuentes de datos, licencias y riesgos

Verificado el 10–11 de octubre de 2026 (descargas reales desde GitHub Actions; ver el test `LiveFeedsTest`).

| Red | Qué usa la app | URL | Estado verificado | Licencia / condiciones |
|---|---|---|---|---|
| Bus EMT València | Horarios GTFS (paradas, líneas, recorridos) | Portal de datos abiertos del Ajuntament (CKAN, recurso `google_transit.zip`) | ✅ Descarga 6,8 MB, vigente 3 oct–9 nov 2026 | Datos abiertos del Ajuntament de València (reutilización con atribución; términos en el portal de Gobierno Abierto) |
| Bus EMT València | **Minutos de espera en vivo por parada** | Servicio web público de la web de EMT (`MapUtilsPetitions.php?sec=getSAE`) | ⚠️ **No verificable desde el entorno de desarrollo** (la red lo bloquea) y **no es una API oficial documentada** | **Riesgo**: sin licencia explícita. Si EMT lo bloquea o lo prohíbe, la app cae automáticamente a horarios (ya implementado). Recomendado: pedir autorización por escrito (ver §Acciones) |
| Metrovalencia (FGV) | Horarios GTFS | `metrovalencia.es/google_transit_feed/google_transit.zip` con espejo en Mobility Database | ⚠️ Feed del operador **caducado** (último servicio 31-dic-2025). La app lo detecta y usa "día equivalente" marcándolo como horario aproximado | Publicado por el operador, sin licencia explícita |
| Metrovalencia | Tiempo real | — | ❌ No hay API abierta oficial | La app **no** hace scraping del operador; solo horarios |
| Cercanías (Renfe) | Horarios GTFS núcleo València (líneas `40T…`) | `ssl.renfe.com/ftransit/Fichero_CER_FOMENTO/fomento_transit.zip` | ✅ 13,9 MB | Renfe Data, datos abiertos (CC BY 4.0) |
| Cercanías (Renfe) | Retrasos/cancelaciones y avisos | `gtfsrt.renfe.com/trip_updates.json` y `alerts.json` | ✅ Funcionan (JSON GTFS-RT) | Renfe Data (CC BY 4.0) |
| Valenbisi | Bicis y huecos | Datos abiertos del Ajuntament (CKAN datastore `6afb29b7-…`) o JCDecaux con clave | ⚠️ No verificable desde el entorno (host bloqueado). Si CKAN falla, la app muestra el último dato guardado | Datos abiertos del Ajuntament; JCDecaux requiere clave gratuita (`JCDECAUX_KEY`) |
| Mapa | Teselas vectoriales | OpenFreeMap (OpenStreetMap + OpenMapTiles) | ✅ | ODbL + CC-BY: atribución visible en el mapa y en Licencias |

## Hallazgos que condicionan el producto
1. **No existe un feed de tiempo real oficial y abierto de EMT ni de Metrovalencia.** Solo Cercanías (Renfe) lo tiene. Por eso el diferenciador no es "tiempo real garantizado" sino: velocidad, **modo sin conexión**, widgets y transparencia (la app siempre indica si es directo o horario).
2. El feed GTFS de Metrovalencia lleva meses sin renovarse: el motor de horarios está preparado (calendario "día equivalente").
3. Avisos de bus y metro: no hay feed abierto → archivo propio `avisos/avisos.json` (curado a mano) + los de Renfe.

## Acciones para Álvaro (no puedo hacerlas yo)
1. **Escribir a EMT València** (atencioalaciutadania@emtvalencia.es, el contacto que consta en su propio GTFS) pidiendo autorización o una vía oficial para consultar los tiempos de paso en una app independiente gratuita. Guarda la respuesta. Si la deniegan, pon `Feeds.BUS_ESTIMATES` en vacío (la app seguirá funcionando con horarios).
2. Pedir a **FGV** (contacto en metrovalencia.es) el GTFS actualizado y, si existe, el acceso a tiempos de paso.
3. Añadir la **clave de JCDecaux** (gratis en developer.jcdecaux.com) si el dato de Valenbisi del Ajuntament no responde.

## Nombres y marcas
- La app no usa los nombres como marca de la app ni sus logos, solo texto factual (nombre de la red). La ficha y la app incluyen aviso de independencia.
- No se venden ni gestionan recargas de tarjetas de transporte.
