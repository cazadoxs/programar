package com.cazadoxs.escanerlimpio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cazadoxs.escanerlimpio.ui.theme.LocalBrand

/** A sheet of paper with rounded corners and a dog-eared top-right corner. */
fun dogEarShape(fold: Dp, radius: Dp): Shape = GenericShape { size, density ->
    val f = with(density) { fold.toPx() }
    val r = with(density) { radius.toPx() }
    val rounded = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
    }
    val corner = Path().apply {
        moveTo(size.width - f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, f)
        close()
    }
    addPath(Path().apply { op(rounded, corner, androidx.compose.ui.graphics.PathOperation.Difference) })
}

/** Paper look: dog-eared shape, soft shadow and the folded flap drawn on top. */
@Composable
fun Modifier.paper(
    fold: Dp = 18.dp,
    radius: Dp = 14.dp,
    elevation: Dp = 2.dp,
    color: Color = LocalBrand.current.sheet,
): Modifier {
    val foldColor = LocalBrand.current.sheetFold
    val shape = dogEarShape(fold, radius)
    return this
        .shadow(elevation, shape, clip = false)
        .clip(shape)
        .background(color)
        .drawWithContent {
            drawContent()
            val f = fold.toPx()
            val flap = Path().apply {
                moveTo(size.width - f, 0f)
                lineTo(size.width - f, f * 0.78f)
                quadraticTo(size.width - f, f, size.width - f * 0.78f, f)
                lineTo(size.width, f)
                close()
            }
            drawPath(flap, foldColor)
            drawLine(Color.Black.copy(alpha = 0.06f), Offset(size.width - f, 0f), Offset(size.width, f), strokeWidth = 1.dp.toPx())
        }
}
