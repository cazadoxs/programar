import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/ledger.dart';
import '../core/models.dart';
import '../core/money.dart';
import 'theme.dart';
import 'widgets.dart';

/// Gráficas del grupo (Plus).
class ChartsView extends StatelessWidget {
  const ChartsView({super.key, required this.data, required this.stats});
  final GroupData data;
  final GroupStats stats;

  @override
  Widget build(BuildContext context) {
    final g = data.group;
    if (stats.total == 0) {
      return const EmptyState(emoji: '📊', title: 'Sin datos aún', text: 'Las gráficas aparecen en cuanto apuntéis gastos.');
    }
    final cats = stats.byCategory.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
    final months = stats.byMonth.entries.toList()..sort((a, b) => a.key.compareTo(b.key));
    final lastMonths = months.length > 12 ? months.sublist(months.length - 12) : months;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 12, 140),
      children: [
        _Card(
          title: 'Por categoría',
          child: Column(
            children: [
              SizedBox(
                height: 180,
                child: CustomPaint(
                  painter: _DonutPainter(
                    [for (final c in cats) c.value],
                    [for (var i = 0; i < cats.length; i++) _seriesColor(context, i)],
                    context.am.line,
                  ),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('TOTAL', style: context.text.labelSmall),
                        Text(formatMoney(stats.total, g.currency), style: context.text.titleLarge),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              for (var i = 0; i < cats.length; i++)
                _LegendRow(
                  color: _seriesColor(context, i),
                  label: '${categoryOf(cats[i].key).emoji} ${categoryOf(cats[i].key).label}',
                  value: formatMoney(cats[i].value, g.currency),
                  percent: cats[i].value / stats.total,
                ),
            ],
          ),
        ),
        _Card(
          title: 'Quién paga y quién gasta',
          subtitle: 'Barra llena: lo que ha pagado. Barra clara: lo que le corresponde.',
          child: Column(
            children: [
              for (final p in g.people)
                _PersonBars(
                  person: p,
                  paid: stats.paidBy[p.id] ?? 0,
                  consumed: stats.consumedBy[p.id] ?? 0,
                  max: [
                    ...stats.paidBy.values,
                    ...stats.consumedBy.values,
                  ].fold<int>(1, math.max),
                  currency: g.currency,
                ),
            ],
          ),
        ),
        _Card(
          title: 'Por mes',
          child: SizedBox(
            height: 180,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                for (final m in lastMonths)
                  Expanded(
                    child: _MonthBar(
                      label: _monthLabel(m.key),
                      value: m.value,
                      max: lastMonths.map((e) => e.value).fold<int>(1, math.max),
                      currency: g.currency,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  static String _monthLabel(String ym) {
    const names = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    final m = int.tryParse(ym.split('-').last) ?? 1;
    return names[(m - 1).clamp(0, 11)];
  }
}

Color _seriesColor(BuildContext context, int i) {
  final primary = Theme.of(context).colorScheme.primary;
  final colors = [
    primary,
    context.am.accent,
    const Color(0xFF8ED1C6),
    const Color(0xFFB9A8FF),
    const Color(0xFFFF9BC6),
    const Color(0xFF9CD67A),
    const Color(0xFF8CC3FF),
    const Color(0xFFFFB86B),
  ];
  return colors[i % colors.length];
}

class _Card extends StatelessWidget {
  const _Card({required this.title, required this.child, this.subtitle});
  final String title;
  final String? subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: StickerCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: context.text.titleLarge),
            if (subtitle != null) ...[const SizedBox(height: 4), Text(subtitle!, style: context.text.bodySmall)],
            const SizedBox(height: 14),
            child,
          ],
        ),
      ),
    );
  }
}

class _LegendRow extends StatelessWidget {
  const _LegendRow({required this.color, required this.label, required this.value, required this.percent});
  final Color color;
  final String label;
  final String value;
  final double percent;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle, border: Border.all(color: context.am.line, width: 1.5)),
          ),
          const SizedBox(width: 10),
          Expanded(child: Text(label, style: context.text.bodyMedium)),
          Text('${(percent * 100).round()} %', style: context.text.bodySmall),
          const SizedBox(width: 12),
          Text(value, style: context.text.titleSmall),
        ],
      ),
    );
  }
}

class _DonutPainter extends CustomPainter {
  _DonutPainter(this.values, this.colors, this.line);
  final List<int> values;
  final List<Color> colors;
  final Color line;

  @override
  void paint(Canvas canvas, Size size) {
    final total = values.fold<int>(0, (a, b) => a + b);
    if (total == 0) return;
    final r = math.min(size.width, size.height) / 2 - 4;
    final c = Offset(size.width / 2, size.height / 2);
    final rect = Rect.fromCircle(center: c, radius: r - 14);
    var start = -math.pi / 2;
    for (var i = 0; i < values.length; i++) {
      final sweep = values[i] / total * math.pi * 2;
      canvas.drawArc(
        rect,
        start,
        sweep,
        false,
        Paint()
          ..color = colors[i]
          ..style = PaintingStyle.stroke
          ..strokeWidth = 28,
      );
      start += sweep;
    }
    final border = Paint()
      ..color = line
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    canvas.drawCircle(c, r, border);
    canvas.drawCircle(c, r - 28, border);
  }

  @override
  bool shouldRepaint(_DonutPainter old) => old.values != values || old.colors != colors;
}

class _PersonBars extends StatelessWidget {
  const _PersonBars({required this.person, required this.paid, required this.consumed, required this.max, required this.currency});
  final Person person;
  final int paid;
  final int consumed;
  final int max;
  final String currency;

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    Widget bar(int v, Color color) => LayoutBuilder(
          builder: (context, c) => Align(
            alignment: Alignment.centerLeft,
            child: Container(
              width: math.max(3, c.maxWidth * v / max),
              height: 12,
              decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(4)),
            ),
          ),
        );
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Avatar(person, size: 30),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(child: Text(person.name, style: context.text.titleSmall, overflow: TextOverflow.ellipsis)),
                    Text('${formatMoney(paid, currency)} / ${formatMoney(consumed, currency)}', style: context.text.bodySmall),
                  ],
                ),
                const SizedBox(height: 4),
                bar(paid, primary),
                const SizedBox(height: 3),
                bar(consumed, primary.withValues(alpha: 0.3)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MonthBar extends StatelessWidget {
  const _MonthBar({required this.label, required this.value, required this.max, required this.currency});
  final String label;
  final int value;
  final int max;
  final String currency;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: formatMoney(value, currency),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 3),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Flexible(
              child: FractionallySizedBox(
                heightFactor: math.max(0.02, value / max),
                child: Container(
                  decoration: BoxDecoration(
                    color: context.am.accent,
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
                    border: Border.all(color: context.am.line, width: 2),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 6),
            Text(label, style: context.text.labelSmall),
          ],
        ),
      ),
    );
  }
}
