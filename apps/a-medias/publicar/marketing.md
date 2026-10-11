# Marketing de A medias: vídeos y publicidad

## Posicionamiento (una frase)

> **Las cuentas claras, a medias.** Splitwise te limita a 3 gastos al día y
> Tricount ahora te lleva a un banco. A medias es gratis, sin límites, sin
> cuenta y sin banco detrás.

Tres promesas que se repiten en todo: **Sin límites · Sin cuenta · Sin banco detrás.**

**Público** (por orden): estudiantes en piso compartido (18–26) → grupos de
viaje y festivales (22–35) → parejas que viven juntas (25–40).
**Motor de crecimiento**: cada grupo trae a 3–8 personas nuevas. El mejor
marketing es el botón «Invitar»: mensaje de invitación claro, entrada sin
registro y que la persona que entra vea valor en 10 segundos.

## Vídeos cortos (TikTok / Reels / Shorts) — guiones de 15–30 s

Formato vertical 9:16, subtítulos grandes siempre (se ve sin sonido), primer
segundo = gancho, cierre con «A medias, gratis en Google Play». Grabar con
pantalla real del móvil + una persona hablando a cámara.

**1. «El límite de 3 gastos» (gancho de comparación)**
- 0–2 s, a cámara: «Apuntar el cuarto gasto del día en la app de gastos… cuesta dinero.»
- 2–10 s, pantalla: la app de la competencia pidiendo esperar/pagar (recreación sin marca visible, o con voz «otra app»).
- 10–22 s, pantalla de A medias: apuntar 5 gastos seguidos, sin pausa, contador subiendo.
- 22–28 s: «A medias: sin límites, sin cuenta. Gratis en Google Play.»

**2. «Piso compartido: quién debe qué» (relatable)**
- 0–3 s: tres compañeros mirando la factura de la luz. «¿Quién pagó el súper el martes?»
- 3–12 s: cada uno discute (humor). Corte.
- 12–25 s: pantalla de Saldos: «Dani te debe 425,33 €». Botón «Saldar», Bizum.
- 25–30 s: «Dejad de discutir. A medias.»

**3. «Viaje con 8 amigos» (el más compartible)**
- Montaje rápido de fotos de viaje; texto: «8 personas, 5 países, 3 monedas, 0 discusiones».
- Pantalla: gasto en dólares → tipo de cambio automático → saldo final.
- Cierre: «Una app, sin registro. Mándales el enlace.»

**4. «Escanea el tique» (demo de Plus)**
- 0–3 s: tique de bar arrugado. «¿Pasar esto a mano? No.»
- 3–15 s: foto → importe, comercio y fecha rellenos. Cámara lenta del autorrelleno.
- 15–25 s: reparte entre 4 → listo. «Plus: un pago, para siempre.»

**5. «Sin cuenta» (el diferenciador de enlaces)**
- Pantalla dividida: tú crea el grupo; tu amigo abre el enlace (en iPhone, navegador) y ya está dentro.
- Texto: «Tu amigo no se registra. Ni instala nada.»

**6. «Bizum en un toque» (muy español)**
- Ramón mira «Debes 23,40 €». Toca → copiar importe → abre Bizum → pega. «Y marcado como pagado.»

**7. Serie «Cuentas de piso»** (recurrente, 1 por semana): anécdotas absurdas de gastos
de piso (el kilo de lentejas de 14 €, la regleta, la batidora) como contenido de comunidad.
Pide en comentarios historias de «gasto más raro» y respóndelas con vídeo.

Reglas de producción: gancho en <2 s · una idea por vídeo · mostrar la app
funcionando antes de los 5 s · subtítulos · sin música con copyright (usa la
biblioteca de la plataforma) · el mismo cierre y logo en todos.

## Calendario de lanzamiento (8 semanas)

