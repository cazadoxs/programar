# Logo e identidad

## Icono incluido
El icono adaptativo ya está en la app (`app/src/main/res/drawable/ic_launcher_*.xml`): un código de barras cuyo último trazo se convierte en tenedor, cruzado por una línea de escaneo tomate sobre verde huerta. Versión PNG para la tienda: `grafica/icono-512.png`.

## Paleta
| Nombre | Hex | Uso |
|---|---|---|
| Huerta | `#1E4D2B` | Fondo, color principal |
| Crema | `#FBF6EC` | Fondo claro, trazos |
| Tomate | `#E2553A` | Línea de escaneo, acento |
| Mostaza | `#F0A92E` | Hidratos / avisos suaves |

Tipografía: Bricolage Grotesque (SIL OFL), pesos SemiBold y ExtraBold.

## Prompt para generar un logo alternativo con IA (Midjourney / Firefly / DALL·E / Ideogram)
```
Minimal app icon, flat vector style, rounded square, deep forest green background (#1E4D2B).
A cream-colored (#FBF6EC) barcode made of vertical bars where the last bars on the right form a fork.
A single horizontal tomato-red (#E2553A) scanning line crosses the barcode with rounded ends.
Centered, generous padding, no text, no gradients, no shadows, high contrast, friendly and modern.
Looks sharp at 48px. Square 1:1, 1024x1024.
```
Prompt negativo: `text, letters, photo, 3d, gradient, shadow, clutter, realistic food, watermark`.

Variantes a probar en las pruebas A/B de Play ("Experimentos de ficha"): (1) el actual, (2) tenedor + barras sobre fondo crema, (3) solo la línea de escaneo sobre un plato.

## Si generas uno nuevo
Exporta 1024×1024 sin esquinas redondeadas (Play las aplica), reduce a 512×512 PNG de 32 bits para la ficha. Para cambiar el icono dentro de la app hay que rehacer `ic_launcher_foreground.xml` (vectorízalo con Android Studio: Image Asset > Local file).
