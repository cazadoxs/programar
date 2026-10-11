# A medias

Gastos compartidos para pisos, viajes y parejas. **Sin límites diarios, sin
cuenta, sin anuncios y sin banco detrás.** App de Android (Flutter) con
versión web para quien entra por un enlace.

Lo gratis y sin límites: grupos, gastos, repartos iguales / por importes / por
porcentajes / por partes, varias monedas, simplificación de deudas, enlaces
para unirse sin registro, modo sin conexión, exportar a CSV y ayuda para
pagar por Bizum.
Lo de pago (compra única de ~3,49 €, «A medias Plus»): escanear tiques,
gráficas y temas de color.

## Por qué Flutter + Firebase

- **Flutter**: un solo código para Android y para la web de los enlaces de
  invitación (los amigos con iPhone entran desde el navegador sin instalar
  nada). Hay ML Kit y Play Billing con paquetes oficiales.
- **Firebase (Firestore + Auth anónima)**: sincronización en tiempo real y
  caché sin conexión incluidas, sin servidor propio que mantener. «Sin
  cuenta» se resuelve con sesión anónima + enlace secreto.
- La app funciona **entera sin Firebase** (grupos solo en el móvil); la nube
  solo se activa si compilas con las claves (ver `publicar/PUBLICAR.md`).

## Estructura

```
lib/core/      lógica pura y probada: dinero, repartos, saldos, CSV, OCR, enlaces
lib/data/      almacenamiento local, estado, Firestore, tipos de cambio
lib/premium/   Play Billing (compra única) y ML Kit
lib/ui/        pantallas y tema
firebase/      reglas de seguridad de Firestore + sus pruebas (emulador)
publicar/      ficha de Play Store, logo, marketing, seguridad, pasos de publicación
android/ web/  proyectos de plataforma
```

## Desarrollo

```bash
flutter pub get
flutter analyze
flutter test                      # lógica de dinero, repartos, saldos, CSV, OCR, enlaces
flutter run -d chrome             # sin Firebase: todo en local
flutter run --dart-define-from-file=firebase.env.json   # con Firebase
(cd firebase/rules-test && npm i && npm test)           # reglas de Firestore
```

Compilar para Play Store: `flutter build appbundle --dart-define-from-file=firebase.env.json`
(detalles, firma y Play Console en [`publicar/PUBLICAR.md`](publicar/PUBLICAR.md)).

## Estado

Probado: análisis estático limpio, 22 pruebas de lógica, 36 pruebas de las
reglas de Firestore contra el emulador, y la app web recorrida en un
navegador. **No probado en un móvil ni compilado a APK/AAB** desde el entorno
donde se escribió (no tiene el SDK de Android); hay que hacer la primera
compilación y una prueba en un teléfono real antes de subir a Play.
