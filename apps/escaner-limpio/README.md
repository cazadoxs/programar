# Escáner limpio

Escáner de documentos para Android: sin marcas de agua, sin anuncios, sin cuenta y sin permiso de Internet.

- Escaneo con el escáner de Google (ML Kit Document Scanner) y OCR en español en el dispositivo.
- PDFs con texto seleccionable y buscable; nombres automáticos y carpetas inteligentes.
- Pro (pago único con Google Play Billing): paquete Renta, unir PDFs, organizar páginas, importar PDFs.
- Kotlin + Jetpack Compose, minSdk 26.

```
cd apps/escaner-limpio
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Publicación, ficha de la tienda, privacidad, seguridad, logo y guía de vídeos y publicidad: carpeta [`publicar/`](publicar/PUBLICAR.md).
