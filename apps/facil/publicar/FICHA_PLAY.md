# Ficha de Google Play — Fácil

## Datos básicos
- **Nombre** (máx. 30): `Fácil: móvil para mayores`
- **Paquete:** `app.facil.launcher`
- **Categoría:** Herramientas (alternativa: Estilo de vida) · **Tipo:** Aplicación · **Gratis** con compra integrada (suscripción)
- **Idioma principal:** Español (España). Añadir traducción EN/PT/FR más adelante.
- **Email de contacto / política de privacidad:** ver `PRIVACIDAD.md` (alojar en una URL pública).

## Descripción breve (máx. 80)
`Móvil fácil para mayores: botones grandes, ayuda SOS y tu familia lo configura. Sin anuncios` → usa esta (79 car.):
**Botones grandes, ayuda SOS y tu familia lo configura a distancia. Sin anuncios.**

## Descripción completa
> **El móvil de tus padres o abuelos, sin complicaciones. Y sin anuncios.**
>
> Fácil convierte cualquier Android en un móvil pensado para mayores: una pantalla de inicio limpia, con el reloj enorme y solo los botones que de verdad usan.
>
> **PARA LA PERSONA MAYOR (gratis, sin anuncios, para siempre)**
> • Botones grandes: llamar a hijos y nietos con su foto, WhatsApp, cámara, fotos, linterna…
> • Botón rojo de AYUDA: se mantiene pulsado, avisa con una cuenta atrás (se puede cancelar) y llama a tu contacto de emergencia. El 112 siempre a un toque.
> • Recordatorios de pastillas y citas con botón "Hecho".
> • Dice la hora en voz alta. Letra grande, tres temas de color y alto contraste.
> • Ajustes con PIN: nadie lo desconfigura sin querer.
>
> **PARA LA FAMILIA (Fácil Familia, unos 12 € al año)**
> • Configura el móvil de tu padre o madre **desde el tuyo**: cambia botones, teléfonos, fotos y recordatorios sin ir a su casa.
> • Emparejamiento en segundos con un código QR.
> • Recibe avisos: batería baja, móvil sin usar en muchas horas, pastilla sin marcar, botón de ayuda pulsado.
>
> **PRIVACIDAD, SIN LETRA PEQUEÑA**
> • La persona mayor acepta a cada familiar y ve siempre qué comparte.
> • No vemos mensajes, llamadas ni ubicación. No usamos accesibilidad. Puedes borrar todo cuando quieras.
>
> Instala Fácil, ponlo como pantalla de inicio y deja que tu familia haga el resto.

## Etiquetas / palabras clave (ASO)
launcher para mayores, móvil fácil, móvil para abuelos, pantalla de inicio mayores, letra grande, botón SOS, ayuda familiar, controlar móvil de mis padres (¡ojo: Play penaliza "espiar"; usar "ayudar"), recordatorio pastillas, launcher sin anuncios.

## Notas de la versión 1.0
`Primera versión: pantalla de inicio para mayores, botón de ayuda, recordatorios y gestión por la familia.`

## Prompt para generar el logo con una IA de imágenes
> *Icono de aplicación móvil, cuadrado con esquinas redondeadas, estilo plano y moderno. Fondo en degradado naranja cálido (#EA580C a #B4300A). En el centro, una casa blanca muy simple y redondeada con un corazón naranja oscuro (#C2410C) recortado en su interior. Sensación de calidez, cercanía y confianza, sin texto, sin sombras duras, sin detalles pequeños; legible a 48 px. Vector, alta resolución, 1024×1024.*

Variantes: "versión monocromática blanca para icono temático", "gráfico de funciones 1024×500 con móvil mostrando botones grandes de colores, fondo crema".
El icono ya incluido (hecho en código) está en `assets/icono-512.png` y como icono adaptable dentro de la app.

## Gráficos
- Icono 512×512: `assets/icono-512.png` ✔
- Gráfico de funciones 1024×500: `assets/grafico-funciones-1024x500.png` ✔
- **Capturas de pantalla (hacer con un móvil real, 4–8):** 1) Inicio con reloj y 4–6 botones 2) Botón AYUDA con cuenta atrás 3) Ajustes de botones 4) Recordatorio con "Hecho" 5) Panel del familiar con la batería 6) Emparejar con QR 7) Tema alto contraste. Pon sobre cada una un titular corto: «Botones grandes», «Ayuda con un toque», «Tu familia lo configura», «Sin anuncios».

## Cuestionarios de Play Console
- **Clasificación de contenido:** utilidad, sin violencia ni UGC → PEGI 3.
- **Público objetivo:** adultos (18+). No dirigida a niños.
- **Anuncios:** No contiene anuncios.
- **Seguridad de los datos (Data safety):**
  - Recopila: *Información personal → Nombre* (de móvil y de familiar, opcional), *Info de la app y rendimiento → ninguno*, *Actividad en la app → "última vez usado"* (para alertas), *Salud y forma física → no*, *Ubicación → no*, *Mensajes/llamadas → no*, *Fotos → "foto de contacto reducida"* (solo dentro de la configuración).
  - Compartición con terceros: **No** (Firebase actúa como encargado).
  - Cifrado en tránsito: **Sí**. Posibilidad de pedir borrado: **Sí** (en la app y por email).
  - Propósito: *Funcionalidad de la app*. No publicidad.
- **Permisos sensibles / políticas:** no usa Accesibilidad, ubicación en segundo plano, SMS ni registro de llamadas. Declarar `CALL_PHONE` (funcionalidad principal: llamar al pulsar un botón) y, si Play lo pide, el vídeo de demostración de la función de ayuda.
- **Política de "stalkerware/monitorización":** la app sirve para ayudar a un familiar **con su conocimiento y consentimiento** (código QR + aceptación + notificación permanente + opción de retirar). Redactar la ficha con "ayudar", nunca "vigilar/espiar/rastrear".
- **Launcher:** la app se declara como pantalla de inicio (category HOME); describirlo en la ficha.
- **Suscripción:** producto `facil_familia` con dos planes base: `anual` (≈ 11,99 €/año, **prueba gratis 14 días**) y `mensual` (≈ 1,99 €/mes). Incluir en la descripción que se cancela cuando se quiera desde Play.
