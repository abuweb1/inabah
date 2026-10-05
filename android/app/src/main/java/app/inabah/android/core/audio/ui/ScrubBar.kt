package app.inabah.android.core.audio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.Size
import kotlinx.coroutines.flow.collectLatest

private val TouchZoneHeight = 30.dp
private const val THUMB_DRAG_SCALE = 1.5f

/** Бегунок увеличивается под пальцем быстро, без отскока (iOS snappy 0,15 с). */
private const val THUMB_STIFFNESS = 1500f
private const val NO_DRAG = -1f

/**
 * Полоса воспроизведения (iOS `ScrubBar`, `docs/android/05-design-system.md`, «Бегунок плеера»):
 * дорожка, золотая заливка и бегунок. Позиция [positionMs] читается только при отрисовке и между
 * отсчётами плеера (раз в 250 мс) едет линейно — лента не перерисовывается. Перемотка — при отпускании
 * ([onSeek]); во время перетаскивания [onScrub] сообщает время под пальцем (`null` — отпустили).
 */
@Composable
fun ScrubBar(
    position: StateFlow<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onScrub: (Long?) -> Unit,
    label: String,
    /** Значение для TalkBack по позиции: «0:11 из 0:45». */
    value: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val played = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Для TalkBack: полоса пересобирается раз в отсчёт; заливка и бегунок — только в отрисовке.
    val positionMs by position.collectAsStateWithLifecycle()
    var dragFraction by remember { mutableFloatStateOf(NO_DRAG) }
    var width by remember { mutableIntStateOf(0) }
    val duration = durationMs.coerceAtLeast(1)
    LaunchedEffect(position, duration) {
        // Поток, не snapshotFlow: позиция плеера — StateFlow, не состояние Compose.
        position.map { (it.toFloat() / duration).coerceIn(0f, 1f) }.collectLatest { target ->
            // Скачок назад (перемотка, новая запись) — сразу; вперёд — линейно до следующего отсчёта.
            if (target < played.value) played.snapTo(target) else played.animateTo(target, tween(TICK_MILLIS, easing = LinearEasing))
        }
    }
    val thumbScale by animateFloatAsState(
        if (dragFraction == NO_DRAG) 1f else THUMB_DRAG_SCALE,
        spring(stiffness = THUMB_STIFFNESS), label = "thumb",
    )
    fun shown(): Float = if (dragFraction == NO_DRAG) played.value else dragFraction
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TouchZoneHeight)
            .onSizeChanged { width = it.width }
            .pointerInput(duration) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun fractionAt(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    dragFraction = fractionAt(down.position.x)
                    onScrub((dragFraction * duration).toLong())
                    down.consume()
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            dragFraction = fractionAt(change.position.x)
                            onScrub((dragFraction * duration).toLong())
                            change.consume()
                        } else {
                            finished = true
                        }
                    }
                    val target = (dragFraction * duration).toLong()
                    // Полоса остаётся там, где отпустили, а не откатывается к старой позиции до отсчёта.
                    if (finished) {
                        val released = dragFraction
                        scope.launch { played.snapTo(released) }
                    }
                    dragFraction = NO_DRAG
                    onScrub(null)
                    if (finished) onSeek(target)
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                // Позиция последнего отсчёта (раз в 250 мс), не кадр анимации.
                stateDescription = value(positionMs)
                progressBarRangeInfo = ProgressBarRangeInfo((positionMs.toFloat() / duration).coerceIn(0f, 1f), 0f..1f)
                setProgress { fraction ->
                    onSeek((fraction.coerceIn(0f, 1f) * duration).toLong())
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(Size.progressBarHeight).background(palette.track, CircleShape))
        // Заливка и бегунок — только трансформациями отрисовки: ширина полосы меряется при изменении размера.
        Box(
            Modifier
                .fillMaxWidth()
                .height(Size.progressBarHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = shown()
                }
                .background(palette.gold, CircleShape),
        )
        Box(
            Modifier
                .size(Size.scrubThumb)
                .graphicsLayer {
                    translationX = shown() * width - Size.scrubThumb.toPx() / 2
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .background(palette.goldLight, CircleShape),
        )
    }
}

private const val TICK_MILLIS = 250
