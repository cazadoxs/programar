import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:path/path.dart' as p;

/// Compresión y descompresión.
///
/// Un archivo suelto se comprime a .gz; varios archivos o una carpeta, a .zip.
/// Se descomprime .zip, .gz, .tar, .tar.gz/.tgz.
class Compressor {
  static Future<File> compress(List<String> paths, String outDir,
      {String? zipName}) async {
    if (paths.isEmpty) throw ArgumentError('No hay archivos.');
    await Directory(outDir).create(recursive: true);
    if (paths.length == 1 && await FileSystemEntity.isFile(paths.first)) {
      final src = File(paths.first);
      final out = File(uniquePath(outDir, '${p.basename(src.path)}.gz'));
      await src.openRead().transform(gzip.encoder).pipe(out.openWrite());
      return out;
    }
    final out = File(uniquePath(outDir, zipName ?? 'comprimido.zip'));
    final enc = ZipFileEncoder()..create(out.path);
    try {
      for (final path in paths) {
        if (await FileSystemEntity.isDirectory(path)) {
          await enc.addDirectory(Directory(path));
        } else {
          await enc.addFile(File(path));
        }
      }
    } finally {
      await enc.close();
    }
    return out;
  }

  /// Descomprime [path] dentro de [outDir]. Devuelve los archivos creados.
  static Future<List<String>> decompress(String path, String outDir) async {
    final lower = path.toLowerCase();
    await Directory(outDir).create(recursive: true);
    if (lower.endsWith('.zip')) {
      final input = InputFileStream(path);
      try {
        return _extract(ZipDecoder().decodeStream(input), outDir);
      } finally {
        await input.close();
      }
    }
    if (lower.endsWith('.tar')) {
      final input = InputFileStream(path);
      try {
        return _extract(TarDecoder().decodeStream(input), outDir);
      } finally {
        await input.close();
      }
    }
    if (lower.endsWith('.tar.gz') || lower.endsWith('.tgz')) {
      final tmp = File(p.join(outDir, '.pasalo_tmp.tar'));
      await File(path).openRead().transform(gzip.decoder).pipe(tmp.openWrite());
      try {
        return await decompress(tmp.path, outDir);
      } finally {
        if (await tmp.exists()) await tmp.delete();
      }
    }
    if (lower.endsWith('.gz')) {
      final name = p.basenameWithoutExtension(path);
      final out = File(uniquePath(outDir, name.isEmpty ? 'archivo' : name));
      await File(path).openRead().transform(gzip.decoder).pipe(out.openWrite());
      return [out.path];
    }
    throw const FormatException(
        'Formato no soportado. Usa .zip, .gz, .tar o .tar.gz');
  }

  static List<String> _extract(Archive archive, String outDir) {
    final root = p.normalize(p.absolute(outDir));
    final created = <String>[];
    for (final entry in archive) {
      if (entry.isSymbolicLink) continue;
      final target = p.normalize(p.join(root, entry.name));
      // Evita "zip slip": nada puede salir de la carpeta de destino.
      if (!p.isWithin(root, target)) continue;
      if (!entry.isFile) {
        Directory(target).createSync(recursive: true);
        continue;
      }
      Directory(p.dirname(target)).createSync(recursive: true);
      final out = OutputFileStream(target);
      try {
        entry.writeContent(out);
      } finally {
        out.closeSync();
      }
      created.add(target);
    }
    return created;
  }
}

/// Devuelve una ruta libre en [dir] para [name] ("foto (1).jpg" si existe).
String uniquePath(String dir, String name) {
  var candidate = p.join(dir, name);
  if (!File(candidate).existsSync() && !Directory(candidate).existsSync()) {
    return candidate;
  }
  final ext = p.extension(name);
  final base = p.basenameWithoutExtension(name);
  for (var i = 1;; i++) {
    candidate = p.join(dir, '$base ($i)$ext');
    if (!File(candidate).existsSync() && !Directory(candidate).existsSync()) {
      return candidate;
    }
  }
}

/// Limpia un nombre recibido de otro dispositivo para que sea un nombre de
/// archivo seguro (sin rutas, sin caracteres reservados en Windows).
String sanitizeFileName(String name) {
  var n = name.replaceAll('\\', '/').split('/').last;
  n = n.replaceAll(RegExp(r'[<>:"|?*\x00-\x1F]'), '_').trim();
  while (n.startsWith('.')) {
    n = n.substring(1);
  }
  n = n.replaceAll(RegExp(r'[. ]+$'), '');
  const reserved = {
    'CON', 'PRN', 'AUX', 'NUL', 'COM1', 'COM2', 'COM3', 'COM4', 'COM5',
    'COM6', 'COM7', 'COM8', 'COM9', 'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5',
    'LPT6', 'LPT7', 'LPT8', 'LPT9'
  };
  if (reserved.contains(p.basenameWithoutExtension(n).toUpperCase())) {
    n = '_$n';
  }
  if (n.isEmpty) n = 'archivo';
  if (n.length > 180) {
    final ext = p.extension(n);
    n = n.substring(0, 180 - ext.length) + ext;
  }
  return n;
}
