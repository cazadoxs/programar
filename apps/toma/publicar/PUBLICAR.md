# Publicar Toma en Google Play

Todo lo que necesita tus manos (cuentas y claves) está aquí; el resto ya está hecho.

## 1. Cuenta de desarrollador
Crea una cuenta en <https://play.google.com/console> (pago único de 25 $). Si es cuenta personal creada después de noviembre de 2023, Google exige **prueba cerrada con 12 testers durante 14 días** antes de publicar en producción.

## 2. Clave de firma
Recomendado: **Play App Signing**. Genera una clave de subida:
```
keytool -genkeypair -v -keystore toma-upload.jks -alias toma -keyalg RSA -keysize 4096 -validity 10000
```
Guarda `toma-upload.jks` y su contraseña en un gestor de contraseñas (no en git). Crea `apps/toma/keystore.properties` (ya está ignorado por git):
```
storeFile=toma-upload.jks
storePassword=...
keyAlias=toma
keyPassword=...
```

## 3. Compilar el AAB
Con Android Studio (abre la carpeta `apps/toma`) o por terminal:
```
cd apps/toma
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:bundleRelease
```
El archivo queda en `app/build/outputs/bundle/release/app-release.aab`. GitHub Actions también compila un AAB sin firmar en cada cambio (pestaña Actions → artefacto `toma-builds`).

## 4. Política de privacidad
Publica `publicar/politica-privacidad.md` en una URL pública (por ejemplo GitHub Pages o un Gist) tras poner tu correo en la última línea. Play exige la URL.

## 5. Ficha
Rellena la ficha con `ficha-play-store.md`. Sube `assets/icono-512.png` y `assets/grafico-funciones-1024x500.png`, y tus capturas.

## 6. Compra integrada (Toma Plus)
Play Console → Monetizar → Productos → Productos únicos → crear con ID **`toma_plus`**, precio 3,99 €. Actívalo.
Luego copia la **clave de licencia** (Monetizar → Configuración de monetización → Licencias) y añádela a `~/.gradle/gradle.properties`:
```
toma.playLicenseKey=MIIBIjANBgkq...
```
Sin esa clave, la compra funciona pero no se verifica la firma.

## 7. Formularios de la consola
- **Seguridad de los datos**: recopila datos de salud, *procesados solo en el dispositivo*, no se comparten (el modo cuidador envía nombre de perfil y hora al servidor ntfy elegido por el usuario: márcalo como «datos compartidos, opcional»).
- **Clasificación de contenido**: cuestionario → app de utilidad, sin contenido sensible.
- **Público objetivo**: 18+ (no es una app para niños).
- **Anuncios**: no contiene anuncios.
- **App de salud**: en «Aplicaciones de salud» declara «Gestión de medicamentos» y marca que **no** es un producto sanitario ni da consejo médico.
- **Permisos sensibles** (declaración): `SCHEDULE_EXACT_ALARM` (alarmas de medicación elegidas por el usuario) y `USE_FULL_SCREEN_INTENT` (pantalla de alarma). Explica que la función principal es una alarma de toma de medicación confirmada por el usuario.

## 8. Prueba antes de lanzar (checklist en un móvil real)
- [ ] Añadir un medicamento para dentro de 2 minutos: suena aunque la pantalla esté bloqueada.
- [ ] No tocar nada: vuelve a sonar cada 5 min hasta confirmar.
- [ ] «Ya la tomé» desde la notificación y desde la pantalla de alarma.
- [ ] Reiniciar el móvil con una alarma pendiente: sigue sonando.
- [ ] Cambiar la hora del sistema y la zona horaria.
- [ ] Revocar «Alarmas y recordatorios»: aparece el aviso rojo en Hoy.
- [ ] En Xiaomi/Samsung: seguir la guía de batería y repetir la primera prueba con la app cerrada.
- [ ] Modo cuidador: «Enviar prueba» llega a la app ntfy de otro móvil.
- [ ] Copia cifrada: crear, desinstalar, instalar, restaurar.
- [ ] PDF e CSV se abren y se comparten.
- [ ] Compra de Toma Plus con una cuenta de prueba de licencias.

## 9. Lanzamiento
Prueba cerrada → producción con despliegue gradual (20 % → 100 %). Mira la guía de `guia-videos-y-publicidad.md` para el lanzamiento.
