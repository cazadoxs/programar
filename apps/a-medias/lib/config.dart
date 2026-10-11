/// Configuración que se inyecta al compilar con
/// `--dart-define-from-file=firebase.env.json` (ver README). Sin ella la app
/// funciona igual, pero solo en este dispositivo: no se pueden compartir
/// grupos.
class AppConfig {
  static const firebaseApiKey = String.fromEnvironment('FIREBASE_API_KEY');
  static const firebaseProjectId = String.fromEnvironment('FIREBASE_PROJECT_ID');
  static const firebaseSenderId = String.fromEnvironment('FIREBASE_SENDER_ID');
  static const firebaseAndroidAppId = String.fromEnvironment('FIREBASE_ANDROID_APP_ID');
  static const firebaseWebAppId = String.fromEnvironment('FIREBASE_WEB_APP_ID');
  static const firebaseWebApiKey = String.fromEnvironment('FIREBASE_WEB_API_KEY');

  /// Clave de sitio de reCAPTCHA Enterprise para App Check en la web.
  static const recaptchaSiteKey = String.fromEnvironment('RECAPTCHA_SITE_KEY');

  /// Activa Firebase App Check (Play Integrity en Android).
  static const appCheck = bool.fromEnvironment('APP_CHECK');

  /// Dominio de la versión web, que es también el de los enlaces de
  /// invitación (Firebase Hosting o un dominio propio).
  static const webHost = String.fromEnvironment('WEB_HOST', defaultValue: 'amedias.web.app');

  /// Producto de compra única en Google Play.
  static const plusProductId = 'a_medias_plus';

  static const privacyUrl = 'https://$webHost/privacidad.html';

  /// Correo de soporte que aparece en Ajustes (vacío = no se muestra).
  static const supportEmail = String.fromEnvironment('SUPPORT_EMAIL');

  static bool get cloudConfigured =>
      firebaseApiKey.isNotEmpty && firebaseProjectId.isNotEmpty && firebaseSenderId.isNotEmpty;
}
