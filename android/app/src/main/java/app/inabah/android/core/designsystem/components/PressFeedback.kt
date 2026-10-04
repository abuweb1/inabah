package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
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

/**
 * Доля нажатия 0…1 для своих эффектов (стекло навбара, линза панели вкладок): то же правило, что у
 * [pressFeedback], — быстрое касание доигрывает нажатие до конца, отмена (прокрутка) — сразу назад.
 * Значение читать в фазе отрисовки (`graphicsLayer`, `drawBehind`), не в композиции.
 */
@Composable
fun rememberPressProgress(
    interactionSource: InteractionSource,
    press: AnimationSpec<Float> = Motion.press(),
    release: AnimationSpec<Float> = press,
): State<Float> {
    val progress = remember(interactionSource) { Animatable(0f) }
    LaunchedEffect(interactionSource) {
        val presses = mutableSetOf<PressInteraction.Press>()
        var running: Job? = null
        interactionSource.interactions.collect { interaction ->
            val released = when (interaction) {
                is PressInteraction.Press -> {
                    presses += interaction
                    false
                }
                is PressInteraction.Release -> {
                    presses -= interaction.press
                    true
                }
                is PressInteraction.Cancel -> {
                    presses -= interaction.press
                    false
                }
                else -> return@collect
            }
            running?.cancel()
            running = launch {
                when {
                    presses.isNotEmpty() -> progress.animateTo(1f, press)
                    released -> {
                        progress.animateTo(1f, press)
                        progress.animateTo(0f, release)
                    }
                    else -> progress.animateTo(0f, release)
                }
            }
        }
    }
    return progress.asState()
}

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
            var running: Job? = null
            interactionSource.interactions.collect { interaction ->
                val released = when (interaction) {
                    is PressInteraction.Press -> {
                        presses += interaction
                        false
                    }
                    is PressInteraction.Release -> {
                        presses -= interaction.press
                        true
                    }
                    is PressInteraction.Cancel -> {
                        presses -= interaction.press
                        false
                    }
                    else -> return@collect
                }
                val spec: AnimationSpec<Float> = when (animation) {
                    PressAnimation.Card -> Motion.cardPress()
                    PressAnimation.Control -> Motion.press()
                    PressAnimation.Instant -> snap()
                }
                running?.cancel()
                running = launch {
                    when {
                        presses.isNotEmpty() -> progress.animateTo(1f, spec)
                        // Быстрое касание: в прокрутке нажатие приходит с задержкой вместе с отпусканием —
                        // сначала доиграть сжатие, иначе отклика не видно. Отмена (это была прокрутка) — сразу назад.
                        released -> {
                            progress.animateTo(1f, spec)
                            progress.animateTo(0f, spec)
                        }
                        else -> progress.animateTo(0f, spec)
                    }
                }
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
