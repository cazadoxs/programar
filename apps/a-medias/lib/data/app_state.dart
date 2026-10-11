import 'dart:async';

import 'package:flutter/foundation.dart';

import '../core/invite.dart';
import '../core/ledger.dart';
import '../core/models.dart';
import 'cloud.dart';
import 'local_store.dart';

enum ThemePref { system, light, dark }

/// Estado de la app: grupos, ajustes y sincronización. Las pantallas lo
/// escuchan con ListenableBuilder.
class AppState extends ChangeNotifier {
  AppState(this._store) {
    _groups = {for (final g in _store.loadGroups()) g.group.id: g};
    final s = _store.loadSettings();
    themeId = s['themeId'] as String? ?? 'tomate';
    themePref = ThemePref.values.firstWhere(
      (t) => t.name == s['themePref'],
      orElse: () => ThemePref.system,
    );
    myName = s['myName'] as String? ?? '';
    plus = s['plus'] as bool? ?? false;
    onboarded = s['onboarded'] as bool? ?? false;
    for (final d in _groups.values) {
      if (d.cloud) _watch(d.group.id);
    }
  }

  final LocalStore _store;
  late Map<String, GroupData> _groups;
  final _watchers = <String, Future<void> Function()>{};

  String themeId = 'tomate';
  ThemePref themePref = ThemePref.system;
  String myName = '';
  bool plus = false;
  bool onboarded = false;

  /// Último error de sincronización para mostrarlo sin bloquear nada.
  String? syncError;

  Cloud? get cloud => Cloud.instance;
  bool get canShare => cloud != null;

  List<GroupData> get groups {
    final list = _groups.values.toList()
      ..sort((a, b) => b.group.updatedAt.compareTo(a.group.updatedAt));
    return list;
  }

  GroupData? group(String id) => _groups[id];

  int _now() => DateTime.now().millisecondsSinceEpoch;

  // ---------------------------------------------------------------- ajustes

  Future<void> _saveSettings() => _store.saveSettings({
        'themeId': themeId,
        'themePref': themePref.name,
        'myName': myName,
        'plus': plus,
        'onboarded': onboarded,
      });

  void setTheme(String id) {
    themeId = id;
    _saveSettings();
    notifyListeners();
  }

  void setThemePref(ThemePref p) {
    themePref = p;
    _saveSettings();
    notifyListeners();
  }

  void setMyName(String name) {
    myName = cleanText(name, Limits.nameLength);
    _saveSettings();
    notifyListeners();
  }

  void setPlus(bool value) {
    plus = value;
    _saveSettings();
    notifyListeners();
  }

  void finishOnboarding() {
    onboarded = true;
    _saveSettings();
    notifyListeners();
  }

  // ----------------------------------------------------------------- grupos

  Future<GroupData> createGroup({
    required String name,
    required String emoji,
    required String currency,
    required List<String> otherNames,
  }) async {
    final now = _now();
    final me = Person(id: newId(12), name: myName.isEmpty ? 'Yo' : myName);
    final people = [
      me,
      for (final n in otherNames)
        if (cleanText(n, Limits.nameLength).isNotEmpty)
          Person(id: newId(12), name: cleanText(n, Limits.nameLength)),
    ];
    final d = GroupData(
      group: Group(
        id: newId(),
        name: cleanText(name, Limits.groupNameLength),
        currency: currency,
        emoji: emoji,
        people: people.take(Limits.maxPeople).toList(),
        createdAt: now,
        updatedAt: now,
      ),
      expenses: [],
      meId: me.id,
    );
    _groups[d.group.id] = d;
    await _store.saveGroup(d);
    notifyListeners();
    return d;
  }

  void _persist(GroupData d, {bool groupChanged = false}) {
    d.group.updatedAt = _now();
    _store.saveGroup(d);
    if (d.cloud && groupChanged) {
      _cloudWrite(cloud?.saveGroup(d.group));
    }
    notifyListeners();
  }

