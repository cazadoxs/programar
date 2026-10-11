# Ficha de Google Play · Filtro 400

Todo lo de aquí se copia y pega en Play Console. Los límites de caracteres son los de Play.

## Datos básicos

| Campo | Valor |
|---|---|
| Nombre (máx. 30) | `Filtro 400: antispam llamadas` (29) |
| Resumen corto (máx. 80) | `Bloquea el prefijo 400 y el spam telefónico. No subimos tu agenda.` (66) |
| Categoría | Herramientas (alternativa: Comunicación) |
| Etiquetas | Bloqueador de llamadas, Seguridad, Privacidad |
| Idioma | Español (España) |
| Precio | Gratis, con suscripción dentro de la app |
| Anuncios | No contiene anuncios |
| Clasificación de contenido | Para todos los públicos (cuestionario IARC: sin violencia, sin compras de azar, sin contenido de usuarios) |
| Correo de contacto | *(el tuyo)* |
| Política de privacidad | URL pública de `politica-privacidad.md` (ver `README.md` para publicarla gratis) |

## Descripción completa (máx. 4000)

```
Desde el 17 de octubre de 2026, las empresas solo pueden llamarte para venderte algo desde números que empiezan por 400. Filtro 400 los corta antes de que suene el teléfono. Y lo hace sin pedirte la agenda.

CORTA EL 400 DE UN TOQUE
• Bloquea, silencia o deja sonar todo el rango 400, el reservado a la venta telefónica.
• Reglas listas para otros prefijos molestos: 803, 806, 807, 905 (tarificación adicional), 901 y 902, y números extranjeros.
• Crea tus propios prefijos: todo lo que empiece por 91, por +44 o por lo que quieras.

NUNCA SUBIMOS TU AGENDA
• No pedimos permiso de contactos, ni de registro de llamadas, ni de estado del teléfono.
• Tus contactos siempre suenan: Android ni siquiera los pasa al filtro.
• Todo se decide en tu móvil con tus reglas. Sin cuenta, sin registro, sin anuncios.

LLAMADAS ESPERADAS
¿Esperas al repartidor, al centro de salud o al taller? Pulsa "Espero una llamada" y deja pasar números desconocidos durante 1 hora, 3 horas, el día o 3 días. Se desactiva solo. El 400 sigue cortado, porque un repartidor o un médico no llaman desde ahí.

PRUEBA UN NÚMERO
Escribe cualquier número y mira qué haría el filtro y por qué, sin esperar a que te llamen.

HORARIO DE SILENCIO
Silencia las llamadas de desconocidos por la noche o los fines de semana. Tus contactos y tus números permitidos siempre suenan.

HISTORIAL PRIVADO
Ve qué llamadas se han cortado o silenciado y por qué. Permite o bloquea un número desde ahí con un toque. El historial vive solo en tu teléfono y se borra solo (7, 30 o 90 días).

AJUSTES RÁPIDOS
Enciende o apaga el filtro desde el panel de ajustes rápidos, sin abrir la app.

GUÍA ANTISPAM
Te explicamos paso a paso cómo apuntarte a la Lista Robinson, cómo pedir a tu operador que desconecte el 400 y cómo reclamar ante la AEPD. Gratis, para que no dependas solo de una app.

COMUNIDAD (PREMIUM, OPCIONAL)
• Lista de números denunciados por otros usuarios, actualizada cada día: pilla a quienes llaman desde móviles y fijos normales para saltarse el 400.
• Solo entran números denunciados por varias personas distintas.
• La lista se descarga y se consulta en tu teléfono: el servidor nunca sabe quién te llama.
• Denunciar un número es gratis para todos y anónimo: se envía solo ese número y la categoría.

GRATIS DE VERDAD
El filtrado local es gratis para siempre y sin límites: no hay "bloqueos gratis hasta X". Premium solo paga el mantenimiento de la lista de la comunidad. Sin anuncios en ninguna versión.

CÓMO FUNCIONA
Filtro 400 usa la función oficial de Android para apps de identificación de llamadas y spam. Al abrirla, Android te pregunta si quieres que Filtro 400 sea tu app de filtrado. Es el único permiso que necesita. Puedes cambiarlo cuando quieras en Ajustes.

LIMITACIONES HONESTAS
• Android no entrega al filtro las llamadas con número oculto ni las de tus contactos.
• Una llamada comercial ilegal desde un número que no es 400 solo se cortará si coincide con tus reglas o con la lista de la comunidad.
• Filtro 400 no es asesoramiento legal.

Hecho en España, para la ley española.
```

## Suscripción en Play Console

Monetización → Productos → Suscripciones → Crear suscripción.

| Campo | Valor |
|---|---|
| ID del producto | `comunidad` (debe ser exactamente este: lo usa el código) |
| Nombre | Filtro 400 Premium |
| Descripción | Lista de la comunidad actualizada a diario. Sin anuncios. |
| Plan base 1 | ID `mensual` · facturación mensual, renovación automática · 0,99 € |
| Plan base 2 | ID `anual` · facturación anual, renovación automática · 7,99 € |
| Oferta de prueba | En el plan anual: prueba gratuita de 7 días para suscriptores nuevos |
| Periodo de gracia | 7 días · recuperación de cuenta: 30 días |

Precios por país: deja que Play los convierta desde el euro.

## Capturas

Mínimo 2, recomendado 6, 1080×1920 (9:16). Orden y texto superpuesto sugerido:

1. **Escudo** (protegido, contadores, gráfica): «Que llame quien tú quieras»
2. **Reglas** (selector bloquear/silenciar/sonar del 400): «El 400, fuera de un toque»
3. **Llamada esperada** (diálogo abierto): «¿Esperas al repartidor? Déjalo pasar»
4. **Historial** (lista con motivos): «Sabes qué se cortó y por qué»
5. **Probar un número** (resultado «Bloquear · Comercial (400)»): «Pruébalo antes de que llamen»
6. **Privacidad** : «Nunca subimos tu agenda»

Cómo hacerlas: instala el APK de depuración (artefacto de GitHub Actions), usa un emulador Pixel 8 en modo oscuro, y añade algunas llamadas de ejemplo desde el emulador (Extended controls → Phone → llamada entrante con `400123456`, `+34902123456`, `+442071234567`). Puedes superponer los textos con Canva o Figma usando la paleta: fondo `#0E1015`, texto `#F4F0E6`, acento `#FF5A36`.

## Gráficos

- Icono 512×512: `graficos/icono-512.png`
- Gráfico de funciones 1024×500: `graficos/grafico-funciones-1024x500.png`

## Notas de la versión 1.0.0

```
Primera versión de Filtro 400. Bloquea o silencia el prefijo 400, tarificación adicional y los prefijos que elijas. Sin pedir tu agenda.
```
