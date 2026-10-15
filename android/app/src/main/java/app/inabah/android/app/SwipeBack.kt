package app.inabah.android.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs

/**
 * Свайп «назад» как на iPhone (решение пользователя 2026-10-15): тянешь экран вправо - он уезжает за
 * пальцем, под ним виден предыдущий (та же анимация, что у системного жеста: `NavDisplay`
 * `predictivePopTransitionSpec`); отпустил дальше [COMPLETE_FRACTION] ширины или быстро - назад,
 * иначе - на место. Начинать можно в любом месте экрана: левый край на Android забирает системный жест.
 * Жест, забранный прокруткой или кнопкой внутри экрана, не наш. На экранах с горизонтальным листанием
 * свайп «назад» выключен совсем ([enabled], решение пользователя): иначе на первой странице свайп вправо
 * уводил бы с экрана. [enabled] - есть куда возвращаться и экран не листается вбок.
 */
@Composable
fun Modifier.swipeBack(enabled: Boolean): Modifier {
    val owner = LocalNavigationEventDispatcherOwner.current ?: return this
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(owner, input) {
        owner.navigationEventDispatcher.addInput(input)
        onDispose { owner.navigationEventDispatcher.removeInput(input) }
    }
    val isEnabled by rememberUpdatedState(enabled)
    val slop = LocalViewConfiguration.current.touchSlop
    return pointerInput(input) {
        val flingVelocity = FLING_VELOCITY.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!isEnabled) return@awaitEachGesture
            var started = false
            var progress = 0f
            var dx = 0f
            var dy = 0f
            val velocity = VelocityTracker()
            while (true) {
                // Основной проход - после детей: если жест забрало листание или прокрутка, он не наш.
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!started) {
                    if (change.isConsumed) break
                    val delta = change.positionChange()
                    dx += delta.x
                    dy += delta.y
                    if (abs(dx) > slop || abs(dy) > slop) {
                        // Только вправо и в основном по горизонтали.
                        if (dx <= 0f || abs(dx) < abs(dy) * HORIZONTAL_DOMINANCE) break
                        started = true
                        input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, change.position.x, change.position.y))
                    }
                }
                if (started) {
                    change.consume()
                    velocity.addPosition(change.uptimeMillis, change.position)
                    progress = ((change.position.x - down.position.x) / size.width).coerceIn(0f, 1f)
                    input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, progress, change.position.x, change.position.y))
                }
                if (!change.pressed) break
            }
            if (started) {
                val fling = velocity.calculateVelocity().x > flingVelocity
                if (progress > COMPLETE_FRACTION || fling) input.backCompleted() else input.backCancelled()
            }
        }
    }
}

private const val COMPLETE_FRACTION = 0.35f
private const val HORIZONTAL_DOMINANCE = 1.5f
private val FLING_VELOCITY = 600.dp
