# Publicar en Google Play (lo que necesita tus manos)

1. **Cuenta de desarrollador** de Google Play (25 € una vez). Si es personal y nueva, Play exige una prueba cerrada con 12 testers durante 14 días antes de pasar a producción.
2. **Clave de firma**: genera un keystore (una sola vez, guárdalo y haz copia):
   `keytool -genkeypair -v -keystore escaner.jks -alias escaner -keyalg RSA -keysize 4096 -validity 10000`
   Usa «Play App Signing» al subir el primer `.aab`.
3. **Crea la app** en Play Console: nombre, idioma español, gratuita. Copia el texto de `FICHA-PLAY-STORE.md`.
4. **Producto de pago único**: Monetizar → Productos → Productos únicos → ID **`escaner_limpio_pro`**, precio 3,99 €, activo.
5. **Clave de licencia**: Monetizar → Configuración de monetización → «Clave de licencia». Cópiala.
6. **Compila el `.aab`** firmado, en un archivo `apps/escaner-limpio/keystore.properties` (no se sube a Git):
   ```
   storeFile=/ruta/escaner.jks
   storePassword=...
   keyAlias=escaner
   keyPassword=...
   playLicenseKey=<clave de licencia base64>
   ```
   `cd apps/escaner-limpio && ./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
7. **Sube** el `.aab` a una prueba interna, instala desde el enlace de prueba y comprueba la compra con una cuenta de licencia de prueba (Configuración → Pruebas de licencia).
8. **Rellena** «Seguridad de los datos» (no se recopilan datos), clasificación de contenido, anuncios (no), política de privacidad (publica `PRIVACIDAD.md`, por ejemplo en GitHub Pages).
9. **Gráficos**: icono 512 (`logo/icono-play-512.png`), gráfico de funciones 1024×500 y 4-8 capturas (guía en `VIDEOS-Y-PUBLICIDAD.md`).
10. Producción → revisión de Google (unos días).

## Decisión de monetización
**Gratis con todo lo esencial + Pro de pago único (3,99 €).** Razón: el hueco frente a CamScanner es «gratis de verdad, sin marca de agua ni anuncios»; limitar el OCR (lo que cobra CamScanner) iría contra la promesa y ante Google Drive/Lens/Adobe Scan no se sostendría. Pro cobra por trabajo de oficina (Renta, unir, organizar, importar PDF), que lo valora quien maneja muchos papeles. Alternativa si quieres más ingresos: subir a 4,99 € tras las primeras 100 reseñas.

## Riesgos conocidos
- El escáner de Google es beta y necesita ≥ 1,7 GB de RAM y Google Play Services. Si no existe, la app ofrece escoger fotos del documento.
- El OCR de imágenes se hace con ML Kit (modelo latino incluido); la precisión baja con fotos oscuras o torcidas.
- Las compilaciones no se han probado en un móvil real desde el entorno de desarrollo: haz la prueba interna antes de producción.
