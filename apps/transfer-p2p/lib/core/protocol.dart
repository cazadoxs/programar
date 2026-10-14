import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;

import 'compressor.dart';

/// Un canal de bytes bidireccional: un socket TCP, Bluetooth RFCOMM o una
/// tubería en memoria para los tests.
abstract class ByteLink {
  Stream<Uint8List> get input;
  Future<void> write(Uint8List data);
  Future<void> close();
}

class SocketLink implements ByteLink {
  final Socket socket;
  int _unflushed = 0;
  SocketLink(this.socket) {
    socket.setOption(SocketOption.tcpNoDelay, true);
  }

  @override
  Stream<Uint8List> get input => socket;

  @override
  Future<void> write(Uint8List data) async {
    socket.add(data);
    _unflushed += data.length;
    if (_unflushed >= 256 * 1024 || data.length < 1024) {
      _unflushed = 0;
      await socket.flush();
    }
  }

  @override
  Future<void> close() async {
    try {
      await socket.flush();
      await socket.close();
    } catch (_) {}
    socket.destroy();
  }
}

enum FrameType { hello, accept, reject, fileStart, data, fileEnd, done, error }

class Frame {
  final FrameType type;
  final Uint8List payload;
  Frame(this.type, this.payload);
  Map<String, dynamic> get json =>
      jsonDecode(utf8.decode(payload)) as Map<String, dynamic>;
}

const int protocolVersion = 1;
const int maxFramePayload = 4 << 20;
const int dataChunkSize = 64 * 1024;

Uint8List encodeFrame(FrameType type, List<int> payload) {
  final out = Uint8List(5 + payload.length);
  out[0] = type.index + 1;
  ByteData.sublistView(out).setUint32(1, payload.length);
  out.setAll(5, payload);
  return out;
}

Uint8List encodeJsonFrame(FrameType type, Map<String, dynamic> json) =>
    encodeFrame(type, utf8.encode(jsonEncode(json)));

/// Lee tramas "tipo (1) | largo (u32) | datos" de un flujo de bytes.
class FrameReader {
  final StreamIterator<Uint8List> _it;
  final Queue<Uint8List> _chunks = Queue();
  int _buffered = 0;
  int _offset = 0;

  FrameReader(Stream<Uint8List> input) : _it = StreamIterator(input);

  Future<bool> _fill(int n) async {
    while (_buffered < n) {
      if (!await _it.moveNext()) return false;
      final c = _it.current;
      if (c.isEmpty) continue;
      _chunks.add(c);
      _buffered += c.length;
    }
    return true;
  }

  Uint8List _take(int n) {
    final out = Uint8List(n);
    var w = 0;
    while (w < n) {
      final c = _chunks.first;
      final avail = c.length - _offset;
      final k = avail < n - w ? avail : n - w;
      out.setRange(w, w + k, c, _offset);
      w += k;
      _offset += k;
      if (_offset == c.length) {
        _chunks.removeFirst();
        _offset = 0;
      }
    }
    _buffered -= n;
    return out;
  }

  /// La siguiente trama, o null si el otro lado cerró limpiamente.
  Future<Frame?> next() async {
    if (!await _fill(5)) {
      if (_buffered == 0) return null;
      throw const TransferException('La conexión se cortó a mitad de trama.');
    }
    final head = _take(5);
    final t = head[0];
    final len = ByteData.sublistView(head).getUint32(1);
    if (t < 1 || t > FrameType.values.length) {
      throw const TransferException('Trama desconocida: ¿es otra app?');
    }
    if (len > maxFramePayload) {
      throw const TransferException('Trama demasiado grande.');
    }
    if (!await _fill(len)) {
      throw const TransferException('La conexión se cortó.');
    }
    return Frame(FrameType.values[t - 1], _take(len));
  }

  Future<Frame> expect() async {
    final f = await next();
    if (f == null) throw const TransferException('El otro dispositivo cerró.');
    if (f.type == FrameType.error) {
      throw TransferException(
          (f.json['message'] as String?) ?? 'Error en el otro dispositivo.');
    }
    return f;
  }

  Future<void> cancel() => _it.cancel();
}

class TransferException implements Exception {
  final String message;
  const TransferException(this.message);
  @override
  String toString() => message;
}

