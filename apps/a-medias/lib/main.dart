import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/invite.dart';
import 'data/app_state.dart';
import 'data/cloud.dart';
import 'data/local_store.dart';
import 'premium/billing.dart';
import 'ui/home_screen.dart';
import 'ui/join_screen.dart';
import 'ui/onboarding_screen.dart';
import 'ui/theme.dart';

final navigatorKey = GlobalKey<NavigatorState>();

late final AppState appState;
late final Billing billing;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final store = await LocalStore.open();
  await Cloud.init();
  appState = AppState(store);
  billing = Billing(appState);
  unawaited(billing.init());
  runApp(const AMediasApp());
}

class AMediasApp extends StatefulWidget {
  const AMediasApp({super.key});

  @override
  State<AMediasApp> createState() => _AMediasAppState();
}

class _AMediasAppState extends State<AMediasApp> {
  StreamSubscription<Uri>? _links;
  Invite? _pendingInvite;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      _pendingInvite = Invite.parse(Uri.base.toString());
    } else {
      final links = AppLinks();
      links.getInitialLink().then((uri) {
        if (uri != null) _openInvite(uri);
      });
      _links = links.uriLinkStream.listen(_openInvite);
    }
    if (_pendingInvite != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _showJoin(_pendingInvite!));
    }
  }

  void _openInvite(Uri uri) {
    final invite = Invite.parse(uri.toString());
    if (invite == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) => _showJoin(invite));
  }

  void _showJoin(Invite invite) {
    navigatorKey.currentState?.push(
      MaterialPageRoute(builder: (_) => JoinScreen(initial: invite)),
    );
  }

  @override
  void dispose() {
    _links?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appState,
      builder: (context, _) {
        final palette = paletteOf(appState.plus ? appState.themeId : 'tomate');
        final light = buildTheme(palette, Brightness.light);
        final dark = buildTheme(palette, Brightness.dark);
        return MaterialApp(
          title: 'A medias',
          debugShowCheckedModeBanner: false,
          navigatorKey: navigatorKey,
          theme: light,
          darkTheme: dark,
          themeMode: switch (appState.themePref) {
            ThemePref.system => ThemeMode.system,
            ThemePref.light => ThemeMode.light,
            ThemePref.dark => ThemeMode.dark,
          },
          locale: const Locale('es'),
          supportedLocales: const [Locale('es')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          builder: (context, child) => AnnotatedRegion<SystemUiOverlayStyle>(
            value: Theme.of(context).brightness == Brightness.dark
                ? SystemUiOverlayStyle.light
                : SystemUiOverlayStyle.dark,
            child: child!,
          ),
          home: appState.onboarded ? const HomeScreen() : const OnboardingScreen(),
        );
      },
    );
  }
}
