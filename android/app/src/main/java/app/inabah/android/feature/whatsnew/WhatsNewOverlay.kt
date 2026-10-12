package app.inabah.android.feature.whatsnew

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.PopoverActionButton
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.releases.ReleaseNotes
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

private val CardMaxWidth = 360.dp
private val ScreenMargin = 24.dp
private val CardRadius = 34.dp

/** Доля высоты экрана под пункты: длинный список прокручивается, «ОК» всегда видна. */
private const val ITEMS_MAX_HEIGHT_FRACTION = 0.5f
private const val BULLET = "•"

// Стекло окна (решение пользователя 2026-10-11): полупрозрачная карточка в цвет раздела, под ней — размытый экран.
/** Затемнение экрана под окном. */
private const val SCRIM_ALPHA = 0.35f

/** Доля цвета раздела в стекле и непрозрачность стекла: сквозь него видно размытый экран. */
private const val GLASS_TINT = 0.35f
private const val GLASS_ALPHA = 0.55f
private const val RIM_ALPHA = 0.6f

/** Ширина «ОК» — доля ширины карточки. */
private const val BUTTON_WIDTH_FRACTION = 0.55f


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
 * Окно «Что нового» после обновления (docs/android/06-screens.md): слой поверх всех вкладок, один раз.
 * Не системный диалог: размытие ([backdrop] — экран вкладки, как под панелью вкладок) берётся только
 * из того же окна. [tint] — цвет раздела, на котором окно появилось (в единой теме — цвет темы).
 * «ОК», касание мимо и «Назад» — одно и то же [onDismiss] (версия запоминается — `WhatsNew.dismiss`).
 */
@Composable
fun WhatsNewOverlay(notes: ReleaseNotes, tint: Color, backdrop: HazeState?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
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
        // (закрыть — «ОК» или жест «Назад»), а карточку над ним он читает.
        Box(
            Modifier
                .matchParentSize()
                .background(palette.background.copy(alpha = SCRIM_ALPHA))
                // Касание без `clickable`: иначе для TalkBack — кнопка на весь экран без подписи.
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .clearAndSetSemantics {},
        )
        val itemsMaxHeight = maxHeight * ITEMS_MAX_HEIGHT_FRACTION
        WhatsNewContent(
            notes,
            onDismiss,
            itemsMaxHeight,
            tint = tint,
            backdrop = backdrop,
            modifier = Modifier
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
 * Карточка окна — для превью и UI-тестов тоже. Закреплённый текст, как у всплывающих окон;
 * пункты выше [itemsMaxHeight] прокручиваются, «ОК» всегда видна. [backdrop] `null` — без размытия.
 */
@Composable
fun WhatsNewContent(
    notes: ReleaseNotes,
    onDismiss: () -> Unit,
    itemsMaxHeight: Dp,
    modifier: Modifier = Modifier,
    tint: Color = InabahTheme.palette.gold,
    backdrop: HazeState? = null,
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
                paneTitle = notes.version.text
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
        ) {
            Text(
                stringResource(R.string.whats_new_title, notes.version.text),
                color = palette.onAccent,
                style = InabahType.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Column(
                Modifier
                    .heightIn(max = itemsMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                notes.items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        // Маркер TalkBack не читает — только пункт.
                        Text(BULLET, color = tint, style = InabahType.callout, modifier = Modifier.clearAndSetSemantics {})
                        Text(item, color = palette.onAccent, style = InabahType.callout)
                    }
                }
            }
            // Как «Сбросить» в подтверждении (решение пользователя 2026-10-11), текст — в цвет раздела.
            // Уже карточки, по центру (решение пользователя 2026-10-11: во всю ширину — слишком растянута).
            PopoverActionButton(
                label = stringResource(R.string.whats_new_ok),
                color = tint,
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(BUTTON_WIDTH_FRACTION),
            )
        }
    }
}
