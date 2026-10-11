import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import '../config.dart';
import '../core/ledger.dart';
import '../core/models.dart';
import '../core/money.dart';
import '../main.dart';
import 'charts.dart';
import 'expense_editor.dart';
import 'group_settings.dart';
import 'plus_screen.dart';
import 'settle_sheet.dart';
import 'theme.dart';
import 'widgets.dart';

class GroupScreen extends StatefulWidget {
  const GroupScreen({super.key, required this.groupId});
  final String groupId;

  @override
  State<GroupScreen> createState() => _GroupScreenState();
}

class _GroupScreenState extends State<GroupScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabs = TabController(length: 3, vsync: this);
  bool _askedMe = false;

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _addExpense({bool scan = false}) async {
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => ExpenseEditor(groupId: widget.groupId, startWithScan: scan)),
    );
  }

  Future<void> _share(GroupData d) async {
    if (!appState.canShare) {
      toast(context, 'Para compartir grupos hace falta conexión con el servidor de A medias.');
      return;
    }
    try {
      final invite = await appState.share(widget.groupId);
      final link = invite.toUri(AppConfig.webHost).toString();
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        builder: (_) => ShareSheet(groupName: d.group.name, link: link),
      );
    } catch (e) {
      if (mounted) toast(context, 'No se pudo crear el enlace. ¿Tienes conexión?');
    }
  }

  void _maybeAskWhoAmI(GroupData d) {
    if (_askedMe || d.meId != null || d.group.people.isEmpty) return;
    _askedMe = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await showWhoAmI(context, d);
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appState,
      builder: (context, _) {
        final d = appState.group(widget.groupId);
        if (d == null) {
          return Scaffold(appBar: AppBar(), body: const EmptyState(emoji: '👋', title: 'Este grupo ya no está', text: 'Puede que lo hayas quitado de este móvil.'));
        }
        _maybeAskWhoAmI(d);
        final g = d.group;
        final net = balances(g, d.expenses);
        final stats = GroupStats(g, d.expenses);
        return Scaffold(
          body: NestedScrollView(
            headerSliverBuilder: (context, _) => [
              SliverAppBar(
                pinned: true,
                title: Text(g.name, overflow: TextOverflow.ellipsis),
                actions: [
                  IconButton(
                    tooltip: 'Invitar',
                    icon: const Icon(Icons.person_add_alt_1_rounded),
                    onPressed: () => _share(d),
                  ),
                  IconButton(
                    tooltip: 'Ajustes del grupo',
                    icon: const Icon(Icons.more_horiz_rounded),
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => GroupSettings(groupId: g.id)),
                    ),
                  ),
                ],
              ),
              SliverToBoxAdapter(child: _GroupHeader(data: d, net: net, total: stats.total)),
              SliverPersistentHeader(
                pinned: true,
                delegate: _TabsHeader(
                  TabBar(
                    controller: _tabs,
                    labelStyle: context.text.labelLarge,
                    unselectedLabelStyle: context.text.labelLarge?.copyWith(fontWeight: FontWeight.w500),
                    labelColor: context.am.ink,
                    unselectedLabelColor: context.am.muted,
                    indicatorColor: Theme.of(context).colorScheme.primary,
                    indicatorWeight: 4,
                    dividerColor: context.am.line.withValues(alpha: 0.15),
                    tabs: const [Tab(text: 'Gastos'), Tab(text: 'Saldos'), Tab(text: 'Gráficas')],
                  ),
                  context.am.paper,
                ),
              ),
            ],
            body: TabBarView(
              controller: _tabs,
              children: [
                _ExpensesTab(data: d),
                _BalancesTab(data: d, net: net),
                appState.plus ? ChartsView(data: d, stats: stats) : const PlusTeaser(feature: 'Gráficas'),
              ],
            ),
          ),
          floatingActionButton: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              FloatingActionButton(
                heroTag: 'scan',
                tooltip: 'Escanear tique',
                backgroundColor: context.am.accent,
                foregroundColor: const Color(0xFF1C1828),
                onPressed: () => _addExpense(scan: true),
                child: const Icon(Icons.document_scanner_rounded),
              ),
              const SizedBox(width: 12),
              FloatingActionButton.extended(
                heroTag: 'add',
                onPressed: _addExpense,
                icon: const Icon(Icons.add),
                label: const Text('Gasto'),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _TabsHeader extends SliverPersistentHeaderDelegate {
  _TabsHeader(this.tabBar, this.color);
  final TabBar tabBar;
  final Color color;

  @override
  double get minExtent => tabBar.preferredSize.height;
  @override
  double get maxExtent => tabBar.preferredSize.height;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) =>
      ColoredBox(color: color, child: tabBar);

  @override
  bool shouldRebuild(_TabsHeader old) => old.tabBar != tabBar || old.color != color;
}

class _GroupHeader extends StatelessWidget {
  const _GroupHeader({required this.data, required this.net, required this.total});
  final GroupData data;
  final Map<String, int> net;
  final int total;

  @override
  Widget build(BuildContext context) {
    final g = data.group;
    final am = context.am;
    final me = data.meId;
    final mine = me == null ? null : net[me] ?? 0;
    final primary = Theme.of(context).colorScheme.primary;
    String headline;
    Color color;
    if (mine == null) {
      headline = formatMoney(total, g.currency);
      color = am.ink;
    } else if (mine == 0) {
      headline = 'En paz';
      color = am.ink;
    } else {
      headline = formatMoney(mine.abs(), g.currency);
      color = mine > 0 ? am.positive : am.negative;
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 12, 12),
      child: StickerCard(
        color: primary,
        padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(g.emoji, style: const TextStyle(fontSize: 22)),
                const SizedBox(width: 8),
                Text(
                  mine == null
                      ? 'GASTO TOTAL'
                      : mine > 0
                          ? 'TE DEBEN'
                          : mine < 0
                              ? 'DEBES'
                              : 'TU SALDO',
                  style: context.text.labelSmall?.copyWith(color: Colors.white.withValues(alpha: 0.9)),
                ),
                const Spacer(),
                if (data.cloud) const Pill('Compartido', icon: Icons.sync_rounded),
              ],
            ),
            const SizedBox(height: 6),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
                decoration: BoxDecoration(
                  color: am.card,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: am.line, width: 2),
                ),
                child: Text(headline, style: context.text.displaySmall?.copyWith(color: color)),
              ),
            ),
            const SizedBox(height: 10),
            Text(
              '${formatMoney(total, g.currency)} en ${data.expenses.where((e) => !e.isTransfer).length} gastos · ${g.people.length} personas',
              style: context.text.bodyMedium?.copyWith(color: Colors.white, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }
}

