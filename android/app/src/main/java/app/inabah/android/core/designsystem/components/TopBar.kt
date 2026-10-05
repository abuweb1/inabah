package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.OkLch
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing

// Навбар как в iOS 26 (решение пользователя 2026-10-04): «стеклянная» круглая «‹» слева,
// заголовок и подзаголовок по центру, капсула действий справа. Размытия нет — подложки
// полупрозрачные цветом раздела ([tint]), как у панели вкладок.

private val BarHeight = 56.dp

/**
 * Круг «‹» и высота капсулы «А− А+». Мельче iOS (43 pt) по пропорции узкого экрана Android
 * (решение пользователя 2026-10-04): с ростом ×1,38 круг 40 → 55 не выходит за шапку 56.
 */
private val GlassButtonSize = 40.dp
/** У значка «‹» Material большие поля: стрелка ~0,47 значка — во весь круг она 0,47 круга, как в iOS. */
val GlassChevronSize = 40.dp

/** Обычный значок в стеклянном круге (↺ «Восстановить») — как символ iOS в круге 43 (снимок IMG_9743). */
val GlassSymbolSize = 22.dp

/** Сбоку от заголовка — место под кнопки, чтобы длинный заголовок не наезжал на них. */
private val SideSlotWidth = 104.dp

private const val GLASS_FILL_ALPHA = 0.2f
private const val GLASS_RIM_ALPHA = 0.4f

/** Фон навбара под кнопками: над сплошной шапкой стекло своё — непрозрачное и насыщеннее шапки. */
private val LocalBarBackground = staticCompositionLocalOf<Color?> { null }

// Стекло iOS 26 над цветной шапкой (снимки пользователя android/docs/hadith: back, next-hadith,
// text-button; замеры по пикселям). В покое — шапка того же оттенка, но насыщеннее (#1A5C4A → #02543E),
// обводка светлее подложки (#189168), «‹» не белая, а светлая в тон раздела (#B6FFF3). Нажатие: круг растёт
// ×1,4 и заливается ярким цветом раздела (#01C384), значок светлеет (#6DFFFF), обводка плавно разгорается
// (#1AEDA4); капсула «А− А+» растёт ×1,17 и светится с нажатой стороны — ярко (#01FEC5) у пальца,
// к другому краю темнее (#018B62).

private const val GLASS_PRESS_SCALE = 1.38f
private const val CAPSULE_PRESS_SCALE = 1.16f

// Цвета — сдвигами в OKLCH (светлота / насыщенность / оттенок), снятыми с тех же снимков: одно правило
// даёт и зелёное стекло хадисов, и фиолетовое азкаров (IMG_9729: шапка #5C33A0 → стекло #5723AD).
// Подложка — от цвета шапки, остальное — от цвета раздела (`tabHadith` #5ED6A6 у снимков).

/** Подложка: шапка чуть темнее и насыщеннее. */
private const val FILL_DL = -0.02f
private const val FILL_DC = 0.02f

/**
 * Сдвиги от цвета раздела (светлота, насыщенность, оттенок) для обводки, значка и света нажатия.
 * Одной формулой iOS не описывается: у фиолетового свет намного насыщеннее и уходит в розовый,
 * дальний край капсулы — в сине-фиолетовый; поэтому два набора, каждый — со своих эталонов.
 */
private class GlassShifts(
    val rim: FloatArray,
    val icon: FloatArray,
    val pressedFill: FloatArray,
    val pressedRim: FloatArray,
    val pressedIcon: FloatArray,
    val glowNear: FloatArray,
    val glowFar: FloatArray,
    /** Доля ширины капсулы от нажатой стороны, где свет ещё яркий. */
    val glowSolid: Float,
)

/** Зелёный (хадисы): снимки back, next-hadith, text-button в android/docs/hadith. Он же — для остальных палитр. */
private val DefaultShifts = GlassShifts(
    rim = floatArrayOf(-0.21f, -0.01f, 0f),
    icon = floatArrayOf(0.155f, -0.055f, 19f),
    pressedFill = floatArrayOf(-0.072f, 0.031f, -3f),
    pressedRim = floatArrayOf(0.044f, 0.052f, -3f),
    pressedIcon = floatArrayOf(0.127f, 0f, 31f),
    glowNear = floatArrayOf(0.092f, 0.048f, 5f),
    glowFar = floatArrayOf(-0.23f, -0.01f, 0f),
    glowSolid = 0.35f,
)

