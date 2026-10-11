import 'dart:convert';

import 'package:http/http.dart' as http;

/// Tipos de cambio de referencia del Banco Central Europeo a través de
/// Frankfurter (gratis, sin clave y sin datos personales). Si no hay
/// conexión, la persona escribe el tipo a mano.
class Rates {
  static final _cache = <String, double>{};

  static Future<double?> fetch(String from, String to) async {
    if (from == to) return 1;
    if (!RegExp(r'^[A-Z]{3}$').hasMatch(from) || !RegExp(r'^[A-Z]{3}$').hasMatch(to)) {
      return null;
    }
    final key = '$from-$to';
    if (_cache.containsKey(key)) return _cache[key];
    try {
      final uri = Uri.https('api.frankfurter.dev', '/v1/latest', {'base': from, 'symbols': to});
      final res = await http.get(uri).timeout(const Duration(seconds: 6));
      if (res.statusCode != 200) return null;
      final body = jsonDecode(res.body) as Map<String, dynamic>;
      final rate = ((body['rates'] as Map?)?[to] as num?)?.toDouble();
      if (rate == null || rate <= 0) return null;
      _cache[key] = rate;
      return rate;
    } catch (_) {
      return null;
    }
  }
}
