import 'package:flutter/material.dart';

import '../core/ledger.dart';
import '../core/models.dart';
import '../core/money.dart';
import '../main.dart';
import 'group_editor.dart';
import 'group_screen.dart';
import 'join_screen.dart';
import 'settings_screen.dart';
import 'theme.dart';
import 'widgets.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _showArchived = false;

  Future<void> _newGroup() async {
    final d = await Navigator.of(context).push<GroupData>(
      MaterialPageRoute(builder: (_) => const GroupEditor()),
    );
    if (d != null && mounted) {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => GroupScreen(groupId: d.group.id)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appState,
      builder: (context, _) {
        final all = appState.groups;
        final active = all.where((g) => !g.group.archived).toList();
        final archived = all.where((g) => g.group.archived).toList();
        return Scaffold(
          body: SafeArea(
            bottom: false,
            child: CustomScrollView(
              slivers: [
                SliverToBoxAdapter(child: _Header(onSettings: () {
                  Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettingsScreen()));
                })),
                if (appState.syncError != null)
                  SliverToBoxAdapter(child: _SyncBanner(message: appState.syncError!)),
                if (all.isNotEmpty) SliverToBoxAdapter(child: _Summary(groups: active)),
                if (all.isEmpty)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: EmptyState(
                      emoji: '🧾',
                      title: 'Aún no hay grupos',
                      text: 'Crea uno para el piso, un viaje o la pareja, o entra en el de tus amigos con su enlace.',
                      action: Column(
                        children: [
                          FilledButton.icon(
                            onPressed: _newGroup,
                            icon: const Icon(Icons.add),
                            label: const Text('Crear grupo'),
                          ),
                          const SizedBox(height: 10),
                          TextButton(
                            onPressed: () => Navigator.of(context).push(
                              MaterialPageRoute(builder: (_) => const JoinScreen()),
                            ),
                            child: const Text('Tengo un enlace'),
                          ),
                        ],
                      ),
                    ),
                  ),
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 12, 0),
                  sliver: SliverList.separated(
                    itemCount: active.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (context, i) => _GroupCard(data: active[i]),
                  ),
                ),
                if (archived.isNotEmpty)
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(8, 16, 8, 0),
                      child: TextButton.icon(
                        onPressed: () => setState(() => _showArchived = !_showArchived),
                        icon: Icon(_showArchived ? Icons.expand_less : Icons.expand_more),
                        label: Text('Archivados (${archived.length})'),
                      ),
                    ),
                  ),
                if (_showArchived)
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 12, 0),
                    sliver: SliverList.separated(
                      itemCount: archived.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 10),
                      itemBuilder: (context, i) => Opacity(opacity: 0.7, child: _GroupCard(data: archived[i])),
                    ),
                  ),
                const SliverToBoxAdapter(child: SizedBox(height: 120)),
              ],
            ),
          ),
          floatingActionButton: all.isEmpty
              ? null
              : FloatingActionButton.extended(
                  onPressed: _newGroup,
                  icon: const Icon(Icons.add),
                  label: const Text('Nuevo grupo'),
                ),
        );
      },
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.onSettings});
  final VoidCallback onSettings;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 8, 8),
      child: Row(
        children: [
          const SplitLogo(size: 34),
          const SizedBox(width: 10),
          Text('A medias', style: context.text.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
          const Spacer(),
          IconButton(
            tooltip: 'Unirse con un enlace',
            icon: const Icon(Icons.link_rounded),
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const JoinScreen())),
          ),
          IconButton(
            tooltip: 'Ajustes',
            icon: const Icon(Icons.tune_rounded),
            onPressed: onSettings,
          ),
        ],
      ),
    );
  }
}

class _SyncBanner extends StatelessWidget {
  const _SyncBanner({required this.message});
  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 12, 8),
      child: StickerCard(
        offset: 2,
        color: context.am.accent,
        padding: const EdgeInsets.fromLTRB(14, 6, 4, 6),
        child: Row(
          children: [
            const Icon(Icons.cloud_off_rounded, color: Color(0xFF1C1828)),
            const SizedBox(width: 10),
            Expanded(child: Text(message, style: const TextStyle(color: Color(0xFF1C1828), fontWeight: FontWeight.w600))),
            IconButton(
              icon: const Icon(Icons.close, color: Color(0xFF1C1828)),
              onPressed: appState.clearSyncError,
            ),
          ],
        ),
      ),
    );
  }
}

/// Resumen global: cuánto te deben y cuánto debes, por moneda.
class _Summary extends StatelessWidget {
  const _Summary({required this.groups});
  final List<GroupData> groups;

  @override
  Widget build(BuildContext context) {
    final owed = <String, int>{};
    final owe = <String, int>{};
    for (final d in groups) {
      final me = d.meId;
      if (me == null) continue;
      final b = balances(d.group, d.expenses)[me] ?? 0;
      if (b > 0) owed[d.group.currency] = (owed[d.group.currency] ?? 0) + b;
      if (b < 0) owe[d.group.currency] = (owe[d.group.currency] ?? 0) - b;
    }
    String fmt(Map<String, int> m) =>
        m.isEmpty ? formatMoney(0, 'EUR') : m.entries.map((e) => formatMoney(e.value, e.key)).join(' · ');
    final am = context.am;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 12, 8),
      child: Row(
        children: [
          Expanded(
            child: StickerCard(
              color: am.positive.withValues(alpha: 0.16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('TE DEBEN', style: context.text.labelSmall?.copyWith(color: am.muted)),
                  const SizedBox(height: 6),
                  FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerLeft,
                    child: Text(fmt(owed), style: context.text.headlineSmall?.copyWith(color: am.positive)),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: StickerCard(
              color: am.negative.withValues(alpha: 0.12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('DEBES', style: context.text.labelSmall?.copyWith(color: am.muted)),
                  const SizedBox(height: 6),
                  FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerLeft,
                    child: Text(fmt(owe), style: context.text.headlineSmall?.copyWith(color: am.negative)),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _GroupCard extends StatelessWidget {
  const _GroupCard({required this.data});
  final GroupData data;

  @override
  Widget build(BuildContext context) {
    final g = data.group;
    final am = context.am;
    final me = data.meId;
    final myBalance = me == null ? null : balances(g, data.expenses)[me] ?? 0;
    final total = GroupStats(g, data.expenses).total;
    return StickerCard(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => GroupScreen(groupId: g.id))),
      child: Row(
        children: [
          Container(
            width: 52,
            height: 52,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: am.accent.withValues(alpha: 0.35),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: am.line, width: 2),
            ),
            child: Text(g.emoji, style: const TextStyle(fontSize: 26)),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(g.name, style: context.text.titleLarge, maxLines: 1, overflow: TextOverflow.ellipsis),
                const SizedBox(height: 4),
                Row(
                  children: [
                    if (data.cloud) ...[
                      Icon(Icons.people_alt_rounded, size: 14, color: am.muted),
                      const SizedBox(width: 4),
                    ],
                    Flexible(
                      child: Text(
                        '${g.people.length} personas · ${formatMoney(total, g.currency)}',
                        style: context.text.bodySmall,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          if (myBalance != null)
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  myBalance > 0
                      ? 'te deben'
                      : myBalance < 0
                          ? 'debes'
                          : 'en paz',
                  style: context.text.bodySmall,
                ),
                if (myBalance != 0)
                  MoneyText(myBalance.abs() * (myBalance > 0 ? 1 : -1), g.currency,
                      style: context.text.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
              ],
            ),
        ],
      ),
    );
  }
}
