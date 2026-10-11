import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../core/models.dart';

/// Guarda cada grupo como un JSON en el almacenamiento local (SharedPreferences
/// en Android, localStorage en la web). Es la fuente de verdad de los grupos
/// que no están compartidos y la caché de los compartidos.
class LocalStore {
  LocalStore(this._prefs);

  static Future<LocalStore> open() async =>
      LocalStore(await SharedPreferences.getInstance());

  final SharedPreferences _prefs;

  static const _indexKey = 'groups.index';
  static const _settingsKey = 'settings';

  List<GroupData> loadGroups() {
    final ids = _prefs.getStringList(_indexKey) ?? const [];
    final out = <GroupData>[];
    for (final id in ids) {
      final raw = _prefs.getString('group.$id');
      if (raw == null) continue;
      try {
        out.add(GroupData.fromJson(jsonDecode(raw) as Map<String, dynamic>));
      } catch (_) {
        // Un grupo corrupto no debe impedir abrir la app.
      }
    }
    return out;
  }

  Future<void> saveGroup(GroupData d) async {
    final ids = _prefs.getStringList(_indexKey) ?? <String>[];
    if (!ids.contains(d.group.id)) {
      await _prefs.setStringList(_indexKey, [...ids, d.group.id]);
    }
    await _prefs.setString('group.${d.group.id}', jsonEncode(d.toJson()));
  }

  Future<void> deleteGroup(String id) async {
    final ids = _prefs.getStringList(_indexKey) ?? <String>[];
    await _prefs.setStringList(_indexKey, ids.where((x) => x != id).toList());
    await _prefs.remove('group.$id');
  }

  Map<String, dynamic> loadSettings() {
    final raw = _prefs.getString(_settingsKey);
    if (raw == null) return {};
    try {
      return jsonDecode(raw) as Map<String, dynamic>;
    } catch (_) {
      return {};
    }
  }

  Future<void> saveSettings(Map<String, dynamic> s) =>
      _prefs.setString(_settingsKey, jsonEncode(s));
}
