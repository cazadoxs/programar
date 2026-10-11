import 'ledger.dart';
import 'models.dart';
import 'money.dart';

/// CSV pensado para abrirse en Excel o Google Sheets en español: separador
/// ";", decimales con coma y BOM UTF-8 para que salgan bien las tildes.
String exportCsv(Group g, List<Expense> expenses) {
  final people = g.people;
  final header = [
    'Fecha',
    'Concepto',
    'Categoría',
    'Tipo',
    'Pagado por',
    'Importe',
    'Moneda',
    'Tipo de cambio',
    'Importe (${g.currency})',
    for (final p in people) 'Parte de ${p.name} (${g.currency})',
    'Nota',
  ];
  final rows = <List<String>>[header];
  final sorted = [...expenses]..sort((a, b) => a.date.compareTo(b.date));
  for (final e in sorted) {
    final shares = owedSharesInGroupCurrency(e, g.currency);
    final d = DateTime.fromMillisecondsSinceEpoch(e.date);
    rows.add([
      '${d.year}-${_two(d.month)}-${_two(d.day)}',
      e.isTransfer ? 'Pago a ${g.nameOf(e.split.keys.first)}' : e.title,
      e.isTransfer ? 'Pago' : categoryOf(e.category).label,
      e.isTransfer ? 'Pago' : e.splitMode.label,
      g.nameOf(e.payerId),
      _num(e.amountMinor, e.currency),
      e.currency,
      e.rate.toString().replaceAll('.', ','),
      _num(amountInGroupCurrency(e, g.currency), g.currency),
      for (final p in people) _num(shares[p.id] ?? 0, g.currency),
      e.note,
    ]);
  }
  final net = balances(g, expenses);
  rows.add([]);
  rows.add(['Saldo final']);
  for (final p in people) {
    rows.add([p.name, _num(net[p.id] ?? 0, g.currency)]);
  }
  return '﻿${rows.map((r) => r.map(_cell).join(';')).join('\r\n')}\r\n';
}

String _two(int n) => n.toString().padLeft(2, '0');

String _num(int minor, String code) {
  final c = currencyOf(code);
  final neg = minor < 0;
  final abs = minor.abs();
  final s = c.decimals == 0
      ? '$abs'
      : '${abs ~/ c.factor},${(abs % c.factor).toString().padLeft(c.decimals, '0')}';
  return neg ? '-$s' : s;
}

/// Escapa una celda. Además de las comillas, neutraliza la inyección de
/// fórmulas: un concepto que empiece por = + - @ (o tabulador/retorno) se
/// prefija con ' para que la hoja de cálculo no lo ejecute.
String _cell(String v) {
  var s = v;
  if (s.isNotEmpty && RegExp(r'^[=+\-@\t\r]').hasMatch(s) && !_isPlainNumber(s)) {
    s = "'$s";
  }
  if (s.contains(RegExp(r'[;"\r\n]'))) {
    s = '"${s.replaceAll('"', '""')}"';
  }
  return s;
}

bool _isPlainNumber(String s) => RegExp(r'^-\d+(,\d+)?$').hasMatch(s);
