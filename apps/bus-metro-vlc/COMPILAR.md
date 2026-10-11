# Compilar, firmar y publicar

## Requisitos
Android Studio (Narwhal o posterior) o JDK 17 + Android SDK 36. Nada más: el wrapper de Gradle descarga el resto.

## Probar en el emulador / móvil
```bash
cd apps/bus-metro-vlc
./gradlew :app:installDebug        # APK de depuración (applicationId …busmetrovlc.debug)
./gradlew :core:test               # pruebas del motor de horarios (sin Android)
LIVE_FEEDS=1 CORE_ONLY=1 ./gradlew :core:test   # además descarga y compila los feeds reales
```
La CI (`.github/workflows/bus-metro-vlc.yml`) hace todo esto en cada push y deja el APK como artefacto.

## Claves (nunca en el repositorio)
Crea `apps/bus-metro-vlc/keystore.properties` (está en `.gitignore`) o usa variables de entorno / secretos de GitHub:
```properties
ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
ADMOB_BANNER_ID=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
JCDECAUX_KEY=            # opcional (Valenbisi vía JCDecaux)
RELEASE_STORE_FILE=release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=busmetro
RELEASE_KEY_PASSWORD=...
```
Sin ellas la app compila con los IDs de **prueba** de AdMob (correcto para desarrollo; ¡nunca publiques así!).

## Clave de firma (una vez)
```bash
keytool -genkeypair -v -keystore release.jks -alias busmetro -keyalg RSA -keysize 4096 -validity 10000
```
Guarda `release.jks` y las contraseñas fuera del repo y con copia de seguridad. Usa **Play App Signing** (Play Console lo ofrece al subir la primera versión): entonces esta es solo la clave de subida y, si la pierdes, se puede restablecer.

## Generar el AAB para Play
```bash
./gradlew :app:bundleRelease       # → app/build/outputs/bundle/release/app-release.aab
```
Sube el `.aab` a Play Console → Producción (o Pruebas internas primero). Sube `versionCode` en `app/build.gradle.kts` en cada versión.

## Checklist antes de publicar
- [ ] `ADMOB_APP_ID` y `ADMOB_BANNER_ID` reales (no los de prueba).
- [ ] Producto `pro_unlock` creado y **activo** en Play Console.
- [ ] Probado en dispositivo real: primera descarga, modo avión, widget, compra de prueba (cuenta de licencia de pruebas), restaurar compra.
- [ ] Política de privacidad accesible por URL pública (`publicar/politica-privacidad.md` en GitHub, o tu web).
- [ ] Ficha rellena (`publicar/ficha-play-store.md`), capturas e iconos subidos, cuestionarios de contenido y Data safety.
- [ ] Respuesta de EMT/FGV sobre el uso de datos guardada (ver `publicar/fuentes-de-datos.md`).
- [ ] Prueba cerrada con ≥12 testers durante 14 días (requisito de Play para cuentas personales nuevas).
