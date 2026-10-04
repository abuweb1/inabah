package app.inabah.android.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Spacing

// Всплывающая карточка у элемента, как popover iOS 26 (выбор времени DatePicker, подтверждение
// confirmationDialog): появляется из точки привязки, закрывается касанием мимо или «Назад».

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
private const val APPEAR_SCALE = 0.85f

/**
 * Карточка поверх экрана у элемента-якоря (Popup пишет координаты якоря — достаточно разместить
 * этот вызов внутри якоря). Подложка — фон палитры почти непрозрачный, обводка цветом [tint].
 */
@Composable
fun AnchoredPopover(
    placement: PopoverPlacement,
    tint: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val provider = remember(placement, density) { AnchoredPositionProvider(placement, density) }
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    val palette = InabahTheme.palette
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val origin = when (placement) {
            PopoverPlacement.BelowEnd -> TransformOrigin(1f, 0f)
            PopoverPlacement.AboveCenter -> TransformOrigin(0.5f, 1f)
        }
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(Motion.press()) + scaleIn(Motion.highlight(), APPEAR_SCALE, origin),
            exit = fadeOut(Motion.press()) + scaleOut(Motion.press(), APPEAR_SCALE, origin),
        ) {
            Box(
                Modifier
                    .padding(Spacing.xs)
                    .surface(
                        // Стекло iOS над фоном раздела — фон, подкрашенный цветом раздела.
                        SolidColor(lerp(palette.background, tint, POPOVER_TINT).copy(alpha = POPOVER_FILL_ALPHA)),
                        RoundedCornerShape(PopoverRadius),
                        border = tint.copy(alpha = POPOVER_RIM_ALPHA),
                        shadow = ShadowToken.floating(palette),
                    ),
            ) { content() }
        }
    }
}

private class AnchoredPositionProvider(
    private val placement: PopoverPlacement,
    density: Density,
) : PopupPositionProvider {
    private val gap = with(density) { PopoverGap.roundToPx() }
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
        return IntOffset(
            x.coerceIn(margin, (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)),
            y.coerceAtLeast(margin),
        )
    }
}
