# Logo

## Icono incluido (listo para usar)
`graficos/icono-512.png` — icono de tienda 512×512. En la app es el icono adaptativo vectorial
(`app/src/main/res/drawable/ic_launcher_foreground.xml`, con versión monocroma para iconos temáticos de Android 13+).

**Concepto:** una "V" (València · Valencià) formada por una línea de ruta con tres paradas; el punto naranja de abajo es
"tu parada" y los dos arcos son la señal de tiempo real. Fondo azul tinta `#0B1F2A`, naranja "taronja" `#FF6B1A`, crema `#FFF7EC`.
No usa ningún símbolo, color ni forma de EMT, Metrovalencia, Renfe o Valenbisi.

## Prompt para generar variantes con una IA de imágenes
```
App icon, flat vector, square, no text. A bold letter "V" drawn as a transit route line
with rounded ends: three circular stops (two at the top tips, one larger glowing orange stop
at the bottom vertex). Two small concentric arcs next to the top-right stop suggesting a live
signal. Colors: deep sea-ink navy #0B1F2A background, cream #FFF7EC lines, vivid orange #FF6B1A
accent. Minimal, geometric, generous margins, centered, high contrast, modern Mediterranean
feel, subtle sense of València. Soft flat style, no gradients, no shadows, no letters, no
transit-company logos. 1024x1024.
```
Variantes que merece la pena probar: (a) "V" dentro de un círculo naranja; (b) V + silueta de las Torres de Serranos o el
Micalet muy simplificada; (c) V hecha con la forma de una naranja con hoja.
Elige la que mejor se lea a 48 px. Antes de sustituir el icono, comprueba que **no se parece** a logos de apps de transporte existentes.

## Si la IA lo genera como PNG
1. Redimensiona a 512×512 (PNG de 32 bits, sin transparencia, esquinas cuadradas: Play aplica la máscara).
2. Para el icono de la app vuelve a vectorizar (Figma/Illustrator "Image trace") y sustituye `ic_launcher_foreground.xml`.
