import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../core/protocol.dart';

/// Puente con el código nativo de Android. En Windows casi todo es "no-op".
class Native {
  static const _ch = MethodChannel('pasalo/native');
  static const _bt = EventChannel('pasalo/bt');
  static bool get isAndroid => Platform.isAndroid;

  static void onLink(void Function(String link) handler) {
    if (!isAndroid) return;
    _ch.setMethodCallHandler((call) async {
      if (call.method == 'onLink') handler(call.arguments as String);
    });
  }

  static Future<String?> initialLink() async =>
      isAndroid ? _ch.invokeMethod<String>('getInitialLink') : null;

  static Future<String> deviceName() async {
    if (isAndroid) {
      return (await _ch.invokeMethod<String>('deviceName')) ?? 'Android';
    }
    return Platform.localHostname;
  }

  static Future<int> sdkInt() async =>
      isAndroid ? (await _ch.invokeMethod<int>('sdkInt')) ?? 0 : 0;

  /// Carpeta donde se guarda lo recibido: Descargas/Pasalo.
  static Future<String> saveDir() async {
    String? base;
    try {
      if (isAndroid) {
        base = await _ch.invokeMethod<String>('downloadsDir');
      } else {
        base = (await getDownloadsDirectory())?.path;
      }
      if (base != null) {
        final d = Directory(p.join(base, 'Pasalo'));
        await d.create(recursive: true);
        // Comprobamos que de verdad se puede escribir.
        final probe = File(p.join(d.path, '.pasalo'));
        await probe.writeAsString('ok');
        await probe.delete();
        return d.path;
      }
    } catch (_) {}
    final fallback = isAndroid
        ? (await getExternalStorageDirectory())?.path
        : (await getApplicationDocumentsDirectory()).path;
    final d = Directory(p.join(
        fallback ?? (await getApplicationDocumentsDirectory()).path, 'Pasalo'));
    await d.create(recursive: true);
    return d.path;
  }

  static Future<void> keepAliveStart(String title, String text) async {
    if (!isAndroid) return;
    try {
      await _ch.invokeMethod('keepAliveStart', {'title': title, 'text': text});
    } catch (_) {}
  }

  static Future<void> keepAliveUpdate(String title, String text, int percent) async {
    if (!isAndroid) return;
    try {
      await _ch.invokeMethod(
          'keepAliveUpdate', {'title': title, 'text': text, 'percent': percent});
    } catch (_) {}
  }

  static Future<void> keepAliveStop() async {
    if (!isAndroid) return;
    try {
      await _ch.invokeMethod('keepAliveStop');
    } catch (_) {}
  }

  static Future<void> multicastLock(bool on) async {
    if (!isAndroid) return;
    try {
      await _ch.invokeMethod('multicastLock', {'on': on});
    } catch (_) {}
  }

  static Future<void> scanFile(String path) async {
    if (!isAndroid) return;
    try {
      await _ch.invokeMethod('scanFile', {'path': path});
    } catch (_) {}
  }

  /// Crea un grupo Wi-Fi Direct. Devuelve ssid, pass e ip.
  static Future<Map<String, String>> wifiDirectCreate() async {
    final m = await _ch.invokeMapMethod<String, String>('wifiDirectCreate');
    return m!;
  }

  static Future<void> wifiDirectRemove() async {
    if (!isAndroid) return;
    try {
      await _ch.invokeMethod('wifiDirectRemove');
    } catch (_) {}
  }

  static Future<bool> btAvailable() async =>
      isAndroid && ((await _ch.invokeMethod<bool>('btAvailable')) ?? false);

  static Future<List<BtDevice>> btBonded() async {
    final list = await _ch.invokeListMethod<Map>('btBonded') ?? [];
    return [
      for (final m in list)
        BtDevice(m['name'] as String, m['address'] as String)
    ];
  }

  /// Escucha (receptor) o conecta (emisor) y devuelve el canal cuando hay
  /// conexión. [cancel] corta la espera.
  static Future<BluetoothLink> btOpen({String? address, Completer<void>? cancel}) async {
    final events = _bt.receiveBroadcastStream().cast<Map>();
    final input = StreamController<Uint8List>();
    final ready = Completer<BluetoothLink>();
    late StreamSubscription sub;
    sub = events.listen((e) {
      switch (e['type']) {
        case 'connected':
          if (!ready.isCompleted) {
            ready.complete(BluetoothLink._(input, sub, e['name'] as String? ?? ''));
          }
        case 'data':
          input.add(e['data'] as Uint8List);
        case 'closed':
          if (!input.isClosed) input.close();
        case 'error':
          if (!ready.isCompleted) {
            ready.completeError(TransferException(e['message'] as String? ?? 'Error de Bluetooth'));
          } else if (!input.isClosed) {
            input.close();
          }
      }
    });
    try {
      if (address == null) {
        await _ch.invokeMethod('btListen');
      } else {
        await _ch.invokeMethod('btConnect', {'address': address});
      }
      cancel?.future.then((_) {
        if (!ready.isCompleted) {
          ready.completeError(const TransferException('Cancelado.'));
        }
      });
      return await ready.future;
    } catch (_) {
      await sub.cancel();
      await _ch.invokeMethod('btClose');
      rethrow;
    }
  }
}

class BtDevice {
  final String name;
  final String address;
  BtDevice(this.name, this.address);
}

class BluetoothLink implements ByteLink {
  final StreamController<Uint8List> _input;
  final StreamSubscription _sub;
  final String remoteName;
  BluetoothLink._(this._input, this._sub, this.remoteName);

  @override
  Stream<Uint8List> get input => _input.stream;

  @override
  Future<void> write(Uint8List data) =>
      Native._ch.invokeMethod('btWrite', {'data': data});

  @override
  Future<void> close() async {
    await Native._ch.invokeMethod('btClose');
    await _sub.cancel();
    if (!_input.isClosed) await _input.close();
  }
}
