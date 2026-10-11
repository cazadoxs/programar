# Publicar Fácil en Google Play — lista paso a paso

## Lo que tiene que hacer Álvaro (necesita sus manos)
1. **Cuenta de desarrollador de Google Play** (25 € una vez). Si es cuenta personal nueva: Play exige **prueba cerrada con ≥12 testers durante 14 días** antes de producción.
2. **Firebase**: seguir `docs/FIREBASE.md` y copiar la clave y la URL a `local.properties`.
3. **Firma de la app**: crear el almacén de claves y guardarlo a buen recaudo (¡sin él no se puede actualizar la app!):
   ```
   keytool -genkeypair -v -keystore facil-release.jks -alias facil -keyalg RSA -keysize 2048 -validity 10000
   ```
   y crear `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword). Activar **Play App Signing** al subir.
4. **Alojar la política de privacidad** (`PRIVACIDAD.md`) en una URL pública y cambiar `POLICY_URL` en `PrivacyActivity.kt` si no es la de GitHub. Rellenar TU_EMAIL en los documentos.
5. **Suscripción**: Play Console → Monetizar → Suscripciones → crear `facil_familia` con planes base `anual` (≈11,99 €, prueba 14 días) y `mensual` (≈1,99 €). Poner el estado en Activo.
6. Hacer las capturas (ver `FICHA_PLAY.md`) y subir icono + gráfico.

## Construir
```
cd apps/facil
./gradlew bundleRelease          # app/build/outputs/bundle/release/app-release.aab
```
(En un entorno sin Google Maven: `tools/build-local.sh` genera un APK de pruebas.)

## Pruebas manuales antes de publicar (no se han podido hacer en el entorno de desarrollo)
- [ ] Instalar en un móvil real y en Android 8, 12, 14 y 15 (emulador vale).
- [ ] Pedir ser pantalla de inicio (Ajustes → "Usar Fácil como pantalla de inicio") y que el botón inicio funcione.
- [ ] Crear botón de llamada con foto desde la agenda; llamar (permiso se pide solo una vez); WhatsApp con y sin número.
- [ ] Mantener pulsado AYUDA: cuenta atrás, cancelar, llamada. 112 abre el marcador.
- [ ] PIN: crear, entrar, 5 fallos → bloqueo.
- [ ] Recordatorio: suena a la hora, "Hecho" desde la notificación y desde la pantalla.
- [ ] Emparejar dos móviles: QR y código a mano; aceptar/rechazar; el familiar edita un botón y aparece en minutos.
- [ ] Alertas: batería baja, inactividad (poner 4 h y esperar), pastilla sin marcar.
- [ ] Quitar a un familiar → pierde acceso. "Borrar todos mis datos" → desaparece de Firebase.
- [ ] Compra de prueba con una cuenta de licencia en Play (probar plan con prueba gratis) y restauración.
- [ ] TalkBack: botones se leen bien; letra "Enorme" no corta textos.
- [ ] Modo avión: la app sigue funcionando (solo falla la sincronización).

## Después
Subir a **prueba interna → cerrada → producción**. Revisar Android Vitals (ANR/crashes) cada semana la primera vez.
