import 'ocr_stub.dart' if (dart.library.io) 'ocr_mlkit.dart' as impl;

/// Reconoce el texto de una foto de un tique. En el móvil usa ML Kit, que
/// funciona sin conexión y no envía la foto a ningún servidor. En la web no
/// está disponible.
bool get ocrSupported => impl.ocrSupported;

Future<String> recognizeText(String imagePath) => impl.recognizeText(imagePath);
