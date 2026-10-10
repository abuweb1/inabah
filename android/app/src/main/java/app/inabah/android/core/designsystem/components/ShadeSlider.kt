package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
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
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import kotlin.math.abs

// Плавный ползунок от −1 до +1 с серединой «как задумано» («Фон перевода», 2026-10-10). Вид — как у
// StepSlider: дорожка, белый бегунок; заливка — от середины к бегунку, у середины бегунок прилипает к 0.

private val TrackHeight = 4.dp
private val CenterTickWidth = 3.dp
private val CenterTickHeight = 12.dp
private val ThumbSize = 26.dp
private val EndSwatchSize = 16.dp

/** Насколько близко к середине бегунок прилипает к 0 — доля половины дорожки. */
internal const val SHADE_SNAP = 0.06f

/**
 * Значение под касанием [x] на дорожке шириной [width]: края отступают на радиус бегунка [inset];
 * вне дорожки — край, около середины ([SHADE_SNAP]) — ровно 0.
 */
internal fun shadeAt(x: Float, width: Float, inset: Float): Float {
    val usable = (width - 2 * inset).coerceAtLeast(1f)
    val value = (((x - inset) / usable) * 2f - 1f).coerceIn(-1f, 1f)
    return if (abs(value) < SHADE_SNAP) 0f else value
}

/**
 * Ползунок сдвига от −1 до +1, выбрано [value]. По краям — образцы цвета [startSwatch] и [endSwatch] (что будет
 * на краю). [onValueChange] — на каждое движение, [onValueChangeFinished] — палец отпущен; TalkBack меняет
 * значение смахиванием и сразу завершает. [label] — что настраивается, [valueTitle] — описание значения.
 * Тактильный щелчок — когда бегунок прилип к середине.
 */
@Composable
fun ShadeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    label: String,
    valueTitle: String,
    startSwatch: Color,
    endSwatch: Color,
    modifier: Modifier = Modifier,
) = FixedTextSize {
    // Закреплён, как StepSlider: смена шага интерфейса под пальцем не должна менять плотность и сбрасывать жест.
    val palette = InabahTheme.palette
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    // Последнее сообщённое значение — сразу, не дожидаясь нового [value]: щелчок у середины — один раз.
    val reported = remember { floatArrayOf(value) }
    SideEffect { reported[0] = value }
    val change = { target: Float ->
        val clamped = target.coerceIn(-1f, 1f)
        if (clamped != reported[0]) {
            if (clamped == 0f) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            reported[0] = clamped
            currentOnChange(clamped)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.minTapTarget)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = valueTitle
                progressBarRangeInfo = ProgressBarRangeInfo(value, -1f..1f)
                setProgress { target ->
                    change(if (abs(target) < SHADE_SNAP) 0f else target)
                    currentOnFinished()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Swatch(startSwatch)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .heightIn(min = Size.minTapTarget),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val insetPx = with(density) { (ThumbSize / 2).toPx() }
            val halfPx = (widthPx - 2 * insetPx).coerceAtLeast(1f) / 2
            val fill = palette.accentLight
            val track = palette.track
            val tickW = with(density) { CenterTickWidth.toPx() }
            val tickH = with(density) { CenterTickHeight.toPx() }
            val trackH = with(density) { TrackHeight.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.minTapTarget)
                    .pointerInput(widthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            // Завершение — и при отмене жеста (экран ушёл посреди перетаскивания): черновик не теряется.
                            try {
                                change(shadeAt(down.position.x, widthPx, insetPx))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!pointer.pressed) break
                                    pointer.consume()
                                    change(shadeAt(pointer.position.x, widthPx, insetPx))
                                }
                            } finally {
                                currentOnFinished()
                            }
                        }
                    }
                    .drawBehind {
                        val cy = size.height / 2
                        val start = insetPx
                        val center = insetPx + halfPx
                        val radius = CornerRadius(trackH / 2)
                        drawRoundRect(track, Offset(start, cy - trackH / 2), androidx.compose.ui.geometry.Size(halfPx * 2, trackH), radius)
                        val thumbX = center + halfPx * value
                        drawRoundRect(
                            fill,
                            Offset(minOf(center, thumbX), cy - trackH / 2),
                            androidx.compose.ui.geometry.Size(abs(thumbX - center), trackH),
                            radius,
                        )
                        drawRoundRect(
                            if (value == 0f) fill else track,
                            Offset(center - tickW / 2, cy - tickH / 2),
                            androidx.compose.ui.geometry.Size(tickW, tickH),
                            CornerRadius(tickW / 2),
                        )
                    },
            )
            // Бегунок — только сдвигом отрисовки.
            Box(
                Modifier
                    .graphicsLayer { translationX = halfPx * (value + 1f) }
                    .size(ThumbSize)
                    .surface(palette.onAccent, ThumbSize / 2, shadow = ShadowToken.card(palette)),
            )
        }
        Swatch(endSwatch)
    }
}

/** Образец цвета на краю — кружок с тонкой рамкой (тёмный край не сливается с фоном группы). */
@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(EndSwatchSize).surface(color, EndSwatchSize / 2, border = InabahTheme.palette.hairline))
}
