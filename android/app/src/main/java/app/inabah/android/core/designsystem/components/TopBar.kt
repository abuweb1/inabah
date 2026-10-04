package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing

// Навбар как в iOS 26 (решение пользователя 2026-10-04): «стеклянная» круглая «‹» слева,
// заголовок и подзаголовок по центру, капсула действий справа. Размытия нет — подложки
// полупрозрачные цветом раздела ([tint]), как у панели вкладок.

private val BarHeight = 56.dp
private val GlassButtonSize = 46.dp
private val GlassIconSize = 26.dp

/** Сбоку от заголовка — место под кнопки, чтобы длинный заголовок не наезжал на них. */
private val SideSlotWidth = 104.dp

private const val GLASS_FILL_ALPHA = 0.2f
private const val GLASS_RIM_ALPHA = 0.4f

/** «Стекло» навбара: полупрозрачная подложка и обводка цветом раздела. */
fun Modifier.glass(tint: Color, shape: Shape = CircleShape): Modifier =
    surface(SolidColor(tint.copy(alpha = GLASS_FILL_ALPHA)), shape, border = tint.copy(alpha = GLASS_RIM_ALPHA))

// Нажатие на стекло iOS 26 (запись пользователя IMG_9751): кнопка увеличивается и заливается
// ярким светом в тон раздела — свет ярче и насыщеннее самого цвета, с уходом оттенка к пурпуру
// (у фиолетового раздела — розово-малиновый, как в iOS); у капсулы «А− А+» — со стороны нажатой половины.

private const val GLASS_PRESS_SCALE = 1.15f
private const val CAPSULE_PRESS_SCALE = 1.06f
private const val GLOW_HUE_SHIFT = 40f
private const val GLOW_MIN_SATURATION = 0.6f
private const val GLOW_EDGE_ALPHA = 0.7f

/** Нажатие — пружина с небольшим отскоком, отпускание — плавно. */
private fun glassPressSpec() = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
private fun glassReleaseSpec() = tween<Float>(durationMillis = 300)

/** Цвет света нажатого стекла из цвета раздела [tint]: оттенок сдвинут, насыщенность и яркость подняты. */
internal fun glassHighlight(tint: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
    hsv[0] = (hsv[0] + GLOW_HUE_SHIFT) % FULL_TURN
    hsv[1] = maxOf(hsv[1], GLOW_MIN_SATURATION)
    hsv[2] = 1f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private const val FULL_TURN = 360f

/** Свет из точки [center] (доли размера) с непрозрачностью [alpha]; рисовать поверх подложки, под содержимым. */
private fun DrawScope.drawGlassGlow(glow: Color, alpha: Float, shape: Shape, centerX: Float = 0.5f) {
    if (alpha <= 0f) return
    val brush = Brush.radialGradient(
        listOf(glow, glow.copy(alpha = GLOW_EDGE_ALPHA)),
        center = Offset(size.width * centerX, size.height / 2),
        radius = maxOf(size.width, size.height) * 0.75f,
    )
    drawOutline(shape.createOutline(size, layoutDirection, this), brush, alpha = alpha.coerceIn(0f, 1f))
}

/**
 * Навбар экрана: фон [background] до верха экрана (под строкой состояния; `null` — прозрачный,
 * как в настройках), «‹» [onBack], заголовок [title] и необязательный [subtitle] по центру,
 * справа — [actions]. Заголовок для TalkBack — один заголовок.
 */
@Composable
fun InabahTopBar(
    title: String,
    tint: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    background: Color? = null,
    /**
     * Заголовок по центру (как в iOS) или сразу за «‹» — когда справа широкие действия
     * («А− А+»): на узком экране центрированный заголовок наезжал на них (снимок с телефона).
     */
    centerTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = InabahTheme.palette
    val titleBlock: @Composable (Modifier, Alignment.Horizontal, TextAlign) -> Unit = { titleModifier, alignment, textAlign ->
        Column(titleModifier.semantics(mergeDescendants = true) { heading() }, horizontalAlignment = alignment) {
            Text(
                title,
                color = palette.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                style = InabahType.headline.copy(fontWeight = FontWeight.Bold),
            )
            subtitle?.invoke()
        }
    }
    val back = @Composable { backModifier: Modifier ->
        GlassIconButton(
            onClick = onBack,
            iconRes = R.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.common_back),
            tint = tint,
            modifier = backModifier,
        )
    }
    val barModifier = modifier
        .fillMaxWidth()
        .then(if (background != null) Modifier.background(background) else Modifier)
        .windowInsetsPadding(WindowInsets.statusBars)
        .height(BarHeight)
        .padding(horizontal = Spacing.xl)
    if (centerTitle) {
        Box(barModifier) {
            back(Modifier.align(Alignment.CenterStart))
            titleBlock(Modifier.align(Alignment.Center).padding(horizontal = SideSlotWidth / 2),
                Alignment.CenterHorizontally, TextAlign.Center)
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    } else {
        Row(barModifier, verticalAlignment = Alignment.CenterVertically) {
            back(Modifier)
            titleBlock(Modifier.weight(1f).padding(horizontal = Spacing.s), Alignment.Start, TextAlign.Start)
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Подзаголовок навбара (caption2, onAccentSecondary). */
@Composable
fun TopBarSubtitle(text: String) {
    Text(text, color = InabahTheme.palette.onAccentSecondary, maxLines = 1, style = InabahType.caption2)
}

/** Круглая «стеклянная» кнопка навбара: значок 26 в круге 46 (зона касания 48). */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = rememberPressProgress(interaction, glassPressSpec(), glassReleaseSpec())
    val glow = glassHighlight(tint)
    Box(
        modifier = modifier
            .size(Size.minTapTarget)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else PressFeedback.DISABLED_OPACITY),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val scale = 1f + (GLASS_PRESS_SCALE - 1f) * pressed.value
                    scaleX = scale
                    scaleY = scale
                }
                .size(GlassButtonSize)
                .glass(tint)
                .drawBehind { drawGlassGlow(glow, pressed.value, CircleShape) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), contentDescription, tint = InabahTheme.palette.onAccent,
                modifier = Modifier.size(GlassIconSize))
        }
    }
}

