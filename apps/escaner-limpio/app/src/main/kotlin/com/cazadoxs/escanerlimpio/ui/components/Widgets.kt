package com.cazadoxs.escanerlimpio.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cazadoxs.escanerlimpio.classify.Category
import com.cazadoxs.escanerlimpio.data.DocEntry
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand
import com.cazadoxs.escanerlimpio.ui.theme.categoryColors
import com.cazadoxs.escanerlimpio.util.Format

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun CategoryChip(category: Category, modifier: Modifier = Modifier) {
    val (bg, fg) = categoryColors(category)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(category.label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        "PRO",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiary,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.tertiary).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** The "highlighter" underline used under key words in headings. */
fun Modifier.highlight(color: Color) = drawBehind {
    val h = size.height * 0.36f
    drawRect(color.copy(alpha = 0.55f), topLeft = Offset(-4f, size.height - h - size.height * 0.08f), size = androidx.compose.ui.geometry.Size(size.width + 8f, h))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocRow(
    doc: DocEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCategory: Boolean = true,
) {
    val selBorder = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, selBorder, RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 54.dp, height = 70.dp).paper(fold = 11.dp, radius = 8.dp, elevation = 3.dp)) {
            DocThumbnail(doc, Modifier.matchParentSize())
            if (selected) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (doc.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, "Favorito", tint = LocalBrand.current.highlighter, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(Format.docSubtitle(doc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showCategory) {
                Spacer(Modifier.height(6.dp))
                CategoryChip(doc.category)
            }
        }
    }
}

@Composable
fun CategoryTile(category: Category, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, fg) = categoryColors(category)
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(category.icon, null, tint = fg, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(category.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 doc." else "$count docs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Animated sheet with a highlighter scan line, shown while a document is processed. */
@Composable
fun ScanningSheet(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    val brand = LocalBrand.current
    Box(
        modifier
            .size(width = 64.dp, height = 84.dp)
            .paper(fold = 14.dp, radius = 8.dp, elevation = 4.dp)
            .drawBehind {
                val lineColor = brand.ink.copy(alpha = 0.18f)
                for (i in 0 until 6) {
                    val y = size.height * (0.28f + i * 0.1f)
                    drawLine(lineColor, Offset(size.width * 0.16f, y), Offset(size.width * (if (i % 2 == 0) 0.84f else 0.66f), y), strokeWidth = 3.dp.toPx())
                }
            }
            .drawWithScan(t, brand.highlighter),
    )
}

private fun Modifier.drawWithScan(t: Float, color: Color) = this.then(
    Modifier.drawBehind {
        val y = size.height * t
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.35f)), startY = y - size.height * 0.25f, endY = y),
            topLeft = Offset(0f, y - size.height * 0.25f),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.25f),
        )
        drawRect(color, topLeft = Offset(0f, y - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
    },
)

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

