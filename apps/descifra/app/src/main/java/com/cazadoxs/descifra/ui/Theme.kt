package com.cazadoxs.descifra.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cazadoxs.descifra.R

/** "Papel y sello": warm paper, navy ink, and a vermilion stamp for what needs action. */
@Immutable
data class Palette(
    val paper: Color,
    val sheet: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val stamp: Color,
    val stampSoft: Color,
    val blue: Color,
    val green: Color,
    val amber: Color,
    val onStamp: Color,
    val dark: Boolean,
)

private val Light = Palette(
    paper = Color(0xFFF4EFE6), sheet = Color(0xFFFCFAF5), ink = Color(0xFF1B2A41), inkSoft = Color(0xFF55607A),
    line = Color(0xFFDDD3C2), stamp = Color(0xFFB8321F), stampSoft = Color(0xFFF3D9D3), blue = Color(0xFF2F4B7C),
    green = Color(0xFF2E6B4F), amber = Color(0xFF9A6410), onStamp = Color(0xFFFFFFFF), dark = false,
)

private val Dark = Palette(
    paper = Color(0xFF111924), sheet = Color(0xFF1A2433), ink = Color(0xFFF1EADB), inkSoft = Color(0xFF9AA7BD),
    line = Color(0xFF2C3A4F), stamp = Color(0xFFE8705B), stampSoft = Color(0xFF3A2420), blue = Color(0xFF8DA9DB),
    green = Color(0xFF7FC4A0), amber = Color(0xFFE0B25A), onStamp = Color(0xFF1A0F0C), dark = true,
)

val LocalPalette = staticCompositionLocalOf { Light }

val Serif = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.opticalSizing(30.sp), FontVariation.Setting("WONK", 0f), FontVariation.Setting("SOFT", 0f))),
    Font(R.font.fraunces, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.opticalSizing(48.sp), FontVariation.Setting("WONK", 0f), FontVariation.Setting("SOFT", 0f))),
    Font(R.font.fraunces, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(750), FontVariation.opticalSizing(72.sp), FontVariation.Setting("WONK", 0f), FontVariation.Setting("SOFT", 0f))),
)

/** Atkinson Hyperlegible: designed for low-vision readers, ideal for dense official text. */
val Sans = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
    Font(R.font.atkinson_bold, FontWeight.SemiBold),
    Font(R.font.atkinson_bold, FontWeight.Medium),
)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Sans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, lineBreak = LineBreak.Paragraph)

private val Type = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = body(17, 24, FontWeight.Bold),
    titleSmall = body(15, 20, FontWeight.Bold),
    bodyLarge = body(18, 28),
    bodyMedium = body(16, 24),
    bodySmall = body(14, 20),
    labelLarge = body(16, 20, FontWeight.Bold),
    labelMedium = body(14, 18, FontWeight.Bold),
    labelSmall = body(12, 16, FontWeight.Bold),
)

private val Corners = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private fun scheme(p: Palette): ColorScheme =
    (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.ink, onPrimary = p.paper,
        secondary = p.stamp, onSecondary = p.onStamp,
        tertiary = p.blue,
        background = p.paper, onBackground = p.ink,
        surface = p.sheet, onSurface = p.ink,
        surfaceVariant = p.paper, onSurfaceVariant = p.inkSoft,
        surfaceContainer = p.sheet, surfaceContainerHigh = p.sheet, surfaceContainerHighest = p.sheet,
        surfaceContainerLow = p.paper, surfaceContainerLowest = p.paper,
        outline = p.line, outlineVariant = p.line,
        error = p.stamp, onError = p.onStamp, errorContainer = p.stampSoft, onErrorContainer = p.ink,
        primaryContainer = p.ink, onPrimaryContainer = p.paper,
        secondaryContainer = p.stampSoft, onSecondaryContainer = p.ink,
    )

val MaterialTheme.palette: Palette
    @Composable get() = LocalPalette.current

@Composable
fun DescifraTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme(p), typography = Type, shapes = Corners, content = content)
    }
}
