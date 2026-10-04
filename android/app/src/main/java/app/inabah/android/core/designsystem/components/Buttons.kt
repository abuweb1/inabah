package app.inabah.android.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing

// Кнопки дизайн-системы (iOS ButtonStyles.swift). Отклик — масштаб или прозрачность вместо ряби
// Material; зона касания не меньше 48 dp, видимый размер — по токенам iOS. Порядок модификаторов:
// нажатие снаружи (зона касания не сжимается при нажатии, как scaleEffect в iOS), затем прозрачность
// неактивной — на всё, включая фон и тень, затем отклик, затем подложка.

/** Форма подложки [IconButton]. */
enum class IconButtonShape { Circle, RoundedSquare }

/** Доля значка от размера кнопки (iOS — кегль символа 0,36 размера; у Material Symbols поле вокруг знака). */
private const val ICON_FRACTION = 0.45f

/** Значок без подложки — как символ title2 (22 pt) в iOS. */
private val BareIconSize = 24.dp

/** Значок на круглой кнопке плеера — как символ title (28 pt) bold. */
private val ProminentIconSize = 30.dp

/** Значок в кнопке на всю ширину — как символ body. */
private val PrimaryIconSize = 20.dp

/** Значок плитки-переключателя — как title3 (плитка закреплена — не растёт с шагом интерфейса). */
private val ToggleIconSize = 22.sp

// Все кнопки закреплены (`FixedTextSize`): не растут с шагом интерфейса (docs/android/10-typography.md).

private fun Modifier.disabledAlpha(enabled: Boolean) = alpha(if (enabled) 1f else PressFeedback.DISABLED_OPACITY)

/**
 * Кнопка-значок на подложке (iOS `IconButtonStyle`): круг или скруглённый квадрат 12,
 * значок ≈ 0,36 размера, при нажатии — 0,92.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    icon: Painter,
    contentDescription: String,
    foreground: Color,
    background: Color,
    modifier: Modifier = Modifier,
    shape: IconButtonShape = IconButtonShape.Circle,
    size: Dp = Size.visibleTapTarget,
    border: Color? = null,
    enabled: Boolean = true,
) {
    IconButton(onClick, contentDescription, background, modifier, shape, size, border, enabled) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(size * ICON_FRACTION))
    }
}

/**
 * То же с произвольным содержимым [content] вместо значка («Аа» на карточке зикра);
 * для TalkBack кнопка озвучивается [contentDescription], содержимое — нет.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    contentDescription: String,
    background: Color,
    modifier: Modifier = Modifier,
    shape: IconButtonShape = IconButtonShape.Circle,
    size: Dp = Size.visibleTapTarget,
    border: Color? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = FixedTextSize {
    val interaction = remember { MutableInteractionSource() }
    val corner = when (shape) {
        IconButtonShape.Circle -> CircleShape
        IconButtonShape.RoundedSquare -> RoundedCornerShape(Radius.control)
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .disabledAlpha(enabled),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                .size(size)
                .surface(SolidColor(background), corner, border)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** Значок без подложки (iOS `BareIconButtonStyle`): при нажатии — прозрачность 0,5 сразу, без анимации. */
@Composable
fun BareIconButton(
    onClick: () -> Unit,
    icon: Painter,
    contentDescription: String,
    foreground: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = FixedTextSize {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(Size.visibleTapTarget)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .disabledAlpha(enabled)
            .pressFeedback(interaction, opacity = PressFeedback.BARE_OPACITY, animation = PressAnimation.Instant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = foreground, modifier = Modifier.size(BareIconSize))
    }
}

/** Главная круглая кнопка плеера (iOS `ProminentRoundButtonStyle`): 64 dp, золотой градиент и тень. */
@Composable
fun ProminentRoundButton(
    onClick: () -> Unit,
    icon: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = FixedTextSize {
    val palette = InabahTheme.palette
    val gradient = InabahTheme.gradients.counterButton
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(Size.playerMainButton)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .disabledAlpha(enabled)
            .pressFeedback(interaction, scale = PressFeedback.ROUND_SCALE)
            .surface(gradient, CircleShape, shadow = ShadowToken.goldButton(palette)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = palette.parchmentInk, modifier = Modifier.size(ProminentIconSize))
    }
}

/** Кнопка на всю ширину (iOS `PrimaryButtonStyle`): 50 dp, фон accent, при нажатии 0,98 и 0,8. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Значок перед текстом («←» у «На главную»), для TalkBack не озвучивается. */
    leadingIcon: Painter? = null,
) = FixedTextSize {
    val palette = InabahTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .disabledAlpha(enabled)
            .pressFeedback(interaction, scale = PressFeedback.WIDE_SCALE, opacity = PressFeedback.WIDE_OPACITY)
            .defaultMinSize(minHeight = Size.primaryButtonHeight)
            .surface(palette.accent, Radius.box)
            .padding(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { Icon(it, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(PrimaryIconSize)) }
        Text(text, color = palette.onAccent, textAlign = TextAlign.Center,
            style = InabahType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Плитка-переключатель (iOS `ToggleTileButtonStyle`, отметки хадиса): значок над подписью.
 * Включена — фон [activeFill], текст onAccent; выключена — [fill], цвет [tint]; смена — 300 мс.
 * Для TalkBack — переключатель с постоянной подписью [accessibilityLabel] («Прочитан»; видимая
 * подпись меняется «Прочитать» → «Прочитан», состояние озвучивает сам переключатель), смена —
 * с тактильным откликом. [icon] получает цвет и размер значка.
 */
@Composable
fun ToggleTile(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    tint: Color,
    fill: Color,
    activeFill: Color,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    icon: @Composable (color: Color, size: TextUnit) -> Unit,
) = FixedTextSize {
    val palette = InabahTheme.palette
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val color by animateColorAsState(if (checked) palette.onAccent else tint, Motion.highlight(), label = "toggleColor")
    val background by animateColorAsState(if (checked) activeFill else fill, Motion.highlight(), label = "toggleFill")
    Column(
        modifier = modifier
            .toggleable(checked, interaction, indication = null, role = Role.Switch) { value ->
                haptics.performHapticFeedback(if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onCheckedChange(value)
            }
            .clearAndSetSemantics { contentDescription = accessibilityLabel }
            .pressFeedback(interaction, scale = PressFeedback.CARD_SCALE)
            .surface(background, Radius.control)
            // Как в iOS: минимум 50 — у содержимого, отступы 12 сверху и снизу — поверх него.
            .padding(vertical = Spacing.m)
            .defaultMinSize(minHeight = Size.primaryButtonHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterVertically),
    ) {
        icon(color, ToggleIconSize)
        Text(label, color = color, style = InabahType.footnote.copy(fontWeight = FontWeight.SemiBold))
    }
}
