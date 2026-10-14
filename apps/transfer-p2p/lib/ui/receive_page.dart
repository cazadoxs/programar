import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../app/state.dart';
import '../platform/native.dart';
import 'theme.dart';
import 'widgets.dart';

class ReceivePage extends StatelessWidget {
  const ReceivePage({super.key});

  Future<void> start(BuildContext context, ReceiveMode mode) async {
    final state = AppState.instance;
    if (Platform.isAndroid) {
      final perms = <Permission>[Permission.notification];
      final sdk = await Native.sdkInt();
      if (sdk <= 29) perms.add(Permission.storage);
      if (mode == ReceiveMode.wifiDirect) {
        perms.add(sdk >= 33 ? Permission.nearbyWifiDevices : Permission.locationWhenInUse);
      }
      if (mode == ReceiveMode.bluetooth) perms.add(Permission.bluetoothConnect);
      final res = await perms.request();
      final denied = res.entries
          .where((e) => e.key != Permission.notification && !e.value.isGranted);
      if (denied.isNotEmpty) {
        if (context.mounted) toast(context, 'Faltan permisos para este modo.');
        return;
      }
      if (mode == ReceiveMode.bluetooth && !await Native.btAvailable()) {
        if (context.mounted) toast(context, 'Activa el Bluetooth.');
        return;
      }
    }
    await state.startReceiving(mode);
  }

  @override
  Widget build(BuildContext context) {
    final state = AppState.instance;
    return ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final mode = state.receiving;
        return PageBody(children: [
          const PageHeader('Recibir', 'Muestra tu código o QR y acepta lo que te manden.'),
          if (state.status != null) ...[TransferCard(state.status!), const SizedBox(height: 12)],
          if (state.receiveError != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(state.receiveError!, style: const TextStyle(color: Palette.coral)),
            ),
          if (mode == null) ..._chooser(context) else ..._session(context, state, mode),
          const SizedBox(height: 18),
          SelectableText('Lo recibido se guarda en: ${state.saveDir}',
              style: const TextStyle(color: Palette.muted, fontSize: 12)),
        ]);
      },
    );
  }

  List<Widget> _chooser(BuildContext context) {
    Widget option(ReceiveMode m, IconData icon, String title, String text) => Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Card(
            child: InkWell(
              borderRadius: BorderRadius.circular(22),
              onTap: () => start(context, m),
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Row(children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                        color: Palette.lime.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(14)),
                    child: Icon(icon, color: Palette.lime),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                      const SizedBox(height: 2),
                      Text(text, style: const TextStyle(color: Palette.muted, fontSize: 13)),
                    ]),
                  ),
                  const Icon(Icons.chevron_right_rounded, color: Palette.muted),
                ]),
              ),
            ),
          ),
        );
    return [
      const SectionLabel('¿Cómo os conectáis?'),
      option(ReceiveMode.wifi, Icons.wifi_rounded, 'Misma Wi-Fi',
          'Lo más rápido. Código, enlace o QR. Móvil y PC.'),
      if (Platform.isAndroid) ...[
        option(ReceiveMode.wifiDirect, Icons.wifi_tethering_rounded, 'Wi-Fi Direct (sin router)',
            'Este móvil crea su propia red. El otro (móvil o PC) se une a ella.'),
        option(ReceiveMode.bluetooth, Icons.bluetooth_rounded, 'Bluetooth',
            'Entre dos Android emparejados. Lento, para archivos pequeños.'),
      ],
    ];
  }

  List<Widget> _session(BuildContext context, AppState state, ReceiveMode mode) {
    final host = state.host;
    final link = state.link;
    final stop = SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
          onPressed: state.stopReceiving,
          icon: const Icon(Icons.stop_rounded),
          label: const Text('Dejar de recibir')),
    );
    if (mode == ReceiveMode.bluetooth) {
      return [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(22),
            child: Column(children: [
              const Icon(Icons.bluetooth_searching_rounded, size: 56, color: Palette.lime),
              const SizedBox(height: 12),
              Text('Esperando por Bluetooth como "${state.deviceName}"',
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              const Text('En el otro móvil: Enviar → Bluetooth → elige este dispositivo.',
                  textAlign: TextAlign.center, style: TextStyle(color: Palette.muted)),
            ]),
          ),
        ),
        const SizedBox(height: 12),
        stop,
      ];
    }
    if (host == null) {
      return [const Center(child: Padding(padding: EdgeInsets.all(30), child: CircularProgressIndicator()))];
    }
    final wd = state.wifiDirect;
    final code = host.code;
    return [
      if (!state.foreground)
        const Padding(
          padding: EdgeInsets.only(bottom: 12),
          child: Text('En segundo plano no se aceptan transferencias nuevas.',
              style: TextStyle(color: Palette.coral)),
        ),
      if (wd != null) ...[
        const SectionLabel('Paso 1 · Unir el otro dispositivo a esta red'),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(children: [
              _qr('WIFI:S:${_esc(wd['ssid']!)};T:WPA;P:${_esc(wd['pass']!)};;', 170),
              const SizedBox(height: 12),
              _copyRow(context, 'Red', wd['ssid']!),
              _copyRow(context, 'Contraseña', wd['pass']!),
              const SizedBox(height: 6),
              const Text(
                  'En otro Android: Ajustes → Wi-Fi → icono QR y escanea esto. '
                  'En el PC: conéctate a esa red Wi-Fi con la contraseña.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Palette.muted, fontSize: 13)),
            ]),
          ),
        ),
        const SectionLabel('Paso 2 · Conectar con Pásalo'),
      ],
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(children: [
            if (host.codeAvailable) ...[
              const Text('Código', style: TextStyle(color: Palette.muted)),
              Text('${code.substring(0, 3)} ${code.substring(3)}',
                  style: const TextStyle(
                      fontSize: 46, fontWeight: FontWeight.w900, letterSpacing: 4, color: Palette.lime)),
              const SizedBox(height: 14),
            ],
            if (link != null) ...[
              _qr(link, 200),
              const SizedBox(height: 10),
              const Text('Escanéalo desde Pásalo en el móvil que envía.',
                  style: TextStyle(color: Palette.muted, fontSize: 13)),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: link));
                    toast(context, 'Enlace copiado. Pégalo en el otro dispositivo.');
                  },
                  icon: const Icon(Icons.copy_rounded),
                  label: const Text('Copiar enlace'),
                ),
              ),
            ],
            const SizedBox(height: 10),
            Text('IP: ${state.localIps.join(', ')} · puerto ${host.port}',
                style: const TextStyle(color: Palette.muted, fontSize: 12)),
          ]),
        ),
      ),
      const SizedBox(height: 12),
      stop,
    ];
  }

  static String _esc(String s) => s.replaceAllMapped(RegExp(r'([\\;,:"])'), (m) => '\\${m[1]}');

  Widget _qr(String data, double size) => Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(18)),
        child: QrImageView(data: data, size: size, backgroundColor: Colors.white),
      );

  Widget _copyRow(BuildContext context, String label, String value) => Row(children: [
        Text('$label: ', style: const TextStyle(color: Palette.muted)),
        Expanded(child: SelectableText(value, style: const TextStyle(fontWeight: FontWeight.w700))),
        IconButton(
          icon: const Icon(Icons.copy_rounded, size: 18),
          onPressed: () {
            Clipboard.setData(ClipboardData(text: value));
            toast(context, '$label copiada.');
          },
        ),
      ]);
}
