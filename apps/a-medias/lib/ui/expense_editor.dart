import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../core/ledger.dart';
import '../core/models.dart';
import '../core/money.dart';
import '../core/receipt_parser.dart';
import '../data/rates.dart';
import '../main.dart';
import '../premium/ocr.dart';
import 'plus_screen.dart';
import 'theme.dart';
import 'widgets.dart';

class ExpenseEditor extends StatefulWidget {
  const ExpenseEditor({super.key, required this.groupId, this.expense, this.startWithScan = false});

  final String groupId;
  final Expense? expense;
  final bool startWithScan;

  @override
  State<ExpenseEditor> createState() => _ExpenseEditorState();
}

class _ExpenseEditorState extends State<ExpenseEditor> {
  late final GroupData _d = appState.group(widget.groupId)!;
  Group get _g => _d.group;

  final _amount = TextEditingController();
  final _title = TextEditingController();
  final _note = TextEditingController();
  final _rate = TextEditingController();
  final _values = <String, TextEditingController>{};

  late String _currency;
  late String _payer;
  String _category = 'food';
  DateTime _date = DateTime.now();
  SplitMode _mode = SplitMode.equal;
  final _included = <String>{};
  final _weights = <String, int>{};
  bool _isTransfer = false;
  String? _error;
  bool _scanning = false;
  bool _fetchingRate = false;

  bool get _editing => widget.expense != null;

