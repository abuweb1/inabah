package app.inabah.android.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.monospacedDigits
import app.inabah.android.core.formatting.formatPercent
import kotlin.math.roundToInt

/** Толщина дуги по умолчанию (iOS `ProgressRing.lineWidth`). */
private val DefaultRingWidth = 4.dp

/** Наибольший масштаб шрифта процента в кольце (iOS — до `.xxxLarge`, ≈ 1,35). */
private const val RING_MAX_FONT_SCALE = 1.35f

/** До полного выполнения — не больше 99 %: «100 %» без галочки сбивало бы с толку. */
private const val RING_MAX_PERCENT_BEFORE_DONE = 99

private const val FULL_CIRCLE = 360f
private const val TOP = -90f

/**
 * Кольцо прогресса (iOS `ProgressRing`): дорожка и дуга с круглыми концами от верха по часовой.
 * Дуга внутри фигуры (отступ — половина толщины), изменение — 350 мс easeOut. Для TalkBack скрыто:
 * значение озвучивает тот, кто показывает кольцо.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    lineWidth: Dp = DefaultRingWidth,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.counterRing(), label = "ring")
    Canvas(modifier.clearAndSetSemantics {}) {
        val stroke = lineWidth.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        drawArc(trackColor, 0f, FULL_CIRCLE, useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(stroke))
        if (animated > 0f) {
            drawArc(fillColor, TOP, FULL_CIRCLE * animated, useCenter = false, topLeft = Offset(inset, inset),
                size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Цвета кольца карточки главной: утренние — дорожка track, заливка sunRays; вечерние — золото. */
@Immutable
data class NavCardRingStyle(val track: Color, val fill: Color, val text: Color)

/**
 * Кольцо 44 dp на карточке главной (iOS `NavCardProgressRing`): процент или галочка при выполнении.
 * Процент не растёт со шрифтом выше «очень крупного»; для TalkBack — один элемент.
 */
@Composable
fun NavCardProgressRing(
    fraction: Double,
    style: NavCardRingStyle,
    modifier: Modifier = Modifier,
) {
    val percent = navRingPercent(fraction)
    val isDone = percent == null
    val description = if (isDone) {
        stringResource(R.string.section_progress_done)
    } else {
        stringResource(R.string.section_progress_percent, percent)
    }
    Box(
        modifier = modifier
            .size(Size.visibleTapTarget)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        ProgressRing(fraction.toFloat(), style.track, style.fill, Modifier.fillMaxSize(), lineWidth = Size.ringStroke)
        AnimatedContent(
            targetState = percent,
            transitionSpec = { (fadeIn(Motion.highlight()) + scaleIn(Motion.highlight())) togetherWith fadeOut(Motion.highlight()) },
            label = "ringLabel",
        ) { value ->
            if (value == null) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = style.fill,
                    modifier = Modifier.size(RingCheckSize))
            } else {
                // Растёт со шрифтом, но не больше ×1,35: сначала обычный sp, затем потолок в dp.
                val density = LocalDensity.current
                val scaled = with(density) { InabahType.caption2.fontSize.toDp() }
                val cap = (InabahType.caption2.fontSize.value * RING_MAX_FONT_SCALE).dp
                val cappedSize = with(density) { minOf(scaled, cap).toSp() }
                val locale = LocalConfiguration.current.locales[0]
                Text(
                    text = formatPercent(value, locale),
                    color = style.text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = InabahType.caption2.monospacedDigits().copy(fontWeight = FontWeight.Bold, fontSize = cappedSize),
                )
            }
        }
    }
}

private val RingCheckSize = 18.dp

/**
 * Процент кольца карточки (iOS `NavCardProgressRing`): `null` — выполнено (галочка); иначе округление
 * до ближайшего, не больше 99. Считается в Double, как в iOS: во Float 21/40 давало бы 52 вместо 53.
 */
internal fun navRingPercent(fraction: Double): Int? {
    if (fraction >= 1.0) return null
    if (fraction.isNaN()) return 0
    return (fraction.coerceAtLeast(0.0) * 100).roundToInt().coerceAtMost(RING_MAX_PERCENT_BEFORE_DONE)
}

/**
 * Полоса прогресса (iOS `LinearProgressBar`): капсула высотой 4 dp, дорожка и заливка;
 * изменение — 500 мс. Для TalkBack скрыта. Ширина заливки меняется в отрисовке, не в раскладке.
 */
@Composable
fun LinearProgressBar(
    fraction: Float,
    track: Color,
    fill: Brush,
    modifier: Modifier = Modifier,
    height: Dp = Size.progressBarHeight,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.progress(), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {}
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val width = size.width * animated
                if (width > 0f) {
                    // Градиент — по заполненной капсуле, как в iOS: кисть берёт размер области рисования.
                    inset(left = 0f, top = 0f, right = size.width - width, bottom = 0f) {
                        drawRoundRect(fill, cornerRadius = radius)
                    }
                }
            },
    )
}

@Composable
fun LinearProgressBar(
    fraction: Float,
    track: Color,
    fill: Color,
    modifier: Modifier = Modifier,
    height: Dp = Size.progressBarHeight,
) = LinearProgressBar(fraction, track, SolidColor(fill), modifier, height)
