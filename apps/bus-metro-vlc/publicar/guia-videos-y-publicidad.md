# Guía de vídeos y publicidad

Objetivo: primeras 1.000 instalaciones orgánicas en 60 días, centradas en **UPV, UV, Erasmus y turistas**.

## 1. Mensaje central (una frase)
> **"Cuándo pasa tu bus o tu metro en València, sin esperas ni cuelgues — y también sin conexión."**

Tres ideas que se repiten en todo: ⚡ rápida · 📴 funciona sin datos · 🧩 widget en la pantalla de inicio.
Tono: cercano, con humor valenciano ("¿Otra vez esperando el 99 en la parada?"). Nunca ataques con nombre a EMT ni a Metrovalencia: compara con "apps que se cuelgan".

## 2. Vídeos (formato vertical 9:16, 15–30 s, subtítulos siempre)
Graba **capturas reales** de la app (emulador o móvil) y sube con subtítulos. Usa música libre (YouTube Audio Library / Pixabay).

| # | Título/gancho (primeros 2 s) | Guion visual | Cierre |
|---|---|---|---|
| 1 | "El 99 ya se ha ido… ¿o no?" | Abres app → favoritas con minutos en verde → widget en inicio | "Bus i Metro VLC · gratis en Google Play" |
| 2 | "Sin cobertura en el metro y aun así sé la hora" | Modo avión → la app sigue mostrando horarios | "Funciona sin conexión" |
| 3 | "Erasmus en València: cómo moverte" (EN) | Mapa → líneas → cerca de ti → Valenbisi | "Download free" |
| 4 | "De la UPV a la playa en 3 toques" | Buscar parada → favorita → widget | "Hecha por un valenciano" (si procede) |
| 5 | "Mi widget de cada mañana" | Widget de parada en 3 tamaños, resalta minutos | "Pro: widget con todas tus paradas" |
| 6 | Aviso de incidencia (reactivo) | Notificación de línea favorita → toca → aviso | "Te avisa solo de lo tuyo" |

Producción: 1 toma por vídeo, grabación de pantalla del emulador (Android Studio → Device Manager → "Record"), edición en CapCut / DaVinci Resolve (gratis). Exporta 1080×1920.
Hazlo en **valencià, castellano e inglés** (cambia subtítulos).

## 3. Canales y plan de 30 días
| Semana | Acción |
|---|---|
| 0 | Prueba cerrada en Play (mín. 12 testers 14 días si es cuenta personal nueva: pide ayuda a compañeros de clase). Crea cuentas @busimetrovlc en TikTok, Instagram y X. |
| 1 | Lanzamiento: vídeos 1-3 en TikTok/Reels/Shorts; post en r/valencia, r/vlc, r/Erasmus y r/spain con enlace honesto ("la he hecho yo, feedback bienvenido"; respeta las normas de cada sub). |
| 2 | Carteles QR (ver §5) en UPV (Camino de Vera), UV (Blasco Ibáñez, Tarongers), residencias, hostels; mensaje a delegaciones de estudiantes y a la oficina Erasmus/ESN València. |
| 3 | Grupos de Telegram/WhatsApp/Facebook de Erasmus Valencia, "Valencia Expats", "Estudiantes UPV". Pide reseñas (2 min) a quien la use. |
| 4 | Medios locales/blogs: nota de prensa corta (§6) a Las Provincias, Levante-EMV, Valencia Plaza, Culturplaza, blogs de transporte. Publica un hilo "Cómo moverte por València en 2026". |

## 4. Anuncios de pago (cuando haya reseñas ≥ 4,3★ y ≥ 20 reseñas)
- **Google Ads → campaña de app (UAC)**, sólo España, ubicación **València (radio 30 km)**, idiomas es/ca/en. Presupuesto de prueba **5 €/día × 14 días**; objetivo: instalación. Puja por CPI; pausa si CPI > 0,60 €.
- **Meta/Instagram Ads** con los vídeos 1-3, segmentación 18–30 años en València + intereses "Erasmus", "Universitat Politècnica de València".
- **TikTok Spark Ads** sobre el vídeo orgánico que mejor funcione (más barato que crear uno nuevo).
- Mide con las URLs de instalación con UTM de Play (`&referrer=utm_source%3Dtiktok%26utm_campaign%3Dlanzamiento`).

Textos de anuncio (máx. 90 car. cada uno):
1. "Cuándo pasa tu bus en València. Sin esperas. Gratis."
2. "Horarios de bus y metro sin conexión. Bus i Metro VLC."
3. "Your València bus & metro times — works offline. Free."
4. "El teu bus i metro a València, sempre al dia. Gratis."

## 5. Cartel / flyer (A5) — contenido
- Titular: **"¿Cuándo pasa mi bus?"**
- Subtítulo: "Autobús, metro y Cercanías de València. Funciona sin conexión."
- QR al enlace de Play (con UTM `utm_source=cartel`), logo `graficos/icono-512.png`, colores `#0B1F2A` y `#FF6B1A`, tipografía Space Grotesk.
- Pie: "App independiente · gratis" (cumple el aviso de no afiliación).

## 6. Nota de prensa (borrador)
> **Nace Bus i Metro VLC, una app valenciana que muestra cuándo pasa tu bus o tu metro incluso sin conexión**
> La aplicación, gratuita y sin registro, combina horarios abiertos de autobús, metro y Cercanías con tiempos reales cuando están disponibles, widgets para la pantalla de inicio y un mapa de toda la red. Está pensada para estudiantes y visitantes. [Cita del autor + enlace a Google Play + capturas].

## 7. Reseñas y comunidad
- Pide la reseña **dentro** de la app (ya está: una vez, tras 8 aperturas, con la API oficial de Play). Nunca pagues ni condiciones reseñas (infringe la política de Play).
- Responde todas las reseñas en 24–48 h; arregla lo reportado y avisa en la respuesta.
- Canal de feedback: issues de GitHub y el correo de la ficha.
- Mide: instalaciones, retención D1/D7/D30, % de Pro, valoración, "ANR/crash" en Play Console → Android vitals (objetivo: <0,5 % de ANR).

## 8. Reglas que NO romper
- No uses logos, colores corporativos ni nombres de operadores como reclamo ni en capturas editadas como si fuesen oficiales.
- No vendas ni muestres recargas de tarjetas de transporte (riesgo de política y legal).
- No prometas tiempo real donde no lo hay: la app ya lo distingue (verde = en directo).
