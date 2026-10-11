package com.cazadoxs.escaneaycome.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cazadoxs.escaneaycome.core.Labels
import com.cazadoxs.escaneaycome.ui.theme.Bricolage
import com.cazadoxs.escaneaycome.ui.theme.NutriScoreColors

/** Warm rounded card used across the app. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/**
 * Energy ring. Past 100 % the extra wraps in a softer tone instead of turning red:
 * eating more than a target is information, not a failure.
 */
@Composable
fun EnergyRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    stroke: Dp = 18.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    center: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 2f), tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val over = MaterialTheme.colorScheme.tertiary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            drawArc(color, -90f, 360f * animated.coerceAtMost(1f), false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            if (animated > 1f) {
                drawArc(over, -90f, 360f * (animated - 1f), false, topLeft, arcSize, style = Stroke(s * 0.55f, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

@Composable
fun MacroBar(
    label: String,
    value: Double,
    goal: Int,
    color: Color,
    showNumbers: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = if (goal > 0) (value / goal).toFloat() else 0f
    val animated by animateFloatAsState(p.coerceIn(0f, 1f), tween(600), label = "macro")
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (showNumbers) {
                Text(
                    "${value.toInt()}/${goal} g",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Official-looking Nutri-Score strip with the product's grade enlarged. */
@Composable
fun NutriScore(grade: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F1F1))
            .padding(4.dp)
            .semantics { contentDescription = "Nutri-Score ${grade.uppercase()}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (g in listOf("a", "b", "c", "d", "e")) {
            val selected = g == grade
            Box(
                Modifier
                    .size(if (selected) 34.dp else 24.dp)
                    .clip(RoundedCornerShape(if (selected) 10.dp else 6.dp))
                    .background(NutriScoreColors.of(g).copy(alpha = if (selected) 1f else 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    g.uppercase(),
                    color = Color.White,
                    fontFamily = Bricolage,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (selected) 20.sp else 13.sp,
                )
            }
        }
    }
}

@Composable
fun NovaBadge(nova: Int, modifier: Modifier = Modifier) {
    val color = when (nova) {
        1 -> Color(0xFF038141)
        2 -> Color(0xFF85BB2F)
        3 -> Color(0xFFEE8100)
        else -> Color(0xFFE63E11)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text("$nova", color = Color.White, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("NOVA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Labels.novaText(nova), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, onColor: Color = Color.White) {
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = onColor,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun BigNumber(value: String, unit: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = color)
        Spacer(Modifier.width(4.dp))
        Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimals: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            val allowed = v.filter { it.isDigit() || (decimals && (it == ',' || it == '.')) }.take(7)
            onValue(allowed)
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 8.dp, bottom = 8.dp))
}
