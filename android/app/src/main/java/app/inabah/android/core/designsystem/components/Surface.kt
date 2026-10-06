package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size

/**
 * Скруглённая подложка (iOS `surface`): заливка, рамка внутрь фигуры, тень — **только у подложки**,
 * не у содержимого: меняющееся содержимое не заставляет пересчитывать размытие тени.
 */
fun Modifier.surface(
    fill: Brush,
    cornerRadius: Dp,
    border: Color? = null,
    lineWidth: Dp = Size.hairline,
    shadow: ShadowToken? = null,
): Modifier = surface(fill, RoundedCornerShape(cornerRadius), border, lineWidth, shadow)

fun Modifier.surface(
    fill: Color,
    cornerRadius: Dp,
    border: Color? = null,
    lineWidth: Dp = Size.hairline,
    shadow: ShadowToken? = null,
): Modifier = surface(SolidColor(fill), cornerRadius, border, lineWidth, shadow)

/** То же для фигуры с разными скруглениями (пергамент). */
fun Modifier.surface(
    fill: Brush,
    shape: Shape,
    border: Color? = null,
    lineWidth: Dp = Size.hairline,
    shadow: ShadowToken? = null,
): Modifier {
    val shadowed = if (shadow == null) {
        this
    } else {
        dropShadow(shape, Shadow(radius = shadow.radius, color = shadow.color, offset = DpOffset(0.dp, shadow.offsetY)))
    }
    val filled = shadowed.background(fill, shape)
    return if (border == null) filled else filled.border(lineWidth, border, shape)
}
