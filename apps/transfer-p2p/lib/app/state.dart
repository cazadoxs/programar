import 'dart:async';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:path/path.dart' as p;

import '../core/host.dart';
import '../core/pairing.dart';
import '../core/protocol.dart';
import '../platform/native.dart';

enum ReceiveMode { wifi, wifiDirect, bluetooth }

enum Direction { send, receive }

enum Phase { connecting, waiting, running, done, failed }

class TransferStatus {
  final Direction direction;
  String peer;
  Phase phase;
  TransferProgress? progress;
  String? message;
  List<File> files = [];
  final CancelFlag cancel = CancelFlag();
  TransferStatus(this.direction, this.peer, this.phase);
  bool get active =>
      phase == Phase.connecting || phase == Phase.waiting || phase == Phase.running;
}

class PendingOffer {
  final IncomingOffer offer;
  final Completer<bool> answer = Completer();
  PendingOffer(this.offer);
}

/// Estado global de la app: recibir, enviar y el ciclo de vida.
class AppState extends ChangeNotifier with WidgetsBindingObserver {
  AppState._();
  static final AppState instance = AppState._();

  String deviceName = 'Pásalo';
  String saveDir = '';
  bool foreground = true;

  // Recepción
  ReceiveMode? receiving;
  TransferHost? host;
  Map<String, String>? wifiDirect;
  List<String> localIps = [];
  String? receiveError;
  PendingOffer? pendingOffer;
  Completer<void>? _btCancel;

  // Transferencia en curso (una a la vez)
  TransferStatus? status;

  // Archivos preparados para enviar y enlace abierto desde fuera
  final List<String> toSend = [];
  PairingInfo? incomingLink;
  String? _lastLink;
  DateTime _lastNotify = DateTime.fromMillisecondsSinceEpoch(0);
  DateTime _lastKeepAlive = DateTime.fromMillisecondsSinceEpoch(0);

  Future<void> init() async {
    WidgetsBinding.instance.addObserver(this);
    deviceName = await Native.deviceName();
    saveDir = await Native.saveDir();
    Native.onLink(openLink);
    final l = await Native.initialLink();
    if (l != null) openLink(l);
    notifyListeners();
  }

  void openLink(String link) {
    if (link == _lastLink) return;
    _lastLink = link;
    final info = PairingInfo.parse(link);
    if (info != null) {
      incomingLink = info;
      notifyListeners();
    }
  }

  void clearIncomingLink() {
    incomingLink = null;
    notifyListeners();
  }