class TransferRejected extends TransferException {
  const TransferRejected(super.message);
}

class OutgoingFile {
  final String name;
  final int size;
  final Stream<List<int>> Function() open;
  OutgoingFile(this.name, this.size, this.open);

  factory OutgoingFile.fromFile(File f) =>
      OutgoingFile(p.basename(f.path), f.lengthSync(), () => f.openRead());
}

class OfferedFile {
  final String name;
  final int size;
  OfferedFile(this.name, this.size);
}

class IncomingOffer {
  final String deviceName;
  final List<OfferedFile> files;
  IncomingOffer(this.deviceName, this.files);
  int get totalBytes => files.fold(0, (a, f) => a + f.size);
}

class TransferProgress {
  final int bytesDone;
  final int bytesTotal;
  final String currentFile;
  final int fileIndex;
  final int fileCount;
  TransferProgress(this.bytesDone, this.bytesTotal, this.currentFile,
      this.fileIndex, this.fileCount);
  double get fraction => bytesTotal == 0 ? 1 : bytesDone / bytesTotal;
}

/// Bandera de cancelación compartida entre la UI y la transferencia.
class CancelFlag {
  bool cancelled = false;
  void cancel() => cancelled = true;
}

/// Lado que envía: saluda, espera a que acepten y manda los archivos.
Future<void> sendFiles({
  required ByteLink link,
  required String token,
  required String deviceName,
  required List<OutgoingFile> files,
  void Function()? onAccepted,
  void Function(TransferProgress)? onProgress,
  CancelFlag? cancel,
}) async {
  final reader = FrameReader(link.input);
  try {
    await link.write(encodeJsonFrame(FrameType.hello, {
      'v': protocolVersion,
      'app': 'pasalo',
      'token': token,
      'name': deviceName,
      'files': [
        for (final f in files) {'name': f.name, 'size': f.size}
      ],
    }));
    final answer = await reader.expect();
    if (answer.type == FrameType.reject) {
      throw TransferRejected(
          (answer.json['reason'] as String?) ?? 'El otro dispositivo rechazó.');
    }
    if (answer.type != FrameType.accept) {
      throw const TransferException('Respuesta inesperada.');
    }
    onAccepted?.call();
    final total = files.fold<int>(0, (a, f) => a + f.size);
    var done = 0;
    for (var i = 0; i < files.length; i++) {
      final f = files[i];
      await link.write(encodeJsonFrame(
          FrameType.fileStart, {'i': i, 'name': f.name, 'size': f.size}));
      final digest = _DigestSink();
      final hasher = sha256.startChunkedConversion(digest);
      var sent = 0;
      await for (final chunk in f.open()) {
        for (var off = 0; off < chunk.length; off += dataChunkSize) {
          if (cancel?.cancelled ?? false) {
            await link.write(encodeJsonFrame(
                FrameType.error, {'message': 'El emisor canceló.'}));
            throw const TransferException('Cancelado.');
          }
          final end = off + dataChunkSize < chunk.length
              ? off + dataChunkSize
              : chunk.length;
          final piece = Uint8List.sublistView(
              chunk is Uint8List ? chunk : Uint8List.fromList(chunk),
              off,
              end);
          hasher.add(piece);
          await link.write(encodeFrame(FrameType.data, piece));
          sent += piece.length;
          done += piece.length;
          onProgress?.call(TransferProgress(done, total, f.name, i, files.length));
        }
      }
      hasher.close();
      if (sent != f.size) {
        throw TransferException('El archivo ${f.name} cambió mientras se enviaba.');
      }
      await link.write(encodeJsonFrame(
          FrameType.fileEnd, {'sha256': digest.value.toString()}));
    }
    await link.write(encodeFrame(FrameType.done, const []));
    final ack = await reader.expect();
    if (ack.type != FrameType.done) {
      throw const TransferException('El receptor no confirmó el final.');
    }
  } finally {
    await reader.cancel();
    await link.close();
  }
}

