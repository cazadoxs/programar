import 'dart:io';

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

bool get ocrSupported => Platform.isAndroid;

Future<String> recognizeText(String imagePath) async {
  final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
  try {
    final result = await recognizer.processImage(InputImage.fromFilePath(imagePath));
    return result.text;
  } finally {
    await recognizer.close();
    // La foto del tique no se guarda: se borra en cuanto se ha leído.
    try {
      await File(imagePath).delete();
    } catch (_) {}
  }
}
