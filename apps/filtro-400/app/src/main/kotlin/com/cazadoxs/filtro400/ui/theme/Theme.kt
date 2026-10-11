package com.cazadoxs.filtro400.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Identidad visual "señal de tráfico": tinta casi negra, papel cálido y un
 * naranja de señal que solo aparece cuando algo se corta. Los números siempre
 * en monoespaciada, como en una centralita.
 */
object Palette {
    val Ink = Color(0xFF0E1015)
    val Ink2 = Color(0xFF171A21)
    val Ink3 = Color(0xFF222632)
    val Line = Color(0xFF2E3341)
    val Paper = Color(0xFFF4F0E6)
    val Paper2 = Color(0xFFEAE4D6)
    val Paper3 = Color(0xFFFFFCF5)
    val Signal = Color(0xFFFF5A36)
    val SignalSoft = Color(0xFFFFD9CF)
    val Amber = Color(0xFFFFB020)
    val Mint = Color(0xFF22C997)
    val Muted = Color(0xFF8A90A2)
}

@Immutable
data class StatusColors(val blocked: Color, val silenced: Color, val allowed: Color, val muted: Color)

val LocalStatus = staticCompositionLocalOf {
    StatusColors(Palette.Signal, Palette.Amber, Palette.Mint, Palette.Muted)
}

private val Dark = darkColorScheme(
    primary = Palette.Signal,
    onPrimary = Palette.Ink,
    primaryContainer = Color(0xFF3A1A12),
    onPrimaryContainer = Palette.SignalSoft,
    secondary = Palette.Mint,
    onSecondary = Palette.Ink,
    tertiary = Palette.Amber,
    background = Palette.Ink,
    onBackground = Palette.Paper,
    surface = Palette.Ink,
    onSurface = Palette.Paper,
    surfaceVariant = Palette.Ink2,
    onSurfaceVariant = Color(0xFFB7BCCB),
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Ink2,
    surfaceContainer = Palette.Ink2,
    surfaceContainerHigh = Palette.Ink3,
    surfaceContainerHighest = Color(0xFF2A2F3C),
    outline = Palette.Line,
    outlineVariant = Color(0xFF242835),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFFE5431F),
    onPrimary = Color.White,
    primaryContainer = Palette.SignalSoft,
    onPrimaryContainer = Color(0xFF3A1A12),
    secondary = Color(0xFF0E9E76),
    onSecondary = Color.White,
    tertiary = Color(0xFFB57700),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Paper2,
    onSurfaceVariant = Color(0xFF4A4F5C),
    surfaceContainerLowest = Palette.Paper3,
    surfaceContainerLow = Palette.Paper3,
    surfaceContainer = Palette.Paper3,
    surfaceContainerHigh = Palette.Paper2,
    surfaceContainerHighest = Color(0xFFE0D9C9),
    outline = Color(0xFFD3CBB8),
    outlineVariant = Color(0xFFE3DCCB),
)

val Mono = FontFamily.Monospace

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-2).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    )
}

/** Estilo para números de teléfono y contadores. */
val NumberStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.5.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun Filtro400Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val status = if (dark) StatusColors(Palette.Signal, Palette.Amber, Palette.Mint, Palette.Muted)
    else StatusColors(Color(0xFFE5431F), Color(0xFFB57700), Color(0xFF0E9E76), Color(0xFF6E7383))
    CompositionLocalProvider(LocalStatus provides status) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
