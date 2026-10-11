package app.apunta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.apunta.data.SearchQuery
import app.apunta.ui.theme.Apunta

/** Live level bars; newest on the right. */
@Composable
fun Waveform(levels: List<Float>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        val n = 60
        val gap = size.width / n
        val barW = gap * 0.55f
        val mid = size.height / 2
        val padded = List(n - levels.size.coerceAtMost(n)) { 0f } + levels.takeLast(n)
        padded.forEachIndexed { i, l ->
            val h = (kotlin.math.sqrt(l.coerceIn(0f, 1f)) * size.height).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * (i / n.toFloat())),
                topLeft = Offset(i * gap + (gap - barW) / 2, mid - h / 2),
                size = Size(barW, h), cornerRadius = CornerRadius(barW / 2),
            )
        }
    }
}

/** Highlights query words (accent- and case-insensitive) with the yellow highlighter. */
@Composable
fun highlighted(text: String, query: String): AnnotatedString {
    val ranges = SearchQuery.highlightRanges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    val bg = Apunta.colors.highlighter
    val fg = Apunta.colors.ink
    return buildAnnotatedString {
        var pos = 0
        for (r in ranges) {
            val s = r.first.coerceIn(0, text.length); val e = (r.last + 1).coerceIn(0, text.length)
            if (s < pos) continue
            append(text.substring(pos, s))
            withStyle(SpanStyle(background = bg, color = fg)) { append(text.substring(s, e)) }
            pos = e
        }
        append(text.substring(pos))
    }
}

/** Ruled-notebook background lines behind lists. */
@Composable
fun Modifier.ruled(): Modifier {
    val line = Apunta.colors.ruled
    val margin = Apunta.colors.margin.copy(alpha = 0.35f)
    return this.then(Modifier.drawBehindRuled(line, margin))
}

private fun Modifier.drawBehindRuled(line: Color, margin: Color): Modifier =
    this.then(androidx.compose.ui.draw.drawBehind {
        val step = 32.dp.toPx()
        var y = step
        while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
        drawLine(margin, Offset(40.dp.toPx(), 0f), Offset(40.dp.toPx(), size.height), 1.5.dp.toPx())
    })

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
fun Bars(value: Int, max: Int = 5, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { i ->
            Box(
                Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i < value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}
