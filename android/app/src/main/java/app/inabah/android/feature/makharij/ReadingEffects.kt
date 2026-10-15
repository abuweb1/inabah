package app.inabah.android.feature.makharij

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Живость «Учимся читать» (просьба пользователя 2026-10-15: «экраны бездушные»): вспышка звёздочек,
// пульсация, тряска. Только трансформации отрисовки (graphicsLayer, Canvas) - вёрстка не меняется.

/**
 * Вспышка звёздочек ✦ (рисуются, не шрифтом - «✦» нет во встроенных шрифтах): [count] звёзд разлетаются
 * от центра и гаснут за [durationMillis]. Перезапускается при смене [key].
 */
@Composable
fun SparkleBurst(
    key: Any?,
    color: Color,
    modifier: Modifier = Modifier,
    count: Int = SPARKLE_COUNT,
    durationMillis: Int = SPARKLE_MILLIS,
) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val radius = size.minDimension / 2 * (SPARKLE_START + (1 - SPARKLE_START) * t)
        val starSize = SparkleSize.toPx() * (1 - t * SPARKLE_SHRINK)
        val alpha = 1f - t
        repeat(count) { i ->
            val angle = (2 * Math.PI * i / count + SPARKLE_TWIST * t).toFloat()
            val center = Offset(size.width / 2 + radius * cos(angle), size.height / 2 + radius * sin(angle))
            drawSparkle(center, starSize, color.copy(alpha = color.alpha * alpha))
        }
    }
}

/** Четырёхлучевая звезда ✦ с центром [center]. */
fun DrawScope.drawSparkle(center: Offset, size: Float, color: Color) {
    val r = size / 2
    val waist = r * SPARKLE_WAIST
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + waist, center.y - waist, center.x + r, center.y)
        quadraticTo(center.x + waist, center.y + waist, center.x, center.y + r)
        quadraticTo(center.x - waist, center.y + waist, center.x - r, center.y)
        quadraticTo(center.x - waist, center.y - waist, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}

/** Мягкая пульсация 1 ↔ [scale] (текущий шаг, заголовок окна завершения). */
@Composable
fun Modifier.breathing(scale: Float = BREATH_SCALE, millis: Int = BREATH_MILLIS): Modifier {
    val transition = rememberInfiniteTransition(label = "breathing")
    val value by transition.animateFloat(
        initialValue = 1f,
        targetValue = scale,
        animationSpec = infiniteRepeatable(tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathingScale",
    )
    return graphicsLayer {
        scaleX = value
        scaleY = value
    }
}

/**
 * Толчок при каждом новом значении [trigger] (нажатие на слово, верный ответ): 1 → [scale] → 1.
 * Первое значение не толкает - только изменения.
 */
@Composable
fun Modifier.popOn(trigger: Any?, scale: Float = POP_SCALE): Modifier {
    val value = remember { Animatable(1f) }
    val last = remember { LastValue(trigger) }
    LaunchedEffect(trigger) {
        if (!last.changedTo(trigger)) return@LaunchedEffect
        value.animateTo(1f, keyframes {
            durationMillis = POP_MILLIS
            scale at POP_MILLIS / 3
            1f at POP_MILLIS
        })
    }
    return graphicsLayer {
        scaleX = value.value
        scaleY = value.value
    }
}

/** Тряска «нет» при каждом новом значении [trigger] (неверный ответ). */
@Composable
fun Modifier.shakeOn(trigger: Any?): Modifier {
    val offset = remember { Animatable(0f) }
    val last = remember { LastValue(trigger) }
    val amplitude = ShakeAmplitude
    LaunchedEffect(trigger) {
        if (!last.changedTo(trigger)) return@LaunchedEffect
        offset.animateTo(0f, keyframes {
            durationMillis = SHAKE_MILLIS
            -1f at SHAKE_MILLIS / 6
            1f at SHAKE_MILLIS * 2 / 6
            -0.7f at SHAKE_MILLIS * 3 / 6
            0.5f at SHAKE_MILLIS * 4 / 6
            -0.2f at SHAKE_MILLIS * 5 / 6
        })
    }
    return graphicsLayer { translationX = offset.value * amplitude.toPx() }
}

/** Прошлое значение триггера: эффект срабатывает только на изменение, не при первом показе. */
private class LastValue(private var value: Any?) {
    fun changedTo(new: Any?): Boolean {
        if (new == value) return false
        value = new
        return true
    }
}

private const val SPARKLE_COUNT = 8
private const val SPARKLE_MILLIS = 700
private const val SPARKLE_START = 0.35f
private const val SPARKLE_SHRINK = 0.5f
private const val SPARKLE_TWIST = 0.6
private const val SPARKLE_WAIST = 0.22f
private val SparkleSize = 10.dp
private const val BREATH_SCALE = 1.08f
private const val BREATH_MILLIS = 1_100
private const val POP_SCALE = 1.08f
private const val POP_MILLIS = 260
private const val SHAKE_MILLIS = 380
private val ShakeAmplitude = 8.dp
