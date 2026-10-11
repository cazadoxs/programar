package vlc.busmetro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vlc.busmetro.R

/**
 * Identity: València at dusk. Deep sea-ink surfaces, "taronja" (orange) as the
 * one loud accent, warm cream text. Network colours are our own, not the
 * operators' brand colours.
 */
object Palette {
    val Ink = Color(0xFF0B1F2A)
    val InkSoft = Color(0xFF133141)
    val InkRaised = Color(0xFF1B3F52)
    val Orange = Color(0xFFFF6B1A)
    val OrangeDeep = Color(0xFFD9500A)
    val Cream = Color(0xFFFFF7EC)
    val Sand = Color(0xFFF4E9D8)
    val Muted = Color(0xFF9DB4BF)
    val Teal = Color(0xFF2EC4B6)
    val Live = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFC857)
    val Bad = Color(0xFFFF5A5F)

    val Bus = Color(0xFFE8482C)
    val Metro = Color(0xFF1E88E5)
    val Rail = Color(0xFF8E44AD)
    val Bike = Color(0xFF2EA66B)
}

private val Dark = darkColorScheme(
    primary = Palette.Orange, onPrimary = Color.White,
    primaryContainer = Palette.OrangeDeep, onPrimaryContainer = Color.White,
    secondary = Palette.Teal, onSecondary = Palette.Ink,
    tertiary = Palette.Warn, onTertiary = Palette.Ink,
    background = Palette.Ink, onBackground = Palette.Cream,
    surface = Palette.Ink, onSurface = Palette.Cream,
    surfaceVariant = Palette.InkSoft, onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.InkSoft, surfaceContainerHigh = Palette.InkRaised, surfaceContainerHighest = Palette.InkRaised,
    outline = Color(0xFF3C5B6B), outlineVariant = Color(0xFF244253),
    error = Palette.Bad,
)

private val Light = lightColorScheme(
    primary = Palette.OrangeDeep, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC8), onPrimaryContainer = Color(0xFF3A1500),
    secondary = Color(0xFF0E7C72), onSecondary = Color.White,
    tertiary = Color(0xFF8A5A00), onTertiary = Color.White,
    background = Palette.Cream, onBackground = Palette.Ink,
    surface = Palette.Cream, onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand, onSurfaceVariant = Color(0xFF4F6772),
    surfaceContainer = Color(0xFFFBEEDC), surfaceContainerHigh = Palette.Sand, surfaceContainerHighest = Color(0xFFEBDDC6),
    outline = Color(0xFF86989F), outlineVariant = Color(0xFFD9CBB4),
    error = Color(0xFFBA1A1A),
)

val Grotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private fun t(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 6, spacing: Double = 0.0) =
    TextStyle(fontFamily = Grotesk, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val Type = Typography(
    displayLarge = t(56, FontWeight.Bold, 60, -1.0),
    displayMedium = t(44, FontWeight.Bold, 48, -0.5),
    displaySmall = t(34, FontWeight.Bold, 38),
    headlineLarge = t(30, FontWeight.Bold, 36),
    headlineMedium = t(26, FontWeight.Bold, 32),
    headlineSmall = t(22, FontWeight.Bold, 28),
    titleLarge = t(20, FontWeight.Bold, 26),
    titleMedium = t(16, FontWeight.Medium, 22),
    titleSmall = t(14, FontWeight.Medium, 20),
    bodyLarge = t(16, FontWeight.Normal, 24),
    bodyMedium = t(14, FontWeight.Normal, 20),
    bodySmall = t(12, FontWeight.Normal, 16),
    labelLarge = t(14, FontWeight.Medium, 20, 0.1),
    labelMedium = t(12, FontWeight.Medium, 16, 0.3),
    labelSmall = t(11, FontWeight.Medium, 16, 0.4),
)

val LocalDark = staticCompositionLocalOf { true }

@Composable
fun BusMetroTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}

fun networkColor(network: String): Color = when (network) {
    "bus" -> Palette.Bus
    "metro" -> Palette.Metro
    "rail" -> Palette.Rail
    "bike" -> Palette.Bike
    else -> Palette.Orange
}
