import 'package:flutter/material.dart';

/// Paleta de un tema. La de "tomate" es gratis; el resto vienen con Plus.
class Palette {
  const Palette({
    required this.id,
    required this.name,
    required this.primary,
    required this.accent,
    required this.paper,
    this.plus = true,
  });

  final String id;
  final String name;
  final Color primary;
  final Color accent;
  final Color paper;
  final bool plus;
}

const palettes = <Palette>[
  Palette(
    id: 'tomate',
    name: 'Tomate',
    primary: Color(0xFFFF5A36),
    accent: Color(0xFFFFC94A),
    paper: Color(0xFFFFF7EC),
    plus: false,
  ),
  Palette(
    id: 'menta',
    name: 'Menta',
    primary: Color(0xFF0E9F84),
    accent: Color(0xFFFFB4A2),
    paper: Color(0xFFF0FAF5),
  ),
  Palette(
    id: 'uva',
    name: 'Uva',
    primary: Color(0xFF6C4CF1),
    accent: Color(0xFFFFC857),
    paper: Color(0xFFF6F3FF),
  ),
  Palette(
    id: 'mostaza',
    name: 'Mostaza',
    primary: Color(0xFFD99A00),
    accent: Color(0xFF2E6BE6),
    paper: Color(0xFFFFFAE8),
  ),
  Palette(
    id: 'chicle',
    name: 'Chicle',
    primary: Color(0xFFE5408A),
    accent: Color(0xFF34C3B0),
    paper: Color(0xFFFFF2F7),
  ),
  Palette(
    id: 'mar',
    name: 'Mar',
    primary: Color(0xFF1F6FEB),
    accent: Color(0xFFFF8A3D),
    paper: Color(0xFFF1F6FF),
  ),
];

Palette paletteOf(String id) =>
    palettes.firstWhere((p) => p.id == id, orElse: () => palettes.first);

/// Colores propios de A medias que Material no tiene.
@immutable
class AmColors extends ThemeExtension<AmColors> {
  const AmColors({
    required this.ink,
    required this.paper,
    required this.card,
    required this.muted,
    required this.positive,
    required this.negative,
    required this.accent,
    required this.line,
  });

  final Color ink;
  final Color paper;
  final Color card;
  final Color muted;
  final Color positive;
  final Color negative;
  final Color accent;
  final Color line;

  @override
  AmColors copyWith() => this;

