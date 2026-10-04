package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing

/** Центр радиального блика — доли ширины и высоты; радиус — dp (iOS `RadialGradient`). */
private const val GLOW_CENTER_X = 0.3f
private const val GLOW_CENTER_Y = 0.2f
private val GlowRadius = 180.dp


/**
 * Пергамент (iOS `ParchmentPanel`): градиент, радиальный блик, полоса 3 dp сверху, «✦» по углам,
 * рамка successDeep 2 dp. В карточке зикра — скругление сверху 18 (0 под свёрнутым заголовком),
 * снизу 0; на экране хадиса — все 18. Отступы содержимого: сверху 22, снизу 14, по бокам 22.
 */
@Composable
fun ParchmentPanel(
    modifier: Modifier = Modifier,
    topCornerRadius: Dp = Radius.card,
    bottomCornerRadius: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    val shape = RoundedCornerShape(
        topStart = topCornerRadius,
        topEnd = topCornerRadius,
        bottomStart = bottomCornerRadius,
        bottomEnd = bottomCornerRadius,
    )
    val glowRadius = with(LocalDensity.current) { GlowRadius.toPx() }
    val stripeHeight = with(LocalDensity.current) { Size.accentStripe.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawWithCache {
                // Кисть блика зависит от размера — создаётся при его смене, не на каждом кадре.
                val glow = Brush.radialGradient(
                    colors = listOf(palette.parchmentGlow, Color.Transparent),
                    center = Offset(size.width * GLOW_CENTER_X, size.height * GLOW_CENTER_Y),
                    radius = glowRadius,
                )
                onDrawBehind {
                    drawRect(gradients.parchment)
                    drawRect(glow)
                    drawRect(gradients.parchmentStripe, size = size.copy(height = stripeHeight))
                }
            }
            .border(Size.parchmentBorder, palette.successDeep, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xxl, bottom = Spacing.l),
            content = content,
        )
        // Поверх содержимого, как overlay в iOS.
        Ornaments(palette.successDeep)
    }
}

/** «✦» по четырём углам — вектором, размер не растёт с системным шрифтом (iOS — фиксированный кегль 11). */
@Composable
private fun BoxScope.Ornaments(color: Color) {
    for (alignment in listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)) {
        Sparkle(color, OrnamentSize, Modifier.align(alignment).padding(Spacing.s))
    }
}

/** Видимый размер «✦» кеглем 11 — сам знак занимает около 0,8 кегля. */
private val OrnamentSize = Size.ornament * 0.8f
