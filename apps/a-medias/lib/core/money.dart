/// Importes en unidades mínimas (céntimos) para no arrastrar errores de coma
/// flotante. Toda la aritmética de repartos y saldos se hace con enteros.
library;

class CurrencyInfo {
  const CurrencyInfo(this.code, this.symbol, this.decimals, this.name,
      {this.symbolBefore = false});

  final String code;
  final String symbol;
  final int decimals;
  final String name;
  final bool symbolBefore;

  int get factor => _pow10(decimals);
}

int _pow10(int n) {
  var r = 1;
  for (var i = 0; i < n; i++) {
    r *= 10;
  }
  return r;
}

/// Monedas habituales en viajes desde España. El orden es el de la lista
/// de selección.
const currencies = <CurrencyInfo>[
  CurrencyInfo('EUR', '€', 2, 'Euro'),
  CurrencyInfo('USD', '\$', 2, 'Dólar estadounidense', symbolBefore: true),
  CurrencyInfo('GBP', '£', 2, 'Libra esterlina', symbolBefore: true),
  CurrencyInfo('CHF', 'CHF', 2, 'Franco suizo'),
  CurrencyInfo('MXN', 'MX\$', 2, 'Peso mexicano', symbolBefore: true),
  CurrencyInfo('ARS', 'AR\$', 2, 'Peso argentino', symbolBefore: true),
  CurrencyInfo('COP', 'COL\$', 2, 'Peso colombiano', symbolBefore: true),
  CurrencyInfo('CLP', 'CLP\$', 0, 'Peso chileno', symbolBefore: true),
  CurrencyInfo('PEN', 'S/', 2, 'Sol peruano', symbolBefore: true),
  CurrencyInfo('BRL', 'R\$', 2, 'Real brasileño', symbolBefore: true),
  CurrencyInfo('MAD', 'MAD', 2, 'Dírham marroquí'),
  CurrencyInfo('JPY', '¥', 0, 'Yen japonés', symbolBefore: true),
  CurrencyInfo('CNY', '¥', 2, 'Yuan chino', symbolBefore: true),
  CurrencyInfo('THB', '฿', 2, 'Baht tailandés', symbolBefore: true),
  CurrencyInfo('SEK', 'kr', 2, 'Corona sueca'),
  CurrencyInfo('NOK', 'kr', 2, 'Corona noruega'),
  CurrencyInfo('DKK', 'kr', 2, 'Corona danesa'),
  CurrencyInfo('PLN', 'zł', 2, 'Esloti polaco'),
  CurrencyInfo('CZK', 'Kč', 2, 'Corona checa'),
  CurrencyInfo('HUF', 'Ft', 2, 'Forinto húngaro'),
  CurrencyInfo('TRY', '₺', 2, 'Lira turca'),
  CurrencyInfo('CAD', 'CA\$', 2, 'Dólar canadiense', symbolBefore: true),
  CurrencyInfo('AUD', 'A\$', 2, 'Dólar australiano', symbolBefore: true),
];

CurrencyInfo currencyOf(String code) => currencies.firstWhere(
      (c) => c.code == code,
      orElse: () => CurrencyInfo(code, code, 2, code),
    );

/// Formatea céntimos con el estilo español: separador de miles con punto y
/// decimales con coma. `signed` añade "+" a los positivos.
String formatMoney(int minor, String currencyCode, {bool signed = false}) {
  final c = currencyOf(currencyCode);
  final negative = minor < 0;
  final abs = minor.abs();
  final whole = abs ~/ c.factor;
  final frac = abs % c.factor;
  final wholeStr = _groupThousands(whole);
  final number = c.decimals == 0
      ? wholeStr
      : '$wholeStr,${frac.toString().padLeft(c.decimals, '0')}';
  final sign = negative ? '−' : (signed && minor > 0 ? '+' : '');
  return c.symbolBefore ? '$sign${c.symbol}$number' : '$sign$number ${c.symbol}';
}

String _groupThousands(int n) {
  final s = n.toString();
  final buf = StringBuffer();
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) buf.write('.');
    buf.write(s[i]);
  }
  return buf.toString();
}

