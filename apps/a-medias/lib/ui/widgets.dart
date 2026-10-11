import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/models.dart';
import '../core/money.dart';
import 'theme.dart';

/// Tarjeta con borde grueso y sombra dura desplazada: la seña visual de
/// A medias (como una pegatina sobre papel).
class StickerCard extends StatelessWidget {
  const StickerCard({
    super.key,
    required this.child,
    this.color,
    this.onTap,
    this.padding = const EdgeInsets.all(16),
    this.offset = 4,
    this.radius = 20,
  });

  final Widget child;
  final Color? color;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;
  final double offset;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    final r = BorderRadius.circular(radius);
    return Padding(
      padding: EdgeInsets.only(right: offset, bottom: offset),
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: r,
          boxShadow: [BoxShadow(color: am.line, offset: Offset(offset, offset))],
        ),
        child: Material(
          // Opaco aunque se pase un color translúcido: si no, la sombra dura
          // se transparenta por debajo.
          color: color == null ? am.card : Color.alphaBlend(color!, am.card),
          shape: RoundedRectangleBorder(
            borderRadius: r,
            side: BorderSide(color: am.line, width: 2),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: Padding(padding: padding, child: child),
          ),
        ),
      ),
    );
  }
}

const _avatarColors = [
  Color(0xFFFFC94A),
  Color(0xFF8ED1C6),
  Color(0xFFFFA48E),
  Color(0xFFB9A8FF),
  Color(0xFF9CD67A),
  Color(0xFFFF9BC6),
  Color(0xFF8CC3FF),
  Color(0xFFFFB86B),
];

Color avatarColor(String id) {
  var h = 0;
  for (final c in id.codeUnits) {
    h = (h * 31 + c) & 0x7fffffff;
  }
  return _avatarColors[h % _avatarColors.length];
}

class Avatar extends StatelessWidget {
  const Avatar(this.person, {super.key, this.size = 36, this.highlight = false});

  final Person person;
  final double size;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: avatarColor(person.id),
        shape: BoxShape.circle,
        border: Border.all(color: highlight ? Theme.of(context).colorScheme.primary : am.line, width: highlight ? 3 : 2),
      ),
      child: Text(
        person.initials,
        style: TextStyle(
          fontFamily: display,
          fontWeight: FontWeight.w800,
          fontSize: size * 0.38,
          color: const Color(0xFF1C1828),
          height: 1,
        ),
      ),
    );
  }
}

/// Importe con color según el signo: verde si te deben, rojo si debes.
class MoneyText extends StatelessWidget {
  const MoneyText(
    this.minor,
    this.currency, {
    super.key,
    this.style,
    this.signed = false,
    this.colored = true,
  });

  final int minor;
  final String currency;
  final TextStyle? style;
  final bool signed;
  final bool colored;

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    final color = !colored || minor == 0
        ? null
        : minor > 0
            ? am.positive
            : am.negative;
    return Text(
      formatMoney(minor, currency, signed: signed),
      style: (style ?? context.text.titleMedium)?.copyWith(
        color: color,
        fontFeatures: const [FontFeature.tabularFigures()],
      ),
    );
  }
}

/// El logo: una moneda partida en dos mitades desplazadas.
class SplitLogo extends StatelessWidget {
  const SplitLogo({super.key, this.size = 40});
  final double size;

  @override
  Widget build(BuildContext context) {
    final p = Theme.of(context).colorScheme;
    return SizedBox.square(
      dimension: size,
      child: CustomPaint(
        painter: _LogoPainter(p.primary, context.am.accent, context.am.line),
      ),
    );
  }
}

class _LogoPainter extends CustomPainter {
  _LogoPainter(this.a, this.b, this.ink);
  final Color a;
  final Color b;
  final Color ink;

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width;
    final r = s * 0.40;
    final gap = s * 0.05;
    final c = Offset(s / 2, s / 2);
    final stroke = Paint()
      ..color = ink
      ..style = PaintingStyle.stroke
      ..strokeWidth = s * 0.055
      ..strokeJoin = StrokeJoin.round;

    void half(Offset center, double start, Color color) {
      final rect = Rect.fromCircle(center: center, radius: r);
      final path = Path()
        ..moveTo(center.dx, center.dy)
        ..arcTo(rect, start, math.pi, false)
        ..close();
      canvas.drawPath(path, Paint()..color = color);
      canvas.drawPath(path, stroke);
    }

    // Mitad izquierda (arriba-izquierda) y derecha, separadas en diagonal.
    const tilt = -math.pi / 4;
    final dir = Offset(math.cos(tilt + math.pi / 2), math.sin(tilt + math.pi / 2));
    half(c - dir * gap, tilt + math.pi, a);
    half(c + dir * gap, tilt, b);
  }

  @override
  bool shouldRepaint(_LogoPainter old) => old.a != a || old.b != b || old.ink != ink;
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.emoji, required this.title, required this.text, this.action});

  final String emoji;
  final String title;
  final String text;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(emoji, style: const TextStyle(fontSize: 56)),
            const SizedBox(height: 12),
            Text(title, style: context.text.headlineSmall, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(text, style: context.text.bodyMedium?.copyWith(color: context.am.muted), textAlign: TextAlign.center),
            if (action != null) ...[const SizedBox(height: 20), action!],
          ],
        ),
      ),
    );
  }
}

/// Etiqueta pequeña tipo píldora.
class Pill extends StatelessWidget {
  const Pill(this.label, {super.key, this.color, this.icon});
  final String label;
  final Color? color;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color ?? am.accent,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: am.line, width: 1.5),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 14, color: const Color(0xFF1C1828)), const SizedBox(width: 4)],
          Text(
            label,
            style: const TextStyle(
              fontFamily: body,
              fontWeight: FontWeight.w700,
              fontSize: 12,
              color: Color(0xFF1C1828),
            ),
          ),
        ],
      ),
    );
  }
}

void toast(BuildContext context, String message, {SnackBarAction? action}) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message), action: action));
}

String relativeDay(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(d.year, d.month, d.day);
  final diff = today.difference(day).inDays;
  if (diff == 0) return 'Hoy';
  if (diff == 1) return 'Ayer';
  const months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  const weekdays = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
  if (diff > 1 && diff < 7) return weekdays[d.weekday - 1][0].toUpperCase() + weekdays[d.weekday - 1].substring(1);
  final y = d.year == now.year ? '' : ' ${d.year}';
  return '${d.day} ${months[d.month - 1]}$y';
}

String formatPercent(int basisPoints) {
  final whole = basisPoints ~/ 100;
  final frac = basisPoints % 100;
  return frac == 0 ? '$whole %' : '$whole,${frac.toString().padLeft(2, '0')} %';
}

String currencyLabel(String code) {
  final c = currencyOf(code);
  return '${c.code} · ${c.name}';
}
