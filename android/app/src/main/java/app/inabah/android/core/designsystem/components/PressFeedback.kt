package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import app.inabah.android.core.designsystem.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Отклик на нажатие вместо ряби Material (iOS PressScale/PressDim): только трансформация отрисовки,
// без изменения размеров. Значение анимации читается в слое при размещении — нажатие меняет
// отрисовку, а не композицию и не раскладку.

/** Скорость отклика: карточки и плитки — [Card] (180 мс), кнопки — [Control] (120 мс), [Instant] — сразу. */
enum class PressAnimation { Card, Control, Instant }

/**
 * Отклик на нажатие: масштаб [scale] и прозрачность [opacity] в нажатом состоянии
 * (значения — из `PressFeedback`; 1 — не меняется).
 */
fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    scale: Float = 1f,
    opacity: Float = 1f,
    animation: PressAnimation = PressAnimation.Control,
): Modifier = this then PressFeedbackElement(interactionSource, scale, opacity, animation)

private data class PressFeedbackElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float,
    val pressedAlpha: Float,
    val animation: PressAnimation,
) : ModifierNodeElement<PressFeedbackNode>() {
    override fun create() = PressFeedbackNode(interactionSource, pressedScale, pressedAlpha, animation)

    override fun update(node: PressFeedbackNode) {
        node.update(interactionSource, pressedScale, pressedAlpha, animation)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressFeedback"
        properties["scale"] = pressedScale
        properties["opacity"] = pressedAlpha
        properties["animation"] = animation
    }
}

private class PressFeedbackNode(
    private var interactionSource: InteractionSource,
    private var pressedScale: Float,
    private var pressedAlpha: Float,
    private var animation: PressAnimation,
) : Modifier.Node(), LayoutModifierNode {
    /** 0 — отпущено, 1 — нажато. */
    private val progress = Animatable(0f)
    private var collector: Job? = null

    override fun onAttach() {
        // Узел мог отсоединиться посреди анимации (элемент списка переиспользован) — начать с «отпущено».
        coroutineScope.launch { progress.snapTo(0f) }
        collect()
    }

    fun update(source: InteractionSource, scale: Float, alpha: Float, newAnimation: PressAnimation) {
        pressedScale = scale
        pressedAlpha = alpha
        animation = newAnimation
        if (source != interactionSource) {
            interactionSource = source
            collector?.cancel()
            collect()
        }
    }

    private fun collect() {
        collector = coroutineScope.launch {
            val presses = mutableSetOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val spec: AnimationSpec<Float> = when (animation) {
                    PressAnimation.Card -> Motion.cardPress()
                    PressAnimation.Control -> Motion.press()
                    PressAnimation.Instant -> snap()
                }
                launch { progress.animateTo(if (presses.isEmpty()) 0f else 1f, spec) }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val t = progress.value
                val scale = 1f + (pressedScale - 1f) * t
                scaleX = scale
                scaleY = scale
                alpha = 1f + (pressedAlpha - 1f) * t
            }
        }
    }
}
