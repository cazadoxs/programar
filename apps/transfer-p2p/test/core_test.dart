import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:pasalo/core/aes_cipher.dart';
import 'package:pasalo/core/bit_image.dart';
import 'package:pasalo/core/compressor.dart';
import 'package:pasalo/core/host.dart';
import 'package:pasalo/core/mirror_cipher.dart';
import 'package:pasalo/core/pairing.dart';
import 'package:pasalo/core/protocol.dart';
import 'package:path/path.dart' as p;

Uint8List randomBytes(int n, [int seed = 1]) {
  final r = Random(seed);
  return Uint8List.fromList(List.generate(n, (_) => r.nextInt(256)));
}

class PipeLink implements ByteLink {
  @override
  final Stream<Uint8List> input;
  final StreamController<Uint8List> _out;
  PipeLink(this.input, this._out);
  @override
  Future<void> write(Uint8List data) async => _out.add(Uint8List.fromList(data));
  @override
  Future<void> close() async {
    if (!_out.isClosed) await _out.close();
  }
}

(PipeLink, PipeLink) pipe() {
  final a = StreamController<Uint8List>();
  final b = StreamController<Uint8List>();
  return (PipeLink(a.stream, b), PipeLink(b.stream, a));
}

void main() {
  late Directory tmp;
  setUp(() => tmp = Directory.systemTemp.createTempSync('pasalo_test'));
  tearDown(() => tmp.deleteSync(recursive: true));

  group('Cifrado Espejo', () {
    test('invierte los bits y mezcla, y vuelve al original', () {
      final data = randomBytes(10000);
      final c = MirrorCipher('clave');
      final enc = c.encryptBytes(data);
      expect(enc.length, data.length + MirrorCipher.headerSize);
      expect(enc.sublist(MirrorCipher.headerSize), isNot(equals(data)));
      expect(c.decryptBytes(enc), equals(data));
    });

    test('sin mezcla ni rotación sería la inversión pura', () {
      // Un único byte: la permutación no cambia nada, así que el resultado
      // es el byte invertido y rotado.
      final c = MirrorCipher('');
      final out = c.encryptBlock(Uint8List.fromList([0x0F]), 0);
      final back = c.decryptBlock(out, 0);
      expect(back, [0x0F]);
      final ones = out[0].toRadixString(2).replaceAll('0', '').length;
      expect(ones, 4); // ~0x0F = 0xF0 tiene 4 unos y rotar no cambia eso.
    });

    test('contraseña incorrecta se detecta', () {
      final enc = MirrorCipher('a').encryptBytes(randomBytes(100));
      expect(() => MirrorCipher('b').decryptBytes(enc),
          throwsA(isA<FormatException>()));
    });

    test('archivos por bloques', () async {
      final src = File(p.join(tmp.path, 'a.bin'))
        ..writeAsBytesSync(randomBytes(MirrorCipher.blockSize * 3 + 17));
      final enc = File(p.join(tmp.path, 'a.esp'));
      final dec = File(p.join(tmp.path, 'a.out'));
      await MirrorCipher('x').encryptFile(src, enc);
      await MirrorCipher('x').decryptFile(enc, dec);
      expect(dec.readAsBytesSync(), src.readAsBytesSync());
      expect(MirrorCipher('x').encryptBytes(src.readAsBytesSync()),
          enc.readAsBytesSync());
    });
  });

  group('AES-256-GCM', () {
    test('ida y vuelta con varios trozos', () async {
      final src = File(p.join(tmp.path, 'b.bin'))
        ..writeAsBytesSync(randomBytes(AesFileCipher.chunkSize * 2 + 5));
      final enc = File(p.join(tmp.path, 'b.aes'));
      final dec = File(p.join(tmp.path, 'b.out'));
      await AesFileCipher(iterations: 1000).encryptFile(src, enc, 'secreto');
      await AesFileCipher.decryptFile(enc, dec, 'secreto');
      expect(dec.readAsBytesSync(), src.readAsBytesSync());
    });

    test('archivo vacío', () async {
      final src = File(p.join(tmp.path, 'e'))..writeAsBytesSync([]);
      final enc = File(p.join(tmp.path, 'e.aes'));
      final dec = File(p.join(tmp.path, 'e.out'));
      await AesFileCipher(iterations: 1000).encryptFile(src, enc, 'k');
      await AesFileCipher.decryptFile(enc, dec, 'k');
      expect(dec.readAsBytesSync(), isEmpty);
    });

    test('contraseña mala o archivo cortado fallan', () async {
      final src = File(p.join(tmp.path, 'c'))
        ..writeAsBytesSync(randomBytes(AesFileCipher.chunkSize + 100));
      final enc = File(p.join(tmp.path, 'c.aes'));
      final dec = File(p.join(tmp.path, 'c.out'));
      await AesFileCipher(iterations: 1000).encryptFile(src, enc, 'bien');
      await expectLater(AesFileCipher.decryptFile(enc, dec, 'mal'),
          throwsA(isA<FormatException>()));
      expect(dec.existsSync(), isFalse);
      final bytes = enc.readAsBytesSync();
      final cut = File(p.join(tmp.path, 'cut.aes'))
        ..writeAsBytesSync(bytes.sublist(
            0, AesFileCipher.headerSize + AesFileCipher.chunkSize + 16));
      await expectLater(AesFileCipher.decryptFile(cut, dec, 'bien'),
          throwsA(isA<FormatException>()));
    });
  });

  group('Archivo a imagen', () {
    for (final mode in BitImageMode.values) {
      test('modo ${mode.name}', () {
        final data = randomBytes(5000, mode.index + 3);
        final png = BitImage.encode(data, 'notas ñ.txt', mode);
        final back = BitImage.decode(png);
        expect(back.name, 'notas ñ.txt');
        expect(back.mode, mode);
        expect(back.data, data);
      });
    }

    test('una imagen cualquiera no se acepta', () {
      final png = BitImage.encode(randomBytes(10), 'x', BitImageMode.color);
      final broken = Uint8List.fromList(png);
      expect(() => BitImage.decode(Uint8List.fromList([1, 2, 3])),
          throwsA(isA<FormatException>()));
      expect(BitImage.decode(broken).data.length, 10);
    });
  });

  group('Compresor', () {
    test('un archivo va a .gz y vuelve', () async {
      final src = File(p.join(tmp.path, 'texto.txt'))
        ..writeAsStringSync('hola ' * 2000);
      final gz = await Compressor.compress([src.path], p.join(tmp.path, 'out'));
      expect(gz.path.endsWith('texto.txt.gz'), isTrue);
      expect(gz.lengthSync(), lessThan(src.lengthSync()));
      final files =
          await Compressor.decompress(gz.path, p.join(tmp.path, 'dec'));
      expect(File(files.single).readAsStringSync(), src.readAsStringSync());
    });

    test('varios archivos van a .zip y vuelven', () async {
      final a = File(p.join(tmp.path, 'a.txt'))..writeAsStringSync('A' * 500);
      final b = File(p.join(tmp.path, 'b.bin'))..writeAsBytesSync(randomBytes(800));
      final zip = await Compressor.compress(
          [a.path, b.path], p.join(tmp.path, 'out'));
      final files =
          await Compressor.decompress(zip.path, p.join(tmp.path, 'dec'));
      expect(files.map(p.basename).toSet(), {'a.txt', 'b.bin'});
      expect(File(p.join(tmp.path, 'dec', 'b.bin')).readAsBytesSync(),
          b.readAsBytesSync());
    });

    test('nombres peligrosos se limpian', () {
      expect(sanitizeFileName('../../etc/passwd'), 'passwd');
      expect(sanitizeFileName(r'C:\Windows\a:b?.txt'), 'a_b_.txt');
      expect(sanitizeFileName('CON.txt'), '_CON.txt');
      expect(sanitizeFileName('...'), 'archivo');
    });
  });

  group('Emparejamiento', () {
    test('enlace de ida y vuelta', () {
      final info = PairingInfo('192.168.1.20', 47820, 'abc_DEF-1', name: 'PC de Álvaro');
      final parsed = PairingInfo.parse('Toca aquí: ${info.toLink()}')!;
      expect(parsed.host, '192.168.1.20');
      expect(parsed.port, 47820);
      expect(parsed.token, 'abc_DEF-1');
      expect(parsed.name, 'PC de Álvaro');
    });

    test('enlaces inválidos', () {
      expect(PairingInfo.parse('hola'), isNull);
      expect(PairingInfo.parse('pasalo://c?h=evil.com&p=1&t=x'), isNull);
      expect(PairingInfo.parse('pasalo://c?h=1.2.3.4&p=99999&t=x'), isNull);
    });

    test('código de 6 cifras', () {
      expect(newCode(), matches(RegExp(r'^\d{6}$')));
      expect(newToken().length, greaterThanOrEqualTo(20));
    });
  });

  group('Protocolo', () {
    Future<List<File>> run(List<OutgoingFile> files,
        {bool accept = true, String token = 't'}) async {
      final (a, b) = pipe();
      final progress = <double>[];
      final recv = receiveFiles(
        link: b,
        checkToken: (t) => t == 't',
        onOffer: (o) async => accept,
        saveDir: p.join(tmp.path, 'recibidos'),
        onProgress: (pr) => progress.add(pr.fraction),
      );
      final send = sendFiles(
          link: a, token: token, deviceName: 'Móvil', files: files);
      final results = await Future.wait([recv, send.then((_) => <File>[])]);
      if (files.isNotEmpty && files.any((f) => f.size > 0)) {
        expect(progress.last, 1.0);
      }
      return results.first;
    }

    test('envía varios archivos íntegros', () async {
      final d1 = randomBytes(200000, 7), d2 = randomBytes(10, 8);
      final saved = await run([
        OutgoingFile('uno.bin', d1.length, () => Stream.value(d1)),
        OutgoingFile('../dos.bin', d2.length, () => Stream.value(d2)),
        OutgoingFile('vacío.txt', 0, () => const Stream.empty()),
      ]);
      expect(saved.map((f) => p.basename(f.path)).toList(),
          ['uno.bin', 'dos.bin', 'vacío.txt']);
      expect(saved[0].readAsBytesSync(), d1);
      expect(saved[1].readAsBytesSync(), d2);
    });

    test('rechazo y código incorrecto', () async {
      final f = [OutgoingFile('a', 1, () => Stream.value([1]))];
      await expectLater(run(f, accept: false), throwsA(isA<TransferRejected>()));
      await expectLater(run(f, token: 'malo'), throwsA(isA<TransferRejected>()));
    });

    test('tamaño mentido se detecta', () async {
      final f = [OutgoingFile('a', 1, () => Stream.value([1, 2, 3]))];
      await expectLater(run(f), throwsA(isA<TransferException>()));
      expect(Directory(p.join(tmp.path, 'recibidos')).listSync(), isEmpty);
    });
  });

  test('TCP real + búsqueda por código en la red local', () async {
    final saveDir = p.join(tmp.path, 'tcp');
    final received = Completer<List<File>>();
    final host = await TransferHost.start(
      name: 'PC',
      tcpPort: 0,
      udpPort: 0,
      beacon: false,
      onConnection: (link, check) async {
        received.complete(await receiveFiles(
            link: link,
            checkToken: check,
            onOffer: (_) async => true,
            saveDir: saveDir));
      },
    );
    final beacon = await CodeBeacon.start(
        code: host.code, tcpPort: host.port, name: 'PC', port: 47999);
    final found = await findByCode(host.code,
        port: 47999, extraTargets: [InternetAddress.loopbackIPv4]);
    expect(found, isNotNull);
    expect(found!.port, host.port);
    final data = randomBytes(300000, 9);
    final socket = await Socket.connect(InternetAddress.loopbackIPv4, found.port);
    await sendFiles(
        link: SocketLink(socket),
        token: found.token,
        deviceName: 'Móvil',
        files: [OutgoingFile('foto.jpg', data.length, () => Stream.value(data))]);
    final files = await received.future;
    expect(files.single.readAsBytesSync(), data);
    beacon.close();
    await host.close();
  });

  test('cinco códigos malos cambian el código', () async {
    String? changed;
    final host = await TransferHost.start(
        name: 'x',
        tcpPort: 0,
        beacon: false,
        onConnection: (_, _) async {},
        onCodeChanged: (c) => changed = c);
    final old = host.code;
    for (var i = 0; i < 5; i++) {
      expect(host.checkToken('000000x'), isFalse);
    }
    expect(changed, isNotNull);
    expect(host.code, isNot(old));
    expect(host.checkToken(host.token), isTrue);
    await host.close();
  });
}