| Semana | Foco |
|---|---|
| −2 a 0 | Prueba cerrada de 14 días con 12+ testers (amigos, compañeros). Recoge fallos y 5 testimonios cortos. |
| 1 | Lanzamiento en España. Publica 1 vídeo diario durante 7 días (los guiones 1–6). Post en r/spain, r/es, r/SpainFinance y grupos de Facebook de pisos compartidos de tu ciudad (con la historia honesta del porqué, no solo el enlace). |
| 2 | Pide reseñas dentro del flujo (ver abajo). Responde todas las reseñas. Product Hunt (versión en inglés de la ficha como segunda localización). |
| 3–4 | Universidades: carteles con QR en residencias/comedores (A4: «¿Quién debe a quién? Sin registro. QR»). Micro-influencers de vida universitaria y viajes (canjear Plus gratis, no pagar). |
| 5–6 | Experimentos de ficha en Play Console (título, icono, captura 1). Lanza el vídeo de viajes antes de vacaciones/puentes. |
| 7–8 | Publicidad de pago si el coste por instalación es sostenible (ver abajo). Localización a catalán, euskera, gallego, inglés, portugués, italiano y mexicano. |

## Qué hacer dentro de la app para crecer

- **Invitación con valor**: el mensaje ya incluye el nombre del grupo; probar variantes («Cuentas del piso, entra sin registrarte»).
- **Pedir reseña en el momento bueno**: tras el 5.º gasto apuntado o tras marcar un pago como saldado, con la API `in_app_review`. Nunca antes de la 1.ª semana de uso ni tras un error.
- **Pantalla de «todo saldado»**: es el momento de mayor satisfacción; añade botón «Compartir logro / Invitar a otro grupo».
- **Web con vista previa**: la página de enlaces (`/unirse`) es la cara para quien no tiene la app: mostrar qué es A medias en 3 líneas y botón de Google Play (para quien tiene Android).

## Publicidad de pago (cuando haya producto estable)

Objetivo: instalaciones de **grupos que se activan** (crean grupo + 3 gastos), no solo instalaciones.
Regla: parar cualquier campaña cuyo coste por usuario activado supere ~1,5 € (la compra única de 3,49 € la pagan pocos; el valor está en el crecimiento por invitación).

| Canal | Cómo | Presupuesto de prueba |
|---|---|---|
| **Google App Campaigns (UAC)** | Campaña de instalaciones Android, España, 18–35. Usa 4 textos + las capturas + los vídeos 1, 2 y 3 recortados a 15 s. Optimizar a evento «grupo creado» una vez haya 50 conversiones. | 5–10 €/día × 14 días |
| **TikTok Ads (Spark Ads)** | Impulsar los vídeos orgánicos que mejor funcionen (≥ 5 % de retención a 15 s). | 5 €/día × 7 días por vídeo |
| **Meta (Instagram Reels)** | Mismo vídeo, público: estudiantes y 22–30 interesados en viajes. | 5 €/día |
| **Apple/iOS** | No aplica (solo Android); los iPhone entran por la web. | — |

Textos de anuncio (probar A/B):
1. «Gastos compartidos sin límites. Sin cuenta. Gratis.»
2. «Tu piso, tu viaje, tus cuentas. A medias.»
3. «Splitwise te limita. A medias no.» *(solo si la plataforma permite nombrar marcas; si no, «Esa app te limita los gastos al día. Esta no.»)*
4. «Mándales un enlace. Ya están dentro.»

## Métricas a vigilar

| Métrica | Meta primer trimestre |
|---|---|
| Conversión ficha → instalación (Play Console) | ≥ 30 % |
| Grupos creados por 100 instalaciones | ≥ 60 |
| Personas que entran por enlace por grupo | ≥ 2,5 |
| Retención día 7 | ≥ 25 % |
| Valoración media | ≥ 4,5 |
| Conversión a Plus | 2–4 % de usuarios activos |
| Fallos (Android vitals) | < 1 % de sesiones |

## Kit de prensa y comunidad

- Una frase: «A medias es la app de gastos compartidos sin límites, sin cuenta y sin banco detrás.»
- Historia honesta de por qué existe (la app de siempre te limita y la otra ahora es de un banco).
- Enviar a: blogs de apps y ahorro (Xataka, Genbeta, Hipertextual, Maldita Tecnología), newsletters de viajes y a estudiantes (Erasmus), subreddits y grupos de pisos.
- Ofrecer códigos promocionales de Plus (Play Console → Promociones → Códigos promocionales) a quien la reseñe.

## Aviso legal

No uses los logotipos ni las capturas de la competencia en los anuncios;
cuando compares, hazlo con hechos verificables y fecha (los límites de otras
apps cambian) o evita nombrar marcas.
