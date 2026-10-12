# Ficha de Google Play: Descifra

Todo lo que hay que pegar en Play Console (Crecimiento > Presencia en la tienda > Ficha principal). Los límites de caracteres son los de Google.

## Datos básicos

| Campo | Valor |
|---|---|
| Nombre (máx. 30) | `Descifra: cartas oficiales` (26) |
| Descripción breve (máx. 80) | `Haz foto a una carta de Hacienda o del banco y entiende qué piden y para cuándo.` (79) |
| Categoría | Productividad (alternativa: Herramientas) |
| Etiquetas | Documentos, Organizadores, Finanzas personales |
| Idioma predeterminado | Español (España) |
| Correo de contacto | el tuyo (obligatorio y público) |
| Sitio web | la página de privacidad (ver `03-politica-de-privacidad.md`) |
| Política de privacidad | URL pública de esa página (obligatoria) |

## Descripción completa (máx. 4000)

```
¿Te ha llegado una carta de Hacienda, la Seguridad Social, Tráfico, el ayuntamiento, el banco o tu casero y no sabes qué te piden?

Descifra la lee por ti. Haz una foto y en segundos sabes:

• Qué es: apremio, requerimiento, multa, propuesta de liquidación, citación, reclamación de deuda…
• Qué te piden, explicado como te lo explicaría un amigo.
• Cuánto tienes que pagar.
• Para cuándo, con la fecha en tu calendario y un aviso antes de que venza.
• Qué hacer, paso a paso.

SIEMPRE VERÁS EL ORIGINAL
Un resumen automático puede fallar, y en asuntos de dinero y plazos no queremos que te fíes a ciegas. El texto original va junto al resumen, cada fecha enseña la frase de la que sale y los plazos calculados se marcan como «estimados».

TUS CARTAS SE QUEDAN EN TU MÓVIL
• Sin cuenta, sin registro, sin anuncios ni rastreadores.
• La foto se lee en el propio teléfono y se borra al momento.
• El texto se guarda cifrado en tu móvil, protegido por el chip de seguridad.
• Si tu móvil tiene IA integrada (Gemini Nano: Pixel 9 y 10, Galaxy S25 y otros), el resumen también se hace sin internet.
• Bloqueo con huella, cara o PIN, y borrado total con un toque.

HECHA PARA CARTAS ESPAÑOLAS
Entiende los plazos de la Administración: días hábiles sin sábados, domingos ni festivos nacionales, plazos en meses, y los plazos de pago de la Agencia Tributaria. Reconoce NIF, NIE, IBAN, números de expediente, código seguro de verificación y matrículas.

GRATIS PARA LO IMPORTANTE
Escanear, leer, entender, calendario y avisos son gratis. Con Descifra Pro (un solo pago, sin suscripción) puedes además:
• Usar IA más potente con tu propia clave de Claude, OpenAI o Gemini.
• Preguntar lo que no entiendas de cada carta.
• Redactar borradores de alegaciones, aplazamientos o respuestas.
• Exportar el resumen en PDF.

IMPORTANTE
Descifra usa reglas e inteligencia artificial y puede equivocarse. No es asesoramiento legal, fiscal ni financiero y no está vinculada a ninguna administración pública ni entidad. Comprueba siempre fechas e importes en la carta original y, ante un embargo, un juicio o una sanción importante, consulta con un profesional.
```

## Notas de la versión (inicial)

```
Primera versión de Descifra. Escanea una carta oficial y entiende qué piden, cuánto y para cuándo.
```

## Gráficos

| Recurso | Archivo | Requisito de Play |
|---|---|---|
| Icono | `assets/icono-512.png` | 512 x 512 PNG, máx. 1 MB |
| Gráfico de funciones | `assets/grafico-funciones-1024x500.png` | 1024 x 500 PNG o JPG |
| Capturas de teléfono | ver `04-capturas-y-gancho-visual.md` | 2 a 8, lado entre 320 y 3840 px, relación máx. 2:1 |

## Cuestionario de clasificación de contenido (IARC)

Categoría: Utilidad / Productividad. Contestar «No» a violencia, sexo, drogas, juego, lenguaje soez y contenido generado por usuarios visible a otros. «¿Comparte la app la ubicación del usuario?»: No. Resultado esperado: PEGI 3 / Todos.

## Público objetivo

Mayores de 18 años. No está dirigida a menores. Marcar «No» en «atrae a niños».

## Declaración de seguridad de los datos (Data safety)

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte datos de usuario? | Sí, solo en el modo opcional «IA con tu clave» |
| ¿Se cifran los datos en tránsito? | Sí (HTTPS) |
| ¿Pueden los usuarios pedir que se borren? | Sí: Ajustes > Borrar todo; los datos solo viven en el móvil |
| Datos locales (cartas, notas, ajustes) | **No se recogen**: se quedan en el dispositivo y nunca los recibe el desarrollador |
| Con «IA con tu clave» activada por el usuario | El texto de la carta se envía al proveedor que el usuario elige, con su clave. Declarar: *Información financiera* y *Mensajes/contenido* **compartidos** con terceros, finalidad *funcionalidad de la app*, **opcional**, el usuario elige activarlo. No es una venta |
| Historial de compras | Lo gestiona Google Play; no lo recibe el desarrollador |
| Identificadores, ubicación, contactos, fotos, analítica, publicidad | No |

Declaración de anuncios: **la app no contiene anuncios**.

## Declaraciones de política que aplican

- **Contenido generado por IA**: marcar que la app genera contenido con IA. Cumplimiento: la app muestra un aviso en cada resumen y tiene un botón «Avisar de un error» en cada carta para reportar contenido incorrecto.
- **Funciones de salud / finanzas**: no aplica (no concede préstamos ni da consejos de inversión). La app no es una app financiera regulada.
- **Permisos sensibles**: ninguno (sin cámara directa, sin lectura de fotos, sin calendario, sin SMS). La cámara la gestiona el escáner de Google (ML Kit Document Scanner) y el calendario se abre con la app del usuario.
- **Acceso a la app**: sin restricciones, no hace falta usuario ni contraseña para revisarla.

## Producto integrado en la app

| Campo | Valor |
|---|---|
| ID del producto | `descifra_pro` (debe ser exactamente este) |
| Tipo | Producto único (no consumible) |
| Nombre | Descifra Pro |
| Descripción | IA con tu clave, preguntas sobre la carta, borradores y exportar en PDF. Un solo pago. |
| Precio sugerido | 4,99 EUR (Play lo convierte a cada país). Empieza a 3,99 EUR en el lanzamiento si quieres impulsar las primeras valoraciones |
