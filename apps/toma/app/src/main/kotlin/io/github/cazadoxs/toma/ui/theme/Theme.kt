package io.github.cazadoxs.toma.ui.theme

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cazadoxs.toma.R

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: Int) = Font(
    R.font.fraunces, FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("SOFT", 100f),
        FontVariation.Setting("opsz", 72f),
    ),
)

val Nunito = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800))
val Fraunces = FontFamily(fraunces(500), fraunces(600), fraunces(700))

private val TomaTypography = Typography(
    displayLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(600), fontSize = 52.sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(600), fontSize = 40.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(600), fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(600), fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(600), fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(800), fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(700), fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(800), fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(700), fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight(700), fontSize = 11.sp, lineHeight = 15.sp),
)

private val TomaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Colours Material doesn't name: dose states and the medication swatches. */
@Immutable
data class TomaColors(
    val taken: Color,
    val skipped: Color,
    val missed: Color,
    val due: Color,
    val refill: Color,
    val paperLine: Color,
    val swatches: List<Color>,
)

val LocalTomaColors = staticCompositionLocalOf {
    TomaColors(Color.Green, Color.Gray, Color.Red, Color.Red, Color.Yellow, Color.LightGray, listOf(Color.Gray))
}

/** A palette: Botica and Contraste are free, the rest come with Toma Plus. */
data class Palette(
    val id: String,
    val nameRes: Int,
    val plus: Boolean,
    val light: ColorScheme,
    val dark: ColorScheme,
)

private fun scheme(
    dark: Boolean,
    primary: Long,
    onPrimary: Long,
    primaryContainer: Long,
    onPrimaryContainer: Long,
    secondary: Long,
    background: Long,
    surface: Long,
    surfaceVariant: Long,
    onSurface: Long,
    onSurfaceVariant: Long,
    outline: Long,
): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(secondary).copy(alpha = 0.18f), onSecondaryContainer = Color(onSurface),
        tertiary = Color(0xFFC99A2E), onTertiary = Color(0xFF1D1600),
        background = Color(background), onBackground = Color(onSurface),
        surface = Color(surface), onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
        surfaceContainerLowest = Color(surface), surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surfaceVariant), surfaceContainerHigh = Color(surfaceVariant),
        surfaceContainerHighest = Color(surfaceVariant),
        outline = Color(outline), outlineVariant = Color(outline).copy(alpha = 0.6f),
        error = Color(0xFFC6402B), onError = Color(0xFFFFFFFF),
    )
}

