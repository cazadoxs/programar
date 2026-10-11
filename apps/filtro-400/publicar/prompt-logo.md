# Logo: prompts para generador de imágenes

El icono actual (`graficos/icono-512.png` y los vectores de `app/src/main/res/drawable/`) ya está hecho y es el que usa la app. Estos prompts sirven si quieres una versión ilustrada para anuncios o para explorar alternativas.

## Concepto

Un escudo naranja de señal sobre fondo tinta casi negra, con «400» dentro y una barra diagonal que lo tacha, como una señal de prohibido. Reconocible a 48 px, funciona en blanco y negro, y comunica en un vistazo «el 400 no pasa».

Paleta: tinta `#0E1015`, naranja señal `#FF5A36`, papel `#F4F0E6`.

## Prompt principal (icono de app)

```
Minimalist Android app icon, flat vector style. A bold shield shape in vivid signal orange (#FF5A36) centered on a near-black ink background (#0E1015). Inside the shield, the number "400" in a heavy geometric monospace typeface, dark ink color, crossed out by a single thick diagonal slash from bottom-left to top-right, like a prohibition sign. The slash has a thin cream (#F4F0E6) outline. No gradients, no shadows, no text other than the 400, no phone, no bell. Perfectly symmetrical shield, generous padding (safe zone 66% of canvas), crisp edges, reads clearly at 48x48 pixels. Square 1024x1024.
```

**Negativo:** `gradients, 3d, glossy, drop shadow, realistic, telephone handset, phone receiver, lots of text, tiny details, watercolor, stock photo`

## Variantes para probar

1. **Solo el escudo con barra, sin cifras** (más limpio a tamaños pequeños):
```
Flat vector app icon, orange shield (#FF5A36) on near-black (#0E1015), a single thick diagonal slash across the shield, cream outline on the slash, minimalist, no text, centered, 1024x1024.
```
2. **Teléfono tachado dentro del escudo** (más literal):
```
Flat vector app icon, orange shield on near-black background, a simple phone handset silhouette in dark ink inside the shield, crossed by a thick diagonal prohibition slash, minimalist, bold geometric shapes, no gradients, 1024x1024.
```
3. **Modo claro** (para anuncios sobre fondo claro):
```
Same shield-with-400-and-slash emblem, orange shield and ink-black digits on a warm cream background (#F4F0E6), flat vector, centered, lots of whitespace.
```

## Ilustración de campaña (opcional)

```
Editorial illustration, flat colors, near-black background with a faint grid. A hand holding a smartphone; from the phone's screen an orange shield rises with "400" crossed out, while small crumpled speech bubbles with phone numbers beginning "400" bounce off it. Palette limited to ink #0E1015, orange #FF5A36 and cream #F4F0E6. Playful but clean, 16:9.
```

## Si cambias el icono

Sustituye `ic_launcher_foreground.xml` (y `ic_launcher_monochrome.xml` para el icono temático de Android 13+) y el PNG de 512 px de la ficha. Play exige 512×512 PNG de 32 bits, máx. 1 MB, sin esquinas redondeadas (Play las aplica).
