bool get ocrSupported => false;

Future<String> recognizeText(String imagePath) async =>
    throw UnsupportedError('El escaneo de tiques solo está en la app de Android.');
