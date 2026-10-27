package app.inabah.android.feature.qibla

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.rememberAnimationsEnabled
import app.inabah.android.core.qibla.QiblaPointer
import app.inabah.android.core.qibla.TurnSide
import kotlin.math.cos
import kotlin.math.sin

/** Кольцо - доля половины стороны: сверху остаётся место под указатель. */
private const val RING_FRACTION = 0.84f

/** Кааба - доля ширины кольца; чуть ниже центра (доля её высоты), как на макете. */
private const val KAABA_FRACTION = 0.44f
private const val KAABA_DROP = 0.05f

/** Деления: 72 через 5°; крупные - через 45°, средние - через 15° (набросок `docs/design/qibla.html`). */
private const val TICKS = 72
private const val TICK_STEP = 5
private const val MAJOR_EVERY = 45
private const val MID_EVERY = 15
private const val TICK_INSET = 0.05f
private const val MAJOR_LENGTH = 0.09f
private const val MID_LENGTH = 0.055f
private const val MINOR_LENGTH = 0.035f
private const val MID_ALPHA = 0.7f
private const val MINOR_ALPHA = 0.4f

/** Участок кольца «найдено» - ± порог выхода совпадения. */
private const val ALIGNED_HALF_ARC = 8f

/** Блик «ищем»: дуга, оборот за 2,4 с; без анимаций стоит под этим углом. */
private const val SCAN_ARC = 40f
private const val SCAN_MILLIS = 2400
private const val SCAN_STILL_ANGLE = 30f

private const val GLOW_ALPHA = 0.35f
private const val KAABA_GLOW_ALPHA = 0.22f

/** Свечение за Каабой - доля радиуса кольца. */
private const val KAABA_GLOW_RADIUS = 0.7f
private const val DIMMED_ALPHA = 0.35f
private const val FULL_TURN = 360f
private const val TOP = -90f

private val RingWidth = 1.6.dp
private val MajorWidth = 2.6.dp
private val MidWidth = 1.4.dp
private val MinorWidth = 1.2.dp
private val ArcWidth = 4.dp
private val ArcGlowWidth = 11.dp
private val ScanWidth = 2.8.dp
private val MarkerRadius = 8.dp
private val MarkerGlowRadius = 14.dp
private val PointerHalfWidth = 8.dp
private val PointerHeight = 13.dp
private val PointerGap = 6.dp

/**
 * Кольцо Къиблы (макеты `IMG_0230…0233`): Кааба неподвижна, белый указатель сверху - верх телефона,
 * золотая метка - направление на Каабу, дуга - кратчайший поворот к ней; при совпадении метка и участок
 * кольца зелёные. [pointer] читается только при отрисовке: показания датчика перерисовывают кольцо,
 * не перестраивая экран. [description] - всё кольцо одним элементом для TalkBack.
 */
@Composable
fun QiblaDial(
    pointer: () -> QiblaPointer?,
    aligned: Boolean,
    dimmed: Boolean,
    searching: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val alignment by animateFloatAsState(if (aligned) 1f else 0f, Motion.highlight(), label = "aligned")
    val markerAlpha by animateFloatAsState(if (dimmed) DIMMED_ALPHA else 1f, Motion.highlight(), label = "dimmed")
    val animate = rememberAnimationsEnabled()
    val scan = if (searching && animate) {
        val transition = rememberInfiniteTransition(label = "scan")
        transition.animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(SCAN_MILLIS, easing = LinearEasing), RepeatMode.Restart), label = "scan")
    } else {
        null
    }
    val gold = palette.gold
    val goldLight = palette.goldLight
    val green = palette.success
    val greenLight = palette.successLight
    val pointerColor = palette.onAccent
    val tickColor = palette.onAccent
    val glow = palette.tabAzkar
    Box(
        modifier
            .aspectRatio(1f)
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 * RING_FRACTION
            // Мягкое свечение за Каабой в тон раздела.
            val glowRadius = radius * KAABA_GLOW_RADIUS
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = KAABA_GLOW_ALPHA), Color.Transparent), center, glowRadius), glowRadius)
            drawCircle(gold, radius, style = Stroke(RingWidth.toPx()))
            drawTicks(radius, gold, tickColor)
            if (searching) {
                val start = TOP - SCAN_ARC + (scan?.value ?: SCAN_STILL_ANGLE)
                drawRingArc(radius, start, SCAN_ARC, goldLight.copy(alpha = GLOW_ALPHA), ArcGlowWidth.toPx())
                drawRingArc(radius, start, SCAN_ARC, goldLight, ScanWidth.toPx())
            }
            pointer()?.let { drawPointer(it, radius, alignment, markerAlpha, goldLight, gold, green, greenLight) }
            drawTopPointer(radius, pointerColor)
        }
        Image(
            painterResource(R.drawable.qibla_kaaba),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(KAABA_FRACTION * RING_FRACTION)
                .aspectRatio(1f)
                .graphicsLayer { translationY = size.height * KAABA_DROP },
        )
    }
}

