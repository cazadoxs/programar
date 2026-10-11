# Cómo publicar Apunta en Google Play (lo que necesita tus manos)

Todo el código, la ficha y los textos están listos. Esto es lo único que solo puedes hacer tú, en orden.

## 1. Cuenta de desarrollador (una vez)
1. https://play.google.com/console/signup → cuenta personal, pago único de 25 USD.
2. Verifica identidad. **Si la cuenta es personal y nueva, Google exige una prueba cerrada con al menos 12 testers durante 14 días seguidos antes de poder publicar en producción.** Empieza esto cuanto antes (ver paso 7).

## 2. Firma de la app (una vez, guarda copia segura)
```bash
keytool -genkeypair -v -keystore apunta-release.jks -alias apunta -keyalg RSA -keysize 4096 -validity 10000
```
- **Nunca** subas el `.jks` ni las contraseñas a GitHub. Si lo pierdes no podrás actualizar la app (con *Play App Signing*, que Google activa por defecto, esto se puede recuperar; úsalo).
- Para compilar firmado en local crea `apps/apunta/keystore.properties` (está en `.gitignore`):
```
STORE_FILE=../apunta-release.jks
STORE_PASSWORD=...
KEY_ALIAS=apunta
KEY_PASSWORD=...
PLAY_LICENSE_KEY=<clave pública base64 del paso 4>
```
- Para compilar firmado en GitHub Actions añade esos valores como *secrets* del repositorio con el prefijo `APUNTA_` (`APUNTA_STORE_PASSWORD`, `APUNTA_KEY_ALIAS`, `APUNTA_KEY_PASSWORD`, `APUNTA_PLAY_LICENSE_KEY`) y el fichero como `APUNTA_STORE_FILE` apuntando a donde lo descodifiques. (Dime y lo dejo automatizado.)

## 3. Crear la app en Play Console
Crear app → Nombre «Apunta: transcribe sin internet», idioma por defecto Español, *App*, *Gratis*. Rellena la ficha con `ficha-play-store.md` y sube `grafico/`.

## 4. Compra dentro de la app
*Monetizar → Productos → Productos de compra única → Crear*: ID **`apunta_pro`** (debe ser exactamente este), nombre «Apunta Pro», precio 7,99 € (puedes ajustar por país). Actívalo.
Después copia la **clave de licencia** (*Monetizar → Configuración de monetización → Licencias*) en `PLAY_LICENSE_KEY`. **Sin esa clave una versión de release no concede Pro** (es una medida de seguridad).

## 5. Política de privacidad
Play exige una URL pública. Está escrita en `politica-privacidad.html`. La forma más sencilla: GitHub → *Settings → Pages* → publicar desde la carpeta `apps/apunta/publicar` de la rama principal; la URL queda `https://cazadoxs.github.io/programar/politica-privacidad.html`. **Actualiza la constante `PRIVACY_URL` en `ui/screens/SettingsScreen.kt` con la URL final**, y pon tu email de contacto en el HTML.

## 6. Subir la primera versión
1. Descarga el `.aab` firmado (artefacto `apunta-release` del workflow *Apunta Android*).
2. *Pruebas → Prueba cerrada → Crear versión* → sube el `.aab`.
3. Completa *Contenido de la app* (cuestionarios) con las respuestas de `ficha-play-store.md` (clasificación, seguridad de los datos, servicios en primer plano con el vídeo corto de demostración).

## 7. Prueba cerrada (obligatoria en cuentas nuevas)
Reúne 12 personas (familia, compañeros, un grupo de Telegram/Reddit de estudiantes), pásales el enlace de la prueba y que **no se den de baja 14 días**. Pídeles el feedback de verdad: es tu mejor material para mejorar.

## 8. Producción
Pide acceso a producción → *Producción → Crear versión* → mismo `.aab` o uno nuevo → enviar a revisión (1-7 días).

## Lo que NO he podido probar (hazlo antes de lanzar)
Este entorno no tiene Android SDK ni móvil, así que la compilación se comprueba en GitHub Actions y el motor de voz se probó en PC, pero **la app no se ha ejecutado en un teléfono**. Antes de subir a producción, en un móvil real comprueba: grabar con pantalla apagada 10 min, importar una nota de WhatsApp, descargar el modelo Base, transcribir una clase de 30 min (calor/batería), compra de prueba con una cuenta de tester de licencia, y rotar/cerrar la app en mitad de una transcripción.
