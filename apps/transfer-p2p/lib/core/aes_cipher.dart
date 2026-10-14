import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';

/// Cifrado real: AES-256-GCM con clave derivada por PBKDF2-HMAC-SHA256.
///
/// Formato:
///   "PSLA" | versión (1) | iteraciones (u32) | sal (16) | nonce base (12)
///   | tamaño de trozo (u32) | trozos...
/// Cada trozo es texto cifrado + etiqueta de 16 bytes. El nonce de cada trozo
/// es el nonce base con su índice mezclado, y el AAD marca el índice y si es
/// el último trozo, así que reordenar o cortar el archivo se detecta.
class AesFileCipher {
  static const List<int> magic = [0x50, 0x53, 0x4C, 0x41]; // "PSLA"
  static const int version = 1;
  static const int defaultIterations = 310000;
  static const int maxIterations = 5000000;
  static const int chunkSize = 1 << 20;
  static const int headerSize = 4 + 1 + 4 + 16 + 12 + 4;
  static const int macSize = 16;

  final int iterations;
  AesFileCipher({this.iterations = defaultIterations});

  static final _aes = AesGcm.with256bits();

  static Future<SecretKey> _deriveKey(
      String password, List<int> salt, int iterations) {
    final kdf = Pbkdf2(
        macAlgorithm: Hmac.sha256(), iterations: iterations, bits: 256);
    return kdf.deriveKeyFromPassword(password: password, nonce: salt);
  }

  static List<int> _nonce(List<int> base, int index) {
    final n = Uint8List.fromList(base);
    for (var k = 0; k < 8; k++) {
      n[11 - k] ^= (index >> (8 * k)) & 0xFF;
    }
    return n;
  }

  static List<int> _aad(int index, bool last) {
    final b = ByteData(9)
      ..setUint32(0, index ~/ 0x100000000)
      ..setUint32(4, index & 0xFFFFFFFF)
      ..setUint8(8, last ? 1 : 0);
    return b.buffer.asUint8List();
  }

  Future<void> encryptFile(File input, File output, String password) async {
    if (password.isEmpty) throw ArgumentError('La contraseña está vacía.');
    final rnd = Random.secure();
    final salt = List<int>.generate(16, (_) => rnd.nextInt(256));
    final base = List<int>.generate(12, (_) => rnd.nextInt(256));
    final key = await _deriveKey(password, salt, iterations);
    final header = ByteData(headerSize);
    final hb = header.buffer.asUint8List();
    hb.setAll(0, magic);
    header.setUint8(4, version);
    header.setUint32(5, iterations);
    hb.setAll(9, salt);
    hb.setAll(25, base);
    header.setUint32(37, chunkSize);

    final total = await input.length();
    final raf = await input.open();
    final sink = output.openWrite();
    try {
      sink.add(hb);
      var index = 0;
      var done = 0;
      do {
        final chunk = await raf.read(chunkSize);
        done += chunk.length;
        final last = done >= total;
        final box = await _aes.encrypt(chunk,
            secretKey: key, nonce: _nonce(base, index), aad: _aad(index, last));
        sink.add(box.cipherText);
        sink.add(box.mac.bytes);
        index++;
        if (last) break;
      } while (true);
    } finally {
      await raf.close();
      await sink.close();
    }
  }

  static Future<void> decryptFile(
      File input, File output, String password) async {
    final total = await input.length();
    if (total < headerSize + macSize) {
      throw const FormatException('No es un archivo cifrado con AES de Pásalo.');
    }
    final raf = await input.open();
    final sink = output.openWrite();
    var ok = false;
    try {
      final hb = await raf.read(headerSize);
      final header = ByteData.sublistView(hb);
      for (var i = 0; i < 4; i++) {
        if (hb[i] != magic[i]) {
          throw const FormatException(
              'No es un archivo cifrado con AES de Pásalo.');
        }
      }
      if (header.getUint8(4) != version) {
        throw const FormatException('Versión de cifrado desconocida.');
      }
      final iterations = header.getUint32(5);
      final size = header.getUint32(37);
      if (iterations < 1 ||
          iterations > maxIterations ||
          size < 1 ||
          size > 64 << 20) {
        throw const FormatException('Cabecera de cifrado no válida.');
      }
      final salt = hb.sublist(9, 25);
      final base = hb.sublist(25, 37);
      final key = await _deriveKey(password, salt, iterations);
      var pos = headerSize;
      var index = 0;
      while (true) {
        final want = min(size + macSize, total - pos);
        if (want < macSize) {
          throw const FormatException('El archivo está cortado.');
        }
        final data = await raf.read(want);
        pos += data.length;
        final last = pos >= total;
        final cipher = data.sublist(0, data.length - macSize);
        final mac = Mac(data.sublist(data.length - macSize));
        try {
          final plain = await _aes.decrypt(
              SecretBox(cipher, nonce: _nonce(base, index), mac: mac),
              secretKey: key,
              aad: _aad(index, last));
          sink.add(plain);
        } on SecretBoxAuthenticationError {
          throw const FormatException(
              'Contraseña incorrecta o archivo modificado.');
        }
        index++;
        if (last) break;
      }
      ok = true;
    } finally {
      await raf.close();
      await sink.close();
      if (!ok && await output.exists()) await output.delete();
    }
  }
}
