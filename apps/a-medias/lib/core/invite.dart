import 'models.dart';

/// Enlace para unirse a un grupo sin registrarse. El secreto va en el
/// fragmento (#...) para que nunca llegue a los registros de ningún
/// servidor web: el navegador no envía el fragmento en la petición.
class Invite {
  const Invite(this.groupId, this.secret);
  final String groupId;
  final String secret;

  Uri toUri(String host) =>
      Uri(scheme: 'https', host: host, path: '/unirse', fragment: 'g=$groupId&k=$secret');

  /// Acepta el enlace completo o lo que quede tras copiarlo de un chat
  /// (con espacios o texto alrededor).
  static Invite? parse(String raw) {
    final match = RegExp(r'https?://\S+').firstMatch(raw.trim());
    final uri = Uri.tryParse(match?.group(0) ?? raw.trim());
    if (uri == null) return null;
    if (!uri.path.startsWith('/unirse')) return null;
    final params = Uri.splitQueryString(uri.fragment);
    final g = params['g'];
    final k = params['k'];
    if (g == null || k == null) return null;
    if (!idPattern.hasMatch(g) || !idPattern.hasMatch(k)) return null;
    return Invite(g, k);
  }
}
