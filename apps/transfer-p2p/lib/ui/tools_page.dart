import 'dart:io';
import 'dart:isolate';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;

import '../app/state.dart';
import '../core/aes_cipher.dart';
import '../core/bit_image.dart';
import '../core/compressor.dart';
import '../core/mirror_cipher.dart';
import 'theme.dart';
import 'widgets.dart';

class ToolsPage extends StatefulWidget {
  final VoidCallback onSend;
  const ToolsPage({super.key, required this.onSend});
  @override
  State<ToolsPage> createState() => _ToolsPageState();
}

class _ToolsPageState extends State<ToolsPage> {
  final state = AppState.instance;
  String? busy;

  Future<List<String>> _pick({bool single = false}) async {
    final r = single ? await FilePicker.pickFiles() : await FilePicker.pickFiles();
    final paths = r.map((f) => f.path).whereType<String>().toList();
    return single ? paths.take(1).toList() : paths;
  }

  Future<String?> _password({required bool confirm, bool optional = false}) async {
    final a = TextEditingController(), b = TextEditingController();
    String? error;
    final r = await showDialog<String>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, set) => AlertDialog(
          title: Text(optional ? 'Contraseña (opcional)' : 'Contraseña'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: a, obscureText: true, autofocus: true,
                decoration: const InputDecoration(hintText: 'Contraseña')),
            if (confirm) ...[
              const SizedBox(height: 10),
              TextField(controller: b, obscureText: true,
                  decoration: const InputDecoration(hintText: 'Repite la contraseña')),
            ],
            if (error != null)
              Padding(padding: const EdgeInsets.only(top: 8),
                  child: Text(error!, style: const TextStyle(color: Palette.coral))),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
            FilledButton(
              onPressed: () {
                if (!optional && a.text.isEmpty) return set(() => error = 'Escribe una contraseña.');
                if (confirm && a.text != b.text) return set(() => error = 'No coinciden.');
                Navigator.pop(ctx, a.text);
              },
              child: const Text('Seguir'),
            ),
          ],
        ),
      ),
    );
    return r;
  }

  /// Ejecuta [job] en otro hilo para no congelar la pantalla.
  Future<void> _run(String label, Future<List<String>> Function() job) async {
    setState(() => busy = label);
    try {
      final out = await job();
      if (!mounted) return;
      await _done(out);
    } catch (e) {
      if (mounted) toast(context, 'Error: ${e is FormatException ? e.message : e}');
    } finally {
      if (mounted) setState(() => busy = null);
    }
  }

  Future<void> _done(List<String> out) => showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Listo'),
          content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            for (final f in out.take(8)) Text('• ${p.basename(f)}'),
            if (out.length > 8) Text('… y ${out.length - 8} más'),
            const SizedBox(height: 8),
            SelectableText('En ${p.dirname(out.first)}',
                style: const TextStyle(color: Palette.muted, fontSize: 12)),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cerrar')),
            FilledButton(
              onPressed: () {
                state.addToSend(out);
                Navigator.pop(ctx);
                widget.onSend();
              },
              child: const Text('Enviar'),
            ),
          ],
        ),
      );

  String get outDir => state.toolsDir;

  Future<void> compress({bool folder = false}) async {
    final List<String> paths;
    if (folder) {
      final d = await FilePicker.getDirectoryPath();
      paths = d == null ? [] : [d];
    } else {
      paths = await _pick();
    }
    if (paths.isEmpty) return;
    final dir = outDir;
    final name = folder ? '${p.basename(paths.first)}.zip' : null;
    await _run('Comprimiendo…', () => _bg(_compressJob, (paths, dir, name)));
  }

  Future<void> decompress() async {
    final paths = await _pick(single: true);
    if (paths.isEmpty) return;
    final dir = p.join(outDir, p.basenameWithoutExtension(paths.first));
    await _run('Descomprimiendo…', () => _bg(_decompressJob, (paths.first, dir)));
  }

  Future<void> aes(bool encrypt) async {
    final paths = await _pick(single: true);
    if (paths.isEmpty) return;
    final pw = await _password(confirm: encrypt);
    if (pw == null) return;
    final src = paths.first;
    final dir = outDir;
    await _run(encrypt ? 'Cifrando con AES…' : 'Descifrando…',
        () => _bg(_aesJob, (src, dir, pw, encrypt)));
  }

  Future<void> mirror(bool encrypt) async {
    final paths = await _pick(single: true);
    if (paths.isEmpty) return;
    final pw = await _password(confirm: encrypt, optional: true);
    if (pw == null) return;
    final src = paths.first;
    final dir = outDir;
    await _run('Invirtiendo bits…', () => _bg(_mirrorJob, (src, dir, pw, encrypt)));
  }

  Future<void> toImage() async {
    final paths = await _pick(single: true);
    if (paths.isEmpty || !mounted) return;
    final mode = await showDialog<BitImageMode>(
      context: context,
      builder: (ctx) => SimpleDialog(title: const Text('¿Qué tipo de imagen?'), children: [
        SimpleDialogOption(
          onPressed: () => Navigator.pop(ctx, BitImageMode.color),
          child: const ListTile(
              title: Text('Color (compacta)'),
              subtitle: Text('Cada píxel guarda 3 bytes. Hasta 64 MB.')),
        ),
        SimpleDialogOption(
          onPressed: () => Navigator.pop(ctx, BitImageMode.bits),
          child: const ListTile(
              title: Text('Bits en blanco y negro'),
              subtitle: Text('Cada píxel es un bit: negro = 1, blanco = 0. Hasta 4 MB.')),
        ),
      ]),
    );
    if (mode == null) return;
    final src = paths.first;
    final dir = outDir;
    await _run('Creando imagen…', () => _bg(_toImageJob, (src, dir, mode)));
  }

  Future<void> fromImage() async {
    final paths = await _pick(single: true);
    if (paths.isEmpty) return;
    final src = paths.first;
    final dir = outDir;
    await _run('Leyendo imagen…', () => _bg(_fromImageJob, (src, dir)));
  }

  @override
  Widget build(BuildContext context) {
    Widget tool(IconData icon, Color color, String title, String text, VoidCallback onTap) => Card(
          child: InkWell(
            borderRadius: BorderRadius.circular(22),
            onTap: busy == null ? onTap : null,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(icon, color: color, size: 30),
                const SizedBox(height: 10),
                Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
                const SizedBox(height: 4),
                Text(text, style: const TextStyle(color: Palette.muted, fontSize: 12)),
              ]),
            ),
          ),
        );
    final grid = [
      tool(Icons.compress_rounded, Palette.lime, 'Comprimir', '1 archivo → .gz · varios → .zip', compress),
      if (!Platform.isAndroid)
        tool(Icons.folder_zip_rounded, Palette.lime, 'Comprimir carpeta', 'Carpeta entera → .zip',
            () => compress(folder: true)),
      tool(Icons.unarchive_rounded, Palette.lime, 'Descomprimir', '.zip, .gz, .tar, .tar.gz', decompress),
      tool(Icons.lock_rounded, Palette.sky, 'Cifrar (AES)', 'AES-256-GCM con contraseña. Seguro.', () => aes(true)),
      tool(Icons.lock_open_rounded, Palette.sky, 'Descifrar (AES)', 'Abre un .aes', () => aes(false)),
      tool(Icons.flip_rounded, Palette.coral, 'Cifrado Espejo', 'Invierte cada bit y los mezcla. Casero, no seguro.',
          () => mirror(true)),
      tool(Icons.flip_to_back_rounded, Palette.coral, 'Deshacer Espejo', 'Abre un .espejo', () => mirror(false)),
      tool(Icons.image_rounded, Palette.lime, 'Archivo → imagen', 'Guarda cualquier archivo como PNG de bits', toImage),
      tool(Icons.image_search_rounded, Palette.lime, 'Imagen → archivo', 'Recupera el archivo de ese PNG', fromImage),
    ];
    return LayoutBuilder(builder: (context, c) {
      final cols = c.maxWidth > 560 ? 3 : 2;
      return PageBody(children: [
        const PageHeader('Herramientas', 'Comprime, cifra o convierte antes de enviar.'),
        if (busy != null) ...[
          Row(children: [
            const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 3)),
            const SizedBox(width: 12),
            Text(busy!),
          ]),
          const SizedBox(height: 14),
        ],
        GridView.count(
          crossAxisCount: cols,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 0.95,
          children: grid,
        ),
        const SizedBox(height: 16),
        SelectableText('Los resultados se guardan en ${state.toolsDir}',
            style: const TextStyle(color: Palette.muted, fontSize: 12)),
      ]);
    });
  }
}

