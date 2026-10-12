# Política de privacidad de Descifra

Google Play exige una **URL pública** con esta política. Publícala gratis con GitHub Pages (Settings > Pages, carpeta `/docs`) o en cualquier web tuya, y pega la URL en Play Console (Contenido de la app > Política de privacidad).

Rellena los dos campos entre corchetes antes de publicar.

---

**Última actualización:** [fecha de publicación]

## Quién es el responsable

[Tu nombre o el de tu empresa], correo [tu correo de contacto]. Descifra es una aplicación para Android que ayuda a entender cartas y documentos oficiales.

## Resumen en una frase

Tus cartas se quedan en tu móvil. Descifra no tiene cuentas ni servidores propios y no recibe ni guarda el contenido de tus documentos.

## Qué datos trata la app y dónde

| Dato | Para qué | Dónde se guarda |
|---|---|---|
| Fotos de las cartas | Leer el texto (OCR) | Se procesan en el móvil con ML Kit y se borran en cuanto se lee el texto |
| Texto de las cartas, resumen, notas | Mostrarte la explicación y los plazos | Solo en tu móvil, cifrado con una clave guardada en el chip de seguridad de Android (Keystore) |
| Ajustes (modo de análisis, avisos) | Funcionamiento de la app | Solo en tu móvil |
| Clave de IA de tu proveedor (opcional, Pro) | Llamar a la IA con tu cuenta | Solo en tu móvil, cifrada con Keystore |
| Compra de Descifra Pro | Activar las funciones Pro | La gestiona Google Play. Descifra solo guarda en tu móvil, cifrado, que la compra existe |

La copia de seguridad automática de Android está desactivada para estos datos.

## Inteligencia artificial

1. **En tu móvil (por defecto)**. Si tu teléfono es compatible, el resumen lo hace Gemini Nano mediante el servicio AICore de Android. Se ejecuta en el dispositivo; AICore no tiene acceso directo a internet y no guarda lo que se le envía.
2. **Con tu propia clave (opcional, Pro)**. Solo si tú lo activas y aceptas el aviso, Descifra envía el **texto** de la carta (nunca las imágenes) al proveedor que elijas: Anthropic, OpenAI o Google, usando tu clave y bajo las condiciones y la política de privacidad de ese proveedor. Antes de enviarlo, y si dejas la opción activada, Descifra sustituye NIF, NIE, CIF, IBAN, tarjetas, teléfonos, correos, nombres tras tratamientos como «D.» o «Dña.» y direcciones por marcadores, y los restituye al recibir la respuesta. Esa detección es automática y puede no cubrir todos los casos: revisa el texto antes de enviar si la carta es muy sensible.

Descifra no ve, no guarda ni retransmite esas peticiones: van directamente de tu móvil al proveedor.

## Publicidad, rastreadores y analítica

La app no incluye anuncios, rastreadores, SDK de analítica ni de atribución. No elabora perfiles.

## Permisos

- **Internet**: solo para la IA con tu clave y para Google Play.
- **Notificaciones**: avisos de plazos. El texto no muestra el contenido de la carta.
- **Huella, cara o PIN**: solo si activas el bloqueo de la app.

No pedimos acceso a tus fotos, archivos, contactos, ubicación, micrófono ni calendario. La cámara la gestiona el escáner de documentos de Google (ML Kit) y los eventos de calendario se abren en tu aplicación de calendario para que los confirmes tú.

## Servicios de terceros

- Google Play (compras y distribución).
- Google ML Kit: escáner de documentos y reconocimiento de texto. Funciona en el dispositivo.
- Google AICore / Gemini Nano (en el dispositivo).
- Si los activas: Anthropic, OpenAI o Google (Gemini API), con tu propia clave.

## Tus derechos y cómo borrar tus datos

Tus datos están en tu móvil y tú los controlas: puedes borrar una carta desde su pantalla, o todo desde Ajustes > Borrar todo (se eliminan las cartas, las notas, las claves y se destruye la clave de cifrado). Desinstalar la app también los elimina. Como el responsable no recibe tus datos, no puede acceder a ellos ni rectificarlos; para cualquier consulta sobre protección de datos (RGPD) escribe a [tu correo].

## Menores

Descifra no está dirigida a menores de 18 años.

## Aviso importante

Descifra genera resúmenes automáticos con reglas e inteligencia artificial y puede equivocarse. No es asesoramiento legal, fiscal ni financiero. No está vinculada, patrocinada ni avalada por ninguna administración pública, entidad bancaria o empresa cuyas cartas puedas escanear.

## Cambios

Si esta política cambia, se publicará aquí con una nueva fecha y, si el cambio es relevante, se avisará dentro de la app.
