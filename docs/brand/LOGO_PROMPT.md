# Explorar logos alternativos con IA

El logo actual (`logo.svg`: anillo abierto + punto ámbar con halo) es
definitivo para el lanzamiento. Si quieres explorar alternativas, pega estos
prompts en Midjourney, DALL·E (ChatGPT) o Ideogram. Están en inglés porque
los generadores de imágenes rinden mejor así.

> Consejo: los generadores no dibujan bien vectores limpios. Úsalos para
> **encontrar la idea**; luego redibújala en SVG (Figma, Inkscape o pídemelo)
> antes de usarla en la app.

---

## Prompt 1 — Foco de escenario minimalista

```
Minimal flat vector app icon for a focus app called "Foco": a bold geometric spotlight made of a single warm amber disc (#FFB23F) casting a soft conical beam of light downward, on a deep near-black navy background (#0B0B12). Simple shapes only, perfectly centered, generous padding, no text, no letters, no gradients except a subtle glow, Android adaptive icon style, readable at 48px, Dieter Rams simplicity, high contrast --ar 1:1 --style raw --v 7 --no text, letters, words, 3d, photo, shadows
```

## Prompt 2 — Anillo y punto (la idea actual, variaciones)

```
Logo mark exploration sheet, 9 variations in a 3x3 grid: an open circular ring with a solid offset dot inside, like a lens finding its focus point, warm amber #FFB23F on near-black #0B0B12, thick uniform strokes with rounded ends, flat vector, geometric, Swiss design, each variation slightly different (gap position, dot size, ring thickness), no text, clean white space between items, suitable for an app icon readable at small sizes --ar 1:1 --style raw --v 7 --no text, letters, gradient mesh, 3d
```

## Prompt 3 — Luz que corta el ruido (más rebelde)

```
Flat vector app icon: a single bright amber dot of light (#FFB23F) cutting a clean diagonal path through a field of chaotic thin grey scribbles and fragmented lines representing social media noise, the area around the light is calm and empty, near-black background #0B0B12, bold, minimal, confident, slightly rebellious, centered composition, no text, no letters, no logos of real apps, readable at 48px, modern Android app icon --ar 1:1 --style raw --v 7 --no text, letters, photo, 3d, instagram, tiktok
```

**Ideogram**: quita la parte final (`--ar ... --no ...`), elige *Design*
como estilo, relación 1:1 y añade «no text» al final. **DALL·E / ChatGPT**:
quita también los parámetros con `--` y añade «square, flat vector icon».

Si una variante te gusta, pídele al generador versiones «as a single-color
silhouette, white on black» para comprobar que funciona como icono
monocromo: si deja de entenderse en blanco, descártala.

---

## Exportar a los tamaños de Google Play

Todo sale de un SVG cuadrado (lienzo 512×512, marca centrada). Necesitas:

| Archivo | Tamaño | Fondo | Notas |
|---|---|---|---|
| `docs/store/graphics/icon-512.png` | 512×512 | Noche, opaco | Icono de la ficha de Play. PNG de 32 bits, máx. 1 MB. Play redondea las esquinas: no las redondees tú. |
| `apps/mobile/assets/icon.png` | 1024×1024 | Noche, opaco | Icono general de Expo. |
| `apps/mobile/assets/android-icon-foreground.png` | 512×512 | Transparente | Marca dentro del **66 % central** (el anillo ocupa ~53 % del ancho). El launcher recorta el resto en círculo, *squircle*, etc. |
| `apps/mobile/assets/android-icon-background.png` | 512×512 | Noche sólido | Capa de fondo del icono adaptativo. |
| `apps/mobile/assets/android-icon-monochrome.png` | 432×432 | Transparente | Silueta blanca, sin halo (iconos temáticos de Android 13+). |
| `apps/mobile/assets/splash-icon.png` | 1024×1024 | Transparente | Marca a ~60 % del lienzo. |
| `apps/mobile/assets/favicon.png` | 48×48 | Placa Noche redondeada | Web. |
| `docs/store/graphics/feature-graphic.png` | 1024×500 | Noche | Sin transparencia; deja ~80 px libres en los bordes. |

### Cómo renderizarlos (sin Photoshop)

Con Chromium/Chrome sin interfaz, desde una página HTML que coloque el SVG
centrado al tamaño que toque:

```bash
# Ejemplo: icono 512 de Play
cat > /tmp/icon.html <<'EOF'
<html><body style="margin:0;width:512px;height:512px;background:#0B0B12">
<img src="file:///RUTA/AL/REPO/docs/brand/logo.svg"
     style="position:absolute;left:81px;top:81px;width:350px;height:350px">
</body></html>
EOF
chromium --headless --hide-scrollbars --window-size=512,512 \
  --screenshot=icon-512.png file:///tmp/icon.html
```

Para los de **fondo transparente** añade `--default-background-color=00000000`
y quita el `background` del `body`.

Alternativas sin terminal:

- **Figma** (gratis): importa el SVG, crea un *frame* del tamaño exacto,
  centra la marca y exporta como PNG 1×.
- **Inkscape**: Archivo › Exportar › PNG, fija ancho y alto en píxeles.
- **Android Studio** › *Image Asset* › *Launcher Icons (Adaptive and
  Legacy)*: carga `logo.svg` como *foreground*, pon `#0B0B12` de fondo y te
  enseña la zona segura en directo.

**Comprueba siempre**: abre el PNG, mira que las medidas son exactas
(`file icon-512.png`), que no hay bordes blancos y que a 48 px se entiende.