// ── Trabajos pesados. Son funciones de nivel superior para que Isolate.run
// no arrastre el estado de la pantalla (que no se puede enviar a otro hilo).

Future<List<String>> _bg<A>(Future<List<String>> Function(A) job, A arg) =>
    Isolate.run(() => job(arg));

Future<List<String>> _compressJob((List<String>, String, String?) a) async =>
    [(await Compressor.compress(a.$1, a.$2, zipName: a.$3)).path];

Future<List<String>> _decompressJob((String, String) a) =>
    Compressor.decompress(a.$1, a.$2);

Future<List<String>> _aesJob((String, String, String, bool) a) async {
  final (src, dir, pw, encrypt) = a;
  await Directory(dir).create(recursive: true);
  final name = p.basename(src);
  if (encrypt) {
    final out = File(uniquePath(dir, '$name.aes'));
    await AesFileCipher().encryptFile(File(src), out, pw);
    return [out.path];
  }
  final out = File(uniquePath(dir,
      name.toLowerCase().endsWith('.aes') ? name.substring(0, name.length - 4) : '$name.descifrado'));
  await AesFileCipher.decryptFile(File(src), out, pw);
  return [out.path];
}

Future<List<String>> _mirrorJob((String, String, String, bool) a) async {
  final (src, dir, pw, encrypt) = a;
  await Directory(dir).create(recursive: true);
  final name = p.basename(src);
  final c = MirrorCipher(pw);
  if (encrypt) {
    final out = File(uniquePath(dir, '$name.espejo'));
    await c.encryptFile(File(src), out);
    return [out.path];
  }
  final out = File(uniquePath(dir,
      name.endsWith('.espejo') ? name.substring(0, name.length - 7) : '$name.original'));
  await c.decryptFile(File(src), out);
  return [out.path];
}

Future<List<String>> _toImageJob((String, String, BitImageMode) a) async {
  final (src, dir, mode) = a;
  await Directory(dir).create(recursive: true);
  final png = BitImage.encode(await File(src).readAsBytes(), p.basename(src), mode);
  final out = File(uniquePath(dir, '${p.basename(src)}.png'));
  await out.writeAsBytes(png);
  return [out.path];
}

Future<List<String>> _fromImageJob((String, String) a) async {
  final (src, dir) = a;
  await Directory(dir).create(recursive: true);
  final d = BitImage.decode(await File(src).readAsBytes());
  final out = File(uniquePath(dir, sanitizeFileName(d.name)));
  await out.writeAsBytes(d.data);
  return [out.path];
}