// ------------------------------------------------------------------ gastos

class _ExpensesTab extends StatefulWidget {
  const _ExpensesTab({required this.data});
  final GroupData data;

  @override
  State<_ExpensesTab> createState() => _ExpensesTabState();
}

class _ExpensesTabState extends State<_ExpensesTab> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final d = widget.data;
    final g = d.group;
    final q = _query.toLowerCase();
    final list = d.expenses.where((e) {
      if (q.isEmpty) return true;
      return e.title.toLowerCase().contains(q) ||
          e.note.toLowerCase().contains(q) ||
          g.nameOf(e.payerId).toLowerCase().contains(q) ||
          categoryOf(e.category).label.toLowerCase().contains(q);
    }).toList()
      ..sort((a, b) {
        final c = b.date.compareTo(a.date);
        return c != 0 ? c : b.createdAt.compareTo(a.createdAt);
      });

    if (d.expenses.isEmpty) {
      return const EmptyState(
        emoji: '🍝',
        title: 'Sin gastos todavía',
        text: 'Apunta el primero con el botón de abajo. Sin límite diario, sin esperas.',
      );
    }

    final children = <Widget>[];
    String? lastDay;
    for (final e in list) {
      final day = relativeDay(e.date);
      if (day != lastDay) {
        children.add(Padding(
          padding: const EdgeInsets.fromLTRB(4, 18, 4, 8),
          child: Text(day.toUpperCase(), style: context.text.labelSmall?.copyWith(color: context.am.muted)),
        ));
        lastDay = day;
      }
      children.add(Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: _ExpenseTile(data: d, expense: e),
      ));
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 12, 140),
      children: [
        if (d.expenses.length > 6)
          TextField(
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search_rounded),
              hintText: 'Buscar gasto, persona o categoría',
              isDense: true,
            ),
            onChanged: (v) => setState(() => _query = v.trim()),
          ),
        ...children,
        if (list.isEmpty) const Padding(padding: EdgeInsets.all(24), child: Center(child: Text('Nada coincide con la búsqueda.'))),
      ],
    );
  }
}