/** Фиолетовый (азкары): эталоны android/docs/azkar img.png, img_1.png — #FF66FF, белая стрелка, капсула #8334FF → #FF5EFF. */
private val VioletShifts = GlassShifts(
    rim = floatArrayOf(-0.188f, 0.094f, 4f),
    icon = floatArrayOf(0.197f, -0.06f, 27f),
    pressedFill = floatArrayOf(0.015f, 0.136f, 29f),
    pressedRim = floatArrayOf(0.004f, 0.114f, 24f),
    pressedIcon = floatArrayOf(0.258f, -0.115f, 0f),
    glowNear = floatArrayOf(0.006f, 0.146f, 29f),
    glowFar = floatArrayOf(-0.183f, 0.155f, -6f),
    // Розовый держится дальше середины капсулы, сине-фиолетовый — только у дальнего края.
    glowSolid = 0.6f,
)

private val VIOLET_HUES = 270f..320f

private fun shiftsFor(hue: Float): GlassShifts = if (hue in VIOLET_HUES) VioletShifts else DefaultShifts

private fun OkLch.by(shift: FloatArray): Color = shifted(shift[0], shift[1], shift[2]).toColor()

/** Нажатие — пружина с небольшим отскоком, отпускание — плавно. */
private fun glassPressSpec() = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
private fun glassReleaseSpec() = tween<Float>(durationMillis = 300)

/** Цвета стеклянной кнопки навбара в покое и при нажатии. */
@Immutable
internal data class GlassColors(
    val fill: Color,
    val rim: Color,
    val icon: Color,
    val pressedFill: Color,
    val pressedRim: Color,
    val pressedIcon: Color,
    /** Свет капсулы: у нажатой стороны и у противоположного края. */
    val glowNear: Color,
    val glowFar: Color,
    val glowSolid: Float,
)

/**
 * Цвета стекла из цвета раздела [tint] и фона навбара [barBackground]; `null` — навбар прозрачный
 * (настройки): подложка и обводка полупрозрачные, как [glass], значок — [onAccent].
 */
internal fun glassColors(tint: Color, barBackground: Color?, onAccent: Color): GlassColors {
    val section = OkLch.of(tint)
    val shifts = shiftsFor(section.h)
    val pressed = GlassColors(
        // Над прозрачным навбаром (настройки) — полупрозрачное стекло цветом раздела.
        fill = tint.copy(alpha = GLASS_FILL_ALPHA),
        rim = tint.copy(alpha = GLASS_RIM_ALPHA),
        icon = onAccent,
        pressedFill = section.by(shifts.pressedFill),
        pressedRim = section.by(shifts.pressedRim),
        pressedIcon = section.by(shifts.pressedIcon),
        glowNear = section.by(shifts.glowNear),
        glowFar = section.by(shifts.glowFar),
        glowSolid = shifts.glowSolid,
    )
    if (barBackground == null) return pressed
    return pressed.copy(
        fill = OkLch.of(barBackground).shifted(FILL_DL, FILL_DC).toColor(),
        rim = section.by(shifts.rim),
        icon = section.by(shifts.icon),
    )
}

@Composable
@ReadOnlyComposable
private fun glassColors(tint: Color): GlassColors =
    glassColors(tint, LocalBarBackground.current, InabahTheme.palette.onAccent)

/**
 * Обводка стекла: при нажатии ([pressed] 0…1) плавно разгорается из [GlassColors.rim] в
 * [GlassColors.pressedRim]. Рисовать последней — поверх подложки и света.
 */
private fun DrawScope.drawGlassRim(colors: GlassColors, pressed: Float, shape: Shape) {
    val width = Size.hairline.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, lerp(colors.rim, colors.pressedRim, pressed.coerceIn(0f, 1f)), style = Stroke(width))
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
) = InabahTopBar(
    tint = tint,
    onBack = onBack,
    modifier = modifier,
    background = background,
    centerTitle = centerTitle,
    actions = actions,
) {
    val horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start
    Column(Modifier.semantics(mergeDescendants = true) { heading() }, horizontalAlignment = horizontalAlignment) {
        Text(
            title,
            color = InabahTheme.palette.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centerTitle) TextAlign.Center else TextAlign.Start,
            style = InabahType.headline.copy(fontWeight = FontWeight.Bold),
        )
        subtitle?.invoke()
    }
}

/**
 * Навбар со своим содержимым [titleContent] (‹ «3 из 50» › экрана хадиса): по центру навбара
 * или ([centerTitle] = `false`) сразу за «‹» — содержимое занимает место до [actions].
 */