  /// Las escrituras en la nube no se esperan: sin conexión Firestore las
  /// encola y la promesa no se resuelve hasta volver a tener red.
  void _cloudWrite(Future<void>? f) {
    f?.catchError((Object e) {
      syncError = 'No se pudo sincronizar: ${_friendly(e)}';
      notifyListeners();
    });
  }

  String _friendly(Object e) {
    final s = e.toString();
    if (s.contains('permission-denied')) {
      return 'ya no tienes acceso a este grupo.';
    }
    if (s.contains('unavailable') || s.contains('network')) {
      return 'sin conexión, se reintentará.';
    }
    return 'inténtalo de nuevo más tarde.';
  }

  void clearSyncError() {
    syncError = null;
    notifyListeners();
  }

  void updateGroupInfo(String id, {String? name, String? emoji, String? currency}) {
    final d = _groups[id];
    if (d == null) return;
    if (name != null && cleanText(name, Limits.groupNameLength).isNotEmpty) {
      d.group.name = cleanText(name, Limits.groupNameLength);
    }
    if (emoji != null) d.group.emoji = emoji;
    if (currency != null && canChangeCurrency(id)) d.group.currency = currency;
    _persist(d, groupChanged: true);
  }

  bool canChangeCurrency(String id) => _groups[id]?.expenses.isEmpty ?? false;

  void setArchived(String id, bool archived) {
    final d = _groups[id];
    if (d == null) return;
    d.group.archived = archived;
    _persist(d, groupChanged: true);
  }

  /// Añade una persona. Devuelve false si ya hay demasiadas.
  bool addPerson(String groupId, String name) {
    final d = _groups[groupId];
    final clean = cleanText(name, Limits.nameLength);
    if (d == null || clean.isEmpty) return false;
    if (d.group.people.length >= Limits.maxPeople) return false;
    d.group.people = [...d.group.people, Person(id: newId(12), name: clean)];
    _persist(d, groupChanged: true);
    return true;
  }

  void renamePerson(String groupId, String personId, String name) {
    final d = _groups[groupId];
    final clean = cleanText(name, Limits.nameLength);
    if (d == null || clean.isEmpty) return;
    d.group.people = [
      for (final p in d.group.people) p.id == personId ? Person(id: p.id, name: clean) : p,
    ];
    _persist(d, groupChanged: true);
  }

  /// Solo se puede quitar a alguien que no aparece en ningún gasto, para no
  /// descuadrar las cuentas.
  bool canRemovePerson(String groupId, String personId) {
    final d = _groups[groupId];
    if (d == null || d.group.people.length <= 1) return false;
    return !d.expenses.any((e) => e.payerId == personId || (e.split[personId] ?? 0) > 0);
  }

  void removePerson(String groupId, String personId) {
    final d = _groups[groupId];
    if (d == null || !canRemovePerson(groupId, personId)) return;
    d.group.people = d.group.people.where((p) => p.id != personId).toList();
    if (d.meId == personId) d.meId = null;
    _persist(d, groupChanged: true);
  }

  void setMe(String groupId, String? personId) {
    final d = _groups[groupId];
    if (d == null) return;
    d.meId = personId;
    _store.saveGroup(d);
    notifyListeners();
  }

  /// Borra el grupo de este dispositivo. Si estaba compartido, sale de él
  /// (el resto del grupo lo conserva).
  Future<void> removeGroup(String id) async {
    final d = _groups.remove(id);
    if (d == null) return;
    await _watchers.remove(id)?.call();
    await _store.deleteGroup(id);
    notifyListeners();
    if (d.cloud) _cloudWrite(cloud?.leave(id));
  }

  // ----------------------------------------------------------------- gastos

