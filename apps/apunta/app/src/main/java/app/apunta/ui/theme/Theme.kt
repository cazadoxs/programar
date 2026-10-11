package app.apunta.ui.theme

import android.app.Activity
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.apunta.R

/*
 * Apunta's look: a paper notebook. Warm paper background, ink text, the red
 * margin line of a ruled notebook as the brand accent (it is also the "REC"
 * colour), and a highlighter yellow for search hits.
 */

val Fraunces = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val Mono = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

/** Colours that Material's scheme has no slot for. */
@Immutable
data class ApuntaColors(
    val paper: Color,
    val ink: Color,
    val inkSoft: Color,
    val margin: Color,       // red margin line / record
    val highlighter: Color,  // search hits
    val ruled: Color,        // faint notebook lines
    val done: Color,
    val card: Color,
)

private val LightExtra = ApuntaColors(
    paper = Color(0xFFF6F1E7),
    ink = Color(0xFF1C1A16),
    inkSoft = Color(0xFF6B6457),
    margin = Color(0xFFD9412E),
    highlighter = Color(0xFFFFE27A),
    ruled = Color(0xFFE4DCCB),
    done = Color(0xFF1E6B5C),
    card = Color(0xFFFFFCF6),
)

private val DarkExtra = ApuntaColors(
    paper = Color(0xFF15140F),
    ink = Color(0xFFF1EBDF),
    inkSoft = Color(0xFFA79F90),
    margin = Color(0xFFFF6B55),
    highlighter = Color(0xFF7A6514),
    ruled = Color(0xFF2B2922),
    done = Color(0xFF5CC2A8),
    card = Color(0xFF1F1D17),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFD9412E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF410200),
    secondary = Color(0xFF1E6B5C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEDE3),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF6B5A12),
    tertiaryContainer = Color(0xFFFFE27A),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1C1A16),
    surface = Color(0xFFF6F1E7),
    onSurface = Color(0xFF1C1A16),
    surfaceVariant = Color(0xFFEDE5D6),
    onSurfaceVariant = Color(0xFF6B6457),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFCF6),
    surfaceContainer = Color(0xFFF9F4EA),
    surfaceContainerHigh = Color(0xFFF1EADD),
    surfaceContainerHighest = Color(0xFFEBE3D4),
    outline = Color(0xFFB9AF9D),
    outlineVariant = Color(0xFFE4DCCB),
    error = Color(0xFFB3261E),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B55),
    onPrimary = Color(0xFF3B0A02),
    primaryContainer = Color(0xFF7A1F12),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFF5CC2A8),
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF0E4F43),
    onSecondaryContainer = Color(0xFFCDEDE3),
    tertiary = Color(0xFFE9C94E),
    tertiaryContainer = Color(0xFF524400),
    onTertiaryContainer = Color(0xFFFFE27A),
    background = Color(0xFF15140F),
    onBackground = Color(0xFFF1EBDF),
    surface = Color(0xFF15140F),
    onSurface = Color(0xFFF1EBDF),
    surfaceVariant = Color(0xFF2B2922),
    onSurfaceVariant = Color(0xFFA79F90),
    surfaceContainerLowest = Color(0xFF0F0E0A),
    surfaceContainerLow = Color(0xFF1F1D17),
    surfaceContainer = Color(0xFF23211A),
    surfaceContainerHigh = Color(0xFF2B2922),
    surfaceContainerHighest = Color(0xFF34312A),
    outline = Color(0xFF6E675A),
    outlineVariant = Color(0xFF2B2922),
    error = Color(0xFFFFB4AB),
)

private fun typography(ink: Color) = Typography(
    displayLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 27.sp, color = ink),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

private val ApuntaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalApuntaColors = staticCompositionLocalOf { LightExtra }

object Apunta {
    val colors: ApuntaColors
        @Composable get() = LocalApuntaColors.current
}

/** Monospaced style for timestamps and timers. */
val TimestampStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp)

@Composable
fun ApuntaTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val extra = if (dark) DarkExtra else LightExtra

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalApuntaColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(extra.ink),
            shapes = ApuntaShapes,
            content = content,
        )
    }
}
