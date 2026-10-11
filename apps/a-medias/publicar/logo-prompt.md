# Logo de A medias

## El logo actual

Ya hay un icono hecho y puesto en la app (Android, web y Play Store):
una **moneda partida en dos mitades** (crema y amarilla) que se deslizan en
diagonal, con trazo grueso oscuro y sombra dura sobre fondo tomate `#FF5A36`.
«A medias» = partido por la mitad. Es un icono de formas planas: se lee
bien a 48 px y funciona en versión monocroma (Android 13 «iconos con temas»).

Archivos:
- `graficos/icono-play-512.png` — icono de la ficha de Play (512×512, sin transparencia)
- `graficos/icono-1024.png` — maestro
- `graficos/grafico-funciones-1024x500.png` — cabecera de la ficha
- Android: `android/app/src/main/res/mipmap-*` (+ adaptativo con monocromo)
- Web: `web/icons/`, `web/favicon.png`

Paleta: tomate `#FF5A36` · amarillo `#FFC94A` · crema `#FFF7EC` · tinta `#1C1828`.
Tipografías: Bricolage Grotesque (títulos) e Instrument Sans (texto), ambas con licencia OFL.

## Prompt para un generador de imágenes (si quieres explorar alternativas)

```
Flat vector app icon for a Spanish shared-expenses app called "A medias"
("split in half"). A round coin cut in two halves along a diagonal line, the
two halves slightly offset sliding apart in opposite directions along the cut.
Left/top half cream (#FFF7EC), right/bottom half warm yellow (#FFC94A), both
with a thick rounded near-black outline (#1C1828) and a hard offset drop shadow
in the same dark colour, neo-brutalist sticker style. Solid vivid tomato-red
(#FF5A36) square background. Centered, lots of padding, no text, no gradients,
no glow, no 3D, no realistic coin details, no currency symbol. Bold simple
shapes that stay readable at 48x48 px. 1024x1024, square, no rounded corners.
```

Variantes para probar (cambia solo la última línea del concepto):
- Con una **cuña de pizza/tarta partida** en dos en vez de una moneda.
- Con un **ticket** (recibo) rasgado por la mitad en diagonal.
- Con dos **burbujas de diálogo** que forman una moneda al juntarse.

Negativo: `text, letters, watermark, gradient, 3d render, photo, glossy, euro sign, dollar sign, shadows with blur`.

## Cómo usar un icono nuevo

1. Guarda el maestro en `graficos/icono-1024.png`.
2. Genera los mipmaps con Android Studio (*Image Asset*) o `flutter_launcher_icons`.
3. Para el icono adaptativo, el primer plano debe estar dentro del círculo central del 66 %.
4. Sube el de 512 px a Play Console.