  /// Guarda (crea o reemplaza) un gasto ya validado.
  void saveExpense(String groupId, Expense e) {
    final d = _groups[groupId];
    if (d == null) return;
    e.updatedAt = _now();
    final i = d.expenses.indexWhere((x) => x.id == e.id);
    if (i >= 0) {
      d.expenses[i] = e;
    } else {
      d.expenses.add(e);
    }
    _persist(d);
    if (d.cloud) _cloudWrite(cloud?.saveExpense(groupId, e));
  }

  Expense? deleteExpense(String groupId, String expenseId) {
    final d = _groups[groupId];
    if (d == null) return null;
    final i = d.expenses.indexWhere((x) => x.id == expenseId);
    if (i < 0) return null;
    final removed = d.expenses.removeAt(i);
    _persist(d);
    if (d.cloud) _cloudWrite(cloud?.deleteExpense(groupId, expenseId));
    return removed;
  }

  /// Registra que [from] le ha pagado [amount] a [to] para saldar.
  void recordPayment(String groupId, Transfer t) {
    final d = _groups[groupId];
    if (d == null) return;
    final now = _now();
    saveExpense(
      groupId,
      Expense(
        id: newId(),
        title: 'Pago',
        amountMinor: t.amountMinor,
        currency: d.group.currency,
        rate: 1,
        payerId: t.from,
        splitMode: SplitMode.exact,
        split: {t.to: t.amountMinor},
        category: 'other',
        date: now,
        createdAt: now,
        updatedAt: now,
        isTransfer: true,
      ),
    );
  }

  // ---------------------------------------------------------- compartir

  Future<Invite> share(String groupId) async {
    final d = _groups[groupId];
    final c = cloud;
    if (d == null || c == null) throw StateError('No disponible');
    if (d.cloud && d.inviteSecret != null) return Invite(groupId, d.inviteSecret!);
    if (d.cloud) {
      final secret = await c.rotateInvite(groupId, null);
      d.inviteSecret = secret;
      await _store.saveGroup(d);
      return Invite(groupId, secret);
    }
    final invite = await c.share(d);
    d.cloud = true;
    d.inviteSecret = invite.secret;
    await _store.saveGroup(d);
    _watch(groupId);
    notifyListeners();
    return invite;
  }

  Future<Invite> rotateInvite(String groupId) async {
    final d = _groups[groupId];
    final c = cloud;
    if (d == null || c == null || !d.cloud) throw StateError('No disponible');
    final secret = await c.rotateInvite(groupId, d.inviteSecret);
    d.inviteSecret = secret;
    await _store.saveGroup(d);
    notifyListeners();
    return Invite(groupId, secret);
  }

  /// Entra en un grupo por enlace. Si ya estabas, simplemente lo abre.
  Future<GroupData> join(Invite invite) async {
    final existing = _groups[invite.groupId];
    if (existing != null && existing.cloud) return existing;
    final c = cloud;
    if (c == null) throw StateError('Esta versión no puede unirse a grupos compartidos.');
    final d = await c.join(invite);
    _groups[d.group.id] = d;
    await _store.saveGroup(d);
    _watch(d.group.id);
    notifyListeners();
    return d;
  }

  void _watch(String groupId) {
    final c = cloud;
    if (c == null || _watchers.containsKey(groupId)) return;
    _watchers[groupId] = c.watch(
      groupId,
      onGroup: (g) {
        final d = _groups[groupId];
        if (d == null) return;
        d.group = g;
        if (d.meId != null && g.person(d.meId!) == null) d.meId = null;
        _store.saveGroup(d);
        notifyListeners();
      },
      onExpenses: (list) {
        final d = _groups[groupId];
        if (d == null) return;
        d.expenses = list;
        _store.saveGroup(d);
        notifyListeners();
      },
      onError: (e) {
        syncError = 'No se pudo sincronizar: ${_friendly(e)}';
        notifyListeners();
      },
    );
  }

  @override
  void dispose() {
    for (final cancel in _watchers.values) {
      cancel();
    }
    super.dispose();
  }
}
