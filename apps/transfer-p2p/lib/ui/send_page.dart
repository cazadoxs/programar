import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:path/path.dart' as p;
import 'package:permission_handler/permission_handler.dart';

import '../app/state.dart';
import '../core/pairing.dart';
import '../platform/native.dart';
import 'theme.dart';
import 'widgets.dart';

class SendPage extends StatefulWidget {
  const SendPage({super.key});
  @override
  State<SendPage> createState() => _SendPageState();
}

class _SendPageState extends State<SendPage> {
  final state = AppState.instance;
  final code = TextEditingController();
  final link = TextEditingController();

  @override
  void dispose() {
    code.dispose();
    link.dispose();
    super.dispose();
  }

  Future<void> pick() async {
    final r = await FilePicker.pickFiles();
    state.addToSend(r.map((f) => f.path).whereType<String>());
  }

  bool _ready() {
    if (state.toSend.isEmpty) {
      toast(context, 'Primero elige qué archivos enviar.');
      return false;
    }
    if (state.status?.active == true) {
      toast(context, 'Ya hay una transferencia en marcha.');
      return false;
    }
    return true;
  }

  void sendCode() {
    final c = code.text.replaceAll(RegExp(r'\D'), '');
    if (c.length != 6) {
      toast(context, 'El código tiene 6 cifras.');
      return;
    }
    if (_ready()) state.sendByCode(c);
  }

  void sendLink([String? text]) {
    final info = PairingInfo.parse(text ?? link.text);
    if (info == null) {
      toast(context, 'Ese enlace no es de Pásalo.');
      return;
    }
    if (_ready()) {
      state.clearIncomingLink();
      state.sendByLink(info);
    }
  }

  Future<void> scan() async {
    if (!await Permission.camera.request().isGranted) {
      if (mounted) toast(context, 'Sin permiso de cámara no se puede escanear.');
      return;
    }
    if (!mounted) return;
    final text = await Navigator.push<String>(
        context, MaterialPageRoute(builder: (_) => const ScanPage()));
    if (text == null) return;
    sendLink(text);
  }

  Future<void> bluetooth() async {
    if (!_ready()) return;
    final perms = await [Permission.bluetoothConnect].request();
    if (perms.values.any((s) => !s.isGranted)) {
      if (mounted) toast(context, 'Hace falta el permiso de Bluetooth.');
      return;
    }
    if (!await Native.btAvailable()) {
      if (mounted) toast(context, 'Activa el Bluetooth.');
      return;
    }
    final devices = await Native.btBonded();
    if (!mounted) return;
    final d = await showModalBottomSheet<BtDevice>(
      context: context,
      backgroundColor: Palette.panel,
      builder: (ctx) => SafeArea(
        child: ListView(shrinkWrap: true, children: [
          const ListTile(
            title: Text('Dispositivos emparejados',
                style: TextStyle(fontWeight: FontWeight.w800)),
            subtitle: Text('El otro móvil tiene que estar en Recibir → Bluetooth. '
                'Si no aparece, emparéjalos antes en Ajustes → Bluetooth.'),
          ),
          for (final d in devices)
            ListTile(
              leading: const Icon(Icons.bluetooth_rounded),
              title: Text(d.name),
              subtitle: Text(d.address),
              onTap: () => Navigator.pop(ctx, d),
            ),
          if (devices.isEmpty)
            const ListTile(title: Text('No hay dispositivos emparejados.')),
        ]),
      ),
    );
    if (d != null) state.sendByBluetooth(d);
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final total = state.toSend.fold<int>(
            0, (a, f) => a + (File(f).existsSync() ? File(f).lengthSync() : 0));
        final incoming = state.incomingLink;
        return PageBody(children: [
          const PageHeader('Enviar', 'Elige archivos y conéctate al otro dispositivo.'),
          if (state.status != null) TransferCard(state.status!),
          if (incoming != null) ...[
            const SizedBox(height: 12),
            Card(
              color: Palette.sky.withValues(alpha: 0.15),
              child: ListTile(
                leading: const Icon(Icons.link_rounded, color: Palette.sky),
                title: Text('Enlace a ${incoming.name.isEmpty ? incoming.host : incoming.name}'),
                subtitle: const Text('Elige archivos y pulsa Enviar.'),
                trailing: FilledButton(
                    onPressed: () => sendLink(incoming.toLink()),
                    child: const Text('Enviar')),
              ),
            ),
          ],
          const SectionLabel('1 · Archivos'),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(children: [
                for (final f in state.toSend)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.insert_drive_file_rounded, color: Palette.lime),
                    title: Text(p.basename(f), overflow: TextOverflow.ellipsis),
                    subtitle: Text(File(f).existsSync()
                        ? formatBytes(File(f).lengthSync())
                        : 'No encontrado'),
                    trailing: IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: () => state.removeFromSend(f)),
                  ),
                if (state.toSend.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Text('${state.toSend.length} archivo(s) · ${formatBytes(total)}',
                        style: const TextStyle(color: Palette.muted)),
                  ),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                      onPressed: pick,
                      icon: const Icon(Icons.add_rounded),
                      label: const Text('Añadir archivos')),
                ),
              ]),
            ),
          ),
          const SectionLabel('2 · Conectar'),
          if (Platform.isAndroid) ...[
            Row(children: [
              Expanded(
                child: FilledButton.icon(
                    onPressed: () {
                      if (_ready()) scan();
                    },
                    icon: const Icon(Icons.qr_code_scanner_rounded),
                    label: const Text('Escanear QR')),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: OutlinedButton.icon(
                    onPressed: bluetooth,
                    icon: const Icon(Icons.bluetooth_rounded),
                    label: const Text('Bluetooth')),
              ),
            ]),
            const SizedBox(height: 12),
          ],
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('Con código', style: TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 4),
                const Text('El que recibe ve un código de 6 cifras. Misma Wi-Fi.',
                    style: TextStyle(color: Palette.muted, fontSize: 13)),
                const SizedBox(height: 10),
                Row(children: [
                  Expanded(
                    child: TextField(
                      controller: code,
                      keyboardType: TextInputType.number,
                      maxLength: 7,
                      style: const TextStyle(fontSize: 22, letterSpacing: 6, fontWeight: FontWeight.w800),
                      decoration: const InputDecoration(hintText: '000000', counterText: ''),
                      onSubmitted: (_) => sendCode(),
                    ),
                  ),
                  const SizedBox(width: 10),
                  FilledButton(onPressed: sendCode, child: const Text('Enviar')),
                ]),
                const SizedBox(height: 18),
                const Text('Con enlace', style: TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 10),
                Row(children: [
                  Expanded(
                    child: TextField(
                      controller: link,
                      decoration: const InputDecoration(hintText: 'pasalo://c?h=…'),
                      onSubmitted: (_) => sendLink(),
                    ),
                  ),
                  const SizedBox(width: 10),
                  FilledButton(onPressed: () => sendLink(), child: const Text('Enviar')),
                ]),
              ]),
            ),
          ),
        ]);
      },
    );
  }
}

class ScanPage extends StatefulWidget {
  const ScanPage({super.key});
  @override
  State<ScanPage> createState() => _ScanPageState();
}

class _ScanPageState extends State<ScanPage> {
  bool done = false;
  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Escanea el QR del receptor')),
        body: MobileScanner(
          onDetect: (capture) {
            if (done) return;
            for (final b in capture.barcodes) {
              final v = b.rawValue;
              if (v != null && v.startsWith('pasalo://')) {
                done = true;
                Navigator.pop(context, v);
                return;
              }
            }
          },
        ),
      );
}
