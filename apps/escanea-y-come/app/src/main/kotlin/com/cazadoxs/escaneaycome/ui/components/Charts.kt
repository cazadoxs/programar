package com.cazadoxs.escaneaycome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Daily bars against a dashed target line. Bars above target use the tertiary tone, never red. */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    target: Double,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = true,
) {
    val primary = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if (values.isEmpty()) return@Canvas
            val max = maxOf(values.maxOrNull() ?: 0.0, target * 1.15, 1.0)
            val slot = size.width / values.size
            val barW = slot * 0.56f
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                val h = (v / max * size.height).toFloat()
                if (h > 0) {
                    val c = if (target > 0 && v > target * 1.1) over else primary
                    val alpha = if (highlightLast && i != values.lastIndex) 0.75f else 1f
                    drawRoundRect(c.copy(alpha = alpha), Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2))
                }
            }
            if (target > 0) {
                val y = (size.height - target / max * size.height).toFloat()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Raw points plus a smoothed trend line. */
@Composable
fun LineChart(points: List<Double>, trend: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val dot = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        if (points.size < 2) return@Canvas
        val all = points + trend
        val min = all.min() - 0.5
        val max = all.max() + 0.5
        val stepX = size.width / (points.size - 1)
        fun y(v: Double) = (size.height - (v - min) / (max - min) * size.height).toFloat()
        points.forEachIndexed { i, v -> drawCircle(dot, 5f, Offset(i * stepX, y(v))) }
        val path = Path()
        trend.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
        drawPath(path, color, style = Stroke(6f, cap = StrokeCap.Round))
    }
}
