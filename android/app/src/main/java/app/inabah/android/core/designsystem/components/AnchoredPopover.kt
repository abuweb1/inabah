package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Spacing

// Всплывающая карточка у элемента, как popover iOS 26 (выбор времени DatePicker, подтверждение
// confirmationDialog): вырастает из самого элемента и сжимается в него обратно (запись iOS
// IMG_9749); закрывается касанием мимо или «Назад».

/** Куда открывается карточка относительно элемента. */
enum class PopoverPlacement {
    /** Под элементом, по его правому краю (барабан времени у капсулы «17:00»). */
    BelowEnd,

    /** Над элементом, по центру (подтверждение над строкой «Сбросить»). */
    AboveCenter,
}

private val PopoverRadius = 34.dp
private val PopoverGap = 8.dp
private val ScreenMargin = 16.dp
private const val POPOVER_FILL_ALPHA = 0.94f
private const val POPOVER_RIM_ALPHA = 0.45f
private const val POPOVER_TINT = 0.18f

/** Масштаб карточки в начале появления — почти размер элемента, из которого она растёт. */
private const val START_SCALE = 0.2f
private const val CLOSE_MILLIS = 200

/** Появление — пружина с лёгким отскоком, как у popover iOS. */
private fun openSpec() = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * Карточка поверх экрана у элемента-якоря (Popup берёт координаты якоря — достаточно разместить
 * этот вызов внутри якоря). [expanded] — показана ли; после `false` карточка ещё доигрывает
 * закрытие и только потом уходит. Подложка — фон палитры с оттенком [tint], обводка — [tint].
 */
@Composable
fun AnchoredPopover(
    expanded: Boolean,
    placement: PopoverPlacement,
    tint: Color,
    onDismiss: () -> Unit,
    /** Уголок-указатель на элемент (подтверждение в iOS — с ним, барабан времени — без). */
    showsArrow: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 0 — сжата в элемент и прозрачна, 1 — раскрыта.
    val progress = remember { Animatable(0f) }
    var isOnScreen by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            isOnScreen = true
            progress.animateTo(1f, openSpec())
        } else if (isOnScreen) {
            progress.animateTo(0f, tween(CLOSE_MILLIS))
            isOnScreen = false
        }
    }
    if (!expanded && !isOnScreen) return

    val density = LocalDensity.current
    // Центр элемента в долях карточки — точка, из которой она растёт (может лежать вне карточки).
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    // С уголком карточка почти касается элемента остриём (между ними — только отступ под тень).
    val gap = if (showsArrow) 0.dp else PopoverGap
    val provider = remember(placement, density, gap) { AnchoredPositionProvider(placement, density, gap) { origin = it } }
    val palette = InabahTheme.palette
    val inset = with(density) { Spacing.xs.toPx() }
    // Уголок — со стороны элемента, напротив его центра; элемент ниже карточки — уголок снизу.
    val arrowEdge = when {
        !showsArrow -> null
        origin.pivotFractionY > 1f -> ArrowEdge.Bottom
        origin.pivotFractionY < 0f -> ArrowEdge.Top
        else -> null
    }
    val shape = remember(origin, arrowEdge, density) {
        PopoverShape(with(density) { PopoverRadius.toPx() }, arrowEdge, origin.pivotFractionX, inset, density)
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // Содержимое меряется сразу (в первом кадре невидимо) — позиция верна с первого кадра.
        Box(
            Modifier
                .graphicsLayer {
                    val p = progress.value
                    val scale = START_SCALE + (1f - START_SCALE) * p
                    scaleX = scale
                    scaleY = scale
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = origin
                }
                .padding(Spacing.xs)
                .surface(
                    // Стекло iOS над фоном раздела — фон, подкрашенный цветом раздела.
                    SolidColor(lerp(palette.background, tint, POPOVER_TINT).copy(alpha = POPOVER_FILL_ALPHA)),
                    shape,
                    border = tint.copy(alpha = POPOVER_RIM_ALPHA),
                    shadow = ShadowToken.floating(palette),
                )
                .padding(
                    top = if (showsArrow) ArrowHeight else 0.dp,
                    bottom = if (showsArrow) ArrowHeight else 0.dp,
                ),
        ) {
            // Всплывающие окна закреплены — не растут с шагом интерфейса.
            FixedTextSize(content)
        }
    }
}

private val ArrowWidth = 22.dp
private val ArrowHeight = 9.dp

private enum class ArrowEdge { Top, Bottom }

/**
 * Скруглённая карточка с уголком-указателем: под уголок оставлена полоса [ArrowHeight] сверху и снизу
 * (у той, где уголка нет, — просто отступ), уголок напротив центра элемента ([anchorFractionX] —
 * доля внешней ширины вместе с отступом [inset]).
 */
private class PopoverShape(
    private val radius: Float,
    private val arrowEdge: ArrowEdge?,
    private val anchorFractionX: Float,
    private val inset: Float,
    density: Density,
) : Shape {
    private val arrowWidth = with(density) { ArrowWidth.toPx() }
    private val arrowHeight = with(density) { ArrowHeight.toPx() }

    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (arrowEdge == null) {
            return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
        }
        val body = Path().apply {
            addRoundRect(RoundRect(0f, arrowHeight, size.width, size.height - arrowHeight, CornerRadius(radius)))
        }
        // Не заходить на скругления углов.
        val half = arrowWidth / 2
        val centerX = (anchorFractionX * (size.width + 2 * inset) - inset).coerceIn(radius + half, size.width - radius - half)
        val arrow = Path().apply {
            when (arrowEdge) {
                ArrowEdge.Bottom -> {
                    val base = size.height - arrowHeight - 1f
                    moveTo(centerX - half, base)
                    lineTo(centerX, size.height)
                    lineTo(centerX + half, base)
                }
                ArrowEdge.Top -> {
                    val base = arrowHeight + 1f
                    moveTo(centerX - half, base)
                    lineTo(centerX, 0f)
                    lineTo(centerX + half, base)
                }
            }
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, arrow))
    }
}

private class AnchoredPositionProvider(
    private val placement: PopoverPlacement,
    density: Density,
    gap: androidx.compose.ui.unit.Dp,
    private val onOrigin: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
    private val gap = with(density) { gap.roundToPx() }
    private val margin = with(density) { ScreenMargin.roundToPx() }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = when (placement) {
            PopoverPlacement.BelowEnd -> anchorBounds.right - popupContentSize.width
            PopoverPlacement.AboveCenter -> anchorBounds.center.x - popupContentSize.width / 2
        }
        val preferred = when (placement) {
            PopoverPlacement.BelowEnd -> anchorBounds.bottom + gap
            PopoverPlacement.AboveCenter -> anchorBounds.top - gap - popupContentSize.height
        }
        // Не помещается с выбранной стороны — по другую сторону элемента.
        val y = when {
            preferred < margin -> anchorBounds.bottom + gap
            preferred + popupContentSize.height > windowSize.height - margin -> anchorBounds.top - gap - popupContentSize.height
            else -> preferred
        }
        val position = IntOffset(
            x.coerceIn(margin, (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)),
            y.coerceAtLeast(margin),
        )
        if (popupContentSize.width > 0 && popupContentSize.height > 0) {
            onOrigin(
                TransformOrigin(
                    (anchorBounds.center.x - position.x).toFloat() / popupContentSize.width,
                    (anchorBounds.center.y - position.y).toFloat() / popupContentSize.height,
                ),
            )
        }
        return position
    }
}
