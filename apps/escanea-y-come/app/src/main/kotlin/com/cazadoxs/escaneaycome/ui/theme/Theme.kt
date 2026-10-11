package com.cazadoxs.escaneaycome.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cazadoxs.escaneaycome.R
import com.cazadoxs.escaneaycome.data.Accent

/** Bricolage Grotesque (SIL OFL) for headings and numbers; system font for body text. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

/** Macro and nutrition colours, shared by light and dark themes. */
@Immutable
data class FoodColors(
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val water: Color,
    val calm: Color,
)

val LocalFoodColors = staticCompositionLocalOf {
    FoodColors(Color(0xFFE2553A), Color(0xFFF0A92E), Color(0xFF2A9D8F), Color(0xFF3A86C8), Color(0xFF7FA37F))
}

object NutriScoreColors {
    fun of(grade: String): Color = when (grade) {
        "a" -> Color(0xFF038141)
        "b" -> Color(0xFF85BB2F)
        "c" -> Color(0xFFFECB02)
        "d" -> Color(0xFFEE8100)
        else -> Color(0xFFE63E11)
    }
}

private fun accentPair(accent: Accent): Pair<Color, Color> = when (accent) {
    Accent.HUERTA -> Color(0xFF1E4D2B) to Color(0xFF8FD19E)
    Accent.MAR -> Color(0xFF14507A) to Color(0xFF8CC7F0)
    Accent.CIRUELA -> Color(0xFF5B2A5E) to Color(0xFFDDA6E0)
    Accent.NARANJA -> Color(0xFF9A3B12) to Color(0xFFFFB38A)
}

private fun light(accent: Accent): ColorScheme {
    val (primary, _) = accentPair(accent)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.12f).compositeOver(Color(0xFFFBF6EC)),
        onPrimaryContainer = primary,
        secondary = Color(0xFFE2553A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDCD2),
        onSecondaryContainer = Color(0xFF5C1A0B),
        tertiary = Color(0xFFF0A92E),
        onTertiary = Color(0xFF2B1D00),
        background = Color(0xFFFBF6EC),
        onBackground = Color(0xFF1C1B17),
        surface = Color(0xFFFFFDF8),
        onSurface = Color(0xFF1C1B17),
        surfaceVariant = Color(0xFFF2EADB),
        onSurfaceVariant = Color(0xFF5E584B),
        surfaceContainer = Color(0xFFF6EFE2),
        surfaceContainerHigh = Color(0xFFF0E8D8),
        surfaceContainerLow = Color(0xFFFFFAF1),
        outline = Color(0xFFCFC4AE),
        outlineVariant = Color(0xFFE4DBC8),
        error = Color(0xFFB3261E),
    )
}

private fun dark(accent: Accent): ColorScheme {
    val (_, primary) = accentPair(accent)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF0F1A12),
        primaryContainer = primary.copy(alpha = 0.18f).compositeOver(Color(0xFF14130F)),
        onPrimaryContainer = primary,
        secondary = Color(0xFFFF8A6E),
        onSecondary = Color(0xFF3A0B00),
        secondaryContainer = Color(0xFF5C2414),
        onSecondaryContainer = Color(0xFFFFDCD2),
        tertiary = Color(0xFFFFC75F),
        onTertiary = Color(0xFF2B1D00),
        background = Color(0xFF14130F),
        onBackground = Color(0xFFECE6DA),
        surface = Color(0xFF1C1B17),
        onSurface = Color(0xFFECE6DA),
        surfaceVariant = Color(0xFF2A2820),
        onSurfaceVariant = Color(0xFFCBC3B3),
        surfaceContainer = Color(0xFF221F19),
        surfaceContainerHigh = Color(0xFF2B2821),
        surfaceContainerLow = Color(0xFF1A1914),
        outline = Color(0xFF6E6757),
        outlineVariant = Color(0xFF3D3A31),
        error = Color(0xFFFFB4AB),
    )
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

private val Type = Typography().let { t ->
    fun TextStyle.brico(weight: FontWeight, tracking: Double = -0.02) =
        copy(fontFamily = Bricolage, fontWeight = weight, letterSpacing = (fontSize.value * tracking).sp)
    t.copy(
        displayLarge = t.displayLarge.brico(FontWeight.ExtraBold),
        displayMedium = t.displayMedium.brico(FontWeight.ExtraBold),
        displaySmall = t.displaySmall.brico(FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.brico(FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.brico(FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.brico(FontWeight.ExtraBold),
        titleLarge = t.titleLarge.brico(FontWeight.ExtraBold),
        titleMedium = t.titleMedium.brico(FontWeight.SemiBold, 0.0),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun EscaneaTheme(accent: Accent = Accent.HUERTA, content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) dark(accent) else light(accent)
    MaterialTheme(colorScheme = scheme, typography = Type, shapes = AppShapes, content = content)
}
