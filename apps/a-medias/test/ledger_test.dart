import 'package:a_medias/core/ledger.dart';
import 'package:a_medias/core/models.dart';
import 'package:a_medias/core/money.dart';
import 'package:flutter_test/flutter_test.dart';

Group _group([String currency = 'EUR']) => Group(
      id: 'g1',
      name: 'Piso',
      currency: currency,
      emoji: '🏠',
      people: const [
        Person(id: 'ana', name: 'Ana'),
        Person(id: 'bea', name: 'Bea'),
        Person(id: 'carlos', name: 'Carlos'),
      ],
      createdAt: 0,
      updatedAt: 0,
    );

Expense _exp(
  int amount,
  String payer,
  Map<String, int> split, {
  SplitMode mode = SplitMode.equal,
  String currency = 'EUR',
  double rate = 1,
  bool transfer = false,
}) =>
    Expense(
      id: newId(),
      title: 'x',
      amountMinor: amount,
      currency: currency,
      rate: rate,
      payerId: payer,
      splitMode: mode,
      split: split,
      category: 'food',
      date: DateTime(2026, 10, 1).millisecondsSinceEpoch,
      createdAt: 0,
      updatedAt: 0,
      isTransfer: transfer,
    );

void main() {
  group('money', () {
    test('formats Spanish style', () {
      expect(formatMoney(123456, 'EUR'), '1.234,56 €');
      expect(formatMoney(-500, 'EUR'), '−5,00 €');
      expect(formatMoney(1999, 'USD'), '\$19,99');
      expect(formatMoney(1500, 'JPY'), '¥1.500');
      expect(formatMoney(250, 'EUR', signed: true), '+2,50 €');
    });

    test('parses what people type', () {
      expect(parseMoney('12', 'EUR'), 1200);
      expect(parseMoney('12,5', 'EUR'), 1250);
      expect(parseMoney('12.50', 'EUR'), 1250);
      expect(parseMoney('1.234,56', 'EUR'), 123456);
      expect(parseMoney('1,234.56', 'EUR'), 123456);
      expect(parseMoney('1.234', 'EUR'), 123400);
      expect(parseMoney('€ 7', 'EUR'), 700);
      expect(parseMoney('1.500', 'JPY'), 1500);
      expect(parseMoney('0,001', 'EUR'), isNull);
      expect(parseMoney('', 'EUR'), isNull);
      expect(parseMoney('abc', 'EUR'), isNull);
      expect(parseMoney('99999999999999999999', 'EUR'), isNull);
    });

    test('allocate always sums to total', () {
      final r = allocate(100, {'a': 1, 'b': 1, 'c': 1});
      expect(r.values.reduce((a, b) => a + b), 100);
      expect(r, {'a': 34, 'b': 33, 'c': 33});
      expect(allocate(1001, {'a': 3333, 'b': 3333, 'c': 3334}).values.reduce((a, b) => a + b), 1001);
      expect(allocate(5, {}), isEmpty);
    });

    test('convert between currencies with different decimals', () {
      expect(convertMinor(1000, 'JPY', 'EUR', 0.0062), 620);
      expect(convertMinor(1000, 'USD', 'EUR', 0.92), 920);
      expect(convertMinor(1000, 'EUR', 'EUR', 3), 1000);
    });
  });

  group('splits', () {
    test('equal split', () {
      final e = _exp(1000, 'ana', {'ana': 1, 'bea': 1, 'carlos': 1});
      expect(owedShares(e), {'ana': 334, 'bea': 333, 'carlos': 333});
    });

    test('percent split', () {
      final e = _exp(2000, 'ana', {'ana': 5000, 'bea': 2500, 'carlos': 2500},
          mode: SplitMode.percent);
      expect(owedShares(e), {'ana': 1000, 'bea': 500, 'carlos': 500});
      expect(e.validate(_group()), isNull);
      e.split['carlos'] = 2000;
      expect(e.validate(_group()), contains('100 %'));
    });

    test('shares split', () {
      final e = _exp(900, 'ana', {'ana': 2, 'bea': 1}, mode: SplitMode.shares);
      expect(owedShares(e), {'ana': 600, 'bea': 300});
    });

    test('exact split must add up', () {
      final e = _exp(1000, 'ana', {'ana': 300, 'bea': 600}, mode: SplitMode.exact);
      expect(e.validate(_group()), contains('Faltan'));
      e.split['carlos'] = 100;
      expect(e.validate(_group()), isNull);
    });

    test('validation catches bad data', () {
      final g = _group();
      expect(_exp(0, 'ana', {'ana': 1}).validate(g), isNotNull);
      expect(_exp(100, 'nadie', {'ana': 1}).validate(g), isNotNull);
      expect(_exp(100, 'ana', {'fantasma': 1}).validate(g), isNotNull);
      expect(_exp(100, 'ana', {'ana': 1}, rate: 0).validate(g), isNotNull);
      expect(_exp(100, 'ana', {'ana': 1}, transfer: true).validate(g), isNotNull);
      expect(_exp(100, 'ana', {'bea': 1}, transfer: true).validate(g), isNull);
    });
  });

  group('balances', () {
    test('sum to zero and settle', () {
      final g = _group();
      final ex = [
        _exp(3000, 'ana', {'ana': 1, 'bea': 1, 'carlos': 1}),
        _exp(1000, 'bea', {'bea': 1, 'carlos': 1}),
        _exp(1001, 'carlos', {'ana': 1, 'bea': 1, 'carlos': 1}),
      ];
      final net = balances(g, ex);
      expect(net.values.reduce((a, b) => a + b), 0);
      final transfers = simplifyDebts(net);
      // Aplicar los pagos deja a todos a cero.
      final after = Map.of(net);
      for (final t in transfers) {
        after[t.from] = after[t.from]! + t.amountMinor;
        after[t.to] = after[t.to]! - t.amountMinor;
      }
      expect(after.values.every((v) => v == 0), isTrue);
      expect(transfers.length, lessThanOrEqualTo(2));
    });

    test('transfer settles a debt', () {
      final g = _group();
      final ex = [
        _exp(2000, 'ana', {'ana': 1, 'bea': 1}),
        _exp(1000, 'bea', {'ana': 1}, transfer: true),
      ];
      expect(balances(g, ex), {'ana': 0, 'bea': 0, 'carlos': 0});
      expect(simplifyDebts(balances(g, ex)), isEmpty);
    });

    test('foreign currency expense converts', () {
      final g = _group();
      final ex = [
        _exp(10000, 'ana', {'ana': 1, 'bea': 1}, currency: 'USD', rate: 0.9),
      ];
      final net = balances(g, ex);
      expect(net['ana'], 4500);
      expect(net['bea'], -4500);
    });

    test('simplification beats pairwise on chains', () {
      final g = _group();
      final ex = [
        _exp(1000, 'ana', {'bea': 1}, mode: SplitMode.shares),
        _exp(1000, 'bea', {'carlos': 1}, mode: SplitMode.shares),
      ];
      expect(pairwiseDebts(g, ex).length, 2);
      final s = simplifyDebts(balances(g, ex));
      expect(s.length, 1);
      expect(s.first.from, 'carlos');
      expect(s.first.to, 'ana');
    });

    test('stats ignore transfers', () {
      final g = _group();
      final ex = [
        _exp(2000, 'ana', {'ana': 1, 'bea': 1}),
        _exp(1000, 'bea', {'ana': 1}, transfer: true),
      ];
      final s = GroupStats(g, ex);
      expect(s.total, 2000);
      expect(s.consumedBy, {'ana': 1000, 'bea': 1000});
    });
  });

  test('json roundtrip', () {
    final d = GroupData(group: _group(), expenses: [_exp(100, 'ana', {'ana': 1})], meId: 'ana');
    final back = GroupData.fromJson(d.toJson());
    expect(back.group.people.length, 3);
    expect(back.expenses.first.amountMinor, 100);
    expect(back.meId, 'ana');
  });
}
