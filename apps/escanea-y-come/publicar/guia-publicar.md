# Cómo publicar Escanea y come en Google Play

Lo que solo puedes hacer tú (cuentas y claves) está marcado con 🖐. Lo demás ya está preparado.

## 1. Cuenta de desarrollador 🖐
1. https://play.google.com/console → crear cuenta (25 USD, pago único). Cuenta personal: Google exige **12 testers durante 14 días** en prueba cerrada antes de poder publicar en producción (ver paso 5).
2. Verifica tu identidad (DNI y, si te lo piden, dirección).

## 2. Clave de firma 🖐
En tu ordenador (Android Studio trae `keytool`):
```
keytool -genkeypair -v -keystore escanea-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```
Guarda el `.jks` y la contraseña en un gestor de contraseñas **fuera del repositorio** (está en `.gitignore`). Crea `apps/escanea-y-come/keystore.properties`:
```
storeFile=escanea-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```
En Play Console activa **Play App Signing**: Google guarda la clave real y tú solo usas la de subida.

## 3. Generar el paquete
```
cd apps/escanea-y-come
./gradlew :core:test :app:lintRelease :app:bundleRelease
```
Sale `app/build/outputs/bundle/release/app-release.aab`. Sube ese archivo.
(Si prefieres no compilar en local: el flujo de GitHub Actions "Escanea y come" deja el APK de depuración como artefacto; para el `.aab` firmado hay que usar tu clave en local.)

## 4. Crear la app en Play Console 🖐
- Nombre y datos de `ficha-play-store.md`; sube `grafica/icono-512.png` y `grafica/grafico-funciones-1024x500.png`.
- Rellena **Contenido de la app** como indica `ficha-play-store.md` (privacidad, anuncios, público, seguridad de datos, app de salud).
- **Monetizar > Productos > Productos integrados:** crea `plus_para_siempre` (precio único). Actívalo.
- Cuenta de pagos de comerciante (para cobrar): datos fiscales. En España, alta como autónomo cuando los ingresos sean habituales; consulta con una gestoría.

## 5. Pruebas antes de producción
1. **Prueba interna** (hasta 100 testers, sin revisión): sube el `.aab`, añade tu correo y comprueba que la compra funciona con una **cuenta de licencia de prueba** (Configuración > Pruebas de licencia).
2. **Prueba cerrada** (cuentas personales nuevas): reúne 12 testers 14 días seguidos. Consejo: familia, amigos, grupos de Reddit/Telegram de testers; pídeles que abran la app a diario.
3. **Producción:** solicita acceso; la revisión suele tardar de unas horas a unos días.

## 6. Capturas de pantalla
Ver `guia-capturas.md`.

## 7. Después de publicar
- Responde a todas las reseñas en las primeras semanas.
- Activa **Experimentos de ficha** para probar el icono y la descripción corta.
- Mira Play Console > Estadísticas > Android vitals (cierres y ANR deben estar bajo el 1 %).
- Sube versiones pequeñas y frecuentes: `versionCode` +1 en `app/build.gradle.kts` cada vez.

## Lista de comprobación final
- [ ] `./gradlew :core:test :app:lintRelease` en verde
- [ ] Probado en un móvil real con Android 8 y con Android 14+
- [ ] Escáner probado sin conexión (muestra error amable) y con conexión
- [ ] Compra de Plus probada con cuenta de licencia (y reembolso)
- [ ] Política de privacidad accesible por URL pública
- [ ] Declaración de seguridad de datos coherente con la política
