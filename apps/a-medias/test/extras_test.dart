import 'package:a_medias/core/csv_export.dart';
import 'package:a_medias/core/invite.dart';
import 'package:a_medias/core/models.dart';
import 'package:a_medias/core/receipt_parser.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('receipt', () {
    test('total on same line', () {
      final g = parseReceipt('''
MERCADONA S.A.
C/ Mayor 3
LECHE 1,05
PAN 0,89
SUBTOTAL 1,94
IVA 10% 0,18
TOTAL (€) 2,12
EFECTIVO 5,00
CAMBIO 2,88
12/09/2026 18:32''');
      expect(g.totalMinor, 212);
      expect(g.merchant, 'Mercadona Sa');
      expect(g.date, DateTime(2026, 9, 12, 12));
    });

    test('total on next line (columns split)', () {
      final g = parseReceipt('Bar Pepe\nTOTAL A PAGAR\n23,50\nGracias');
      expect(g.totalMinor, 2350);
    });

    test('falls back to the largest amount', () {
      final g = parseReceipt('Kiosko\n1,20\n3,40\n1.250,00\nIVA 200,00');
      expect(g.totalMinor, 125000);
    });

    test('nothing found', () {
      expect(parseReceipt('hola').totalMinor, isNull);
    });
  });

  group('invite', () {
    test('roundtrip and tolerant parsing', () {
      const i = Invite('abcdEFGH1234abcdEFGH', 'k1k2k3k4k5k6k7k8k9k0');
      final link = i.toUri('amedias.web.app').toString();
      expect(link, startsWith('https://amedias.web.app/unirse#'));
      final back = Invite.parse('Únete a mi grupo: $link 👋');
      expect(back?.groupId, i.groupId);
      expect(back?.secret, i.secret);
    });

    test('rejects junk', () {
      expect(Invite.parse('https://x.com/otra#g=abc&k=def'), isNull);
      expect(Invite.parse('https://x.com/unirse#g=../../x&k=aaaaaaaaaa'), isNull);
      expect(Invite.parse('nada'), isNull);
    });
  });

  test('csv escapes formulas and separators', () {
    final g = Group(
      id: 'g',
      name: 'Viaje',
      currency: 'EUR',
      emoji: '✈️',
      people: const [Person(id: 'a', name: 'Ana'), Person(id: 'b', name: 'Bea; "B"')],
      createdAt: 0,
      updatedAt: 0,
    );
    final e = Expense(
      id: 'e1',
      title: '=HYPERLINK("http://evil")',
      amountMinor: 1001,
      currency: 'EUR',
      rate: 1,
      payerId: 'a',
      splitMode: SplitMode.equal,
      split: {'a': 1, 'b': 1},
      category: 'food',
      date: DateTime(2026, 1, 2).millisecondsSinceEpoch,
      createdAt: 0,
      updatedAt: 0,
    );
    final csv = exportCsv(g, [e]);
    expect(csv, startsWith('﻿Fecha;'));
    expect(csv, contains('"\'=HYPERLINK(""http://evil"")"'));
    expect(csv, contains('"Parte de Bea; ""B"" (EUR)"'));
    expect(csv, contains('2026-01-02;'));
    expect(csv, contains(';10,01;'));
    expect(csv, contains('Ana;5,00'));
    expect(csv, contains('-5,00'));
  });
}
