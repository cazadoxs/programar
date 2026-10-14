import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:archive/archive.dart' show getCrc32;
import 'package:image/image.dart' as img;

/// Cómo se guardan los bits del archivo dentro de la imagen.
enum BitImageMode {
  /// Cada píxel guarda 3 bytes (rojo, verde, azul). Imagen compacta y de
  /// colores "ruido".
  color,

  /// Cada píxel es un bit: negro = 1, blanco = 0. Se ve literalmente el
  /// archivo como una imagen de bits, pero ocupa 8 píxeles por byte.
  bits,
}

class DecodedBitImage {
  final String name;
  final Uint8List data;
  final BitImageMode mode;
  DecodedBitImage(this.name, this.data, this.mode);
}

/// Convierte cualquier archivo en una imagen PNG y viceversa.
///
/// Carga útil: "PSLI" | versión | modo | largo nombre (u16) | nombre UTF-8
/// | largo datos (u32) | CRC32 datos (u32) | datos.
/// La imagen tiene que seguir siendo PNG sin retocar: si se recomprime (por
/// ejemplo al mandarla como foto por WhatsApp) los bits se pierden.
class BitImage {
  static const List<int> magic = [0x50, 0x53, 0x4C, 0x49]; // "PSLI"
  static const int version = 1;
  static const int maxColorBytes = 64 << 20;
  static const int maxBitsBytes = 4 << 20;

  static Uint8List encode(Uint8List data, String name, BitImageMode mode) {
    final limit = mode == BitImageMode.color ? maxColorBytes : maxBitsBytes;
    if (data.length > limit) {
      throw ArgumentError(
          'Archivo demasiado grande para este modo (máximo ${limit >> 20} MB).');
    }
    final nameBytes = utf8.encode(name);
    final payload = BytesBuilder(copy: false)
      ..add(magic)
      ..add([version, mode.index, nameBytes.length >> 8, nameBytes.length & 0xFF])
      ..add(nameBytes)
      ..add(_u32(data.length))
      ..add(_u32(getCrc32(data)))
      ..add(data);
    final bytes = payload.takeBytes();

    if (mode == BitImageMode.color) {
      final pixels = (bytes.length + 2) ~/ 3;
      final w = max(1, sqrt(pixels).ceil());
      final h = (pixels + w - 1) ~/ w;
      final buf = Uint8List(w * h * 3)..setAll(0, bytes);
      final image = img.Image.fromBytes(
          width: w, height: h, bytes: buf.buffer, numChannels: 3);
      return img.encodePng(image);
    } else {
      final bits = bytes.length * 8;
      final w = max(8, (sqrt(bits).ceil() + 7) ~/ 8 * 8);
      final h = (bits + w - 1) ~/ w;
      final buf = Uint8List(w * h)..fillRange(0, w * h, 255);
      for (var i = 0; i < bytes.length; i++) {
        final b = bytes[i];
        for (var k = 0; k < 8; k++) {
          if ((b >> (7 - k)) & 1 == 1) buf[i * 8 + k] = 0;
        }
      }
      final image = img.Image.fromBytes(
          width: w, height: h, bytes: buf.buffer, numChannels: 1);
      return img.encodePng(image);
    }
  }

  static DecodedBitImage decode(Uint8List png) {
    final image = img.decodePng(png);
    if (image == null) throw const FormatException('No es una imagen PNG.');
    final rgb = image.getBytes(order: img.ChannelOrder.rgb);
    if (_hasMagic(rgb)) return _parse(rgb, BitImageMode.color);
    final red = image.getBytes(order: img.ChannelOrder.red);
    final bytes = Uint8List(red.length ~/ 8);
    for (var i = 0; i < bytes.length; i++) {
      var b = 0;
      for (var k = 0; k < 8; k++) {
        b = (b << 1) | (red[i * 8 + k] < 128 ? 1 : 0);
      }
      bytes[i] = b;
    }
    if (_hasMagic(bytes)) return _parse(bytes, BitImageMode.bits);
    throw const FormatException(
        'Esta imagen no contiene un archivo de Pásalo (o ha sido recomprimida).');
  }

  static bool _hasMagic(Uint8List b) =>
      b.length >= 4 &&
      b[0] == magic[0] &&
      b[1] == magic[1] &&
      b[2] == magic[2] &&
      b[3] == magic[3];

  static DecodedBitImage _parse(Uint8List b, BitImageMode mode) {
    final bd = ByteData.sublistView(b);
    if (b.length < 8 || b[4] != version) {
      throw const FormatException('Versión de imagen desconocida.');
    }
    final nameLen = bd.getUint16(6);
    var p = 8 + nameLen;
    if (b.length < p + 8) throw const FormatException('Imagen incompleta.');
    final name = utf8.decode(b.sublist(8, p), allowMalformed: true);
    final len = bd.getUint32(p);
    final crc = bd.getUint32(p + 4);
    p += 8;
    if (b.length < p + len) throw const FormatException('Imagen incompleta.');
    final data = Uint8List.fromList(b.sublist(p, p + len));
    if (getCrc32(data) != crc) {
      throw const FormatException('Los datos de la imagen están dañados.');
    }
    return DecodedBitImage(name, data, mode);
  }

  static List<int> _u32(int v) =>
      [(v >> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF];
}
