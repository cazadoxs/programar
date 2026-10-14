import 'package:flutter/material.dart';

import 'app/state.dart';
import 'ui/receive_page.dart';
import 'ui/send_page.dart';
import 'ui/theme.dart';
import 'ui/tools_page.dart';
import 'ui/widgets.dart';

Future<void> main(List<String> args) async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppState.instance.init();
  // En Windows el instalador registra pasalo:// y el enlace llega como argumento.
  for (final a in args) {
    if (a.startsWith('pasalo://')) AppState.instance.openLink(a);
  }
  runApp(const PasaloApp());
}

class PasaloApp extends StatelessWidget {
  const PasaloApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'Pásalo',
        debugShowCheckedModeBanner: false,
        theme: buildTheme(),
        home: const HomeShell(),
      );
}

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  final state = AppState.instance;
  int tab = 0;
  PendingOffer? _shownOffer;

  @override
  void initState() {
    super.initState();
    state.addListener(_onState);
  }

  @override
  void dispose() {
    state.removeListener(_onState);
    super.dispose();
  }

  void _onState() {
    final po = state.pendingOffer;
    if (po != null && !identical(po, _shownOffer)) {
      _shownOffer = po;
      showOfferDialog(context, po);
    }
    if (state.incomingLink != null && tab != 0) setState(() => tab = 0);
  }

  void goTo(int i) => setState(() => tab = i);

  @override
  Widget build(BuildContext context) {
    final pages = [
      SendPage(),
      const ReceivePage(),
      ToolsPage(onSend: () => goTo(0)),
    ];
    const dests = [
      (Icons.north_east_rounded, 'Enviar'),
      (Icons.south_west_rounded, 'Recibir'),
      (Icons.handyman_rounded, 'Herramientas'),
    ];
    final wide = MediaQuery.sizeOf(context).width >= 720;
    final body = SafeArea(child: IndexedStack(index: tab, children: pages));
    if (wide) {
      return Scaffold(
        body: Row(children: [
          NavigationRail(
            selectedIndex: tab,
            onDestinationSelected: goTo,
            labelType: NavigationRailLabelType.all,
            leading: const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Logo(size: 44),
            ),
            destinations: [
              for (final d in dests)
                NavigationRailDestination(icon: Icon(d.$1), label: Text(d.$2))
            ],
          ),
          const VerticalDivider(width: 1),
          Expanded(child: body),
        ]),
      );
    }
    return Scaffold(
      body: body,
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: goTo,
        destinations: [
          for (final d in dests) NavigationDestination(icon: Icon(d.$1), label: d.$2)
        ],
      ),
    );
  }
}
