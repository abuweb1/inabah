package app.inabah.android.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Spacing
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

private val CardMaxWidth = 360.dp
private val ScreenMargin = 24.dp
private val CardRadius = 34.dp

// Стекло окна (решение пользователя 2026-10-11): полупрозрачная карточка в цвет раздела, под ней — размытый экран.
/** Затемнение экрана под окном. */
private const val SCRIM_ALPHA = 0.35f

/** Доля цвета раздела в стекле и непрозрачность стекла: сквозь него видно размытый экран. */
private const val GLASS_TINT = 0.35f
private const val GLASS_ALPHA = 0.55f
private const val RIM_ALPHA = 0.6f

/** Без размытия (превью, тесты) стекло плотнее — иначе текст тонет в пёстром фоне. */
private const val GLASS_ALPHA_NO_BLUR = 0.9f

private val CardBlur = HazeBlurStyle {
    blurRadius(24.dp)
    noiseFactor(0.08f)
}

/** Появление — как у всплывающих окон: из чуть меньшего размера, с лёгким отскоком. */
private const val START_SCALE = 0.9f
private fun openSpec() = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * Окно-стекло по центру поверх экрана («Что нового», «Добавьте ещё азкаров»): затемнение, появление
 * с отскоком. Не системный диалог: размытие под карточкой берётся только из того же окна.
 * Касание мимо и «Назад» — [onDismiss]. [content] получает модификатор карточки ([GlassDialogCard]).
 */
@Composable
fun GlassDialogOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.(cardModifier: Modifier) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val palette = InabahTheme.palette
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, openSpec()) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center,
    ) {
        // Затемнение — отдельный слой под карточкой: касание мимо закрывает; для TalkBack это не элемент
        // (закрыть — кнопкой окна или жестом «Назад»), а карточку над ним он читает.
        Box(
            Modifier
                .matchParentSize()
                .background(palette.background.copy(alpha = SCRIM_ALPHA))
                // Касание без `clickable`: иначе для TalkBack — кнопка на весь экран без подписи.
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .clearAndSetSemantics {},
        )
        content(
            Modifier
                .padding(horizontal = ScreenMargin)
                .graphicsLayer {
                    val scale = START_SCALE + (1f - START_SCALE) * appear.value
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * Карточка окна-стекла — для превью и UI-тестов тоже: стекло в цвет [tint], под ним размытый [backdrop]
 * (`null` — без размытия, стекло плотнее). Закреплённый текст, как у всплывающих окон; [paneTitle] — для TalkBack.
 */
@Composable
fun GlassDialogCard(
    tint: Color,
    backdrop: HazeState?,
    paneTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = FixedTextSize {
    val palette = InabahTheme.palette
    val shape = RoundedCornerShape(CardRadius)
    val glass = lerp(palette.background, tint, GLASS_TINT)
        .copy(alpha = if (backdrop != null) GLASS_ALPHA else GLASS_ALPHA_NO_BLUR)
    Box(
        modifier
            .widthIn(max = CardMaxWidth)
            .fillMaxWidth()
            // Касания по карточке не доходят до затемнения под ней — окно не закрывают.
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics {
                isTraversalGroup = true
                this.paneTitle = paneTitle
            },
    ) {
        if (backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .hazeBlur(HazeInput.Sources(backdrop), CardBlur),
            )
        }
        Column(
            Modifier
                .surface(glass, CardRadius, border = tint.copy(alpha = RIM_ALPHA), shadow = ShadowToken.floating(palette))
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            content = content,
        )
    }
}