class _ExpenseTile extends StatelessWidget {
  const _ExpenseTile({required this.data, required this.expense});
  final GroupData data;
  final Expense expense;

  @override
  Widget build(BuildContext context) {
    final e = expense;
    final g = data.group;
    final am = context.am;
    final me = data.meId;
    String? mineLabel;
    Color? mineColor;
    if (me != null) {
      final shares = owedSharesInGroupCurrency(e, g.currency);
      final paid = e.payerId == me ? amountInGroupCurrency(e, g.currency) : 0;
      final delta = paid - (shares[me] ?? 0);
      if (e.isTransfer) {
        if (e.payerId == me) {
          mineLabel = 'pagaste';
        } else if (e.split.containsKey(me)) {
          mineLabel = 'recibiste';
        }
      } else if (delta > 0) {
        mineLabel = 'prestaste ${formatMoney(delta, g.currency)}';
        mineColor = am.positive;
      } else if (delta < 0) {
        mineLabel = 'te toca ${formatMoney(-delta, g.currency)}';
        mineColor = am.negative;
      } else {
        mineLabel = 'no te afecta';
      }
    }
    final cat = categoryOf(e.category);
    final title = e.isTransfer ? '${g.nameOf(e.payerId)} → ${g.nameOf(e.split.keys.first)}' : e.title;
    final subtitle = e.isTransfer ? 'Pago para saldar' : 'Pagó ${g.nameOf(e.payerId)}';

    return Dismissible(
      key: ValueKey(e.id),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 24),
        decoration: BoxDecoration(color: am.negative, borderRadius: BorderRadius.circular(20)),
        child: const Icon(Icons.delete_outline_rounded, color: Colors.white),
      ),
      onDismissed: (_) {
        final removed = appState.deleteExpense(g.id, e.id);
        if (removed == null) return;
        toast(
          context,
          'Gasto borrado',
          action: SnackBarAction(label: 'Deshacer', onPressed: () => appState.saveExpense(g.id, removed)),
        );
      },
      child: StickerCard(
        offset: 3,
        padding: const EdgeInsets.fromLTRB(12, 12, 14, 12),
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => ExpenseEditor(groupId: g.id, expense: e)),
        ),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: e.isTransfer ? am.positive.withValues(alpha: 0.2) : am.accent.withValues(alpha: 0.35),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(e.isTransfer ? '💸' : cat.emoji, style: const TextStyle(fontSize: 22)),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: context.text.titleMedium, maxLines: 1, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 2),
                  Text(subtitle, style: context.text.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  formatMoney(e.amountMinor, e.currency),
                  style: context.text.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                    fontFeatures: const [FontFeature.tabularFigures()],
                  ),
                ),
                if (mineLabel != null)
                  Text(mineLabel, style: context.text.bodySmall?.copyWith(color: mineColor, fontWeight: FontWeight.w600)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// ------------------------------------------------------------------ saldos

class _BalancesTab extends StatefulWidget {
  const _BalancesTab({required this.data, required this.net});
  final GroupData data;
  final Map<String, int> net;

  @override
  State<_BalancesTab> createState() => _BalancesTabState();
}

class _BalancesTabState extends State<_BalancesTab> {
  bool _simplify = true;

  @override
  Widget build(BuildContext context) {
    final d = widget.data;
    final g = d.group;
    final net = widget.net;
    final am = context.am;
    final maxAbs = net.values.fold<int>(1, (m, v) => v.abs() > m ? v.abs() : m);
    final transfers = _simplify ? simplifyDebts(net) : pairwiseDebts(g, d.expenses);
    final people = [...g.people]..sort((a, b) => (net[b.id] ?? 0).compareTo(net[a.id] ?? 0));

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 12, 140),
      children: [
        StickerCard(
          child: Column(
            children: [
              for (final p in people)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: _BalanceRow(
                    person: p,
                    amount: net[p.id] ?? 0,
                    maxAbs: maxAbs,
                    currency: g.currency,
                    isMe: p.id == d.meId,
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        Row(
          children: [
            Expanded(child: Text('Cómo saldar', style: context.text.headlineSmall)),
            Text('Simplificar', style: context.text.labelMedium),
            const SizedBox(width: 4),
            Switch(value: _simplify, onChanged: (v) => setState(() => _simplify = v)),
          ],
        ),
        Text(
          _simplify
              ? 'El mínimo de pagos para quedar todos a cero.'
              : 'Lo que debe cada uno a cada uno, sin combinar.',
          style: context.text.bodySmall,
        ),
        const SizedBox(height: 12),
        if (transfers.isEmpty)
          StickerCard(
            color: am.positive.withValues(alpha: 0.15),
            child: Row(
              children: [
                const Text('🎉', style: TextStyle(fontSize: 28)),
                const SizedBox(width: 12),
                Expanded(child: Text('Todo el mundo está en paz.', style: context.text.titleMedium)),
              ],
            ),
          ),
        for (final t in transfers)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _TransferCard(data: d, transfer: t),
          ),
      ],
    );
  }
}

class _BalanceRow extends StatelessWidget {
  const _BalanceRow({
    required this.person,
    required this.amount,
    required this.maxAbs,
    required this.currency,
    required this.isMe,
  });

  final Person person;
  final int amount;
  final int maxAbs;
  final String currency;
  final bool isMe;

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    final frac = amount.abs() / maxAbs;
    return Row(
      children: [
        Avatar(person, size: 34, highlight: isMe),
        const SizedBox(width: 10),
        SizedBox(
          width: 82,
          child: Text(
            isMe ? '${person.name} (tú)' : person.name,
            style: context.text.titleSmall,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        const SizedBox(width: 6),
        Expanded(
          child: LayoutBuilder(
            builder: (context, c) {
              final half = c.maxWidth / 2;
              final w = (half * frac).clamp(amount == 0 ? 0.0 : 4.0, half);
              return SizedBox(
                height: 30,
                child: Stack(
                  children: [
                    Positioned(
                      left: half - 1,
                      top: 0,
                      bottom: 0,
                      child: Container(width: 2, color: am.line.withValues(alpha: 0.3)),
                    ),
                    Positioned(
                      left: amount >= 0 ? half : half - w,
                      top: 5,
                      bottom: 5,
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 300),
                        width: w,
                        decoration: BoxDecoration(
                          color: amount >= 0 ? am.positive : am.negative,
                          borderRadius: BorderRadius.circular(6),
                        ),
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
        const SizedBox(width: 6),
        SizedBox(
          width: 92,
          child: Align(
            alignment: Alignment.centerRight,
            child: FittedBox(
              child: MoneyText(amount, currency, signed: true, style: context.text.titleSmall?.copyWith(fontWeight: FontWeight.w800)),
            ),
          ),
        ),
      ],
    );
  }
}

class _TransferCard extends StatelessWidget {
  const _TransferCard({required this.data, required this.transfer});
  final GroupData data;
  final Transfer transfer;

  @override
  Widget build(BuildContext context) {
    final g = data.group;
    final t = transfer;
    final from = g.person(t.from);
    final to = g.person(t.to);
    if (from == null || to == null) return const SizedBox.shrink();
    final iPay = data.meId == t.from;
    final iReceive = data.meId == t.to;
    return StickerCard(
      offset: 3,
      padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
      onTap: () => showSettleSheet(context, data, t),
      child: Row(
        children: [
          Avatar(from, size: 32, highlight: iPay),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 6),
            child: Icon(Icons.arrow_forward_rounded, color: context.am.muted, size: 20),
          ),
          Avatar(to, size: 32, highlight: iReceive),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  iPay
                      ? 'Le debes a ${to.name}'
                      : iReceive
                          ? '${from.name} te debe'
                          : '${from.name} → ${to.name}',
                  style: context.text.titleSmall,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  formatMoney(t.amountMinor, g.currency),
                  style: context.text.titleLarge?.copyWith(fontFeatures: const [FontFeature.tabularFigures()]),
                ),
              ],
            ),
          ),
          FilledButton.tonal(
            style: FilledButton.styleFrom(minimumSize: const Size(0, 40), padding: const EdgeInsets.symmetric(horizontal: 14)),
            onPressed: () => showSettleSheet(context, data, t),
            child: const Text('Saldar'),
          ),
        ],
      ),
    );
  }
}

// --------------------------------------------------------------- compartir

class ShareSheet extends StatelessWidget {
  const ShareSheet({super.key, required this.groupName, required this.link});
  final String groupName;
  final String link;

  @override
  Widget build(BuildContext context) {
    final message = 'Únete a «$groupName» en A medias para llevar las cuentas. No hace falta registrarse: $link';
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Invita al grupo', style: context.text.headlineSmall),
            const SizedBox(height: 8),
            Text(
              'Quien abra el enlace entra en el grupo, desde la app o desde el navegador (también en iPhone). Mándalo solo a gente del grupo.',
              style: context.text.bodyMedium?.copyWith(color: context.am.muted),
            ),
            const SizedBox(height: 16),
            StickerCard(
              offset: 2,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              child: Text(link, style: context.text.bodySmall?.copyWith(color: context.am.ink), maxLines: 2, overflow: TextOverflow.ellipsis),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: () => SharePlus.instance.share(ShareParams(text: message, subject: 'Únete a $groupName')),
              icon: const Icon(Icons.send_rounded),
              label: const Text('Enviar por WhatsApp u otra app'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () {
                Clipboard.setData(ClipboardData(text: link));
                Navigator.of(context).pop();
                toast(context, 'Enlace copiado');
              },
              icon: const Icon(Icons.copy_rounded),
              label: const Text('Copiar enlace'),
            ),
          ],
        ),
      ),
    );
  }
}