val Palettes: List<Palette> = listOf(
    Palette(
        "botica", R.string.palette_botica, plus = false,
        light = scheme(false, 0xFF1F5E55, 0xFFFFFFFF, 0xFFCFE7DF, 0xFF0B2C27, 0xFFE36A4A, 0xFFF6F1E7, 0xFFFFFDF8, 0xFFEDE5D6, 0xFF1D2A27, 0xFF5C6A66, 0xFFD9CFBF),
        dark = scheme(true, 0xFF86D4C2, 0xFF00382F, 0xFF1F4B44, 0xFFCFEFE6, 0xFFFF8D6E, 0xFF0F1D1A, 0xFF182B27, 0xFF213833, 0xFFE9EFEC, 0xFFA9B8B3, 0xFF3A524C),
    ),
    Palette(
        "contraste", R.string.palette_contrast, plus = false,
        light = scheme(false, 0xFF003D33, 0xFFFFFFFF, 0xFFB8F0E0, 0xFF00201A, 0xFFB3261E, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFEFEFEF, 0xFF000000, 0xFF1F1F1F, 0xFF4A4A4A),
        dark = scheme(true, 0xFF9FFFE6, 0xFF001F19, 0xFF004D40, 0xFFFFFFFF, 0xFFFFB4A9, 0xFF000000, 0xFF0A0A0A, 0xFF1A1A1A, 0xFFFFFFFF, 0xFFE6E6E6, 0xFFBDBDBD),
    ),
    Palette(
        "lavanda", R.string.palette_lavender, plus = true,
        light = scheme(false, 0xFF5B4A9E, 0xFFFFFFFF, 0xFFE6DEFF, 0xFF1A0F4A, 0xFFE0708B, 0xFFF6F3FB, 0xFFFFFFFF, 0xFFEAE5F5, 0xFF1F1A2E, 0xFF5F5970, 0xFFD6CFE6),
        dark = scheme(true, 0xFFC8BCFF, 0xFF2B1C6B, 0xFF433383, 0xFFE6DEFF, 0xFFFF9FB3, 0xFF14111D, 0xFF1D1928, 0xFF282336, 0xFFECE6F5, 0xFFB5AEC6, 0xFF453E57),
    ),
    Palette(
        "mar", R.string.palette_sea, plus = true,
        light = scheme(false, 0xFF0D5C8C, 0xFFFFFFFF, 0xFFD2E6F5, 0xFF002237, 0xFFF29E38, 0xFFF1F5F8, 0xFFFFFFFF, 0xFFE2EAF0, 0xFF16222B, 0xFF52616B, 0xFFC9D6DF),
        dark = scheme(true, 0xFF95CBF0, 0xFF00344F, 0xFF0D4A70, 0xFFD2E6F5, 0xFFFFC27A, 0xFF0D161D, 0xFF152029, 0xFF1E2C37, 0xFFE4EDF3, 0xFFA6B5BF, 0xFF34444F),
    ),
    Palette(
        "terracota", R.string.palette_terracotta, plus = true,
        light = scheme(false, 0xFFA34A28, 0xFFFFFFFF, 0xFFFFDBCB, 0xFF390C00, 0xFF2F7D6B, 0xFFFBF3EC, 0xFFFFFCF9, 0xFFF3E4D9, 0xFF2B1D17, 0xFF6B5A51, 0xFFE3CFC1),
        dark = scheme(true, 0xFFFFB596, 0xFF5A1C03, 0xFF7E3415, 0xFFFFDBCB, 0xFF7FD3BE, 0xFF1C130F, 0xFF261B16, 0xFF33251F, 0xFFF3E6DF, 0xFFC7B4AA, 0xFF52413A),
    ),
    Palette(
        "bosque", R.string.palette_forest, plus = true,
        light = scheme(false, 0xFF3B6B2E, 0xFFFFFFFF, 0xFFD6EBC8, 0xFF0D2005, 0xFFD9822B, 0xFFF4F4EA, 0xFFFFFFF8, 0xFFE6E8D8, 0xFF1C2117, 0xFF5A6152, 0xFFD0D4C0),
        dark = scheme(true, 0xFFA9D795, 0xFF153808, 0xFF2A511D, 0xFFD6EBC8, 0xFFFFB774, 0xFF11150E, 0xFF1A1F16, 0xFF252B20, 0xFFE6EADC, 0xFFB3BAA8, 0xFF3F4637),
    ),
)

fun paletteFor(id: String, plusOwned: Boolean): Palette {
    val p = Palettes.firstOrNull { it.id == id } ?: Palettes.first()
    return if (p.plus && !plusOwned) Palettes.first() else p
}

private val SwatchesLight = listOf(
    Color(0xFFE36A4A), Color(0xFF1F5E55), Color(0xFF3F7CC4), Color(0xFFC99A2E),
    Color(0xFF8A5BC4), Color(0xFFD14F84), Color(0xFF4E9A5B), Color(0xFF7A6A58),
)
private val SwatchesDark = listOf(
    Color(0xFFFF8D6E), Color(0xFF86D4C2), Color(0xFF8FB8EE), Color(0xFFF0C766),
    Color(0xFFC3A2F2), Color(0xFFF58DB6), Color(0xFF93D19D), Color(0xFFC7B6A2),
)

@Composable
fun TomaTheme(themeId: String, plusOwned: Boolean, largeText: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = paletteFor(themeId, plusOwned)
    val scheme = if (dark) palette.dark else palette.light
    val toma = if (dark) {
        TomaColors(
            taken = Color(0xFF86D4C2), skipped = Color(0xFF8E9A96), missed = Color(0xFFFF8D6E),
            due = scheme.secondary, refill = Color(0xFFF0C766), paperLine = scheme.outline, swatches = SwatchesDark,
        )
    } else {
        TomaColors(
            taken = Color(0xFF2E7D67), skipped = Color(0xFF8E9A96), missed = Color(0xFFC6402B),
            due = scheme.secondary, refill = Color(0xFFB8860B), paperLine = scheme.outline, swatches = SwatchesLight,
        )
    }
    val density = LocalDensity.current
    val scaled = if (largeText) Density(density.density, density.fontScale * 1.18f) else density
    CompositionLocalProvider(LocalTomaColors provides toma, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = TomaTypography, shapes = TomaShapes, content = content)
    }
}
