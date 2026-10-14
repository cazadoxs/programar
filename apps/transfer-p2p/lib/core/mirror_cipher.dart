import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

/// "Cifrado Espejo": el cifrado casero pedido.
///
/// 1. Invierte cada bit (0 -> 1, 1 -> 0).
/// 2. Rota los bits de cada byte una cantidad que depende de la posición y de
///    la contraseña.
/// 3. Baraja los bytes dentro de bloques de [blockSize] con una permutación
///    sacada de la contraseña.
///
/// Es reversible y divertido, pero NO es seguro: cualquiera que conozca el
/// algoritmo puede romperlo. Para datos sensibles usa [AesFileCipher].
class MirrorCipher {
  static const List<int> magic = [0x50, 0x53, 0x4C, 0x4D]; // "PSLM"
  static const int version = 1;
  static const int blockSize = 4096;
  static const int headerSize = 7;

  final int _seed;
  final int check;

  MirrorCipher(String password)
      : _seed = _fnv1a(utf8.encode('pasalo-espejo:$password')),
        check = _fnv1a(utf8.encode('check:$password')) & 0xFFFF;

  Uint8List header() => Uint8List.fromList(
      [...magic, version, (check >> 8) & 0xFF, check & 0xFF]);

  /// Comprueba la cabecera y devuelve un mensaje de error o null si es válida.
  String? validateHeader(List<int> h) {
    if (h.length < headerSize) return 'El archivo es demasiado corto.';
    for (var i = 0; i < 4; i++) {
      if (h[i] != magic[i]) return 'No es un archivo de Cifrado Espejo.';
    }
    if (h[4] != version) return 'Versión de Cifrado Espejo desconocida.';
    if (((h[5] << 8) | h[6]) != check) return 'Contraseña incorrecta.';
    return null;
  }

  /// Cifra un bloque (de hasta [blockSize] bytes) que ocupa la posición
  /// [blockIndex] dentro del archivo.
  Uint8List encryptBlock(Uint8List block, int blockIndex) {
    final n = block.length;
    final mixed = Uint8List(n);
    for (var i = 0; i < n; i++) {
      final inverted = (~block[i]) & 0xFF;
      mixed[i] = _rotl(inverted, _rotation(blockIndex, i));
    }
    final perm = _permutation(n, blockIndex);
    final out = Uint8List(n);
    for (var j = 0; j < n; j++) {
      out[j] = mixed[perm[j]];
    }
    return out;
  }

  Uint8List decryptBlock(Uint8List block, int blockIndex) {
    final n = block.length;
    final perm = _permutation(n, blockIndex);
    final mixed = Uint8List(n);
    for (var j = 0; j < n; j++) {
      mixed[perm[j]] = block[j];
    }
    final out = Uint8List(n);
    for (var i = 0; i < n; i++) {
      out[i] = (~_rotr(mixed[i], _rotation(blockIndex, i))) & 0xFF;
    }
    return out;
  }

  Uint8List encryptBytes(Uint8List data) {
    final b = BytesBuilder(copy: false)..add(header());
    _forEachBlock(data, (blk, idx) => b.add(encryptBlock(blk, idx)));
    return b.takeBytes();
  }

  Uint8List decryptBytes(Uint8List data) {
    final err = validateHeader(data);
    if (err != null) throw FormatException(err);
    final b = BytesBuilder(copy: false);
    _forEachBlock(Uint8List.sublistView(data, headerSize),
        (blk, idx) => b.add(decryptBlock(blk, idx)));
    return b.takeBytes();
  }

  Future<void> encryptFile(File input, File output) async {
    final raf = await input.open();
    final sink = output.openWrite();
    try {
      sink.add(header());
      var idx = 0;
      while (true) {
        final blk = await raf.read(blockSize);
        if (blk.isEmpty) break;
        sink.add(encryptBlock(blk, idx++));
      }
    } finally {
      await raf.close();
      await sink.close();
    }
  }

  Future<void> decryptFile(File input, File output) async {
    final raf = await input.open();
    try {
      final err = validateHeader(await raf.read(headerSize));
      if (err != null) throw FormatException(err);
      final sink = output.openWrite();
      try {
        var idx = 0;
        while (true) {
          final blk = await raf.read(blockSize);
          if (blk.isEmpty) break;
          sink.add(decryptBlock(blk, idx++));
        }
      } finally {
        await sink.close();
      }
    } finally {
      await raf.close();
    }
  }

  static void _forEachBlock(
      Uint8List data, void Function(Uint8List, int) fn) {
    var idx = 0;
    for (var off = 0; off < data.length; off += blockSize) {
      final end = off + blockSize < data.length ? off + blockSize : data.length;
      fn(Uint8List.sublistView(data, off, end), idx++);
    }
  }

  int _rotation(int blockIndex, int i) =>
      ((_seed >> 3) + blockIndex * 7 + i * 5) & 7;

  /// Fisher-Yates con un xorshift32 sembrado por la contraseña y el bloque.
  List<int> _permutation(int n, int blockIndex) {
    final perm = List<int>.generate(n, (i) => i);
    var x = (_seed ^ (blockIndex * 0x9E3779B1)) & 0xFFFFFFFF;
    if (x == 0) x = 0x6D2B79F5;
    for (var i = n - 1; i > 0; i--) {
      x ^= (x << 13) & 0xFFFFFFFF;
      x ^= x >> 17;
      x ^= (x << 5) & 0xFFFFFFFF;
      final j = x % (i + 1);
      final t = perm[i];
      perm[i] = perm[j];
      perm[j] = t;
    }
    return perm;
  }

  static int _rotl(int b, int r) => ((b << r) | (b >> (8 - r))) & 0xFF;
  static int _rotr(int b, int r) => ((b >> r) | (b << (8 - r))) & 0xFF;

  static int _fnv1a(List<int> bytes) {
    var h = 0x811C9DC5;
    for (final b in bytes) {
      h ^= b;
      h = (h * 0x01000193) & 0xFFFFFFFF;
    }
    return h;
  }
}
