/// Saca el total, el comercio y la fecha del texto que devuelve el OCR de un
/// tique. El OCR suele separar columnas en líneas distintas, así que el
/// importe del total puede estar en la misma línea o en la siguiente.
library;

class ReceiptGuess {
  const ReceiptGuess({this.totalMinor, this.merchant, this.date});
  final int? totalMinor;
  final String? merchant;
  final DateTime? date;
}

final _amount = RegExp(r'(?<![\d.,])(\d{1,6}(?:[.,]\d{3})*[.,]\d{2})(?![\d])');
final _totalWords = RegExp(
    r'\b(total(\s+a\s+pagar)?|importe(\s+total)?|a\s+pagar|suma|total\s+eur|total\s+€)\b',
    caseSensitive: false);
final _excluded = RegExp(
    r'(subtotal|sub-total|base\s*imp|iva|i\.v\.a|cuota|cambio|entregado|efectivo|propina\s+sugerida|descuento|ahorro|dto)',
    caseSensitive: false);
final _date = RegExp(r'\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b');

int? _toMinor(String s) {
  final dec = s[s.length - 3];
  final clean = s.replaceAll(dec == ',' ? '.' : ',', '').replaceAll(dec, '');
  return int.tryParse(clean);
}

List<int> _amountsIn(String line) =>
    _amount.allMatches(line).map((m) => _toMinor(m.group(1)!)).whereType<int>().toList();

ReceiptGuess parseReceipt(String text) {
  final lines = text
      .split(RegExp(r'\r?\n'))
      .map((l) => l.trim())
      .where((l) => l.isNotEmpty)
      .toList();

  int? total;
  for (var i = 0; i < lines.length && total == null; i++) {
    final line = lines[i];
    if (!_totalWords.hasMatch(line) || _excluded.hasMatch(line)) continue;
    final here = _amountsIn(line);
    if (here.isNotEmpty) {
      total = here.last;
    } else if (i + 1 < lines.length) {
      final next = _amountsIn(lines[i + 1]);
      if (next.isNotEmpty) total = next.first;
    }
  }
  if (total == null) {
    var best = 0;
    for (final line in lines) {
      if (_excluded.hasMatch(line)) continue;
      for (final a in _amountsIn(line)) {
        if (a > best) best = a;
      }
    }
    if (best > 0) total = best;
  }

  String? merchant;
  for (final line in lines.take(5)) {
    final letters = line.replaceAll(RegExp(r'[^A-Za-zÁÉÍÓÚÑáéíóúñ ]'), '').trim();
    if (letters.length >= 3 && !_totalWords.hasMatch(line)) {
      merchant = _titleCase(letters);
      break;
    }
  }

  DateTime? date;
  final m = _date.firstMatch(text);
  if (m != null) {
    final d = int.parse(m.group(1)!);
    final mo = int.parse(m.group(2)!);
    var y = int.parse(m.group(3)!);
    if (y < 100) y += 2000;
    if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12 && y >= 2000 && y <= 2100) {
      date = DateTime(y, mo, d, 12);
    }
  }

  return ReceiptGuess(totalMinor: total, merchant: merchant, date: date);
}

String _titleCase(String s) {
  final words = s.toLowerCase().split(RegExp(r'\s+')).where((w) => w.isNotEmpty);
  final out = words.map((w) => w[0].toUpperCase() + w.substring(1)).join(' ');
  return out.length > 40 ? out.substring(0, 40) : out;
}
