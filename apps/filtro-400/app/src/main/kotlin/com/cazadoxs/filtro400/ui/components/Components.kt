package com.cazadoxs.filtro400.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cazadoxs.filtro400.core.CallAction
import com.cazadoxs.filtro400.ui.theme.LocalStatus
import com.cazadoxs.filtro400.ui.theme.Mono
import com.cazadoxs.filtro400.ui.theme.Palette

@Composable
fun actionColor(a: CallAction): Color = when (a) {
    CallAction.BLOCK -> LocalStatus.current.blocked
    CallAction.SILENCE -> LocalStatus.current.silenced
    CallAction.ALLOW -> LocalStatus.current.allowed
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Tarjeta plana con borde fino: la pieza básica de toda la interfaz. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent ?: MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** Selector de 3 posiciones: Bloquear / Silenciar / Dejar sonar. */
@Composable
fun ActionSelector(
    selected: CallAction,
    onSelect: (CallAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val options = listOf(CallAction.BLOCK to "Bloquear", CallAction.SILENCE to "Silenciar", CallAction.ALLOW to "Sonar")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (action, label) ->
            val isSel = action == selected
            val bg by animateColorAsState(if (isSel) actionColor(action) else Color.Transparent, label = "sel")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSel
                    }
                    .clickable(enabled = enabled) { onSelect(action) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSel) Palette.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Contador grande en monoespaciada. */
@Composable
fun BigCount(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Mono),
            color = color,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * El escudo de la pantalla principal: "400" tachado dentro de un escudo, con un
 * pulso lento mientras el filtro está activo.
 */
@Composable
fun ShieldHero(active: Boolean, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val status = LocalStatus.current
    val ink = MaterialTheme.colorScheme.background
    val main by animateColorAsState(if (active) status.blocked else status.muted, label = "shield")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = if (active) "Filtro activo" else "Filtro apagado" },
    ) {
        val w = this.size.width
        if (active) {
            val r = w * (0.36f + 0.16f * ring)
            drawCircle(main.copy(alpha = 0.22f * (1f - ring)), radius = r, center = center, style = Stroke(w * 0.012f))
        }
        val s = w / 108f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val shield = Path().apply {
            moveTo(54f * s, 22f * s)
            lineTo(81f * s, 31.5f * s)
            lineTo(81f * s, 53f * s)
            cubicTo(81f * s, 70f * s, 69f * s, 81.5f * s, 54f * s, 87f * s)
            cubicTo(39f * s, 81.5f * s, 27f * s, 70f * s, 27f * s, 53f * s)
            lineTo(27f * s, 31.5f * s)
            close()
        }
        drawPath(shield, main)
        val stroke = Stroke(width = 3.4f * s, cap = StrokeCap.Round)
        // 4
        drawPath(Path().apply { moveTo(43f * s, 62f * s); lineTo(43f * s, 46f * s); lineTo(36f * s, 57.5f * s); lineTo(46.5f * s, 57.5f * s) }, ink, style = stroke)
        // 0 0
        drawRoundRect(ink, topLeft = p(49f, 46f), size = Size(10f * s, 16f * s), cornerRadius = CornerRadius(5f * s), style = stroke)
        drawRoundRect(ink, topLeft = p(62f, 46f), size = Size(10f * s, 16f * s), cornerRadius = CornerRadius(5f * s), style = stroke)
        if (active) {
            drawLine(main, p(34f, 67f), p(75f, 41f), strokeWidth = 7.5f * s, cap = StrokeCap.Round)
            drawLine(ink, p(34f, 67f), p(75f, 41f), strokeWidth = 3.2f * s, cap = StrokeCap.Round)
        }
    }
}

/** Barras de la última semana. El día de hoy va resaltado. */
@Composable
fun WeekBars(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val accent = LocalStatus.current.blocked
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier.fillMaxWidth().height(120.dp).semantics {
            contentDescription = "Llamadas filtradas por día: " + values.joinToString()
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (v > 0) v.toString() else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((v.toFloat() / max).coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (i == values.lastIndex) accent else accent.copy(alpha = 0.35f).compositeOver(track)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    labels.getOrElse(i) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == values.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
fun IconBadge(color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp = 12.dp) = Spacer(Modifier.width(w))
