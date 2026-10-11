# Marca Foco

> **foco** = el foco de un escenario y el foco de la atención. Una luz cálida
> que corta el ruido. Tranquila, segura de sí misma y un poco rebelde contra
> el *doomscrolling*.

**Lemas** (por orden de preferencia):

1. **Recupera tu tiempo.** — principal (ficha de Play, feature graphic, bio).
2. **Lo importante, a la luz.** — secundario (web, vídeos, cierre de anuncios).
3. *Menos scroll, más vida.* — para redes y vídeos cortos, tono más desenfadado.

En inglés: **Take back your time.** / *Shine a light on what matters.*

---

## 1. Logo

| Archivo | Uso |
|---|---|
| [`logo.svg`](logo.svg) | Marca sola (icono, avatar de redes, favicon, marca de agua en vídeos). |
| [`logo-wordmark.svg`](logo-wordmark.svg) | Marca + «foco» en Papel. Para fondos oscuros (por defecto). |
| [`logo-wordmark-light.svg`](logo-wordmark-light.svg) | Marca + «foco» en Noche. Para fondos claros (Papel o blanco). |
| `apps/mobile/assets/*.png` | Iconos de la app ya exportados (Play, adaptativo, monocromo, splash, favicon). |
| [`../store/graphics/`](../store/graphics) | Icono 512 de Play y feature graphic (ES y EN). |

**La marca**: un anillo abierto con un punto sólido desplazado hacia la
abertura. El punto es la luz (tu atención); el anillo, lo que te rodea; la
abertura, la luz que se escapa del ruido. Un halo suave de ámbar la envuelve
en la versión a color. Se lee bien a 48 px y como silueta de un solo color.

- **Construcción** (lienzo 512×512): anillo de radio 186 y grosor 52 con
  extremos redondeados, abierto entre −80° y −10° (arriba a la derecha);
  punto de radio 80 centrado en (298, 214); halo radial centrado en el punto.
- **Zona de respeto**: alrededor de la marca, deja libre al menos el
  diámetro del punto (≈ 1/3 del anillo). En el *wordmark*, la misma medida
  alrededor de todo el conjunto.
- **Tamaño mínimo**: marca 16 px (sin halo por debajo de 24 px); *wordmark*
  96 px de ancho.
- **Monocromo**: anillo + punto en un solo color (blanco o Noche), sin halo.
  Es el que usa Android para el icono temático.
- El texto del *wordmark* es **Space Grotesk Bold** en minúsculas, con el
  interletrado ligeramente cerrado (−2 %) y convertido a trazados (no
  depende de tener la fuente instalada).

### Sí

- Marca ámbar sobre **Noche** (`#0B0B12`) o Superficie: es la combinación
  principal.
- Marca blanca o Noche, sin halo, cuando solo hay un color (camisetas,
  pegatinas, sellos, icono monocromo).
- Escribir el nombre como **Foco** en frases y **foco** (minúscula) solo en
  el logotipo.
- Dejar que respire: mucho espacio negro alrededor, como un foco en un
  escenario.

### No

- No rotar la marca ni mover la abertura a otro sitio.
- No cambiar el ámbar por otro color, ni ponerle degradados de colores,
  sombras duras, contornos o 3D.
- No poner la marca ámbar sobre fondos amarillos, naranjas o fotos
  recargadas (pierde contraste). Sobre fotos: placa Noche detrás.
- No estirar ni deformar; no recomponer el *wordmark* con otra fuente.
- No meter texto dentro del icono.
- No usar Brasa (rojo) para la marca: el rojo es solo para avisos y modo
  estricto.

---

## 2. Color

