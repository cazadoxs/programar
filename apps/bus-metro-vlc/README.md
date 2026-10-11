# Bus i Metro VLC

Transporte público de València que funciona: **favoritas con próximos pasos, modo sin conexión, mapa de la red, widgets y avisos**.
App Android nativa (Kotlin + Jetpack Compose + MapLibre). Independiente: no está afiliada a EMT, FGV/Metrovalencia, Renfe ni Valenbisi.

## Qué hace
- **Inicio**: favoritas con los próximos pasos (verde = tiempo real, gris = horario), paradas cercanas, buscador por nombre o nº de parada.
- **Parada**: próximos pasos, horario completo del día (con selector de fecha y filtro por línea), accesibilidad, compartir.
- **Tiempo real**: autobús urbano (minutos por parada) y Cercanías (retrasos y cancelaciones). Si no hay datos en vivo cae a horario y lo dice.
- **Sin conexión**: los GTFS de EMT, Metrovalencia y Cercanías (núcleo València) se descargan y compilan en el móvil; se actualizan solos cada semana.
- **Mapa**: paradas, recorrido de cada línea, Valenbisi con bicis y huecos libres.
- **Widgets** (Jetpack Glance): parada (gratis) y favoritas (Pro).
- **Avisos**: Cercanías en vivo + `avisos/avisos.json` propio; notificación solo de las líneas favoritas.
- **Pro (pago único)**: sin anuncios, widget de favoritas, favoritas ilimitadas. Banner AdMob discreto en la versión gratuita con consentimiento UMP.
- es / ca / en, tema claro/oscuro, iconos temáticos, accesibilidad (descripciones, contraste de chips verificado).

## Arquitectura
```
core/   Kotlin puro (JVM) — GTFS → compilador → blobs por parada; calendario con "día equivalente";
        parsers de tiempo real (bus XML endurecido contra XXE, Renfe GTFS-RT JSON, Valenbisi); 14 pruebas + prueba "live" con feeds reales
app/    Android — Room (transit.db derivada, user.db con favoritas), WorkManager, Compose, Glance, Billing, AdMob/UMP, MapLibre
publicar/  Todo para Google Play: ficha, privacidad, logo, monetización, guía de vídeos y publicidad, seguridad
```
Decisiones clave: [`publicar/fuentes-de-datos.md`](publicar/fuentes-de-datos.md) (qué datos hay y qué riesgos) y [`publicar/seguridad.md`](publicar/seguridad.md).

## Documentación
| | |
|---|---|
| Compilar / firmar / publicar | [COMPILAR.md](COMPILAR.md) |
| Ficha de Play Store | [publicar/ficha-play-store.md](publicar/ficha-play-store.md) |
| Monetización | [publicar/monetizacion.md](publicar/monetizacion.md) |
| Vídeos y publicidad | [publicar/guia-videos-y-publicidad.md](publicar/guia-videos-y-publicidad.md) |
| Capturas | [publicar/guia-capturas-y-video.md](publicar/guia-capturas-y-video.md) |
| Logo y prompt | [publicar/logo-prompt.md](publicar/logo-prompt.md) |
| Seguridad | [publicar/seguridad.md](publicar/seguridad.md) |
| Privacidad | [publicar/politica-privacidad.md](publicar/politica-privacidad.md) |

Licencia de la tipografía Space Grotesk: [licenses/SPACE_GROTESK_OFL.txt](licenses/SPACE_GROTESK_OFL.txt).