@Composable
fun InabahTopBar(
    tint: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color? = null,
    centerTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: @Composable () -> Unit,
) = FixedTextSize {
    // Навбар закреплён — не растёт с шагом интерфейса (docs/android/10-typography.md).
    CompositionLocalProvider(LocalBarBackground provides background) {
        TopBarLayout(tint, onBack, modifier, background, centerTitle, actions, titleContent)
    }
}

@Composable
private fun TopBarLayout(
    tint: Color,
    onBack: () -> Unit,
    modifier: Modifier,
    background: Color?,
    centerTitle: Boolean,
    actions: @Composable RowScope.() -> Unit,
    titleContent: @Composable () -> Unit,
) {
    val back = @Composable { backModifier: Modifier ->
        GlassIconButton(
            onClick = onBack,
            iconRes = R.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.common_back),
            tint = tint,
            modifier = backModifier,
            iconSize = GlassChevronSize,
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
            Box(Modifier.align(Alignment.Center).padding(horizontal = SideSlotWidth / 2)) { titleContent() }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    } else {
        Row(barModifier, verticalAlignment = Alignment.CenterVertically) {
            back(Modifier)
            Box(Modifier.weight(1f).padding(horizontal = Spacing.s)) { titleContent() }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Подзаголовок навбара (caption2, onAccentSecondary). */
@Composable
fun TopBarSubtitle(text: String) {
    Text(text, color = InabahTheme.palette.onAccentSecondary, maxLines = 1, style = InabahType.caption2)
}

/**
 * Круглая «стеклянная» кнопка навбара: круг 40 (зона касания 48). [iconSize] — значок Material:
 * обычные значки (↺ «Восстановить») — [GlassSymbolSize]; у «‹» поля шире — она передаёт [GlassChevronSize].
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = GlassSymbolSize,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = rememberPressProgress(interaction, glassPressSpec(), glassReleaseSpec())
    val colors = glassColors(tint)
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
                // Нажатое стекло целиком заливается цветом раздела, обводка разгорается.
                .drawBehind {
                    drawCircle(colors.fill)
                    drawCircle(colors.pressedFill, alpha = pressed.value.coerceIn(0f, 1f))
                    drawGlassRim(colors, pressed.value, CircleShape)
                },
            contentAlignment = Alignment.Center,
        ) {
            // Цвет значка читается при отрисовке: нажатие не перестраивает кнопку каждый кадр.
            Icon(
                painterResource(iconRes),
                contentDescription,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(lerp(colors.icon, colors.pressedIcon, pressed.value.coerceIn(0f, 1f)), blendMode = BlendMode.SrcIn)
                    },
            )
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
    val colors = glassColors(tint)
    Row(
        modifier = modifier
            .graphicsLayer {
                // Капсула лишь чуть подаётся вперёд — меньше круглой кнопки.
                val scale = 1f + (CAPSULE_PRESS_SCALE - 1f) * maxOf(decreasePressed.value, increasePressed.value)
                scaleX = scale
                scaleY = scale
            }
            .height(GlassButtonSize)
            .drawBehind {
                drawRoundRect(colors.fill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                drawSideGlow(colors, decreasePressed.value, fromStart = true)
                drawSideGlow(colors, increasePressed.value, fromStart = false)
                drawGlassRim(colors, maxOf(decreasePressed.value, increasePressed.value), CircleShape)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleTextButton(stringResource(R.string.reading_font_size_decrease_short),
            stringResource(R.string.reading_font_size_decrease), canDecrease, decrease, onDecrease)
        CapsuleTextButton(stringResource(R.string.reading_font_size_increase_short),
            stringResource(R.string.reading_font_size_increase), canIncrease, increase, onIncrease)
    }
}

/** Половина капсулы: 2 × 44 = 88 при высоте 40 — пропорции iOS (96 × 43). */
private val CapsuleButtonWidth = 44.dp

/**
 * Свет капсулы — с нажатой стороны: ярко у пальца, к противоположному краю темнее
 * (снимок text-button, запись IMG_9751).
 */
private fun DrawScope.drawSideGlow(glass: GlassColors, alpha: Float, fromStart: Boolean) {
    if (alpha <= 0f) return
    val colors = listOf(
        0f to glass.glowNear,
        glass.glowSolid to lerp(glass.glowNear, glass.glowFar, SIDE_GLOW_MID),
        1f to glass.glowFar,
    )
    val start = if (fromStart) 0f else size.width
    val end = size.width - start
    drawRoundRect(
        Brush.horizontalGradient(*colors.toTypedArray(), startX = start, endX = end),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/** На границе яркой части ([GlassColors.glowSolid]) свет уже на 30 % ушёл к цвету дальнего края. */
private const val SIDE_GLOW_MID = 0.3f

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
