# Monetización

Regla del plan: **el núcleo es gratis; se cobra por extras reales.**

## Modelo
| | Gratis | Pro (pago único) |
|---|---|---|
| Horarios sin conexión, tiempo real, mapa, líneas, avisos | ✅ | ✅ |
| Widget de una parada | ✅ | ✅ |
| Favoritas | hasta 5 | ilimitadas |
| Widget de favoritas (varias paradas) | ❌ | ✅ |
| Anuncios | un banner discreto abajo | ninguno (AdMob ni se inicializa) |

**Precio recomendado: 2,49 €** (rango del plan: 2–3 €). Con precios locales automáticos de Play. Prueba 1,99 € / 2,99 € con
"experimentos de precios" de Play Console cuando haya ~1.000 instalaciones.

## Implementación
- Compra: Google Play Billing 8, producto único `pro_unlock` (no consumible), reconocida (`acknowledge`) para evitar reembolso automático a los 3 días.
- El derecho se relee de Play en cada arranque y con "Restaurar compra", así que sigue a la cuenta de Google en otros móviles y tras reinstalar.
- Anuncios: AdMob, un **banner adaptativo anclado** (formato menos intrusivo; sin intersticiales ni pantallas completas). Consentimiento con Google UMP antes de pedir anuncios; contenido máximo G; no dirigido a niños.
- IDs de AdMob **no están en el repositorio**: se inyectan al compilar con `ADMOB_APP_ID` y `ADMOB_BANNER_ID` (ver `COMPILAR.md`). Por defecto usa los IDs de prueba de Google, que es lo correcto en desarrollo.

## Qué necesito de ti (solo tú puedes)
1. Cuenta de **Google Play Console** (25 USD una vez).
2. Cuenta de **AdMob** (gratis) → crear la app y una unidad de banner → pásame / guarda `ADMOB_APP_ID` y `ADMOB_BANNER_ID`.
3. Crear el producto `pro_unlock` en Play Console → Monetizar → Productos.
4. Perfil de pagos / datos fiscales (autónomo o persona física) para cobrar.

## Previsión realista (para decidir esfuerzo)
Estimación orientativa, no garantía: con 5.000 usuarios activos/mes, banner ≈ 0,5–1,5 €/mil impresiones y ~40 impresiones
por usuario-mes → 100–300 €/mes; conversión a Pro ~1–2 % → 50–100 compras → 100–250 € netos de comisión (15 % primer millón).
El crecimiento viene sobre todo del boca a boca universitario (UPV/UV/Erasmus): ver `guia-videos-y-publicidad.md`.

## Otras vías (fase 2, no implementadas)
- Propina opcional ("invítame a un café") como producto consumible.
- Patrocinio local no intrusivo (p. ej. una asociación de estudiantes) en la pantalla de Ajustes, sin rastreo.