| Nombre | Hex | RGB | Uso |
|---|---|---|---|
| **Noche** | `#0B0B12` | 11 11 18 | Fondo principal de la app, iconos, gráficos. |
| **Superficie** | `#15151F` | 21 21 31 | Tarjetas, hojas, barra de pestañas. |
| **Superficie 2** | `#1E1E2B` | 30 30 43 | Tarjetas elevadas, campos de texto, chips. |
| **Línea** | `#2A2A3A` | 42 42 58 | Bordes y separadores finos. |
| **Papel** | `#F5F2EA` | 245 242 234 | Texto principal (blanco cálido, nunca `#FFF` puro en texto). |
| **Niebla** | `#A09DB0` | 160 157 176 | Texto secundario, etiquetas, placeholders. |
| **Foco** | `#FFB23F` | 255 178 63 | Color de marca y acción principal (botones, anillo del Pomodoro, enlaces). |
| **Brasa** | `#FF5B49` | 255 91 73 | Peligro, modo estricto, «parar sesión», errores. |
| **Brote** | `#43D9A3` | 67 217 163 | Éxito, rachas, tiempo recuperado. |
| **Cielo** | `#7AA7FF` | 122 167 255 | Calendario, información, eventos. |

**Proporción orientativa**: 70 % Noche/Superficies · 20 % Papel/Niebla ·
8 % Foco · 2 % el resto. El ámbar funciona porque es escaso: un solo
elemento ámbar protagonista por pantalla.

**Contraste** (sobre Noche): Papel 17,5:1 · Niebla 7,4:1 · Foco 10,9:1 ·
Brote 10,9:1 · Cielo 8,2:1 · Brasa 6,4:1 → todos pasan WCAG AA para texto.
Texto **sobre** un botón Foco: siempre Noche (`#0B0B12`), nunca blanco.

Tokens para código:

```ts
export const colors = {
  noche: '#0B0B12', superficie: '#15151F', superficie2: '#1E1E2B', linea: '#2A2A3A',
  papel: '#F5F2EA', niebla: '#A09DB0',
  foco: '#FFB23F', brasa: '#FF5B49', brote: '#43D9A3', cielo: '#7AA7FF',
};
```

---

## 3. Tipografía

| Fuente | Uso | Pesos |
|---|---|---|
| **Space Grotesk** | Titulares, cifras grandes (temporizador, minutos ahorrados), logotipo, textos de vídeos | Bold 700 (titulares), Medium 500 (subtítulos) |
| **Inter** | Cuerpo, botones, interfaz, textos legales | Regular 400, Medium 500, SemiBold 600 |

Ambas son gratuitas (Google Fonts, licencia OFL): se pueden usar en la app,
la web, los vídeos y la publicidad sin pagar nada.

- Titulares cortos, en frase normal (no TODO MAYÚSCULAS), interletrado −1 a
  −2 %.
- Cifras del temporizador en Space Grotesk con `font-variant-numeric:
  tabular-nums` para que no bailen.
- En CapCut/Canva: si no está Space Grotesk, súbela (descárgala de
  fonts.google.com) — no la sustituyas por Montserrat o Poppins.

---

## 4. Voz y tono

- **Tuteo, frases cortas, cero sermones.** Foco no te riñe: te echa un
  cable. «¿Seguro? Te quedan 12 minutos de foco» mejor que «¡No lo hagas!».
- **Honesto**: decimos qué hace y qué no (la IA
  usa tu clave; el uso de apps no sale del móvil).
- **Un poco rebelde**: el enemigo es el scroll infinito, no el usuario.
  «Instagram está diseñado para que no lo sueltes. Foco, para que sí.»
- Palabras de la casa: *foco*, *recuperar*, *a la luz*, *sesión*, *fricción*,
  *modo estricto*. Evitar: *dopamina detox*, *hustle*, *productividad
  extrema*, culpabilizar.

---

## 5. Aplicaciones rápidas

- **Avatar de redes**: marca a color sobre círculo Noche (exporta
  `apps/mobile/assets/icon.png`, que ya lo es).
- **Miniaturas y vídeos**: fondo Noche, titular en Space Grotesk Bold Papel
  con una palabra en Foco («llevas **3 horas** en reels»).
- **Capturas de la ficha de Play**: marco Noche, titular arriba en Papel con
  la palabra clave en ámbar, móvil centrado.
- **Pantalla de bloqueo de la app**: Noche, marca grande con halo, tarea
  actual en Papel, tiempo restante en Foco, botón de salir en Brasa con
  fricción.
