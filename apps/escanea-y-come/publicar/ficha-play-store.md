# Ficha de Google Play — Escanea y come

Todo listo para copiar y pegar en Play Console (Crecimiento > Presencia en la tienda > Ficha principal).

## Datos básicos
- **Nombre (máx. 30):** `Escanea y come: calorías`
- **Descripción corta (máx. 80):** `Cuenta calorías escaneando el código de barras. El escáner es gratis, siempre.`
- **Categoría:** Salud y bienestar (alternativa: Comida y bebida)
- **Etiquetas:** contador de calorías, escáner de código de barras, macros, dieta, Nutri-Score
- **Idioma por defecto:** Español (España)
- **Correo de contacto:** (el tuyo) · **Web:** el README del repositorio sirve al principio
- **Política de privacidad:** URL pública de `politica-de-privacidad.md` (en GitHub: la ruta "raw" o la vista del archivo)

## Descripción larga (máx. 4000)
```
Apunta al código de barras y sabrás qué te estás comiendo. Sin pagar por escanear.

Muchas apps de calorías cobran por el escáner. Aquí no: escanear, anotar, ver macros, Nutri-Score, NOVA y alérgenos es gratis para siempre. Sin cuenta, sin anuncios.

📷 ESCÁNER GRATIS Y RÁPIDO
Funciona con el escáner de Google: sin permisos de cámara y en un segundo. Productos de Mercadona, Lidl, Consum, Carrefour, Dia y de cualquier marca europea gracias a Open Food Facts, la base de datos libre y colaborativa.

⚡ ANOTAR EN DOS TOQUES
Escaneas, ajustas la ración y listo. Desayuno, comida, merienda, cena y picoteo: los momentos del día como se comen aquí. Copia lo de ayer, guarda tus comidas de siempre, marca favoritos.

🥗 LO QUE DE VERDAD IMPORTA
Calorías, proteínas, hidratos y grasas. Nutri-Score, grado de procesado NOVA y alérgenos de la UE (gluten, leche, huevo, frutos secos…) para ver de un vistazo si un producto te conviene.

🛠 ¿NO ESTÁ TU PRODUCTO?
Créalo copiando la etiqueta en 20 segundos. Se guarda en tu móvil y tiene prioridad sobre la base de datos.

💧 Y MÁS
Agua, peso con tendencia suavizada, modo oscuro, gráficas semanales.

🌿 MODO TRANQUILO
Si contar números no te sienta bien, ocúltalos y registra solo qué comes. La app no te regaña, no pone rojo si te pasas y no empuja dietas extremas.

🔒 TUS DATOS SON TUYOS
Todo se queda en tu móvil. Lo único que sale es el código de barras que escaneas, hacia Open Food Facts. Sin registro ni rastreadores.

💚 PLUS: UN SOLO PAGO
Si quieres ir más a fondo: estadísticas de 30 y 90 días, qué alimentos te dan más proteína, reparto del día, exportar a Excel y colores extra. Un pago único, sin suscripción. Lo básico no se bloquea nunca.

Información orientativa; no sustituye el consejo de un médico o dietista. Datos de producto de Open Food Facts (ODbL).
```

## Qué hay de nuevo (v1.0.0)
`Primera versión: escáner gratis, diario, macros, Nutri-Score, alérgenos y modo tranquilo.`

## Gráficos
| Recurso | Archivo | Tamaño |
|---|---|---|
| Icono | `grafica/icono-512.png` | 512×512 |
| Gráfico de funciones | `grafica/grafico-funciones-1024x500.png` | 1024×500 |
| Capturas de teléfono | las haces desde el móvil (guía en `guia-capturas.md`) | mín. 2, 1080×1920 recomendado |

## Formulario de contenido de la app
- **Clasificación de contenido (IARC):** sin violencia, sin contenido sexual, sin juego, sin compras de usuarios entre sí → PEGI 3 / Todos.
- **Anuncios:** No contiene anuncios.
- **Público objetivo:** 18+ (la app es para adultos; así evitas requisitos de Familias).
- **App de salud (Play):** Declarar "Nutrición y control de peso". Marcar que **no** es una app médica ni diagnostica; incluir el aviso "orientativa, no médica". No accede a Health Connect.
- **Seguridad de los datos:** "No recopila datos" ni los comparte con terceros. Matiz honesto: el código de barras escaneado se envía a Open Food Facts (se trata como tráfico de red anónimo, sin identificador). Cifrado en tránsito: Sí (HTTPS). Los usuarios pueden pedir borrar datos: Sí (Ajustes > Borrar todos mis datos).
- **Permisos:** solo INTERNET y estado de red. El escáner de ML Kit no necesita CAMERA.

## Compra integrada (Monetizar > Productos > Productos integrados)
- **ID del producto:** `plus_para_siempre` (debe coincidir con `BillingManager.PRODUCT_ID`)
- **Nombre:** `Escanea y come Plus`
- **Descripción:** `Estadísticas de 30 y 90 días, ranking de alimentos, exportar a CSV y colores extra. Un pago, para siempre.`
- **Precio sugerido:** 4,99 € (alternativas a probar: 3,99 € / 5,99 €). Activa precios locales por país.

## Textos para otras tiendas de lanzamiento (opcional)
- Reddit r/spain, r/Dietetica, r/Android: "Hice una app de calorías con el escáner gratis (porque MyFitnessPal lo movió a Premium)". Sé honesto: soy el desarrollador.