/// Lee un importe escrito por una persona ("12,5", "1.234,56", "12.50",
/// "€ 7") y lo devuelve en unidades mínimas. Devuelve null si no es válido,
/// es negativo o supera [maxMinor].
int? parseMoney(String input, String currencyCode, {int maxMinor = 10000000000}) {
  final c = currencyOf(currencyCode);
  var s = input.trim().replaceAll(RegExp(r'[^\d.,]'), '');
  if (s.isEmpty) return null;

  final lastComma = s.lastIndexOf(',');
  final lastDot = s.lastIndexOf('.');
  String intPart;
  String fracPart = '';
  if (lastComma >= 0 && lastDot >= 0) {
    // El separador que aparece el último es el decimal.
    final dec = lastComma > lastDot ? ',' : '.';
    final thou = dec == ',' ? '.' : ',';
    s = s.replaceAll(thou, '');
    final i = s.lastIndexOf(dec);
    intPart = s.substring(0, i);
    fracPart = s.substring(i + 1);
  } else if (lastComma >= 0 || lastDot >= 0) {
    final sep = lastComma >= 0 ? ',' : '.';
    final parts = s.split(sep);
    final tail = parts.last;
    // "1.234" o "1,234,567" con grupos de tres se interpretan como miles
    // salvo en monedas sin decimales, donde siempre son miles.
    final looksLikeThousands = parts.length > 2 ||
        (tail.length == 3 && parts.first.isNotEmpty && !parts.first.startsWith('0'));
    if (looksLikeThousands || c.decimals == 0) {
      intPart = parts.join();
    } else {
      intPart = parts.sublist(0, parts.length - 1).join();
      fracPart = tail;
    }
  } else {
    intPart = s;
  }
  if (intPart.isEmpty) intPart = '0';
  if (fracPart.length > c.decimals) {
    // Más decimales de los que admite la moneda: no adivinamos.
    return null;
  }
  if (intPart.length > 15) return null;
  final whole = int.tryParse(intPart);
  final frac = fracPart.isEmpty ? 0 : int.tryParse(fracPart.padRight(c.decimals, '0'));
  if (whole == null || frac == null) return null;
  final minor = whole * c.factor + frac;
  if (minor > maxMinor) return null;
  return minor;
}

/// Texto editable para un importe (sin símbolo): 1234,5 -> "1234,50".
String minorToInput(int minor, String currencyCode) {
  final c = currencyOf(currencyCode);
  if (c.decimals == 0) return minor.toString();
  final whole = minor ~/ c.factor;
  final frac = (minor % c.factor).toString().padLeft(c.decimals, '0');
  return '$whole,$frac';
}

/// Convierte un importe entre monedas con un tipo "1 [from] = rate [to]",
/// teniendo en cuenta que cada moneda puede tener distinto número de
/// decimales.
int convertMinor(int minor, String from, String to, double rate) {
  if (from == to) return minor;
  final f = currencyOf(from);
  final t = currencyOf(to);
  final value = minor / f.factor * rate;
  return (value * t.factor).round();
}

/// Reparte [total] en partes proporcionales a [weights] usando el método del
/// mayor resto: la suma de las partes es siempre exactamente [total] y los
/// céntimos sobrantes van a quien tiene mayor resto (desempate estable por
/// orden de clave).
Map<String, int> allocate(int total, Map<String, num> weights) {
  final keys = weights.keys.where((k) => weights[k]! > 0).toList()..sort();
  if (keys.isEmpty) return {};
  final sumW = keys.fold<double>(0, (a, k) => a + weights[k]!.toDouble());
  final result = <String, int>{};
  final remainders = <String, double>{};
  var assigned = 0;
  for (final k in keys) {
    final exact = total * weights[k]!.toDouble() / sumW;
    final base = exact.floor();
    result[k] = base;
    remainders[k] = exact - base;
    assigned += base;
  }
  var left = total - assigned;
  final order = [...keys]
    ..sort((a, b) {
      final c = remainders[b]!.compareTo(remainders[a]!);
      return c != 0 ? c : a.compareTo(b);
    });
  var i = 0;
  while (left > 0 && order.isNotEmpty) {
    result[order[i % order.length]] = result[order[i % order.length]]! + 1;
    left--;
    i++;
  }
  return result;
}
