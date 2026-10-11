package vlc.busmetro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vlc.busmetro.R
import vlc.busmetro.data.BoardSource
import vlc.busmetro.data.LineStyle
import vlc.busmetro.data.StopEntity
import vlc.busmetro.ui.theme.Grotesk
import vlc.busmetro.ui.theme.Palette
import vlc.busmetro.ui.theme.networkColor
import vlc.transit.core.Arrival

fun lineColor(style: LineStyle?, network: String): Color = style?.color?.let { Color(it) } ?: networkColor(network)
fun lineTextColor(style: LineStyle?): Color = style?.textColor?.let { Color(it) } ?: Color.White

/** Small coloured badge with the line name, in the line's own colours. */
@Composable
fun LineChip(line: String, network: String, style: LineStyle?, modifier: Modifier = Modifier, big: Boolean = false) {
    val bg = lineColor(style, network)
    // Guard against a feed shipping an unreadable text colour.
    val fg = lineTextColor(style).takeIf { contrast(bg, it) > 2.5f } ?: if (bg.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier.defaultMinSize(minWidth = if (big) 44.dp else 34.dp, minHeight = if (big) 32.dp else 26.dp)
            .clip(RoundedCornerShape(if (big) 10.dp else 8.dp)).background(bg).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(line, color = fg, fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = if (big) 18.sp else 14.sp, maxLines = 1)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
private fun contrast(a: Color, b: Color): Float {
    val l1 = maxOf(a.luminance(), b.luminance()) + 0.05f
    val l2 = minOf(a.luminance(), b.luminance()) + 0.05f
    return l1 / l2
}

/** "3 min" with a pulsing-free, bold numeral; green when it comes from live data. */
@Composable
fun MinutesLabel(a: Arrival, modifier: Modifier = Modifier) {
    val live = a.realtime
    val color by animateColorAsState(if (a.canceled) Palette.Bad else if (live) Palette.Live else MaterialTheme.colorScheme.onSurface, label = "min")
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (a.canceled) {
            Text(stringResource(R.string.canceled), color = color, style = MaterialTheme.typography.titleMedium)
        } else if (a.minutes <= 0) {
            Text(stringResource(R.string.arriving), color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        } else if (a.minutes >= 60) {
            Text(clockOf(a), color = color, style = MaterialTheme.typography.titleLarge)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${a.minutes}", color = color, fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.min_short), color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        val d = a.delaySec
        if (live && d != null && kotlin.math.abs(d) >= 120) {
            val m = d / 60
            Text(if (m > 0) stringResource(R.string.delay_min, m) else stringResource(R.string.early_min, -m),
                color = if (m > 0) Palette.Warn else Palette.Teal, style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun clockOf(a: Arrival): String {
    val t = java.time.Instant.ofEpochSecond(a.epochSec ?: 0).atZone(vlc.transit.core.gtfs.VALENCIA_ZONE)
    return "%02d:%02d".format(t.hour, t.minute)
}

@Composable
fun ArrivalRow(a: Arrival, network: String, styles: Map<String, LineStyle>, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.a11y_arrival, a.line, a.headsign, if (a.minutes <= 0) stringResource(R.string.arriving) else "${a.minutes} min")
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LineChip(a.line, network, styles[a.line], big = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.headsign.ifEmpty { "—" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (a.realtime) stringResource(R.string.live) else stringResource(R.string.scheduled_at, clockOf(a)),
                style = MaterialTheme.typography.labelMedium, color = if (a.realtime) Palette.Live else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        MinutesLabel(a)
    }
}

@Composable
fun SourceBadge(source: BoardSource, liveFailed: Boolean, modifier: Modifier = Modifier) {
    val (text, color, icon) = when (source) {
        BoardSource.LIVE -> Triple(stringResource(R.string.src_live), Palette.Live, Icons.Filled.Sensors)
        BoardSource.SCHEDULE -> Triple(if (liveFailed) stringResource(R.string.src_schedule_nolive) else stringResource(R.string.src_schedule), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Schedule)
        BoardSource.APPROXIMATE -> Triple(stringResource(R.string.src_approx), Palette.Warn, Icons.Filled.EventRepeat)
        BoardSource.NO_DATA -> Triple(stringResource(R.string.src_none), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.CloudOff)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun NetworkIcon(network: String): ImageVector = when (network) {
    "metro" -> Icons.Filled.Subway
    "rail" -> Icons.Filled.Train
    "bike" -> Icons.AutoMirrored.Filled.DirectionsBike
    else -> Icons.Filled.DirectionsBus
}

@Composable
fun NetworkDot(network: String, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(networkColor(network).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(NetworkIcon(network), null, tint = networkColor(network), modifier = Modifier.size(size * 0.55f))
    }
}

/** Stop in a list: network dot, name, the lines serving it, optional distance. */
@Composable
fun StopRow(stop: StopEntity, distanceM: Double?, onClick: () -> Unit, modifier: Modifier = Modifier, styles: Map<String, Map<String, LineStyle>> = emptyMap(), trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        NetworkDot(stop.network)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val lines = stop.lines.split(',').filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                Text(lines.take(10).joinToString(" · ") + if (lines.size > 10) " …" else "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (distanceM != null) {
            Text(if (distanceM < 1000) "${distanceM.toInt()} m" else "%.1f km".format(distanceM / 1000), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), letterSpacing = 1.2.sp)
        trailing?.invoke()
    }
}

@Composable
fun Card2(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick, modifier, shape = shape, colors = colors, border = border) { Column(Modifier.padding(16.dp), content = content) }
    else Card(modifier, shape = shape, colors = colors, border = border) { Column(Modifier.padding(16.dp), content = content) }
}
