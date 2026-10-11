# Logo de Apunta

## Logo que ya está hecho (listo para usar)
- Vectorial dentro de la app: `app/src/main/res/drawable/ic_launcher_foreground.xml` y `…_background.xml` (icono adaptable + versión monocroma para iconos temáticos de Android 13+).
- PNG de tienda: `grafico/icono-512.png`.

**Idea:** una hoja de cuaderno (fondo crema con renglones suaves) con el **margen rojo** del cuaderno y **cinco barras de onda de voz**; la del centro es roja: *la voz que se convierte en apunte*. Paleta: papel `#F6F1E7`, tinta `#1C1A16`, rojo margen `#D9412E`.

## Prompt para generar variantes con una IA de imágenes
Úsalo en Midjourney, DALL·E, Ideogram, Firefly o Canva *Magic Media*. Pide 4 variantes y elige.

```
App icon for an Android app called "Apunta" (Spanish for "note"), a private offline voice recorder and transcriber.
Concept: a notebook page — warm cream paper (#F6F1E7) with faint ruled lines and a single vertical red margin line (#D9412E) — and five rounded vertical sound-wave bars in the center, four in near-black ink (#1C1A16) and the tallest middle bar in red, symbolising a voice turning into a written note.
Flat vector style, bold simple geometry, perfectly centered, generous safe margin, readable at 48 px, no text, no gradients, no shadows, no photo realism, no microphone clichés, no cloud symbols.
Square 1024x1024, solid background, high contrast.
```

**Negativos (si la herramienta los admite):** `text, letters, gradient, 3d, glossy, cloud, headphones, stock photo, watermark, busy background`.

## Variantes que merece la pena probar
1. **Oscura:** mismo diseño sobre tinta `#15140F` con barras crema y la central en rojo `#FF6B55`.
2. **Solo la «A»:** una «A» tipo serif (Fraunces) cuyo travesaño es una onda de voz.
3. **Lápiz-onda:** la punta de un lápiz dibujando una onda sinusoidal en lugar de una línea.

## Cómo sustituir el icono si eliges otro
1. Genera el PNG 1024×1024.
2. En Android Studio: *New → Image Asset* → *Launcher Icons (Adaptive)* con el PNG como foreground y fondo `#F6F1E7`.
3. Para Play Console, exporta 512×512 PNG de 32 bits (≤ 1 MB).
