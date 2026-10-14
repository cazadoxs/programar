import 'dart:async';
import 'dart:io';

import 'pairing.dart';
import 'protocol.dart';

/// Receptor en red (Wi-Fi o grupo Wi-Fi Direct): escucha por TCP, responde
/// al código por UDP y atiende una transferencia a la vez.
class TransferHost {
  final ServerSocket _server;
  final CodeBeacon? _beacon;
  final String token;
  String code;
  int _failed = 0;
  bool busy = false;

  /// Falso cuando la app está en segundo plano: entonces no se aceptan
  /// transferencias nuevas, solo siguen las que ya empezaron.
  bool acceptingNew = true;

  final void Function(String newCode)? onCodeChanged;
  final Future<void> Function(ByteLink link, bool Function(String) check)
      onConnection;

  TransferHost._(this._server, this._beacon, this.token, this.code,
      this.onConnection, this.onCodeChanged) {
    _server.listen(_handle);
  }

  int get port => _server.port;

  static Future<TransferHost> start({
    required String name,
    required Future<void> Function(ByteLink, bool Function(String)) onConnection,
    void Function(String)? onCodeChanged,
    int tcpPort = preferredTcpPort,
    int udpPort = discoveryPort,
    bool beacon = true,
  }) async {
    ServerSocket server;
    try {
      server = await ServerSocket.bind(InternetAddress.anyIPv4, tcpPort);
    } on SocketException {
      server = await ServerSocket.bind(InternetAddress.anyIPv4, 0);
    }
    final code = newCode();
    CodeBeacon? b;
    if (beacon) {
      try {
        b = await CodeBeacon.start(
            code: code, tcpPort: server.port, name: name, port: udpPort);
      } on SocketException {
        b = null; // Sin código por red, pero el enlace y el QR siguen sirviendo.
      }
    }
    return TransferHost._(
        server, b, newToken(), code, onConnection, onCodeChanged);
  }

  bool get codeAvailable => _beacon != null;

  bool checkToken(String t) {
    if (safeEquals(t, token)) return true;
    if (safeEquals(t, code)) return true;
    // Tras 5 intentos fallidos se cambia el código para frenar a quien adivina.
    if (++_failed >= 5) {
      _failed = 0;
      code = newCode();
      _beacon?.code = code;
      onCodeChanged?.call(code);
    }
    return false;
  }

  Future<void> _handle(Socket s) async {
    final link = SocketLink(s);
    if (busy || !acceptingNew) {
      // Leemos el saludo para que el emisor reciba un motivo claro.
      final reader = FrameReader(link.input);
      try {
        await reader.next().timeout(const Duration(seconds: 5));
        await link.write(encodeJsonFrame(FrameType.reject, {
          'reason': busy
              ? 'El receptor está ocupado con otra transferencia.'
              : 'Abre Pásalo en el receptor para empezar una transferencia nueva.'
        }));
      } catch (_) {
      } finally {
        await reader.cancel();
        await link.close();
      }
      return;
    }
    busy = true;
    try {
      await onConnection(link, checkToken);
    } catch (_) {
      // El error ya lo gestiona onConnection; aquí solo liberamos.
    } finally {
      busy = false;
    }
  }

  Future<void> close() async {
    _beacon?.close();
    await _server.close();
  }
}
