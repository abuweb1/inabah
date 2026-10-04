package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import app.inabah.android.core.designsystem.Motion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Сворачивание блока по высоте (iOS `collapsible`): блок **всегда в иерархии**, высота меняется
 * между натуральной и 0 с обрезкой, содержимое прижато к верху и не переверстывается во время
 * анимации. В свёрнутом виде скрыт для TalkBack. Анимация — [Motion.collapse]; в `Column`
 * соседи сдвигаются в тех же кадрах (одна «транзакция», docs/android/05, «Плавность»).
 *
 * При первом показе состояние ставится сразу, без анимации: выполненная карточка при возврате
 * на экран уже свёрнута. [animate] = false — смена без анимации.
 */
fun Modifier.collapsible(expanded: Boolean, animate: Boolean = true): Modifier =
    clipToBounds()
        .then(CollapsibleElement(expanded, animate))
        .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {})

private data class CollapsibleElement(val expanded: Boolean, val animate: Boolean) :
    ModifierNodeElement<CollapsibleNode>() {
    override fun create() = CollapsibleNode(expanded)

    override fun update(node: CollapsibleNode) = node.update(expanded, animate)

    override fun InspectorInfo.inspectableProperties() {
        name = "collapsible"
        properties["expanded"] = expanded
    }
}

private class CollapsibleNode(expanded: Boolean) : Modifier.Node(), LayoutModifierNode {
    /** Видимая доля натуральной высоты: 1 — раскрыт, 0 — свёрнут. */
    private val fraction = Animatable(if (expanded) 1f else 0f)
    private var expanded = expanded

    fun update(expanded: Boolean, animate: Boolean) {
        if (expanded == this.expanded) return
        this.expanded = expanded
        val target = if (expanded) 1f else 0f
        coroutineScope.launch {
            if (animate) fraction.animateTo(target, Motion.collapse()) else fraction.snapTo(target)
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        // Натуральная высота — без ограничения сверху (как fixedSize в iOS); видна её доля.
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val height = (placeable.height * fraction.value).roundToInt()
        return layout(placeable.width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeable.place(0, 0)
        }
    }
}