  @override
  void initState() {
    super.initState();
    _currency = _g.currency;
    _payer = _d.meId ?? _g.people.first.id;
    for (final p in _g.people) {
      _values[p.id] = TextEditingController();
      _weights[p.id] = 1;
    }
    _included.addAll(_g.people.map((p) => p.id));

    final e = widget.expense;
    if (e != null) {
      _currency = e.currency;
      _amount.text = minorToInput(e.amountMinor, e.currency);
      _title.text = e.title;
      _note.text = e.note;
      _rate.text = _rateText(e.rate);
      _payer = _g.person(e.payerId) != null ? e.payerId : _payer;
      _category = e.category;
      _date = DateTime.fromMillisecondsSinceEpoch(e.date);
      _mode = e.splitMode;
      _isTransfer = e.isTransfer;
      _included
        ..clear()
        ..addAll(e.split.keys.where((k) => (e.split[k] ?? 0) > 0 && _g.person(k) != null));
      for (final entry in e.split.entries) {
        final c = _values[entry.key];
        if (c == null) continue;
        switch (e.splitMode) {
          case SplitMode.exact:
            c.text = minorToInput(entry.value, e.currency);
          case SplitMode.percent:
            c.text = formatPercent(entry.value).replaceAll(' %', '');
          case SplitMode.shares:
            _weights[entry.key] = entry.value;
          case SplitMode.equal:
            break;
        }
      }
    }
    if (widget.startWithScan) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _scan());
    }
  }

  @override
  void dispose() {
    for (final c in [_amount, _title, _note, _rate, ..._values.values]) {
      c.dispose();
    }
    super.dispose();
  }

  String _rateText(double r) {
    var s = r.toStringAsFixed(6);
    s = s.replaceFirst(RegExp(r'0+$'), '').replaceFirst(RegExp(r'\.$'), '');
    return s.replaceAll('.', ',');
  }

  double? get _rateValue {
    if (_currency == _g.currency) return 1;
    return double.tryParse(_rate.text.trim().replaceAll(',', '.'));
  }

  int? get _amountMinor => parseMoney(_amount.text, _currency, maxMinor: Limits.maxAmountMinor);

  Map<String, int> _buildSplit() {
    switch (_mode) {
      case SplitMode.equal:
        return {for (final id in _included) id: 1};
      case SplitMode.exact:
        return {
          for (final p in _g.people)
            if ((parseMoney(_values[p.id]!.text, _currency) ?? 0) > 0)
              p.id: parseMoney(_values[p.id]!.text, _currency)!,
        };
      case SplitMode.percent:
        return {
          for (final p in _g.people)
            if (_parsePercent(_values[p.id]!.text) > 0) p.id: _parsePercent(_values[p.id]!.text),
        };
      case SplitMode.shares:
        return {
          for (final p in _g.people)
            if ((_weights[p.id] ?? 0) > 0) p.id: _weights[p.id]!,
        };
    }
  }

  /// "33,33" -> 3333 centésimas de punto.
  int _parsePercent(String s) {
    final v = double.tryParse(s.trim().replaceAll('%', '').replaceAll(',', '.'));
    if (v == null || v < 0 || v > 100) return 0;
    return (v * 100).round();
  }

  Expense? _build() {
    final amount = _amountMinor;
    final rate = _rateValue;
    final now = DateTime.now().millisecondsSinceEpoch;
    final old = widget.expense;
    return Expense(
      id: old?.id ?? newId(),
      title: cleanText(_title.text, Limits.titleLength),
      amountMinor: amount ?? 0,
      currency: _currency,
      rate: rate ?? 0,
      payerId: _payer,
      splitMode: _isTransfer ? SplitMode.exact : _mode,
      split: _isTransfer
          ? {for (final id in _included.take(1)) id: amount ?? 0}
          : _buildSplit(),
      category: _category,
      date: DateTime(_date.year, _date.month, _date.day, 12).millisecondsSinceEpoch,
      createdAt: old?.createdAt ?? now,
      updatedAt: now,
      note: cleanText(_note.text, Limits.noteLength),
      isTransfer: _isTransfer,
    );
  }

  void _save() {
    if (_amountMinor == null) {
      setState(() => _error = 'Escribe un importe válido.');
      return;
    }
    if (_currency != _g.currency && _rateValue == null) {
      setState(() => _error = 'Falta el tipo de cambio.');
      return;
    }
    final e = _build()!;
    if (_isTransfer && e.title.isEmpty) e.title = 'Pago';
    final err = e.validate(_g);
    if (err != null) {
      setState(() => _error = err);
      return;
    }
    appState.saveExpense(_g.id, e);
    Navigator.of(context).pop();
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('¿Borrar este gasto?'),
        content: const Text('Se borrará para todo el grupo.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Borrar')),
        ],
      ),
    );
    if (ok == true && mounted) {
      appState.deleteExpense(_g.id, widget.expense!.id);
      Navigator.of(context).pop();
    }
  }

  Future<void> _scan() async {
    if (!appState.plus) {
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PlusScreen()));
      if (!appState.plus || !mounted) return;
    }
    if (!ocrSupported) {
      if (mounted) toast(context, 'Escanear tiques solo está en la app de Android.');
      return;
    }
    if (!mounted) return;
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_rounded),
              title: const Text('Hacer foto al tique'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_rounded),
              title: const Text('Elegir de la galería'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    final file = await ImagePicker().pickImage(source: source, maxWidth: 2000, imageQuality: 90);
    if (file == null) return;
    setState(() => _scanning = true);
    try {
      final text = await recognizeText(file.path);
      final guess = parseReceipt(text);
      setState(() {
        if (guess.totalMinor != null) {
          _amount.text = minorToInput(guess.totalMinor!, _currency);
        }
        if (guess.merchant != null && _title.text.isEmpty) _title.text = guess.merchant!;
        if (guess.date != null) _date = guess.date!;
      });
      if (mounted) {
        toast(context, guess.totalMinor == null ? 'No encontré el total; escríbelo a mano.' : 'Revisa el importe antes de guardar.');
      }
    } catch (_) {
      if (mounted) toast(context, 'No pude leer el tique.');
    } finally {
      if (mounted) setState(() => _scanning = false);
    }
  }

  Future<void> _fetchRate() async {
    setState(() => _fetchingRate = true);
    final r = await Rates.fetch(_currency, _g.currency);
    if (!mounted) return;
    setState(() {
      _fetchingRate = false;
      if (r != null) _rate.text = _rateText(r);
    });
    if (r == null) toast(context, 'Sin conexión. Escribe el tipo de cambio a mano.');
  }

  Future<void> _pickDate() async {
    final d = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 366)),
    );
    if (d != null) setState(() => _date = d);
  }

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    final preview = _previewShares();
    return Scaffold(
      appBar: AppBar(
        title: Text(_editing ? (_isTransfer ? 'Pago' : 'Editar gasto') : 'Nuevo gasto'),
        actions: [
          if (_editing) IconButton(tooltip: 'Borrar', icon: const Icon(Icons.delete_outline_rounded), onPressed: _delete),
          if (!_isTransfer)
            IconButton(
              tooltip: 'Escanear tique',
              icon: _scanning
                  ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2.5))
                  : const Icon(Icons.document_scanner_rounded),
              onPressed: _scanning ? null : _scan,
            ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 40),
        children: [
          // Importe grande, lo primero que se escribe.
          StickerCard(
            color: am.accent,
            padding: const EdgeInsets.fromLTRB(16, 10, 10, 10),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _amount,
                    autofocus: !_editing && !widget.startWithScan,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    style: context.text.displaySmall?.copyWith(color: const Color(0xFF1C1828)),
                    decoration: const InputDecoration(
                      hintText: '0,00',
                      filled: false,
                      border: InputBorder.none,
                      enabledBorder: InputBorder.none,
                      focusedBorder: InputBorder.none,
                      contentPadding: EdgeInsets.zero,
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                ),
                _CurrencyButton(
                  value: _currency,
                  onChanged: (c) {
                    setState(() {
                      _currency = c;
                      _rate.clear();
                    });
                    if (c != _g.currency) _fetchRate();
                  },
                ),
              ],
            ),
          ),
          if (_currency != _g.currency) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _rate,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: '1 $_currency = ? ${_g.currency}',
                      helperText: _amountMinor != null && _rateValue != null
                          ? '≈ ${formatMoney(convertMinor(_amountMinor!, _currency, _g.currency, _rateValue!), _g.currency)}'
                          : null,
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton.outlined(
                  tooltip: 'Tipo de cambio de hoy',
                  onPressed: _fetchingRate ? null : _fetchRate,
                  icon: _fetchingRate
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.currency_exchange_rounded),
                ),
              ],
            ),
          ],
          const SizedBox(height: 16),
          if (!_isTransfer) ...[
            TextField(
              controller: _title,
              maxLength: Limits.titleLength,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(labelText: 'Concepto', hintText: 'Cena, súper, gasolina…', counterText: ''),
            ),
            const SizedBox(height: 14),
            SizedBox(
              height: 44,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  for (final c in categories)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text('${c.emoji} ${c.label}'),
                        selected: _category == c.id,
                        onSelected: (_) => setState(() => _category = c.id),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 18),
          ],
          _Section(
            title: _isTransfer ? 'Quién paga' : 'Pagó',
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final p in _g.people)
                  ChoiceChip(
                    avatar: Avatar(p, size: 24),
                    label: Text(p.id == _d.meId ? '${p.name} (tú)' : p.name),
                    selected: _payer == p.id,
                    onSelected: (_) => setState(() => _payer = p.id),
                  ),
              ],
            ),
          ),
          _Section(
            title: 'Fecha',
            child: OutlinedButton.icon(
              onPressed: _pickDate,
              icon: const Icon(Icons.event_rounded),
              label: Text(relativeDay(_date.millisecondsSinceEpoch)),
            ),
          ),
          if (_isTransfer)
            _Section(
              title: 'A quién',
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final p in _g.people.where((p) => p.id != _payer))
                    ChoiceChip(
                      avatar: Avatar(p, size: 24),
                      label: Text(p.name),
                      selected: _included.contains(p.id),
                      onSelected: (_) => setState(() => _included
                        ..clear()
                        ..add(p.id)),
                    ),
                ],
              ),
            )
          else ...[
            _Section(
              title: 'Cómo se reparte',
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: SegmentedButton<SplitMode>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: SplitMode.equal, label: Text('Iguales')),
                    ButtonSegment(value: SplitMode.exact, label: Text('Importes')),
                    ButtonSegment(value: SplitMode.percent, label: Text('%')),
                    ButtonSegment(value: SplitMode.shares, label: Text('Partes')),
                  ],
                  selected: {_mode},
                  onSelectionChanged: (s) => setState(() => _mode = s.first),
                ),
              ),
            ),
            StickerCard(
              offset: 3,
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Column(
                children: [
                  for (final p in _g.people) _splitRow(p, preview[p.id]),
                ],
              ),
            ),
            const SizedBox(height: 8),
            _SplitHint(mode: _mode, amount: _amountMinor, currency: _currency, split: _buildSplit()),
            const SizedBox(height: 16),
            TextField(
              controller: _note,
              maxLength: Limits.noteLength,
              maxLines: 2,
              decoration: const InputDecoration(labelText: 'Nota (opcional)', counterText: ''),
            ),
          ],
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: am.negative, fontWeight: FontWeight.w700)),
          ],
          const SizedBox(height: 20),
          FilledButton(onPressed: _save, child: Text(_editing ? 'Guardar cambios' : 'Guardar gasto')),
        ],
      ),
    );
  }

  Map<String, int> _previewShares() {
    final amount = _amountMinor;
    if (amount == null || amount <= 0) return {};
    final e = _build();
    if (e == null) return {};
    if (_mode == SplitMode.exact) return e.split;
    return owedShares(e);
  }

  Widget _splitRow(Person p, int? share) {
    final am = context.am;
    final label = Text(
      p.id == _d.meId ? '${p.name} (tú)' : p.name,
      style: context.text.titleSmall,
      overflow: TextOverflow.ellipsis,
    );
    final shareText = share == null || share == 0
        ? null
        : Text(formatMoney(share, _currency), style: context.text.bodySmall?.copyWith(color: am.ink, fontWeight: FontWeight.w700));

    Widget trailing;
    switch (_mode) {
      case SplitMode.equal:
        trailing = Checkbox(
          value: _included.contains(p.id),
          onChanged: (v) => setState(() => v == true ? _included.add(p.id) : _included.remove(p.id)),
        );
      case SplitMode.exact:
      case SplitMode.percent:
        trailing = SizedBox(
          width: 108,
          child: TextField(
            controller: _values[p.id],
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            textAlign: TextAlign.end,
            decoration: InputDecoration(
              isDense: true,
              hintText: '0',
              suffixText: _mode == SplitMode.percent ? '%' : currencyOf(_currency).symbol,
              contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
            ),
            onChanged: (_) => setState(() {}),
          ),
        );
      case SplitMode.shares:
        final w = _weights[p.id] ?? 0;
        trailing = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              visualDensity: VisualDensity.compact,
              icon: const Icon(Icons.remove_circle_outline_rounded),
              onPressed: w <= 0 ? null : () => setState(() => _weights[p.id] = w - 1),
            ),
            SizedBox(width: 24, child: Text('$w', textAlign: TextAlign.center, style: context.text.titleMedium)),
            IconButton(
              visualDensity: VisualDensity.compact,
              icon: const Icon(Icons.add_circle_outline_rounded),
              onPressed: w >= 99 ? null : () => setState(() => _weights[p.id] = w + 1),
            ),
          ],
        );
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Row(
        children: [
          Avatar(p, size: 30),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [label, if (shareText != null && _mode != SplitMode.exact) shareText],
            ),
          ),
          trailing,
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.child});
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: context.text.titleSmall),
          const SizedBox(height: 8),
          child,
        ],
      ),
    );
  }
}

