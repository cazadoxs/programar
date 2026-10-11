# Cómo publicar A medias en Google Play

Todo lo que se puede hacer sin tus cuentas ya está hecho en el repositorio.
Esta guía es lo que **solo puedes hacer tú** (cuentas, claves, pagos), en orden.

## 0. Qué necesitas

| Qué | Coste | Para qué |
|---|---|---|
| Cuenta de desarrollador de Google Play | 25 € una vez | Publicar |
| Proyecto de Firebase (plan Spark gratis; Blaze si quieres pasar de la cuota gratis) | 0 € | Grupos compartidos |
| Dominio propio (opcional) | ~10 €/año | Enlaces bonitos; si no, vale `tu-proyecto.web.app` |
| Un móvil Android | — | Probar antes de subir |

Cuenta de Play de **persona física**: Google exige que una cuenta nueva
personal haga una **prueba cerrada con al menos 12 testers durante 14 días**
antes de poder publicar en producción. Empieza por ahí: reparte la prueba
cerrada entre amigos desde el primer día.

## 1. Proyecto de Firebase

1. <https://console.firebase.google.com> → *Añadir proyecto* (sin Analytics).
2. **Ubicación de Firestore: `eur3` (Europa)**. Importante para el RGPD; no se puede cambiar luego.
3. *Compilación → Authentication → Método de acceso → Anónimo* → activar.
4. *Compilación → Firestore Database* → crear en modo producción.
5. *Configuración del proyecto → Tus apps*: añade una app **Android** con el
   paquete `es.amedias.app` y una app **Web**. Copia los valores a un archivo
   `firebase.env.json` (plantilla: `firebase.env.example.json`; **no lo subas a git**).
6. Reglas de seguridad: `npm i -g firebase-tools && firebase login && firebase deploy --only firestore:rules`
   desde `apps/a-medias`. Antes, comprueba que pasan las pruebas:
   `cd firebase/rules-test && npm i && npm test`.
7. **App Check** (evita que cualquier cliente falso use tu base de datos):
   *Compilación → App Check* → registra Android con **Play Integrity** y la web
   con **reCAPTCHA Enterprise**; copia la clave de sitio a `RECAPTCHA_SITE_KEY` y pon
   `"APP_CHECK": "true"`. Cuando funcione, activa «Exigir» en Firestore.
8. **Presupuesto**: *Uso y facturación → Presupuestos y alertas* → alerta a 5–10 €/mes.
9. Restringe la clave de API de Android (Google Cloud → Credenciales) a tu
   paquete y a la huella SHA-1 de tu firma.

## 2. Web y enlaces de invitación

```bash
flutter build web --release --dart-define-from-file=firebase.env.json
firebase deploy --only hosting      # sirve build/web con cabeceras de seguridad
```

- Los enlaces son `https://TU_HOST/unirse#g=…&k=…`. Si no usas `amedias.web.app`,
  cambia `WEB_HOST` en `firebase.env.json` **y** el host en
  `android/app/src/main/AndroidManifest.xml` (intent-filter de `/unirse`).
- **Enlaces que abren la app** (en vez del navegador): en Play Console →
  *Configuración → Integridad de la app → Firma de apps* copia la huella
  **SHA-256 del certificado de firma de la app** y pégala en
  `web/.well-known/assetlinks.json`; vuelve a desplegar el hosting.
- Sube también `web/privacidad.html` (ya incluida en el despliegue): su URL es la
  política de privacidad que pide Play Console.

## 3. Firma y compilación

```bash
keytool -genkey -v -keystore ~/amedias-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Crea `android/key.properties` (ya está en `.gitignore`):

```
storePassword=…
keyPassword=…
keyAlias=upload
storeFile=/ruta/a/amedias-upload.jks
```

```bash
flutter build appbundle --release --dart-define-from-file=firebase.env.json
# → build/app/outputs/bundle/release/app-release.aab
```

Guarda el `.jks` y las contraseñas en un gestor de contraseñas **y haz copia**:
si pierdes la clave de subida tendrás que pedir un reinicio a Google.
Activa *Play App Signing* cuando Play Console lo ofrezca.

> Esta primera compilación no se ha podido hacer en el entorno donde se
> escribió el código (sin SDK de Android). Si Gradle protesta por alguna
> versión, `flutter doctor` y actualizar es lo habitual.

## 4. Play Console

1. *Crear app* → «A medias», español, app, gratuita.
2. **Compra única**: *Monetizar → Productos → Productos integrados* → ID
   **`a_medias_plus`**, precio 3,49 €, activo. (El ID debe ser exactamente ese.)
3. **Ficha**: copia los textos de [`ficha-play-store.md`](ficha-play-store.md) y
   sube de [`graficos/`](graficos) el icono 512 y el gráfico de funciones, y las
   capturas de [`capturas/`](capturas).
4. **Contenido de la app**: política de privacidad (URL de arriba), seguridad
   de los datos y clasificación — respuestas sugeridas en la ficha.
5. *Pruebas → Prueba cerrada* → sube el `.aab`, crea la lista de testers (12+) y
   déjalo 14 días. Después solicita acceso a producción.
6. Producción → *Países* (empieza por España y Latinoamérica) → enviar a revisión.

## 5. Antes de subir: lista de comprobación en un móvil real

- [ ] Crear grupo, añadir gastos de cada tipo de reparto, ver saldos cuadrar.
- [ ] Gasto en otra moneda con tipo de cambio automático y a mano (sin red).
- [ ] Modo avión: apuntar gastos, volver a tener red y ver que sincroniza en otro móvil.
- [ ] Compartir enlace → abrirlo en otro móvil (con y sin la app) → entra sin registro.
- [ ] Cambiar enlace: el viejo deja de funcionar.
- [ ] Compra de Plus con una **cuenta de pruebas de licencias** (Play Console → Configuración → Pruebas de licencia); restaurar compra tras reinstalar.
- [ ] Escanear 5 tiques reales (súper, bar, gasolinera).
- [ ] Exportar CSV y abrirlo en Excel/Sheets.
- [ ] Modo oscuro y fuente grande del sistema.

## 6. Mantenimiento

- Mira *Android vitals* y los fallos en Play Console cada semana el primer mes.
- Responde a todas las reseñas (sobre todo las negativas) en 48 h: sube el ranking.
- Tipos de cambio: el servicio gratuito (Frankfurter) podría cambiar; hay
  entrada manual de respaldo.
