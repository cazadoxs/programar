import 'dart:math';

import 'money.dart';

final _rng = Random.secure();
const _alphabet =
    'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

/// Identificador aleatorio criptográficamente seguro. 20 caracteres base62
/// dan ~119 bits, suficiente para ids de documento y secretos de invitación.
String newId([int length = 20]) =>
    List.generate(length, (_) => _alphabet[_rng.nextInt(_alphabet.length)])
        .join();

final idPattern = RegExp(r'^[A-Za-z0-9]{8,40}$');

/// Límites compartidos con las reglas de Firestore (firebase/firestore.rules).
class Limits {
  static const nameLength = 40;
  static const groupNameLength = 60;
  static const titleLength = 80;
  static const noteLength = 500;
  static const maxPeople = 50;
  /// 100 millones en unidades de la moneda (en céntimos). Con tipos de cambio
  /// de hasta [maxRate] el resultado cabe en los 53 bits exactos de la web.
  static const maxAmountMinor = 10000000000;
  static const maxRate = 100000.0;
}

String cleanText(String s, int max) {
  final t = s.replaceAll(RegExp(r'[\u0000-\u001F\u007F]'), ' ').trim();
  return t.length > max ? t.substring(0, max) : t;
}

class Person {
  const Person({required this.id, required this.name});

  final String id;
  final String name;

  Map<String, dynamic> toJson() => {'id': id, 'name': name};

  factory Person.fromJson(Map<String, dynamic> j) => Person(
        id: j['id'] as String,
        name: cleanText(j['name'] as String? ?? '', Limits.nameLength),
      );

  String get initials {
    final parts =
        name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    String take(String s, int n) => String.fromCharCodes(s.runes.take(n));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return take(parts.first, 2).toUpperCase();
    return (take(parts[0], 1) + take(parts[1], 1)).toUpperCase();
  }
}

class Group {
  Group({
    required this.id,
    required this.name,
    required this.currency,
    required this.emoji,
    required this.people,
    required this.createdAt,
    required this.updatedAt,
    this.ownerUid,
    this.archived = false,
  });

  final String id;
  String name;
  String currency;
  String emoji;
  List<Person> people;
  final int createdAt;
  int updatedAt;
  String? ownerUid;
  bool archived;

  Person? person(String id) {
    for (final p in people) {
      if (p.id == id) return p;
    }
    return null;
  }

  String nameOf(String id) => person(id)?.name ?? 'Alguien';

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'currency': currency,
        'emoji': emoji,
        'people': people.map((p) => p.toJson()).toList(),
        'createdAt': createdAt,
        'updatedAt': updatedAt,
        if (ownerUid != null) 'ownerUid': ownerUid,
        'archived': archived,
      };

  factory Group.fromJson(Map<String, dynamic> j) => Group(
        id: j['id'] as String,
        name: cleanText(j['name'] as String? ?? '', Limits.groupNameLength),
        currency: j['currency'] as String? ?? 'EUR',
        emoji: j['emoji'] as String? ?? '🏠',
        people: ((j['people'] as List?) ?? [])
            .map((e) => Person.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        createdAt: (j['createdAt'] as num?)?.toInt() ?? 0,
        updatedAt: (j['updatedAt'] as num?)?.toInt() ?? 0,
        ownerUid: j['ownerUid'] as String?,
        archived: j['archived'] as bool? ?? false,
      );
}

enum SplitMode { equal, exact, percent, shares }

extension SplitModeLabel on SplitMode {
  String get label => switch (this) {
        SplitMode.equal => 'A partes iguales',
        SplitMode.exact => 'Importes exactos',
        SplitMode.percent => 'Porcentajes',
        SplitMode.shares => 'Por partes',
      };
}

class Category {
  const Category(this.id, this.emoji, this.label);
  final String id;
  final String emoji;
  final String label;
}

const categories = <Category>[
  Category('food', '🍝', 'Comida'),
  Category('groceries', '🛒', 'Súper'),
  Category('home', '🏠', 'Casa'),
  Category('bills', '💡', 'Facturas'),
  Category('transport', '🚕', 'Transporte'),
  Category('travel', '✈️', 'Viaje'),
  Category('lodging', '🛏️', 'Alojamiento'),
  Category('drinks', '🍻', 'Copas'),
  Category('fun', '🎟️', 'Ocio'),
  Category('gifts', '🎁', 'Regalos'),
  Category('health', '💊', 'Salud'),
  Category('other', '🧾', 'Otros'),
];

Category categoryOf(String id) =>
    categories.firstWhere((c) => c.id == id, orElse: () => categories.last);

class Expense {
  Expense({
    required this.id,
    required this.title,
    required this.amountMinor,
    required this.currency,
    required this.rate,
    required this.payerId,
    required this.splitMode,
    required this.split,
    required this.category,
    required this.date,
    required this.createdAt,
    required this.updatedAt,
    this.note = '',
    this.isTransfer = false,
  });

  final String id;
  String title;

  /// Importe en unidades mínimas de [currency].
  int amountMinor;
  String currency;

