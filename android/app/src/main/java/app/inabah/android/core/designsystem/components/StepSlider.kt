package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import kotlin.math.roundToInt

// Ползунок с засечками (iOS `StepSlider`, по образцу Telegram — docs/android/10-typography.md, 10.4):
// мелкая и крупная «А» по краям, дорожка с засечкой на каждом шаге, белый бегунок прилипает
// к ближайшему шагу прямо во время перетаскивания.

private val TrackHeight = 4.dp
private val TickWidth = 3.dp
private val TickHeight = 12.dp
private val ThumbSize = 26.dp

/** «А» по краям — фиксированного размера, не масштабируются. */
private const val SMALL_LETTER = 14f
private const val LARGE_LETTER = 26f

/** Доля шага, на которой засечка уже считается пройденной заливкой. */
private const val TICK_REACHED = 0.01f

/** Кириллическая «А» — подпись, не текст интерфейса. */
private const val LETTER = "А"

/**
 * Шаг под касанием [x] на дорожке шириной [width]: края дорожки отступают на радиус бегунка
 * [inset], шаги — на равных расстояниях, вне дорожки — крайний шаг.
 */
internal fun stepAt(x: Float, width: Float, inset: Float, count: Int): Int {
    if (count <= 1) return 0
    val usable = (width - 2 * inset).coerceAtLeast(1f)
    val step = usable / (count - 1)
    return ((x - inset) / step).roundToInt().coerceIn(0, count - 1)
}

/**
 * Ползунок на [count] шагов, выбран [index]; [label] — что настраивается, [valueTitle] — название
 * шага (для TalkBack — подпись и значение, смахивание меняет шаг). Тактильный щелчок — на каждой
 * смене шага; сам выбор сообщается [onSelect], только если шаг другой.
 */
@Composable
fun StepSlider(
    count: Int,
    index: Int,
    onSelect: (Int) -> Unit,
    label: String,
    valueTitle: String,
    modifier: Modifier = Modifier,
) = FixedTextSize {
    // Закреплён: «А» по краям — фиксированные, а смена шага интерфейса под пальцем не должна
    // менять плотность у самого ползунка (сбросила бы жест).
    val palette = InabahTheme.palette
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Последний выбранный шаг — сразу, не дожидаясь нового [index]: иначе следующее движение
    // в том же шаге щёлкнуло бы ещё раз. Внешняя смена [index] — после композиции.
    val selected = remember { intArrayOf(index) }
    SideEffect { selected[0] = index }
    val select = { target: Int ->
        val clamped = target.coerceIn(0, count - 1)
        if (clamped != selected[0]) {
            selected[0] = clamped
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            currentOnSelect(clamped)
        }
    }
    val last = (count - 1).coerceAtLeast(1)
    val position by animateFloatAsState(index.toFloat(), Motion.stepSnap(), label = "stepThumb")
    val letterColor = palette.onAccentSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.minTapTarget)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = valueTitle
                progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..last.toFloat(), steps = (count - 2).coerceAtLeast(0))
                setProgress { value ->
                    select(value.roundToInt())
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(LETTER, color = letterColor, style = InabahType.body.copy(fontSize = fixedSp(SMALL_LETTER)))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .heightIn(min = Size.minTapTarget),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val insetPx = with(density) { (ThumbSize / 2).toPx() }
            val stepPx = (widthPx - 2 * insetPx).coerceAtLeast(1f) / last
            val fill = palette.accentLight
            val track = palette.track
            val tickW = with(density) { TickWidth.toPx() }
            val tickH = with(density) { TickHeight.toPx() }
            val trackH = with(density) { TrackHeight.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.minTapTarget)
                    .pointerInput(count, widthPx) {
                        // Касание — сразу на шаг, перетаскивание — прилипает к ближайшему по ходу.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            select(stepAt(down.position.x, widthPx, insetPx, count))
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                select(stepAt(change.position.x, widthPx, insetPx, count))
                            }
                        }
                    }
                    .drawBehind {
                        val cy = size.height / 2
                        val start = insetPx
                        val end = size.width - insetPx
                        val radius = CornerRadius(trackH / 2)
                        drawRoundRect(track, Offset(start, cy - trackH / 2), androidx.compose.ui.geometry.Size(end - start, trackH), radius)
                        val filled = start + stepPx * position
                        drawRoundRect(fill, Offset(start, cy - trackH / 2), androidx.compose.ui.geometry.Size(filled - start, trackH), radius)
                        for (i in 0 until count) {
                            val x = start + stepPx * i
                            drawRoundRect(
                                // Засечка загорается, когда до неё дошла заливка.
                                if (i <= position + TICK_REACHED) fill else track,
                                Offset(x - tickW / 2, cy - tickH / 2),
                                androidx.compose.ui.geometry.Size(tickW, tickH),
                                CornerRadius(tickW / 2),
                            )
                        }
                    },
            )
            // Бегунок — только сдвигом отрисовки.
            Box(
                Modifier
                    .graphicsLayer { translationX = stepPx * position }
                    .size(ThumbSize)
                    .surface(palette.onAccent, ThumbSize / 2, shadow = ShadowToken.card(palette)),
            )
        }
        Text(LETTER, color = letterColor, style = InabahType.body.copy(fontSize = fixedSp(LARGE_LETTER)))
    }
}