private fun DrawScope.drawTicks(radius: Float, gold: Color, tick: Color) {
    for (i in 0 until TICKS) {
        val degrees = i * TICK_STEP
        val (length, width, color) = when {
            degrees % MAJOR_EVERY == 0 -> Triple(MAJOR_LENGTH, MajorWidth.toPx(), gold)
            degrees % MID_EVERY == 0 -> Triple(MID_LENGTH, MidWidth.toPx(), tick.copy(alpha = MID_ALPHA))
            else -> Triple(MINOR_LENGTH, MinorWidth.toPx(), tick.copy(alpha = MINOR_ALPHA))
        }
        val outer = radius * (1 - TICK_INSET)
        val inner = outer - radius * length
        drawLine(color, pointOn(degrees.toFloat(), outer), pointOn(degrees.toFloat(), inner), width, StrokeCap.Round)
    }
}

private fun DrawScope.drawPointer(
    pointer: QiblaPointer,
    radius: Float,
    alignment: Float,
    alpha: Float,
    goldLight: Color,
    gold: Color,
    green: Color,
    greenLight: Color,
) {
    val offset = pointer.offset.toFloat()
    // Дуга - в сторону поворота: около 180° сторона держится (QiblaTracker), дуга может пойти длинным путём.
    val sweep = when {
        pointer.side == TurnSide.Right && offset < 0 -> offset + FULL_TURN
        pointer.side == TurnSide.Left && offset > 0 -> offset - FULL_TURN
        else -> offset
    }
    if (alignment < 1f) {
        val arcColor = goldLight.copy(alpha = alpha * (1 - alignment))
        drawRingArc(radius, TOP, sweep, arcColor.copy(alpha = arcColor.alpha * GLOW_ALPHA), ArcGlowWidth.toPx())
        drawRingArc(radius, TOP, sweep, arcColor, ArcWidth.toPx())
    }
    if (alignment > 0f) {
        val okColor = green.copy(alpha = alignment)
        drawRingArc(radius, TOP - ALIGNED_HALF_ARC, ALIGNED_HALF_ARC * 2, okColor.copy(alpha = alignment * GLOW_ALPHA), ArcGlowWidth.toPx())
        drawRingArc(radius, TOP - ALIGNED_HALF_ARC, ALIGNED_HALF_ARC * 2, okColor, ArcWidth.toPx())
    }
    val marker = pointOn(offset, radius)
    val fill = lerp(gold, greenLight, alignment).copy(alpha = alpha)
    drawCircle(fill.copy(alpha = alpha * GLOW_ALPHA), MarkerGlowRadius.toPx(), marker)
    drawCircle(fill, MarkerRadius.toPx(), marker)
}

/** Белый треугольник над кольцом остриём вниз - верх телефона. */
private fun DrawScope.drawTopPointer(radius: Float, color: Color) {
    val tipY = center.y - radius - PointerGap.toPx()
    val baseY = tipY - PointerHeight.toPx()
    val half = PointerHalfWidth.toPx()
    val path = Path().apply {
        moveTo(center.x - half, baseY)
        lineTo(center.x + half, baseY)
        lineTo(center.x, tipY)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawRingArc(radius: Float, start: Float, sweep: Float, color: Color, width: Float) {
    if (sweep == 0f) return
    drawArc(
        color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width, cap = StrokeCap.Round),
    )
}

/** Точка на окружности: [degrees] по часовой стрелке от верха. */
private fun DrawScope.pointOn(degrees: Float, distance: Float): Offset {
    val angle = Math.toRadians(degrees.toDouble())
    return Offset(center.x + distance * sin(angle).toFloat(), center.y - distance * cos(angle).toFloat())
}
