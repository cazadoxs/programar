import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;

import '../app/state.dart';
import 'theme.dart';

class Logo extends StatelessWidget {
  final double size;
  const Logo({super.key, this.size = 36});
  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: Palette.lime,
          borderRadius: BorderRadius.circular(size * 0.32),
        ),
        child: Icon(Icons.swap_horiz_rounded, color: Palette.ink, size: size * 0.66),
      );
}

class PageHeader extends StatelessWidget {
  final String title;
  final String subtitle;
  const PageHeader(this.title, this.subtitle, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title,
              style: const TextStyle(
                  fontSize: 30, fontWeight: FontWeight.w800, letterSpacing: -0.5)),
          const SizedBox(height: 4),
          Text(subtitle, style: const TextStyle(color: Palette.muted)),
        ]),
      );
}

/// Contenedor de página con ancho máximo y scroll.
class PageBody extends StatelessWidget {
  final List<Widget> children;
  const PageBody({super.key, required this.children});
  @override
  Widget build(BuildContext context) => Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 640),
          child: ListView(
            padding: const EdgeInsets.fromLTRB(18, 20, 18, 32),
            children: children,
          ),
        ),
      );
}

class SectionLabel extends StatelessWidget {
  final String text;
  const SectionLabel(this.text, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 22, 4, 10),
        child: Text(text.toUpperCase(),
            style: const TextStyle(
                color: Palette.muted,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.4)),
      );
}

/// Tarjeta de la transferencia en curso o terminada.
class TransferCard extends StatelessWidget {
  final TransferStatus st;
  const TransferCard(this.st, {super.key});

  @override
  Widget build(BuildContext context) {
    final state = AppState.instance;
    final send = st.direction == Direction.send;
    final pr = st.progress;
    final (label, color) = switch (st.phase) {
      Phase.connecting => ('Conectando…', Palette.sky),
      Phase.waiting => (send ? 'Esperando a que acepte…' : 'Esperando…', Palette.sky),
      Phase.running => (send ? 'Enviando' : 'Recibiendo', Palette.lime),
      Phase.done => (send ? 'Enviado' : 'Recibido', Palette.lime),
      Phase.failed => ('No se completó', Palette.coral),
    };
    return Card(
      color: Palette.panelHi,
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(send ? Icons.north_east_rounded : Icons.south_west_rounded, color: color),
            const SizedBox(width: 10),
            Expanded(
              child: Text('$label · ${st.peer}',
                  style: TextStyle(fontWeight: FontWeight.w700, color: color)),
            ),
            if (st.active)
              TextButton(onPressed: state.cancelTransfer, child: const Text('Cancelar'))
            else
              IconButton(
                  onPressed: state.dismissStatus,
                  icon: const Icon(Icons.close_rounded),
                  tooltip: 'Cerrar'),
          ]),
          if (pr != null && st.phase == Phase.running) ...[
            const SizedBox(height: 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: LinearProgressIndicator(
                  value: pr.fraction, minHeight: 10, color: Palette.lime),
            ),
            const SizedBox(height: 8),
            Text(
                '${pr.currentFile} (${pr.fileIndex + 1}/${pr.fileCount}) · '
                '${formatBytes(pr.bytesDone)} de ${formatBytes(pr.bytesTotal)}',
                style: const TextStyle(color: Palette.muted, fontSize: 13)),
            if (Platform.isAndroid)
              const Padding(
                padding: EdgeInsets.only(top: 6),
                child: Text('Puedes salir de la app: esta transferencia seguirá.',
                    style: TextStyle(color: Palette.muted, fontSize: 12)),
              ),
          ],
          if (st.phase == Phase.connecting || st.phase == Phase.waiting) ...[
            const SizedBox(height: 12),
            const LinearProgressIndicator(minHeight: 4),
          ],
          if (st.message != null) ...[
            const SizedBox(height: 8),
            Text(st.message!, style: const TextStyle(color: Palette.coral)),
          ],
          if (st.phase == Phase.done && !send && st.files.isNotEmpty) ...[
            const SizedBox(height: 8),
            for (final f in st.files)
              Text('• ${p.basename(f.path)}', style: const TextStyle(fontSize: 13)),
            const SizedBox(height: 6),
            SelectableText('Guardado en ${state.saveDir}',
                style: const TextStyle(color: Palette.muted, fontSize: 12)),
          ],
        ]),
      ),
    );
  }
}

Future<void> showOfferDialog(BuildContext context, PendingOffer po) async {
  final o = po.offer;
  final ok = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (ctx) {
      // Si la oferta caduca o la app pasa a segundo plano, se cierra sola.
      po.answer.future.then((_) {
        if (ctx.mounted && Navigator.of(ctx).canPop()) Navigator.of(ctx).pop();
      });
      return AlertDialog(
        title: Text('${o.deviceName} quiere enviarte'),
        content: SizedBox(
          width: 360,
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${o.files.length} archivo(s) · ${formatBytes(o.totalBytes)}',
                style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 10),
            for (final f in o.files.take(6))
              Text('• ${f.name} (${formatBytes(f.size)})', overflow: TextOverflow.ellipsis),
            if (o.files.length > 6) Text('… y ${o.files.length - 6} más'),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Rechazar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Aceptar')),
        ],
      );
    },
  );
  AppState.instance.answerOffer(ok ?? false);
}

void toast(BuildContext context, String msg) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating));
}