Future<void> showWhoAmI(BuildContext context, GroupData d) {
  return showModalBottomSheet<void>(
    context: context,
    builder: (context) => SafeArea(
      child: ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Text('¿Quién eres en «${d.group.name}»?', style: context.text.headlineSmall),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 6, 8, 12),
            child: Text('Así te decimos cuánto debes o te deben.', style: context.text.bodyMedium?.copyWith(color: context.am.muted)),
          ),
          for (final p in d.group.people)
            ListTile(
              leading: Avatar(p),
              title: Text(p.name, style: context.text.titleMedium),
              trailing: d.meId == p.id ? const Icon(Icons.check_circle_rounded) : null,
              onTap: () {
                appState.setMe(d.group.id, p.id);
                Navigator.of(context).pop();
              },
            ),
          ListTile(
            leading: const Icon(Icons.visibility_outlined),
            title: const Text('Solo estoy mirando'),
            onTap: () {
              appState.setMe(d.group.id, null);
              Navigator.of(context).pop();
            },
          ),
        ],
      ),
    ),
  );
}

/// Pantalla de reclamo para funciones de Plus.
class PlusTeaser extends StatelessWidget {
  const PlusTeaser({super.key, required this.feature});
  final String feature;

  @override
  Widget build(BuildContext context) {
    return EmptyState(
      emoji: '📊',
      title: '$feature con Plus',
      text: 'En qué se os va el dinero, quién paga más y cómo evoluciona cada mes. Un único pago, para siempre.',
      action: FilledButton(
        onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PlusScreen())),
        child: const Text('Ver A medias Plus'),
      ),
    );
  }
}
