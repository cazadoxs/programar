# Publicar Descifra paso a paso

Lo único que necesita tus manos: la cuenta de desarrollador de Google Play (25 USD, pago único), la clave de firma y pulsar «Enviar». Todo lo demás está preparado.

## 0. Antes de nada

1. Crea la cuenta en https://play.google.com/console. Si es cuenta personal creada después de nov. 2023, Google exige **prueba cerrada con al menos 12 testers durante 14 días** antes de poder pasar a producción. Empieza a reunir testers ya (familia, amigos, grupos de Telegram o Reddit de la comunidad).
2. Verifica identidad y dirección (tarda de 1 a 3 días).
3. Perfil de pagos: necesario para poder cobrar Descifra Pro.

## 1. Clave de firma (una sola vez, guárdala en tres sitios)

```bash
keytool -genkeypair -v -keystore descifra-upload.jks -alias descifra \
  -keyalg RSA -keysize 4096 -validity 10000
```

- No subas el `.jks` ni las contraseñas a GitHub (el `.gitignore` de esta carpeta ya los excluye).
- Activa **Play App Signing** al subir la primera versión: Google guarda la clave definitiva y tú solo conservas la clave de subida, que se puede reemplazar si la pierdes.

## 2. Compilar el paquete firmado (.aab)

Crea `apps/descifra/keystore.properties` (ignorado por git):

```properties
DESCIFRA_KEYSTORE=/ruta/a/descifra-upload.jks
DESCIFRA_KEYSTORE_PASSWORD=...
DESCIFRA_KEY_ALIAS=descifra
DESCIFRA_KEY_PASSWORD=...
DESCIFRA_PLAY_LICENSE_KEY=
```

```bash
cd apps/descifra
./gradlew :core:test :app:lintRelease :app:bundleRelease
# resultado: app/build/outputs/bundle/release/app-release.aab
```

`DESCIFRA_PLAY_LICENSE_KEY` es la clave pública de Play Console (Monetizar > Configuración de la licencia). Con ella la app verifica la firma de cada compra. Se puede dejar vacía al principio.

## 3. Crear la app en Play Console

1. Todas las apps > Crear app: nombre `Descifra: cartas oficiales`, español, app, gratuita, aceptar declaraciones.
2. Panel > «Configurar tu aplicación»: completa cada tarea con `01-ficha-google-play.md` (política de privacidad, acceso a la app, anuncios = no, clasificación de contenido, público objetivo, seguridad de los datos, categoría y contacto).
3. Crecimiento > Ficha principal: textos y gráficos de `01-ficha-google-play.md` y `assets/`.
4. Monetizar > Productos > Productos integrados en la aplicación: crea `descifra_pro`, producto único, precio (sugerido 4,99 EUR), actívalo.
5. Prueba y lanzamiento > Pruebas > **Prueba interna**: sube el `.aab`, añade tu correo como tester, publica e instala desde el enlace. Comprueba escáner, IA y la compra con una cuenta de prueba de licencias (Configuración > Pruebas de licencias).
6. Pasa a **Prueba cerrada** con los 12 testers durante 14 días (si tu cuenta lo exige) y después solicita **Producción**.

## 4. Lista de comprobación antes de enviar a producción

- [ ] La compra de prueba de `descifra_pro` activa Pro y sobrevive a reiniciar la app.
- [ ] Probado en un móvil con Gemini Nano (Pixel 9/10, Galaxy S25) y en uno sin él.
- [ ] Escaneo de 3 cartas reales con poca luz y texto torcido.
- [ ] «Borrar todo» deja la app como recién instalada.
- [ ] Política de privacidad accesible por URL pública y enlazada.
- [ ] Capturas de teléfono subidas (mínimo 2) y gráfico de funciones.
- [ ] Datos de contacto reales en la ficha y en la política.
- [ ] Declaración de IA generativa marcada.

## 5. Tras la publicación

- Contesta cada reseña en menos de 48 horas, sobre todo las de 1 y 2 estrellas.
- Mira Android vitals (cierres y ANR) en Play Console cada semana el primer mes.
- Cuando salga una versión estable de `genai-prompt` (hoy está en beta), actualiza `mlkitGenaiPrompt` en `gradle/libs.versions.toml`.
- Revisa cada trimestre las tablas de festivos y plazos legales en `core/.../rules/BusinessDays.kt`.

## Comandos útiles

| Qué | Comando |
|---|---|
| Probar solo la lógica (sin Android) | `./gradlew -p core test` |
| APK de depuración | `./gradlew :app:assembleDebug` |
| Instalar en el móvil conectado | `./gradlew :app:installDebug` |
| Revisión estática | `./gradlew :app:lintDebug` |
