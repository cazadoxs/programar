@file:OptIn(ExperimentalTextApi::class)

package com.cazadoxs.escanerlimpio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cazadoxs.escanerlimpio.R
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.data.ThemeMode

// "Paper and ink": warm paper, deep ink, a highlighter accent and a stamp red.
object Palette {
    val Paper = Color(0xFFF6F1E7)
    val PaperLight = Color(0xFFFFFCF6)
    val PaperShade = Color(0xFFECE4D4)
    val PaperFold = Color(0xFFDCD1BC)
    val Ink = Color(0xFF16233F)
    val InkSoft = Color(0xFF4A5468)
    val Highlighter = Color(0xFFF2B33D)
    val Stamp = Color(0xFFD5533A)
    val Night = Color(0xFF10141C)
    val NightSurface = Color(0xFF181D27)
    val NightRaised = Color(0xFF222936)
    val Cream = Color(0xFFF1E8D6)
}

private val LightColors = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.PaperLight,
    primaryContainer = Color(0xFFDCE3F2),
    onPrimaryContainer = Palette.Ink,
    secondary = Color(0xFF9A6A12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE3B0),
    onSecondaryContainer = Color(0xFF3A2800),
    tertiary = Palette.Stamp,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD1),
    onTertiaryContainer = Color(0xFF3E0800),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperShade,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainerLowest = Palette.PaperLight,
    surfaceContainerLow = Color(0xFFFBF7EF),
    surfaceContainer = Color(0xFFF2ECDF),
    surfaceContainerHigh = Color(0xFFECE5D7),
    surfaceContainerHighest = Color(0xFFE6DECE),
    outline = Color(0xFFB9AE99),
    outlineVariant = Color(0xFFDDD3C1),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Palette.Cream,
    onPrimary = Palette.Ink,
    primaryContainer = Color(0xFF2C3958),
    onPrimaryContainer = Palette.Cream,
    secondary = Palette.Highlighter,
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF5A4110),
    onSecondaryContainer = Color(0xFFFBE3B0),
    tertiary = Color(0xFFFF8B70),
    onTertiary = Color(0xFF5E1300),
    tertiaryContainer = Color(0xFF7D2A16),
    onTertiaryContainer = Color(0xFFFFDAD1),
    background = Palette.Night,
    onBackground = Palette.Cream,
    surface = Palette.Night,
    onSurface = Palette.Cream,
    surfaceVariant = Palette.NightRaised,
    onSurfaceVariant = Color(0xFFBDB5A6),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF151A23),
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.NightRaised,
    surfaceContainerHighest = Color(0xFF2B3341),
    outline = Color(0xFF6E6759),
    outlineVariant = Color(0xFF3A4150),
)

val Fraunces = FontFamily(
    Font(
        R.font.fraunces, FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.Setting("opsz", 48f), FontVariation.Setting("SOFT", 100f)),
    ),
    Font(
        R.font.fraunces, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.Setting("opsz", 72f), FontVariation.Setting("SOFT", 100f)),
    ),
    Font(
        R.font.fraunces, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("opsz", 96f), FontVariation.Setting("SOFT", 100f)),
    ),
)

val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    displayMedium = base.displayMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    displaySmall = base.displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = base.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = base.bodySmall.copy(fontFamily = Manrope),
    labelLarge = base.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

/** Extra brand colours that Material's scheme has no slot for. */
@Immutable
data class Brand(val sheet: Color, val sheetFold: Color, val ink: Color, val highlighter: Color, val dark: Boolean)

val LocalBrand = staticCompositionLocalOf {
    Brand(Palette.PaperLight, Palette.PaperFold, Palette.Ink, Palette.Highlighter, false)
}

@Composable
fun EscanerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SISTEMA -> isSystemInDarkTheme()
        ThemeMode.CLARO -> false
        ThemeMode.OSCURO -> true
    }
    val brand = if (dark) {
        Brand(Color(0xFF232A37), Color(0xFF2F3848), Palette.Cream, Palette.Highlighter, true)
    } else {
        Brand(Palette.PaperLight, Palette.PaperFold, Palette.Ink, Palette.Highlighter, false)
    }
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}

/** A distinct, quiet tint per smart folder: (container, content). */
@Composable
fun categoryColors(category: Category): Pair<Color, Color> {
    val dark = LocalBrand.current.dark
    val hue = when (category) {
        Category.IDENTIDAD -> 0xFF3D5A98
        Category.NOMINAS -> 0xFF2E7D5B
        Category.FACTURAS -> 0xFFB7791F
        Category.TICKETS -> 0xFFC2410C
        Category.HACIENDA -> 0xFF7A3E9D
        Category.BANCO -> 0xFF0E7490
        Category.CONTRATOS -> 0xFF5B4636
        Category.SEGUROS -> 0xFF4D7C0F
        Category.SALUD -> 0xFFBE3455
        Category.VEHICULO -> 0xFF475569
        Category.ESTUDIOS -> 0xFF4338CA
        Category.OTROS -> 0xFF6B6255
    }
    val c = Color(hue)
    return if (dark) c.copy(alpha = 0.28f) to Color(0xFFF1E8D6) else c.copy(alpha = 0.13f) to c
}

val MonoNumbers = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold)