  @override
  AmColors lerp(ThemeExtension<AmColors>? other, double t) {
    if (other is! AmColors) return this;
    return AmColors(
      ink: Color.lerp(ink, other.ink, t)!,
      paper: Color.lerp(paper, other.paper, t)!,
      card: Color.lerp(card, other.card, t)!,
      muted: Color.lerp(muted, other.muted, t)!,
      positive: Color.lerp(positive, other.positive, t)!,
      negative: Color.lerp(negative, other.negative, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
      line: Color.lerp(line, other.line, t)!,
    );
  }
}

extension AmTheme on BuildContext {
  AmColors get am => Theme.of(this).extension<AmColors>()!;
  TextTheme get text => Theme.of(this).textTheme;
}

const display = 'Bricolage';
const body = 'Instrument';

ThemeData buildTheme(Palette p, Brightness brightness) {
  final dark = brightness == Brightness.dark;
  final ink = dark ? const Color(0xFFF7F1E8) : const Color(0xFF1C1828);
  final paper = dark ? const Color(0xFF15131D) : p.paper;
  final card = dark ? const Color(0xFF211E2C) : Colors.white;
  final am = AmColors(
    ink: ink,
    paper: paper,
    card: card,
    muted: dark ? const Color(0xFFA9A3B5) : const Color(0xFF6E6879),
    positive: dark ? const Color(0xFF3DD6A3) : const Color(0xFF0B9A6D),
    negative: dark ? const Color(0xFFFF7A66) : const Color(0xFFE0402A),
    accent: p.accent,
    line: dark ? const Color(0xFF3A3548) : ink,
  );
  final scheme = ColorScheme.fromSeed(
    seedColor: p.primary,
    brightness: brightness,
  ).copyWith(
    primary: p.primary,
    onPrimary: Colors.white,
    secondary: p.accent,
    surface: card,
    onSurface: ink,
    error: am.negative,
  );

  TextStyle d(double size, FontWeight w, {double h = 1.1, double ls = -0.5}) => TextStyle(
        fontFamily: display,
        fontSize: size,
        fontWeight: w,
        height: h,
        letterSpacing: ls,
        color: ink,
      );
  TextStyle b(double size, FontWeight w, {double h = 1.35}) => TextStyle(
        fontFamily: body,
        fontSize: size,
        fontWeight: w,
        height: h,
        color: ink,
      );

  final textTheme = TextTheme(
    displayLarge: d(56, FontWeight.w800, ls: -2),
    displayMedium: d(44, FontWeight.w800, ls: -1.5),
    displaySmall: d(34, FontWeight.w800, ls: -1),
    headlineLarge: d(30, FontWeight.w800),
    headlineMedium: d(26, FontWeight.w700),
    headlineSmall: d(22, FontWeight.w700),
    titleLarge: d(20, FontWeight.w700, ls: -0.2),
    titleMedium: b(16, FontWeight.w600),
    titleSmall: b(14, FontWeight.w600),
    bodyLarge: b(16, FontWeight.w400),
    bodyMedium: b(14, FontWeight.w400),
    bodySmall: b(12, FontWeight.w500).copyWith(color: am.muted),
    labelLarge: b(15, FontWeight.w700),
    labelMedium: b(13, FontWeight.w600),
    labelSmall: b(11, FontWeight.w700).copyWith(letterSpacing: 0.6),
  );

  final shape = RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(16),
    side: BorderSide(color: am.line, width: 2),
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: paper,
    canvasColor: paper,
    fontFamily: body,
    textTheme: textTheme,
    extensions: [am],
    appBarTheme: AppBarTheme(
      backgroundColor: paper,
      foregroundColor: ink,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: d(22, FontWeight.w800),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: p.primary,
        foregroundColor: Colors.white,
        minimumSize: const Size(64, 54),
        textStyle: b(16, FontWeight.w700),
        shape: shape,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: ink,
        minimumSize: const Size(64, 50),
        textStyle: b(15, FontWeight.w700),
        side: BorderSide(color: am.line, width: 2),
        shape: shape,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: dark ? p.accent : ink,
        textStyle: b(15, FontWeight.w700),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: card,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      labelStyle: b(15, FontWeight.w500).copyWith(color: am.muted),
      hintStyle: b(15, FontWeight.w400).copyWith(color: am.muted),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: am.line, width: 2),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: am.line, width: 2),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: p.primary, width: 2.5),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: card,
      selectedColor: p.accent,
      side: BorderSide(color: am.line, width: 1.5),
      labelStyle: b(14, FontWeight.w600),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
      showCheckmark: false,
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: ink,
      contentTextStyle: b(14, FontWeight.w600).copyWith(color: paper),
      actionTextColor: p.accent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: paper,
      showDragHandle: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: paper,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: am.line, width: 2),
      ),
      titleTextStyle: d(22, FontWeight.w800),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: p.primary,
      foregroundColor: Colors.white,
      shape: shape,
      elevation: 0,
    ),
    segmentedButtonTheme: SegmentedButtonThemeData(
      style: ButtonStyle(
        side: WidgetStatePropertyAll(BorderSide(color: am.line, width: 1.5)),
        textStyle: WidgetStatePropertyAll(b(13, FontWeight.w700)),
      ),
    ),
    dividerTheme: DividerThemeData(color: am.line.withValues(alpha: 0.12), space: 1),
    switchTheme: SwitchThemeData(
      trackOutlineColor: WidgetStatePropertyAll(am.line),
    ),
  );
}