class _SplitHint extends StatelessWidget {
  const _SplitHint({required this.mode, required this.amount, required this.currency, required this.split});
  final SplitMode mode;
  final int? amount;
  final String currency;
  final Map<String, int> split;

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    final sum = split.values.fold<int>(0, (a, b) => a + b);
    String? text;
    var ok = true;
    switch (mode) {
      case SplitMode.exact:
        if (amount != null) {
          final left = amount! - sum;
          ok = left == 0;
          text = left == 0
              ? 'Cuadra al céntimo.'
              : left > 0
                  ? 'Faltan ${formatMoney(left, currency)} por asignar.'
                  : 'Te pasas en ${formatMoney(-left, currency)}.';
        }
      case SplitMode.percent:
        ok = sum == 10000;
        text = ok ? 'Suma 100 %.' : 'Suma ${formatPercent(sum)}; tiene que llegar a 100 %.';
      case SplitMode.shares:
        text = 'Ejemplo: quien tiene 2 partes paga el doble que quien tiene 1.';
      case SplitMode.equal:
        text = split.isEmpty ? 'Elige al menos a una persona.' : 'Entre ${split.length} ${split.length == 1 ? 'persona' : 'personas'}.';
        ok = split.isNotEmpty;
    }
    return Text(
      text ?? '',
      style: context.text.bodySmall?.copyWith(color: ok ? am.muted : am.negative, fontWeight: FontWeight.w600),
    );
  }
}

class _CurrencyButton extends StatelessWidget {
  const _CurrencyButton({required this.value, required this.onChanged});
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      tooltip: 'Moneda',
      initialValue: value,
      onSelected: onChanged,
      itemBuilder: (_) => [
        for (final c in currencies) PopupMenuItem(value: c.code, child: Text(currencyLabel(c.code))),
      ],
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFF1C1828), width: 2),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(value, style: const TextStyle(fontFamily: display, fontWeight: FontWeight.w800, fontSize: 16, color: Color(0xFF1C1828))),
            const Icon(Icons.expand_more_rounded, color: Color(0xFF1C1828)),
          ],
        ),
      ),
    );
  }
}
