package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import kotlin.math.roundToInt

private val HintGap = 9.dp
private val HintArrow = 6.dp

/**
 * Пузырь подсказки у элемента [target] (живые ячейки «Учимся читать», кнопка «Къибла» на главной «Азкаров»):
 * над ним (нет места сверху - под ним), правым краем к правому краю элемента; стрелка - к его середине.
 * [target] и [host] - границы в корне окна; пузырь кладётся в [host] (родитель - во весь хозяин, касаний не
 * перехватывает). TalkBack прочитает его как вежливое объявление. [below] - сразу под элементом (кнопка у верхнего
 * края экрана: сверху пузырь ушёл бы под строку состояния).
 */
@Composable
fun HintBubble(text: String, target: Rect, host: Rect, fill: Color, textColor: Color, below: Boolean = false) {
    val gap = with(LocalDensity.current) { HintGap.toPx() }
    // Где стрелка (x в пузыре) и вниз ли она - считает раскладка, читает отрисовка.
    val arrow = remember { floatArrayOf(0f, 1f) }
    Text(
        text,
        color = textColor,
        style = InabahType.footnote,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, placeable.height) {
                    val x = (target.right - host.left - placeable.width).coerceIn(0f, (host.width - placeable.width).coerceAtLeast(0f))
                    val above = target.top - host.top - placeable.height - gap
                    val onTop = !below && above >= 0f
                    val y = if (onTop) above else target.bottom - host.top + gap
                    arrow[0] = (target.center.x - host.left - x).coerceIn(0f, placeable.width.toFloat())
                    arrow[1] = if (onTop) 1f else 0f
                    placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
                }
            }
            .drawBehind {
                val half = HintArrow.toPx()
                val (cx, down) = arrow[0].coerceIn(half + Radius.control.toPx(), size.width - half - Radius.control.toPx()) to (arrow[1] > 0f)
                val base = if (down) size.height else 0f
                val tip = if (down) size.height + half else -half
                drawPath(Path().apply { moveTo(cx - half, base); lineTo(cx + half, base); lineTo(cx, tip); close() }, fill)
            }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .surface(fill, Radius.control)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
    )
}
