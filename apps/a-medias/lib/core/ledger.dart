import 'money.dart';
import 'models.dart';

/// Cuánto le corresponde pagar a cada persona de un gasto, en la moneda del
/// propio gasto. La suma es siempre exactamente el importe.
Map<String, int> owedShares(Expense e) {
  final active = {
    for (final entry in e.split.entries)
      if (entry.value > 0) entry.key: entry.value,
  };
  switch (e.splitMode) {
    case SplitMode.exact:
      return Map.of(active);
    case SplitMode.equal:
      return allocate(e.amountMinor, {for (final k in active.keys) k: 1});
    case SplitMode.percent:
    case SplitMode.shares:
      return allocate(e.amountMinor, active);
  }
}

/// Igual que [owedShares] pero convertido a la moneda del grupo. Se convierte
/// el total una vez y se reparte, así la suma cuadra al céntimo.
Map<String, int> owedSharesInGroupCurrency(Expense e, String groupCurrency) {
  final shares = owedShares(e);
  if (e.currency == groupCurrency) return shares;
  final total = convertMinor(e.amountMinor, e.currency, groupCurrency, e.rate);
  return allocate(total, shares);
}

int amountInGroupCurrency(Expense e, String groupCurrency) =>
    convertMinor(e.amountMinor, e.currency, groupCurrency, e.rate);

/// Saldo neto de cada persona en la moneda del grupo: positivo = le deben,
/// negativo = debe. La suma de todos los saldos es cero.
Map<String, int> balances(Group g, List<Expense> expenses) {
  final net = {for (final p in g.people) p.id: 0};
  for (final e in expenses) {
    final shares = owedSharesInGroupCurrency(e, g.currency);
    final paid = shares.values.fold<int>(0, (a, b) => a + b);
    net[e.payerId] = (net[e.payerId] ?? 0) + paid;
    shares.forEach((id, v) => net[id] = (net[id] ?? 0) - v);
  }
  return net;
}

class Transfer {
  const Transfer(this.from, this.to, this.amountMinor);
  final String from;
  final String to;
  final int amountMinor;

  @override
  String toString() => '$from→$to:$amountMinor';
}

/// Simplifica deudas: el menor número práctico de pagos para dejar a todo el
/// mundo a cero. Empareja siempre al que más debe con a quien más se le
/// debe, que da como mucho n-1 pagos y en la práctica casi siempre el
/// mínimo. Es determinista (desempate por id) para que todos los móviles
/// del grupo vean las mismas sugerencias.
List<Transfer> simplifyDebts(Map<String, int> net) {
  final debtors = <MapEntry<String, int>>[];
  final creditors = <MapEntry<String, int>>[];
  net.forEach((id, v) {
    if (v < 0) debtors.add(MapEntry(id, -v));
    if (v > 0) creditors.add(MapEntry(id, v));
  });
  int cmp(MapEntry<String, int> a, MapEntry<String, int> b) {
    final c = b.value.compareTo(a.value);
    return c != 0 ? c : a.key.compareTo(b.key);
  }

  final result = <Transfer>[];
  while (debtors.isNotEmpty && creditors.isNotEmpty) {
    debtors.sort(cmp);
    creditors.sort(cmp);
    final d = debtors.first;
    final c = creditors.first;
    final amount = d.value < c.value ? d.value : c.value;
    result.add(Transfer(d.key, c.key, amount));
    debtors.removeAt(0);
    creditors.removeAt(0);
    if (d.value > amount) debtors.add(MapEntry(d.key, d.value - amount));
    if (c.value > amount) creditors.add(MapEntry(c.key, c.value - amount));
  }
  return result;
}

/// Pagos directos sin simplificar: quién le debe a quién según los gastos
/// concretos, compensando solo entre cada pareja.
List<Transfer> pairwiseDebts(Group g, List<Expense> expenses) {
  final owe = <String, int>{}; // "a|b" -> a le debe a b
  for (final e in expenses) {
    final shares = owedSharesInGroupCurrency(e, g.currency);
    shares.forEach((id, v) {
      if (id == e.payerId || v == 0) return;
      final key = '$id|${e.payerId}';
      owe[key] = (owe[key] ?? 0) + v;
    });
  }
  final result = <Transfer>[];
  final seen = <String>{};
  for (final key in owe.keys) {
    final parts = key.split('|');
    final a = parts[0], b = parts[1];
    final pair = ([a, b]..sort()).join('|');
    if (!seen.add(pair)) continue;
    final ab = owe['$a|$b'] ?? 0;
    final ba = owe['$b|$a'] ?? 0;
    if (ab > ba) result.add(Transfer(a, b, ab - ba));
    if (ba > ab) result.add(Transfer(b, a, ba - ab));
  }
  result.sort((x, y) => y.amountMinor.compareTo(x.amountMinor));
  return result;
}

/// Totales para la cabecera y las estadísticas (sin contar pagos entre
/// personas, que no son gasto).
class GroupStats {
  GroupStats(Group g, List<Expense> expenses) {
    for (final e in expenses) {
      if (e.isTransfer) continue;
      final amount = amountInGroupCurrency(e, g.currency);
      total += amount;
      byCategory[e.category] = (byCategory[e.category] ?? 0) + amount;
      final d = DateTime.fromMillisecondsSinceEpoch(e.date);
      final month = '${d.year}-${d.month.toString().padLeft(2, '0')}';
      byMonth[month] = (byMonth[month] ?? 0) + amount;
      paidBy[e.payerId] = (paidBy[e.payerId] ?? 0) + amount;
      owedSharesInGroupCurrency(e, g.currency).forEach((id, v) {
        consumedBy[id] = (consumedBy[id] ?? 0) + v;
      });
    }
  }

  int total = 0;
  final byCategory = <String, int>{};
  final byMonth = <String, int>{};
  final paidBy = <String, int>{};
  final consumedBy = <String, int>{};
}
