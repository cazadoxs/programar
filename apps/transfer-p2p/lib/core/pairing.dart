import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

/// Datos para conectarse a un receptor. Viajan en el enlace y en el QR.
class PairingInfo {
  final String host;
  final int port;
  final String token;
  final String name;
  PairingInfo(this.host, this.port, this.token, {this.name = ''});

  /// pasalo://c?h=192.168.1.5&p=47820&t=TOKEN&n=Nombre
  String toLink() => Uri(scheme: 'pasalo', host: 'c', queryParameters: {
        'h': host,
        'p': '$port',
        't': token,
        if (name.isNotEmpty) 'n': name,
      }).toString();

  /// Acepta el enlace completo o un texto que lo contenga.
  static PairingInfo? parse(String text) {
    final m = RegExp(r'pasalo://\S+').firstMatch(text.trim());
    if (m == null) return null;
    final uri = Uri.tryParse(m.group(0)!);
    if (uri == null) return null;
    final q = uri.queryParameters;
    final h = q['h'];
    final port = int.tryParse(q['p'] ?? '');
    final t = q['t'];
    if (h == null || h.isEmpty || port == null || port <= 0 || port > 65535) {
      return null;
    }
    if (t == null || t.isEmpty) return null;
    if (InternetAddress.tryParse(h) == null) return null;
    return PairingInfo(h, port, t, name: q['n'] ?? '');
  }
}

final _rnd = Random.secure();

String newToken() =>
    base64Url.encode(List<int>.generate(16, (_) => _rnd.nextInt(256)))
        .replaceAll('=', '');

String newCode() => List.generate(6, (_) => _rnd.nextInt(10)).join();

/// Compara sin filtrar tiempos.
bool safeEquals(String a, String b) {
  final x = utf8.encode(a), y = utf8.encode(b);
  if (x.length != y.length) return false;
  var r = 0;
  for (var i = 0; i < x.length; i++) {
    r |= x[i] ^ y[i];
  }
  return r == 0;
}

const int discoveryPort = 47821;
const int preferredTcpPort = 47820;

/// Responde en la red local a quien pregunte por nuestro código de 6 cifras.
class CodeBeacon {
  final RawDatagramSocket _socket;
  String code;
  final int tcpPort;
  final String name;

  CodeBeacon._(this._socket, this.code, this.tcpPort, this.name) {
    _socket.listen((e) {
      if (e != RawSocketEvent.read) return;
      final d = _socket.receive();
      if (d == null) return;
      final msg = utf8.decode(d.data, allowMalformed: true);
      if (msg == 'PASALO?$code') {
        _socket.send(
            utf8.encode('PASALO!$tcpPort|$name'), d.address, d.port);
      }
    });
  }

  static Future<CodeBeacon> start(
      {required String code,
      required int tcpPort,
      required String name,
      int port = discoveryPort}) async {
    final s = await RawDatagramSocket.bind(InternetAddress.anyIPv4, port,
        reuseAddress: true);
    return CodeBeacon._(s, code, tcpPort, name);
  }

  void close() => _socket.close();
}

/// Busca en la red local al receptor que muestra [code].
Future<PairingInfo?> findByCode(String code,
    {Duration timeout = const Duration(seconds: 6),
    int port = discoveryPort,
    List<InternetAddress> extraTargets = const []}) async {
  final s = await RawDatagramSocket.bind(InternetAddress.anyIPv4, 0);
  s.broadcastEnabled = true;
  final targets = <InternetAddress>[
    InternetAddress('255.255.255.255'),
    ...await _subnetBroadcasts(),
    ...extraTargets,
  ];
  final done = Completer<PairingInfo?>();
  s.listen((e) {
    if (e != RawSocketEvent.read) return;
    final d = s.receive();
    if (d == null) return;
    final msg = utf8.decode(d.data, allowMalformed: true);
    if (!msg.startsWith('PASALO!')) return;
    final parts = msg.substring(7).split('|');
    final p = int.tryParse(parts.first);
    if (p != null && !done.isCompleted) {
      done.complete(PairingInfo(d.address.address, p, code,
          name: parts.length > 1 ? parts.sublist(1).join('|') : ''));
    }
  });
  final ping = utf8.encode('PASALO?$code');
  void sendAll() {
    for (final t in targets) {
      try {
        s.send(ping, t, port);
      } catch (_) {}
    }
  }

  sendAll();
  final timer = Timer.periodic(const Duration(milliseconds: 500), (_) => sendAll());
  try {
    return await done.future.timeout(timeout, onTimeout: () => null);
  } finally {
    timer.cancel();
    s.close();
  }
}

/// Direcciones de broadcast /24 de cada interfaz IPv4 (aproximación útil para
/// redes domésticas y para el grupo Wi-Fi Direct 192.168.49.x).
Future<List<InternetAddress>> _subnetBroadcasts() async {
  final out = <InternetAddress>[];
  for (final a in await localIPv4()) {
    final parts = a.split('.');
    out.add(InternetAddress('${parts[0]}.${parts[1]}.${parts[2]}.255'));
  }
  return out;
}

/// IPv4 locales que no son loopback, las de la red local primero.
Future<List<String>> localIPv4() async {
  final list = await NetworkInterface.list(type: InternetAddressType.IPv4);
  final ips = <String>[
    for (final i in list)
      for (final a in i.addresses)
        if (!a.isLoopback && !a.address.startsWith('169.254.')) a.address
  ];
  int rank(String ip) => ip.startsWith('192.168.')
      ? 0
      : ip.startsWith('10.')
          ? 1
          : 2;
  ips.sort((a, b) => rank(a).compareTo(rank(b)));
  return ips;
}