  // ───────────────────────── ciclo de vida ─────────────────────────

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // En Android "salir de la app" la manda a segundo plano: lo que ya se
    // está transfiriendo sigue; lo nuevo se rechaza hasta que vuelva.
    // En Windows minimizar no cuenta como salir.
    if (!Platform.isAndroid) return;
    final fg = state == AppLifecycleState.resumed;
    if (fg == foreground) return;
    foreground = fg;
    host?.acceptingNew = fg;
    if (!fg && pendingOffer != null && !pendingOffer!.answer.isCompleted) {
      pendingOffer!.answer.complete(false);
    }
    notifyListeners();
  }

  // ───────────────────────── recibir ─────────────────────────

  Future<void> startReceiving(ReceiveMode mode) async {
    await stopReceiving();
    receiveError = null;
    receiving = mode;
    notifyListeners();
    try {
      if (mode == ReceiveMode.bluetooth) {
        unawaited(_bluetoothLoop());
        return;
      }
      if (mode == ReceiveMode.wifiDirect) {
        wifiDirect = await Native.wifiDirectCreate();
      }
      await Native.multicastLock(true);
      host = await TransferHost.start(
        name: deviceName,
        onConnection: (link, check) => _receive(link, check),
        onCodeChanged: (_) => notifyListeners(),
      );
      host!.acceptingNew = foreground;
      localIps = mode == ReceiveMode.wifiDirect
          ? [wifiDirect!['ip']!]
          : await localIPv4();
    } catch (e) {
      receiveError = '$e';
      receiving = null;
      await Native.wifiDirectRemove();
    }
    notifyListeners();
  }

  Future<void> _bluetoothLoop() async {
    while (receiving == ReceiveMode.bluetooth) {
      _btCancel = Completer();
      try {
        final link = await Native.btOpen(cancel: _btCancel);
        await _receive(link, (t) => t == 'bt');
      } on TransferException catch (e) {
        if (receiving != ReceiveMode.bluetooth) return;
        receiveError = e.message;
        notifyListeners();
        await Future.delayed(const Duration(seconds: 2));
      } catch (e) {
        receiveError = '$e';
        receiving = null;
        notifyListeners();
        return;
      }
    }
  }

  Future<void> stopReceiving() async {
    final was = receiving;
    receiving = null;
    if (was == ReceiveMode.bluetooth) {
      _btCancel?.complete();
      _btCancel = null;
    }
    await host?.close();
    host = null;
    if (was == ReceiveMode.wifiDirect) await Native.wifiDirectRemove();
    wifiDirect = null;
    await Native.multicastLock(false);
    notifyListeners();
  }

  String? get link {
    final h = host;
    if (h == null || localIps.isEmpty) return null;
    return PairingInfo(localIps.first, h.port, h.token, name: deviceName).toLink();
  }

  Future<void> _receive(ByteLink link, bool Function(String) check) async {
    final st = TransferStatus(Direction.receive, '', Phase.waiting);
    try {
      final files = await receiveFiles(
        link: link,
        checkToken: check,
        saveDir: saveDir,
        cancel: st.cancel,
        onOffer: (offer) async {
          if (!foreground || status?.active == true) return false;
          st.peer = offer.deviceName;
          final po = PendingOffer(offer);
          pendingOffer = po;
          notifyListeners();
          final ok = await po.answer.future.timeout(
              const Duration(minutes: 2),
              onTimeout: () => false);
          pendingOffer = null;
          if (ok) status = st;
          notifyListeners();
          return ok;
        },
        onAccepted: () {
          st.phase = Phase.running;
          Native.keepAliveStart('Recibiendo de ${st.peer}', 'Empezando…');
          notifyListeners();
        },
        onProgress: (pr) => _progress(st, pr),
      );
      st.files = files;
      st.phase = Phase.done;
      for (final f in files) {
        await Native.scanFile(f.path);
      }
    } on TransferRejected {
      if (identical(status, st)) st.phase = Phase.failed;
      pendingOffer = null;
    } catch (e) {
      st.phase = Phase.failed;
      st.message = '$e';
      pendingOffer = null;
    } finally {
      if (identical(status, st)) await Native.keepAliveStop();
      notifyListeners();
    }
  }

  void answerOffer(bool accept) {
    final po = pendingOffer;
    if (po != null && !po.answer.isCompleted) po.answer.complete(accept);
  }

  // ───────────────────────── enviar ─────────────────────────

  Future<void> sendByLink(PairingInfo info) => _send(
      info.name.isEmpty ? info.host : info.name,
      () async => SocketLink(await Socket.connect(info.host, info.port,
          timeout: const Duration(seconds: 8))),
      info.token);

  Future<void> sendByCode(String code) async {
    final st = _beginSend('código $code');
    if (st == null) return;
    await Native.multicastLock(true);
    PairingInfo? info;
    try {
      info = await findByCode(code);
    } finally {
      await Native.multicastLock(false);
    }
    if (info == null) {
      _fail(st, 'No hay ningún receptor con el código $code en esta red. '
          'Comprobad que estáis en la misma Wi-Fi.');
      return;
    }
    final found = info;
    await _send(found.name.isEmpty ? found.host : found.name,
        () async => SocketLink(await Socket.connect(found.host, found.port,
            timeout: const Duration(seconds: 8))),
        code,
        existing: st);
  }

  Future<void> sendByBluetooth(BtDevice d) => _send(
      d.name, () async => Native.btOpen(address: d.address), 'bt');

  TransferStatus? _beginSend(String peer) {
    if (status?.active == true || toSend.isEmpty) return null;
    final st = TransferStatus(Direction.send, peer, Phase.connecting);
    status = st;
    // El usuario acaba de pulsar "Enviar": la transferencia ya ha empezado
    // y puede seguir aunque salga de la app.
    Native.keepAliveStart('Enviando a $peer', 'Conectando…');
    notifyListeners();
    return st;
  }

  void _fail(TransferStatus st, String msg) {
    st.phase = Phase.failed;
    st.message = msg;
    Native.keepAliveStop();
    notifyListeners();
  }

  Future<void> _send(String peer, Future<ByteLink> Function() connect, String token,
      {TransferStatus? existing}) async {
    final st = existing ?? _beginSend(peer);
    if (st == null) return;
    st.peer = peer;
    try {
      final link = await connect();
      if (link is BluetoothLink && link.remoteName.isNotEmpty) st.peer = link.remoteName;
      st.phase = Phase.waiting;
      notifyListeners();
      final files = [for (final f in toSend) OutgoingFile.fromFile(File(f))];
      await sendFiles(
        link: link,
        token: token,
        deviceName: deviceName,
        files: files,
        cancel: st.cancel,
        onAccepted: () {
          st.phase = Phase.running;
          notifyListeners();
        },
        onProgress: (pr) => _progress(st, pr),
      );
      st.phase = Phase.done;
      st.files = [for (final f in toSend) File(f)];
      toSend.clear();
    } on TransferRejected catch (e) {
      st.phase = Phase.failed;
      st.message = e.message;
    } on SocketException catch (e) {
      st.phase = Phase.failed;
      st.message = 'No se pudo conectar (${e.osError?.message ?? e.message}). '
          '¿Estáis en la misma red y el receptor sigue abierto?';
    } catch (e) {
      st.phase = Phase.failed;
      st.message = '$e';
    } finally {
      await Native.keepAliveStop();
      notifyListeners();
    }
  }

  void cancelTransfer() {
    status?.cancel.cancel();
  }

  void dismissStatus() {
    if (status?.active == true) return;
    status = null;
    notifyListeners();
  }

  void _progress(TransferStatus st, TransferProgress pr) {
    st.progress = pr;
    final now = DateTime.now();
    if (now.difference(_lastNotify).inMilliseconds > 100 || pr.fraction >= 1) {
      _lastNotify = now;
      notifyListeners();
    }
    if (now.difference(_lastKeepAlive).inMilliseconds > 1000) {
      _lastKeepAlive = now;
      Native.keepAliveUpdate(
          st.direction == Direction.send ? 'Enviando a ${st.peer}' : 'Recibiendo de ${st.peer}',
          '${pr.currentFile} · ${formatBytes(pr.bytesDone)} de ${formatBytes(pr.bytesTotal)}',
          (pr.fraction * 100).round());
    }
  }

  void addToSend(Iterable<String> paths) {
    for (final x in paths) {
      if (!toSend.contains(x)) toSend.add(x);
    }
    notifyListeners();
  }

  void removeFromSend(String path) {
    toSend.remove(path);
    notifyListeners();
  }

  String get toolsDir => p.join(saveDir, 'Herramientas');
}

String formatBytes(int b) {
  if (b < 1024) return '$b B';
  const units = ['KB', 'MB', 'GB', 'TB'];
  var v = b / 1024;
  var i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return '${v.toStringAsFixed(v < 10 ? 1 : 0)} ${units[i]}';
}
