import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_app_check/firebase_app_check.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';

import '../config.dart';
import '../core/invite.dart';
import '../core/models.dart';

/// Sincronización de grupos compartidos con Firestore.
///
/// Cada dispositivo entra con una sesión anónima de Firebase (nada de correo
/// ni contraseña). Para leer un grupo hace falta un documento
/// `groups/{id}/members/{uid}`, y solo se puede crear presentando el secreto
/// de un enlace de invitación vigente. Las reglas están en
/// firebase/firestore.rules.
class Cloud {
  Cloud._();

  static Cloud? _instance;

  /// null si Firebase no está configurado en esta compilación o falla al
  /// arrancar; la app sigue funcionando en local.
  static Cloud? get instance => _instance;

  static Future<void> init() async {
    if (!AppConfig.cloudConfigured) return;
    try {
      await Firebase.initializeApp(
        options: FirebaseOptions(
          apiKey: kIsWeb && AppConfig.firebaseWebApiKey.isNotEmpty
              ? AppConfig.firebaseWebApiKey
              : AppConfig.firebaseApiKey,
          appId: kIsWeb ? AppConfig.firebaseWebAppId : AppConfig.firebaseAndroidAppId,
          messagingSenderId: AppConfig.firebaseSenderId,
          projectId: AppConfig.firebaseProjectId,
          authDomain: kIsWeb ? '${AppConfig.firebaseProjectId}.firebaseapp.com' : null,
          storageBucket: null,
        ),
      );
      if (AppConfig.appCheck) {
        await FirebaseAppCheck.instance.activate(
          providerAndroid: kDebugMode
              ? const AndroidDebugProvider()
              : const AndroidPlayIntegrityProvider(),
          providerWeb: AppConfig.recaptchaSiteKey.isEmpty
              ? null
              : ReCaptchaEnterpriseProvider(AppConfig.recaptchaSiteKey),
        );
      }
      if (kIsWeb) {
        FirebaseFirestore.instance.settings = const Settings(persistenceEnabled: true);
      }
      _instance = Cloud._();
    } catch (e) {
      debugPrint('Firebase no disponible: $e');
    }
  }

  FirebaseFirestore get _db => FirebaseFirestore.instance;

  DocumentReference<Map<String, dynamic>> _group(String id) => _db.collection('groups').doc(id);

  Future<String> _uid() async {
    final auth = FirebaseAuth.instance;
    final user = auth.currentUser ?? (await auth.signInAnonymously()).user;
    if (user == null) throw StateError('No se pudo iniciar la sesión anónima');
    return user.uid;
  }

  Map<String, dynamic> _groupDoc(Group g) {
    final j = g.toJson();
    j.remove('ownerUid');
    return j;
  }

  Map<String, dynamic> _expenseDoc(Expense e) => e.toJson();

  /// Sube un grupo local a la nube y devuelve el enlace para invitar.
  Future<Invite> share(GroupData d) async {
    final uid = await _uid();
    final secret = newId(24);
    final g = d.group;
    g.ownerUid = uid;
    final ref = _group(g.id);
    final first = _db.batch()
      ..set(ref, {..._groupDoc(g), 'ownerUid': uid})
      ..set(ref.collection('invites').doc(secret), {
        'createdBy': uid,
        'createdAt': DateTime.now().millisecondsSinceEpoch,
      })
      ..set(ref.collection('members').doc(uid), {
        'invite': secret,
        'joinedAt': DateTime.now().millisecondsSinceEpoch,
      });
    await first.commit();
    // Los gastos van después, en lotes, cuando ya somos miembros.
    for (var i = 0; i < d.expenses.length; i += 400) {
      final batch = _db.batch();
      for (final e in d.expenses.skip(i).take(400)) {
        batch.set(ref.collection('expenses').doc(e.id), _expenseDoc(e));
      }
      await batch.commit();
    }
    return Invite(g.id, secret);
  }

  /// Entra en un grupo con un enlace. Lanza si el enlace no es válido o ha
  /// caducado (las reglas rechazan la escritura).
  Future<GroupData> join(Invite invite) async {
    final uid = await _uid();
    final ref = _group(invite.groupId);
    await ref.collection('members').doc(uid).set({
      'invite': invite.secret,
      'joinedAt': DateTime.now().millisecondsSinceEpoch,
    });
    final snap = await ref.get();
    final data = snap.data();
    if (data == null) throw StateError('El grupo no existe');
    final expenses = await ref.collection('expenses').get();
    return GroupData(
      group: Group.fromJson({...data, 'id': snap.id}),
      expenses: _parseExpenses(expenses.docs),
      cloud: true,
      inviteSecret: invite.secret,
    );
  }

  /// Escucha el grupo y sus gastos. Firestore guarda caché local y encola
  /// las escrituras sin conexión, así que esto funciona también offline.
  /// Devuelve una función que cancela la escucha.
  Future<void> Function() watch(
    String groupId, {
    required void Function(Group) onGroup,
    required void Function(List<Expense>) onExpenses,
    required void Function(Object) onError,
  }) {
    final ref = _group(groupId);
    final a = ref.snapshots().listen((s) {
      final data = s.data();
      if (data == null) return;
      try {
        onGroup(Group.fromJson({...data, 'id': s.id}));
      } catch (_) {}
    }, onError: onError);
    final b = ref.collection('expenses').snapshots().listen((s) {
      onExpenses(_parseExpenses(s.docs));
    }, onError: onError);
    return () async {
      await a.cancel();
      await b.cancel();
    };
  }

  /// Un gasto mal formado (escrito por un cliente modificado, por ejemplo) se
  /// ignora en lugar de romper la pantalla de todo el grupo.
  List<Expense> _parseExpenses(List<QueryDocumentSnapshot<Map<String, dynamic>>> docs) {
    final out = <Expense>[];
    for (final d in docs) {
      try {
        final e = Expense.fromJson({...d.data(), 'id': d.id});
        if (e.amountMinor > 0 &&
            e.amountMinor <= Limits.maxAmountMinor &&
            e.rate > 0 &&
            e.rate <= Limits.maxRate &&
            e.split.values.every((v) => v >= 0)) {
          out.add(e);
        }
      } catch (_) {}
    }
    return out;
  }

  Future<void> saveGroup(Group g) =>
      _group(g.id).update(_groupDoc(g)..remove('id')..remove('createdAt'));

  Future<void> saveExpense(String groupId, Expense e) =>
      _group(groupId).collection('expenses').doc(e.id).set(_expenseDoc(e));

  Future<void> deleteExpense(String groupId, String expenseId) =>
      _group(groupId).collection('expenses').doc(expenseId).delete();

  /// Invalida el enlace actual y crea otro. Quien ya está dentro sigue dentro.
  Future<String> rotateInvite(String groupId, String? oldSecret) async {
    final uid = await _uid();
    final secret = newId(24);
    final ref = _group(groupId);
    final batch = _db.batch()
      ..set(ref.collection('invites').doc(secret), {
        'createdBy': uid,
        'createdAt': DateTime.now().millisecondsSinceEpoch,
      });
    if (oldSecret != null) batch.delete(ref.collection('invites').doc(oldSecret));
    await batch.commit();
    return secret;
  }

  /// Sale del grupo en este dispositivo.
  Future<void> leave(String groupId) async {
    final uid = await _uid();
    await _group(groupId).collection('members').doc(uid).delete();
  }
}
