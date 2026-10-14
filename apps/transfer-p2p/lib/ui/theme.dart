import 'package:flutter/material.dart';

/// Paleta "tinta y lima": fondo azul noche, acento lima eléctrica y coral.
class Palette {
  static const ink = Color(0xFF0E1124);
  static const panel = Color(0xFF181C36);
  static const panelHi = Color(0xFF222748);
  static const lime = Color(0xFFC8F53B);
  static const coral = Color(0xFFFF7A59);
  static const sky = Color(0xFF7FD3FF);
  static const text = Color(0xFFEDEFFC);
  static const muted = Color(0xFF9BA1C7);
}

ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: Palette.lime,
    brightness: Brightness.dark,
  ).copyWith(
    primary: Palette.lime,
    onPrimary: Palette.ink,
    secondary: Palette.coral,
    tertiary: Palette.sky,
    surface: Palette.ink,
    onSurface: Palette.text,
    surfaceContainer: Palette.panel,
    surfaceContainerHigh: Palette.panelHi,
    error: Palette.coral,
  );
  final base = ThemeData(colorScheme: scheme, useMaterial3: true);
  return base.copyWith(
    scaffoldBackgroundColor: Palette.ink,
    textTheme: base.textTheme.apply(bodyColor: Palette.text, displayColor: Palette.text),
    cardTheme: CardThemeData(
      color: Palette.panel,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(48, 52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(48, 48),
        foregroundColor: Palette.text,
        side: const BorderSide(color: Palette.panelHi, width: 2),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Palette.panelHi,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide.none,
      ),
    ),
    navigationBarTheme: const NavigationBarThemeData(
      backgroundColor: Palette.panel,
      indicatorColor: Palette.lime,
      iconTheme: WidgetStatePropertyAll(IconThemeData(color: Palette.text)),
    ),
    navigationRailTheme: const NavigationRailThemeData(
      backgroundColor: Palette.panel,
      indicatorColor: Palette.lime,
    ),
  );
}
