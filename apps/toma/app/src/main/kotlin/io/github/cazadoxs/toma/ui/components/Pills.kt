package io.github.cazadoxs.toma.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.cazadoxs.toma.data.DayDose
import io.github.cazadoxs.toma.data.DoseState
import io.github.cazadoxs.toma.data.MedForm
import io.github.cazadoxs.toma.ui.theme.LocalTomaColors

@Composable
fun swatch(index: Int): Color {
    val list = LocalTomaColors.current.swatches
    return list[Math.floorMod(index, list.size)]
}

/** A drawn tablet or capsule, or an icon in a tinted disc for the other forms. */
@Composable
fun PillIcon(form: MedForm, colorIndex: Int, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val color = swatch(colorIndex)
    val paper = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        when (form) {
            MedForm.PILL -> Canvas(Modifier.size(size * 0.62f)) {
                drawCircle(color)
                drawCircle(Color.White.copy(alpha = 0.25f), radius = this.size.minDimension * 0.36f, center = Offset(center.x - 3f, center.y - 3f))
                drawLine(paper.copy(alpha = 0.85f), Offset(this.size.width * 0.22f, center.y), Offset(this.size.width * 0.78f, center.y), strokeWidth = this.size.height * 0.07f)
            }
            MedForm.CAPSULE -> Canvas(Modifier.size(size * 0.7f).rotate(-35f)) {
                val h = this.size.height * 0.42f
                val top = (this.size.height - h) / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(color, topLeft = Offset(0f, top), size = Size(this.size.width, h), cornerRadius = r)
                drawRoundRect(paper, topLeft = Offset(this.size.width / 2, top), size = Size(this.size.width / 2, h), cornerRadius = r)
                drawRect(paper, topLeft = Offset(this.size.width / 2, top), size = Size(h / 2, h))
                drawRoundRect(color, topLeft = Offset(0f, top), size = Size(this.size.width, h), cornerRadius = r, style = Stroke(width = 3f))
            }
            else -> Icon(
                imageVector = when (form) {
                    MedForm.LIQUID -> Icons.Rounded.Science
                    MedForm.DROPS -> Icons.Rounded.WaterDrop
                    MedForm.INJECTION -> Icons.Rounded.Vaccines
                    MedForm.INHALER -> Icons.Rounded.Air
                    MedForm.CREAM -> Icons.Rounded.Spa
                    MedForm.PATCH -> Icons.Rounded.Healing
                    else -> Icons.Rounded.MedicalServices
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/**
 * Today's doses as the cells of a blister pack: each one pops out when taken. It's the
 * signature of the home screen and readable at a glance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlisterStrip(doses: List<DayDose>, description: String, modifier: Modifier = Modifier, cell: Dp = 30.dp) {
    val colors = LocalTomaColors.current
    val pulse by rememberInfiniteTransition(label = "due").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    FlowRow(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        doses.forEach { d ->
            val medColor = swatch(d.med.colorIndex)
            val fill by animateColorAsState(
                when (d.state) {
                    DoseState.TAKEN -> medColor
                    DoseState.MISSED -> colors.missed.copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
                label = "fill",
            )
            val ring = when (d.state) {
                DoseState.DUE -> colors.due.copy(alpha = pulse)
                DoseState.MISSED -> colors.missed
                DoseState.SKIPPED -> colors.skipped
                DoseState.TAKEN -> medColor
                DoseState.UPCOMING -> MaterialTheme.colorScheme.outline
            }
            Box(
                Modifier
                    .size(cell)
                    .clip(CircleShape)
                    .background(fill)
                    .border(if (d.state == DoseState.DUE) 3.dp else 2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (d.state) {
                    DoseState.TAKEN -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(cell * 0.6f))
                    DoseState.SKIPPED -> Icon(Icons.Rounded.Close, null, tint = colors.skipped, modifier = Modifier.size(cell * 0.55f))
                    DoseState.MISSED -> Icon(Icons.Rounded.PriorityHigh, null, tint = colors.missed, modifier = Modifier.size(cell * 0.55f))
                    else -> Unit
                }
            }
        }
    }
}
