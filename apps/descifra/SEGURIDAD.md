# Revisión de seguridad de Descifra

Revisión propia del código (no una auditoría externa). Estado: sin vulnerabilidades conocidas abiertas.

## Datos en el móvil
- Las cartas, el texto OCR, el historial de preguntas y la clave de API se guardan cifrados con AES-256-GCM. La clave vive en el Android Keystore y no sale del dispositivo. El nombre del archivo se usa como dato asociado, así que un archivo copiado a otro nombre no se descifra.
- `allowBackup=false` y reglas de extracción que excluyen todo: nada va a copias de seguridad en la nube ni a transferencias entre móviles.
- `FLAG_SECURE` activo por defecto (sin capturas ni miniatura en recientes). El usuario puede permitir capturas en Ajustes.
- Bloqueo opcional con huella, cara o PIN del sistema (BiometricPrompt). Se salta solo mientras el escáner o el selector de archivos están abiertos.
- Notificaciones de plazos con texto genérico y visibilidad privada: nunca muestran el contenido de la carta.
- Al copiar al portapapeles se marca el contenido como sensible.
- "Borrar todo" elimina cartas, claves y recordatorios.

## Red
- Solo hay conexión si el usuario activa la IA en la nube con su propia clave. Sin ella, la app no envía nada.
- Tráfico solo HTTPS (`cleartextTrafficPermitted=false`). La clave de Gemini va en cabecera, nunca en la URL.
- Antes de enviar, `Redactor` sustituye NIF/NIE, IBAN, tarjetas, teléfonos, correos, nombres tras tratamiento y direcciones por marcadores, y los restituye en la respuesta. Límite: puede dejar pasar datos con formatos poco habituales; la pantalla de envío avisa de ello.
- Los errores de red se muestran con mensajes genéricos; no se registran textos de cartas ni claves.

## Salidas de la IA (alucinaciones e inyección)
- El texto de una carta es entrada no confiable: puede contener instrucciones. La IA no tiene herramientas ni acceso a nada; su salida solo se pinta como texto plano.
- `AiMerge` comprueba cada fecha contra una cita literal de la carta y conserva solo importes que aparecen en el texto. Lo no verificado se marca así.
- El original siempre está a un toque junto al resumen, con aviso de que no es asesoría legal.

## Componentes expuestos
- Solo `MainActivity` está exportada, con filtros SEND para imagen, PDF y texto. Los archivos recibidos se copian a caché privada y se borran después; no se borran archivos que eligió el usuario.
- Los PDF exportados se comparten por `FileProvider` con permiso temporal de lectura y se limpian de la caché.
- Entradas acotadas: el texto analizado se limita a 30.000 caracteres y las expresiones regulares no tienen retroceso catastrófico.

## Compras
- Play Billing con comprobación de firma contra `PLAY_LICENSE_KEY`. Es verificación en el cliente: un móvil con root puede saltarla. Si la app crece, conviene verificar los recibos en un servidor.

## Compilación
- R8 y reducción de recursos en release. La firma usa un almacén de claves externo; nada secreto está en el repositorio (`keystore.properties` está en `.gitignore`).
