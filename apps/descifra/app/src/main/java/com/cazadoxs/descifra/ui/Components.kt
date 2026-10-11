package com.cazadoxs.descifra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cazadoxs.descifra.core.model.Urgency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)
private val dayYearFmt = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", es)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", es)

fun formatDay(date: LocalDate): String =
    (if (date.year == LocalDate.now().year) date.format(dayFmt) else date.format(dayYearFmt)).replaceFirstChar { it.uppercase() }

fun formatShort(date: LocalDate): String = date.format(shortFmt).replace(".", "")

fun daysLeft(date: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, date)

fun daysText(days: Long): String = when {
    days < -1 -> "hace ${-days} días"
    days == -1L -> "ayer"
    days == 0L -> "hoy"
    days == 1L -> "mañana"
    else -> "en $days días"
}

@Composable
fun urgencyColor(u: Urgency): Color {
    val p = MaterialTheme.palette
    return when (u) {
        Urgency.CRITICAL, Urgency.HIGH -> p.stamp
        Urgency.MEDIUM -> p.amber
        Urgency.LOW -> p.green
        Urgency.NONE -> p.inkSoft
    }
}

fun urgencyLabel(u: Urgency): String = when (u) {
    Urgency.CRITICAL -> "URGENTE"
    Urgency.HIGH -> "PRONTO"
    Urgency.MEDIUM -> "CON TIEMPO"
    Urgency.LOW -> "SIN PRISA"
    Urgency.NONE -> "SIN PLAZO"
}

/** A rubber-stamp look: double border, slightly rotated, letter-spaced caps. */
@Composable
fun Stamp(text: String, color: Color, modifier: Modifier = Modifier, angle: Float = -5f) {
    Box(
        modifier
            .rotate(angle)
            .border(BorderStroke(2.dp, color.copy(alpha = 0.9f)), RoundedCornerShape(4.dp))
            .padding(2.dp)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.55f)), RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "Urgencia: $text" },
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
    }
}

/** A sheet of paper with a thin border and a folded top-right corner. */
@Composable
fun Paper(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = MaterialTheme.palette
    val shape = RoundedCornerShape(6.dp)
    val base = modifier
        .clip(shape)
        .background(p.sheet)
        .border(1.dp, p.line, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Box(base) {
        Column(Modifier.padding(padding)) { content() }
        if (accent != null) Box(Modifier.align(Alignment.CenterStart).size(width = 4.dp, height = 44.dp).background(accent))
        Canvas(Modifier.align(Alignment.TopEnd).size(18.dp)) {
            val fold = Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); close()
            }
            drawPath(fold, p.paper)
            drawLine(p.line, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.5f)
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.palette.inkSoft) {
    Text(text.uppercase(), modifier, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .let { if (filled) it.background(color) else it.border(1.dp, color.copy(alpha = 0.7f), shape) }
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = if (filled) MaterialTheme.palette.onStamp else color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    color: Color = MaterialTheme.palette.ink,
    onColor: Color = MaterialTheme.palette.paper,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .let { if (filled) it.background(color.copy(alpha = alpha)) else it.border(1.5.dp, color.copy(alpha = alpha), shape) }
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { it(); androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp)) }
        Text(text, color = if (filled) onColor else color.copy(alpha = alpha), style = MaterialTheme.typography.labelLarge, fontSize = 17.sp)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(MaterialTheme.palette.line))
}

// Small modifier helpers shared by chips (keeps screen files readable).
fun Modifier.androidxClip(): Modifier = this.clip(RoundedCornerShape(50))
fun Modifier.androidxBg(color: Color): Modifier = this.background(color)
fun Modifier.androidxBorder(color: Color): Modifier = this.border(1.5.dp, color, RoundedCornerShape(50))
fun Modifier.androidxClickable(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