/// Lado que recibe. [checkToken] valida el código/enlace, [onOffer] pregunta
/// al usuario y [onAccepted] se llama justo cuando empieza la transferencia
/// (ahí es donde la app activa el servicio en segundo plano).
Future<List<File>> receiveFiles({
  required ByteLink link,
  required bool Function(String token) checkToken,
  required Future<bool> Function(IncomingOffer offer) onOffer,
  required String saveDir,
  void Function()? onAccepted,
  void Function(TransferProgress)? onProgress,
  CancelFlag? cancel,
}) async {
  final reader = FrameReader(link.input);
  final saved = <File>[];
  File? partFile;
  IOSink? sink;
  try {
    final hello = await reader.expect();
    if (hello.type != FrameType.hello) {
      throw const TransferException('Saludo inválido.');
    }
    final h = hello.json;
    if (h['app'] != 'pasalo' || h['v'] != protocolVersion) {
      await link.write(encodeJsonFrame(
          FrameType.reject, {'reason': 'Versión de Pásalo incompatible.'}));
      throw const TransferException('Versión de Pásalo incompatible.');
    }
    if (!checkToken((h['token'] as String?) ?? '')) {
      await link.write(encodeJsonFrame(
          FrameType.reject, {'reason': 'Código o enlace incorrecto.'}));
      throw const TransferRejected('Código o enlace incorrecto.');
    }
    final files = [
      for (final f in (h['files'] as List))
        OfferedFile(sanitizeFileName((f['name'] as String?) ?? 'archivo'),
            (f['size'] as num).toInt())
    ];
    if (files.any((f) => f.size < 0)) {
      throw const TransferException('Tamaño de archivo inválido.');
    }
    final offer =
        IncomingOffer(((h['name'] as String?) ?? 'Dispositivo').trim(), files);
    if (!await onOffer(offer)) {
      await link.write(
          encodeJsonFrame(FrameType.reject, {'reason': 'Rechazado por el receptor.'}));
      throw const TransferRejected('Rechazado.');
    }
    await Directory(saveDir).create(recursive: true);
    await link.write(encodeFrame(FrameType.accept, const []));
    onAccepted?.call();

    final total = offer.totalBytes;
    var done = 0;
    var index = -1;
    var expected = 0;
    var got = 0;
    _DigestSink? digest;
    ByteConversionSink? hasher;
    while (true) {
      if (cancel?.cancelled ?? false) {
        await link.write(
            encodeJsonFrame(FrameType.error, {'message': 'El receptor canceló.'}));
        throw const TransferException('Cancelado.');
      }
      final f = await reader.expect();
      switch (f.type) {
        case FrameType.fileStart:
          index++;
          if (index >= files.length || sink != null) {
            throw const TransferException('Secuencia de archivos inválida.');
          }
          expected = files[index].size;
          got = 0;
          partFile = File('${uniquePath(saveDir, files[index].name)}.part');
          sink = partFile.openWrite();
          digest = _DigestSink();
          hasher = sha256.startChunkedConversion(digest);
        case FrameType.data:
          if (sink == null) throw const TransferException('Datos sin archivo.');
          got += f.payload.length;
          if (got > expected) {
            throw const TransferException('Llegaron más datos de los anunciados.');
          }
          sink.add(f.payload);
          hasher!.add(f.payload);
          done += f.payload.length;
          onProgress?.call(
              TransferProgress(done, total, files[index].name, index, files.length));
        case FrameType.fileEnd:
          if (sink == null) throw const TransferException('Fin sin archivo.');
          await sink.close();
          sink = null;
          hasher!.close();
          if (got != expected ||
              f.json['sha256'] != digest!.value.toString()) {
            throw TransferException(
                'El archivo ${files[index].name} llegó dañado.');
          }
          final finalPath = uniquePath(saveDir, files[index].name);
          saved.add(await partFile!.rename(finalPath));
          partFile = null;
        case FrameType.done:
          if (index != files.length - 1 || sink != null) {
            throw const TransferException('Faltan archivos.');
          }
          await link.write(encodeFrame(FrameType.done, const []));
          return saved;
        default:
          throw const TransferException('Trama inesperada.');
      }
    }
  } finally {
    await sink?.close();
    if (partFile != null && await partFile.exists()) await partFile.delete();
    await reader.cancel();
    await link.close();
  }
}

class _DigestSink implements Sink<Digest> {
  late Digest value;
  @override
  void add(Digest data) => value = data;
  @override
  void close() {}
}