/**
 * «А−  А+» в одной стеклянной капсуле (iOS `FontSizeControls`): кегль арабского текста,
 * кнопки неактивны на границах, для TalkBack — длинные подписи.
 */
@Composable
fun FontSizeControls(
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val decrease = remember { MutableInteractionSource() }
    val increase = remember { MutableInteractionSource() }
    val decreasePressed = rememberPressProgress(decrease, glassPressSpec(), glassReleaseSpec())
    val increasePressed = rememberPressProgress(increase, glassPressSpec(), glassReleaseSpec())
    val glow = glassHighlight(tint)
    Row(
        modifier = modifier
            .graphicsLayer {
                // Капсула лишь чуть подаётся вперёд — меньше круглой кнопки.
                val scale = 1f + (CAPSULE_PRESS_SCALE - 1f) * maxOf(decreasePressed.value, increasePressed.value)
                scaleX = scale
                scaleY = scale
            }
            .height(GlassButtonSize)
            .glass(tint, CircleShape)
            .drawBehind {
                drawSideGlow(glow, decreasePressed.value, fromStart = true)
                drawSideGlow(glow, increasePressed.value, fromStart = false)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleTextButton(stringResource(R.string.reading_font_size_decrease_short),
            stringResource(R.string.reading_font_size_decrease), canDecrease, decrease, onDecrease)
        CapsuleTextButton(stringResource(R.string.reading_font_size_increase_short),
            stringResource(R.string.reading_font_size_increase), canIncrease, increase, onIncrease)
    }
}

private val CapsuleButtonWidth = 48.dp

/**
 * Свет капсулы — только с нажатой стороны: ярко на нажатой половине и градиентом уходит в исходное
 * стекло к противоположному краю (запись IMG_9751).
 */
private fun DrawScope.drawSideGlow(glow: Color, alpha: Float, fromStart: Boolean) {
    if (alpha <= 0f) return
    val colors = listOf(
        0f to glow,
        SIDE_GLOW_SOLID to glow.copy(alpha = SIDE_GLOW_MID),
        1f to glow.copy(alpha = 0f),
    )
    val start = if (fromStart) 0f else size.width
    val end = size.width - start
    drawRoundRect(
        Brush.horizontalGradient(*colors.toTypedArray(), startX = start, endX = end),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/** Сплошной свет — до трети ширины, к середине — вполовину, дальше гаснет. */
private const val SIDE_GLOW_SOLID = 0.35f
private const val SIDE_GLOW_MID = 0.55f

@Composable
private fun CapsuleTextButton(
    label: String,
    description: String,
    enabled: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .widthIn(min = CapsuleButtonWidth)
            .fillMaxHeight()
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) disabled()
            }
            .alpha(if (enabled) 1f else PressFeedback.DISABLED_OPACITY),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = InabahTheme.palette.onAccent, style = InabahType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}
