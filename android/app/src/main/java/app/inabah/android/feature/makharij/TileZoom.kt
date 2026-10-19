package app.inabah.android.feature.makharij

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import kotlinx.coroutines.launch

/**
 * Увеличенная плитка (просьба пользователя 2026-10-17, «Читаем слова»; 2026-10-19 - и слова карточки буквы):
 * долгое нажатие - плитка вылетает из своего места [from] (в координатах корня) в центр и растёт, вокруг -
 * затемнение; по затемнению или «Назад» - возвращается на место, потом [onDismiss]. Полёт - только
 * трансформации отрисовки (`graphicsLayer`), без перевёрстки. [tile] - сама плитка: `contentScale` - поправка
 * масштаба её содержимого (слово растёт одинаково по ширине и высоте: от кегля [fromTextSize] до [textSize]),
 * `modifier` - размер и полёт. [key] - какое слово: другое - полёт заново.
 */
@Composable
internal fun TileZoomOverlay(
    key: Any,
    from: Rect,
    fromTextSize: Float,
    textSize: Float,
    onDismiss: () -> Unit,
    tile: @Composable (contentScale: () -> Offset, modifier: Modifier) -> Unit,
) {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    val scope = rememberCoroutineScope()
    val progress = remember(key) { Animatable(0f) }
    // Где окажется увеличенная плитка - от неё считается полёт из [from]. Пока не измерена, плитка не видна
    // и полёт не начинается: иначе первый кадр рисовался сразу в центре в полный размер.
    var target by remember { mutableStateOf(Rect.Zero) }
    val measured = target.width > 0f && target.height > 0f
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(key, measured) {
        if (measured && !closing) progress.animateTo(1f, Motion.zoom())
    }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, Motion.zoom())
                onDismiss()
            }
        }
    }
    BackHandler(onBack = dismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(palette.shadow.copy(alpha = ZOOM_SCRIM_ALPHA))
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        tile(
            {
                val p = progress.value
                val uniform = lerp(fromTextSize / textSize, 1f, p)
                val (sx, sy) = tileScale(target, from, p)
                Offset(uniform / sx, uniform / sy)
            },
            Modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                .height(ZoomTileHeight)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    if (bounds != target) target = bounds
                }
                .graphicsLayer {
                    val to = target
                    alpha = if (measured) 1f else 0f
                    if (measured && from.width > 0f) {
                        val p = progress.value
                        val (sx, sy) = tileScale(to, from, p)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = sx
                        scaleY = sy
                        translationX = lerp(from.left - to.left, 0f, p)
                        translationY = lerp(from.top - to.top, 0f, p)
                    }
                }
                // Фон плитки полупрозрачный (рассчитан на пергамент) - под увеличенной подложен пергамент.
                .background(gradients.parchment, RoundedCornerShape(Radius.box)),
        )
    }
}

/** Масштаб плитки в полёте по ширине и высоте: из [from] (исходная плитка) в [to] (увеличенная). */
private fun tileScale(to: Rect, from: Rect, progress: Float): Pair<Float, Float> =
    if (to.width > 0f && to.height > 0f && from.width > 0f) {
        lerp(from.width / to.width, 1f, progress) to lerp(from.height / to.height, 1f, progress)
    } else {
        1f to 1f
    }

/** Поправка масштаба содержимого плитки ([scale] - по ширине и высоте), вокруг [origin]; нет - без неё. */
internal fun Modifier.contentScale(scale: (() -> Offset)?, origin: TransformOrigin): Modifier =
    if (scale == null) this else graphicsLayer {
        val (x, y) = scale()
        transformOrigin = origin
        scaleX = x
        scaleY = y
    }

/**
 * Транскрипция под словом плитки (категория «интерфейс»; крупнее прежней `caption` - просьба пользователя
 * 2026-10-19). Не влезает в ширину плитки - уменьшается (не меньше `caption2`), не переносится
 * ([maxLines] - у словосочетаний 2). [zoomed] - под увеличенным словом крупнее. Прозрачная ([alpha] 0) - место
 * остаётся: плитки одной высоты, текст не прыгает.
 */
@Composable
internal fun TileTransliteration(
    text: String,
    alpha: Float,
    modifier: Modifier = Modifier,
    zoomed: Boolean = false,
    maxLines: Int = 1,
) {
    val style = if (zoomed) InabahType.title2 else InabahType.subheadline
    Text(
        text,
        color = readingColors.parchmentCaption,
        style = style,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        // Меньше прежнего кегля (`caption2`) транскрипция не становится.
        autoSize = TextAutoSize.StepBased(minFontSize = InabahType.caption2.fontSize, maxFontSize = style.fontSize),
        modifier = modifier.alpha(alpha),
    )
}

internal val ZoomTileHeight = 240.dp
private const val ZOOM_SCRIM_ALPHA = 0.6f
