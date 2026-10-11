import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import '../core/ledger.dart';
import '../core/models.dart';
import '../core/money.dart';
import '../main.dart';
import 'theme.dart';
import 'widgets.dart';

Future<void> showSettleSheet(BuildContext context, GroupData d, Transfer t) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (_) => _SettleSheet(data: d, transfer: t),
  );
}

/// Saldar una deuda. A medias no mueve dinero: ayuda a hacer el Bizum (o la
/// transferencia) copiando el importe y el concepto, y luego lo apunta.
class _SettleSheet extends StatefulWidget {
  const _SettleSheet({required this.data, required this.transfer});
  final GroupData data;
  final Transfer transfer;

  @override
  State<_SettleSheet> createState() => _SettleSheetState();
}

class _SettleSheetState extends State<_SettleSheet> {
  late final _amount = TextEditingController(
    text: minorToInput(widget.transfer.amountMinor, widget.data.group.currency),
  );

  Group get _g => widget.data.group;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  int? get _value => parseMoney(_amount.text, _g.currency);

  String get _concept => 'A medias · ${_g.name}';

  void _record() {
    final v = _value;
    if (v == null || v <= 0) {
      toast(context, 'Escribe un importe válido.');
      return;
    }
    appState.recordPayment(_g.id, Transfer(widget.transfer.from, widget.transfer.to, v));
    Navigator.of(context).pop();
    toast(context, 'Pago apuntado. ¡Cuentas al día!');
  }

  void _copyForBizum() {
    final v = _value ?? widget.transfer.amountMinor;
    Clipboard.setData(ClipboardData(text: minorToInput(v, _g.currency)));
    toast(context, 'Importe copiado. Abre Bizum en la app de tu banco y pégalo. Concepto: «$_concept».');
  }

  void _remind() {
    final from = _g.nameOf(widget.transfer.from);
    final to = _g.nameOf(widget.transfer.to);
    final v = formatMoney(_value ?? widget.transfer.amountMinor, _g.currency);
    final iAmCreditor = widget.data.meId == widget.transfer.to;
    final text = iAmCreditor
        ? '¡Hola $from! Según A medias, en «${_g.name}» me debes $v. ¿Me haces un Bizum cuando puedas? 🙏'
        : '¡Hola $to! Según A medias, en «${_g.name}» te debo $v. Te lo mando por Bizum.';
    SharePlus.instance.share(ShareParams(text: text));
  }

  @override
  Widget build(BuildContext context) {
    final from = _g.person(widget.transfer.from)!;
    final to = _g.person(widget.transfer.to)!;
    final iPay = widget.data.meId == from.id;
    return Padding(
      padding: EdgeInsets.fromLTRB(24, 0, 24, 24 + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Column(children: [Avatar(from, size: 56), const SizedBox(height: 6), Text(from.name, style: context.text.titleSmall)]),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Icon(Icons.arrow_forward_rounded, size: 32, color: Theme.of(context).colorScheme.primary),
              ),
              Column(children: [Avatar(to, size: 56), const SizedBox(height: 6), Text(to.name, style: context.text.titleSmall)]),
            ],
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            textAlign: TextAlign.center,
            style: context.text.displaySmall,
            decoration: InputDecoration(suffixText: currencyOf(_g.currency).symbol),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 6),
          Text(
            'Puedes apuntar un pago parcial cambiando el importe.',
            textAlign: TextAlign.center,
            style: context.text.bodySmall,
          ),
          const SizedBox(height: 18),
          if (iPay) ...[
            OutlinedButton.icon(
              onPressed: _copyForBizum,
              icon: const Icon(Icons.bolt_rounded),
              label: const Text('Copiar importe para Bizum'),
            ),
            const SizedBox(height: 8),
          ],
          OutlinedButton.icon(
            onPressed: _remind,
            icon: const Icon(Icons.chat_bubble_outline_rounded),
            label: Text(widget.data.meId == to.id ? 'Pedírselo por mensaje' : 'Avisar por mensaje'),
          ),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: _record,
            icon: const Icon(Icons.check_rounded),
            label: const Text('Marcar como pagado'),
          ),
          const SizedBox(height: 10),
          Text(
            'A medias no toca tu dinero: el pago lo hacéis por Bizum o transferencia y aquí solo se apunta.',
            textAlign: TextAlign.center,
            style: context.text.bodySmall,
          ),
        ],
      ),
    );
  }
}