  /// Tipo de cambio: 1 [currency] = [rate] moneda del grupo.
  double rate;
  String payerId;
  SplitMode splitMode;

  /// Según [splitMode]: equal → 1 para cada persona incluida; exact →
  /// unidades mínimas; percent → centésimas de punto (10000 = 100 %);
  /// shares → peso entero.
  Map<String, int> split;
  String category;
  int date;
  final int createdAt;
  int updatedAt;
  String note;

  /// Un pago para saldar deudas: [payerId] pagó a la única persona de
  /// [split].
  bool isTransfer;

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'amountMinor': amountMinor,
        'currency': currency,
        'rate': rate,
        'payerId': payerId,
        'splitMode': splitMode.name,
        'split': split,
        'category': category,
        'date': date,
        'createdAt': createdAt,
        'updatedAt': updatedAt,
        'note': note,
        'isTransfer': isTransfer,
      };

  factory Expense.fromJson(Map<String, dynamic> j) => Expense(
        id: j['id'] as String,
        title: cleanText(j['title'] as String? ?? '', Limits.titleLength),
        amountMinor: (j['amountMinor'] as num).toInt(),
        currency: j['currency'] as String? ?? 'EUR',
        rate: (j['rate'] as num?)?.toDouble() ?? 1,
        payerId: j['payerId'] as String,
        splitMode: SplitMode.values.firstWhere(
          (m) => m.name == j['splitMode'],
          orElse: () => SplitMode.equal,
        ),
        split: Map<String, dynamic>.from(j['split'] as Map? ?? {})
            .map((k, v) => MapEntry(k, (v as num).toInt())),
        category: j['category'] as String? ?? 'other',
        date: (j['date'] as num?)?.toInt() ?? 0,
        createdAt: (j['createdAt'] as num?)?.toInt() ?? 0,
        updatedAt: (j['updatedAt'] as num?)?.toInt() ?? 0,
        note: cleanText(j['note'] as String? ?? '', Limits.noteLength),
        isTransfer: j['isTransfer'] as bool? ?? false,
      );

  Expense copy() => Expense.fromJson(toJson());

  /// Comprueba que el gasto es coherente antes de guardarlo. Devuelve un
  /// mensaje de error legible o null si es válido.
  String? validate(Group group) {
    if (amountMinor <= 0) return 'El importe tiene que ser mayor que cero.';
    if (amountMinor > Limits.maxAmountMinor) return 'El importe es demasiado alto.';
    if (!isTransfer && title.trim().isEmpty) return 'Ponle un nombre al gasto.';
    if (group.person(payerId) == null) return 'Elige quién ha pagado.';
    if (!(rate > 0) || rate > Limits.maxRate) return 'El tipo de cambio no es válido.';
    final ids = split.keys.where((k) => split[k]! > 0).toList();
    if (ids.isEmpty) return 'Elige al menos una persona.';
    if (ids.any((id) => group.person(id) == null)) {
      return 'Hay personas que ya no están en el grupo.';
    }
    if (split.values.any((v) => v < 0)) return 'No se admiten valores negativos.';
    switch (splitMode) {
      case SplitMode.exact:
        final sum = split.values.fold<int>(0, (a, b) => a + b);
        if (sum != amountMinor) {
          final diff = amountMinor - sum;
          return diff > 0
              ? 'Faltan ${formatMoney(diff, currency)} por repartir.'
              : 'Sobran ${formatMoney(-diff, currency)}.';
        }
      case SplitMode.percent:
        final sum = split.values.fold<int>(0, (a, b) => a + b);
        if (sum != 10000) {
          return 'Los porcentajes suman ${(sum / 100).toStringAsFixed(2).replaceAll('.', ',')} %, tienen que sumar 100 %.';
        }
      case SplitMode.equal:
      case SplitMode.shares:
        break;
    }
    if (isTransfer && (ids.length != 1 || ids.first == payerId)) {
      return 'Un pago va de una persona a otra distinta.';
    }
    return null;
  }
}

/// Lo que se guarda de cada grupo: datos, gastos y estado local del
/// dispositivo (quién soy yo, si está compartido).
class GroupData {
  GroupData({
    required this.group,
    required this.expenses,
    this.cloud = false,
    this.meId,
    this.inviteSecret,
  });

  Group group;
  List<Expense> expenses;
  bool cloud;
  String? meId;

  /// Secreto del enlace de invitación vigente (solo grupos compartidos).
  String? inviteSecret;

  Map<String, dynamic> toJson() => {
        'group': group.toJson(),
        'expenses': expenses.map((e) => e.toJson()).toList(),
        'cloud': cloud,
        if (meId != null) 'meId': meId,
        if (inviteSecret != null) 'inviteSecret': inviteSecret,
      };

  factory GroupData.fromJson(Map<String, dynamic> j) => GroupData(
        group: Group.fromJson(Map<String, dynamic>.from(j['group'] as Map)),
        expenses: ((j['expenses'] as List?) ?? [])
            .map((e) => Expense.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        cloud: j['cloud'] as bool? ?? false,
        meId: j['meId'] as String?,
        inviteSecret: j['inviteSecret'] as String?,
      );
}
