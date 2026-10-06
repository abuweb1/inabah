package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.TextUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Значок отметки хадиса: «прочитан» — раскрытая книга, «выучен» — сердце со звездой. */
enum class StatusGlyphKind { Read, Memorized }

/** Обводка контурного значка — доля размера (iOS 7,5 %). */
private const val OUTLINE_WIDTH = 0.075f

/** Толщина строк книги — доля размера (iOS 5 %). */
private const val BOOK_LINE_WIDTH = 0.05f

/** Строки на страницах книги (y в единичном квадрате). */
private val BookLines = listOf(0.38f, 0.52f, 0.66f)

/** Звезда внутри сердца: сторона квадрата — доля размера, центр — (0,5; 0,46). */
private const val HEART_STAR_FRACTION = 0.38f
private const val HEART_STAR_CENTER_Y = 0.46f

/**
 * Значок статуса (iOS `StatusGlyph`), геометрия — в единичном квадрате, как в `StatusGlyph.swift`.
 * Залитый — отмечено (строки и звезда — вырезом), контурный — нет. [size] в sp: растёт с системным
 * шрифтом, как `@ScaledMetric`. Для TalkBack скрыт — статус озвучивает строка или кнопка.
 */
@Composable
fun StatusGlyph(
    kind: StatusGlyphKind,
    color: Color,
    size: TextUnit,
    modifier: Modifier = Modifier,
    isFilled: Boolean = true,
) {
    val side = with(LocalDensity.current) { size.toDp() }
    Canvas(
        modifier
            .size(side)
            // Вырез (BlendMode.Clear) — только в собственном слое, не в фоне под значком;
            // контурному вырез не нужен — без лишнего слоя (значки стоят в строках списков).
            .then(if (isFilled) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
            .clearAndSetSemantics {},
    ) {
        val outline = when (kind) {
            StatusGlyphKind.Read -> bookPages()
            StatusGlyphKind.Memorized -> heart()
        }
        val unit = min(this.size.width, this.size.height)
        if (isFilled) {
            drawPath(outline, color)
            drawDetail(kind, color, BlendMode.Clear)
        } else {
            drawPath(outline, color, style = Stroke(unit * OUTLINE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawDetail(kind, color, BlendMode.SrcOver)
        }
    }
}

private fun DrawScope.p(x: Float, y: Float) = Offset(x * size.width, y * size.height)

/** Две страницы с изогнутыми краями и зазором-корешком. */
private fun DrawScope.bookPages(): Path = Path().apply {
    for ((spine, edge, middle) in listOf(Triple(0.465f, 0.04f, 0.26f), Triple(0.535f, 0.96f, 0.74f))) {
        moveTo(p(spine, 0.26f))
        quadraticTo(p(middle, 0.11f), p(edge, 0.19f))
        lineTo(p(edge, 0.79f))
        quadraticTo(p(middle, 0.71f), p(spine, 0.88f))
        close()
    }
}

private fun DrawScope.heart(): Path = Path().apply {
    moveTo(p(0.5f, 0.90f))
    cubicTo(p(0.20f, 0.68f), p(0.05f, 0.55f), p(0.05f, 0.38f))
    cubicTo(p(0.05f, 0.22f), p(0.15f, 0.12f), p(0.28f, 0.12f))
    cubicTo(p(0.40f, 0.12f), p(0.47f, 0.18f), p(0.5f, 0.25f))
    cubicTo(p(0.53f, 0.18f), p(0.60f, 0.12f), p(0.72f, 0.12f))
    cubicTo(p(0.85f, 0.12f), p(0.95f, 0.22f), p(0.95f, 0.38f))
    cubicTo(p(0.95f, 0.55f), p(0.80f, 0.68f), p(0.5f, 0.90f))
    close()
}

/** Строки книги или звезда в сердце: вырезом ([BlendMode.Clear]) у залитого, цветом — у контурного. */
private fun DrawScope.drawDetail(kind: StatusGlyphKind, color: Color, blendMode: BlendMode) {
    val unit = min(size.width, size.height)
    when (kind) {
        StatusGlyphKind.Read -> {
            val lines = Path().apply {
                for (y in BookLines) {
                    moveTo(p(0.38f, y))
                    quadraticTo(p(0.26f, y - 0.06f), p(0.14f, y - 0.04f))
                    moveTo(p(0.62f, y))
                    quadraticTo(p(0.74f, y - 0.06f), p(0.86f, y - 0.04f))
                }
            }
            drawPath(lines, color, style = Stroke(unit * BOOK_LINE_WIDTH, cap = StrokeCap.Round), blendMode = blendMode)
        }
        StatusGlyphKind.Memorized -> {
            val side = unit * HEART_STAR_FRACTION
            drawPath(
                starPath(center = Offset(size.width / 2, size.height * HEART_STAR_CENTER_Y), outerRadius = side / 2),
                color,
                blendMode = blendMode,
            )
        }
    }
}

/** Внутренний радиус звезды — доля внешнего (iOS `StarShape`). */
private const val STAR_INNER_RATIO = 0.35f
private const val STAR_POINTS = 8

/** Восьмиконечная звезда (iOS `StarShape`): первая вершина вверху, внутренний радиус 0,35 внешнего. */
internal fun starPath(center: Offset, outerRadius: Float): Path = Path().apply {
    val vertices = STAR_POINTS * 2
    for (i in 0 until vertices) {
        val angle = i.toDouble() / vertices * 2 * PI - PI / 2
        val radius = if (i % 2 == 0) outerRadius else outerRadius * STAR_INNER_RATIO
        val point = Offset(center.x + (cos(angle) * radius).toFloat(), center.y + (sin(angle) * radius).toFloat())
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun Path.moveTo(point: Offset) = moveTo(point.x, point.y)
private fun Path.lineTo(point: Offset) = lineTo(point.x, point.y)
private fun Path.quadraticTo(control: Offset, end: Offset) = quadraticTo(control.x, control.y, end.x, end.y)
private fun Path.cubicTo(c1: Offset, c2: Offset, end: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
