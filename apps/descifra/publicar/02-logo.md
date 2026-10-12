# Logo de Descifra

## Idea

Una hoja de papel con la esquina doblada. Arriba, una línea ondulada gris (el texto «cifrado» que no se entiende) y debajo líneas rectas azul tinta (el texto ya claro). Sobre la esquina inferior, un **sello bermellón con un check**: lo que Descifra te devuelve, la carta entendida y revisada. Fondo azul tinta, el de los bolígrafos de oficina; la hoja en color papel crema.

Colores: azul tinta `#1B2A41`, papel `#F4EFE6`, sello `#B8321F`, gris `#8A94A8`.

## Lo que ya está hecho

- `app/src/main/res/drawable/ic_launcher_foreground.xml` y `ic_launcher_background.xml`: icono adaptable de Android, más versión monocroma para los iconos temáticos de Android 13+.
- `assets/icono-512.png`: icono para la ficha de Play (512 x 512).
- `assets/icono-1024.png`: versión grande por si hace falta.
- `assets/grafico-funciones-1024x500.png`: gráfico de funciones.
- `make_assets.py` regenera los PNG dibujando la misma geometría que el vector.

## Prompt para generar una versión ilustrada con IA (opcional)

Úsalo en Midjourney, Ideogram, DALL·E o Firefly si quieres una variante más «de ilustrador» para anuncios o la web. El icono de la app conviene que siga siendo el vectorial de arriba.

```
Flat vector app icon, centered symbol on a deep navy blue (#1B2A41) rounded-square background with a subtle darker diagonal band at the bottom. The symbol is a cream paper letter (#F4EFE6) with a folded top-right corner. On the paper: one wavy grey-blue line (like unreadable scribble) above two straight navy lines (clear text). Overlapping the bottom-right corner of the paper, a vermilion red (#B8321F) circular rubber stamp with a thin cream inner ring and a bold cream checkmark. Minimal, geometric, no gradients, no shadows, no text, no letters, high contrast, crisp edges, friendly and trustworthy, Material Design adaptive-icon style, 1024x1024.
```

Prompt negativo: `text, letters, words, photo, 3d, gradient, shadow, glossy, clutter, watermark`.

### Variante para anuncios y vídeo (mascota opcional)

```
Flat illustration, warm cream background with faint ruled-paper lines. A friendly hand holding a phone that is scanning an official letter; on the phone screen the letter turns from scribbles into clear lines and a red stamp with a checkmark appears. Colour palette limited to navy #1B2A41, cream #F4EFE6, vermilion #B8321F and soft grey-blue. Editorial style, thick clean outlines, no text.
```

## Reglas de uso

- Margen mínimo alrededor del icono: el 25 % de su lado.
- No cambiar el color del sello: es el único rojo de la marca y significa «esto requiere tu atención».
- Sobre fondo claro usar la versión con fondo azul; nunca la hoja sin fondo.
