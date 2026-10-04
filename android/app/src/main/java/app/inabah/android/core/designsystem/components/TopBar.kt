package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.background
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
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = InabahTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (background != null) Modifier.background(background) else Modifier)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(BarHeight)
            .padding(horizontal = Spacing.xl),
    ) {
        GlassIconButton(
            onClick = onBack,
            iconRes = R.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.common_back),
            tint = tint,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = SideSlotWidth / 2)
                .semantics(mergeDescendants = true) { heading() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = palette.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = InabahType.headline.copy(fontWeight = FontWeight.Bold),
            )
            subtitle?.invoke()
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
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
    Box(
        modifier = modifier
            .size(Size.minTapTarget)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else PressFeedback.DISABLED_OPACITY),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                .size(GlassButtonSize)
                .glass(tint),
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
    Row(
        modifier = modifier
            .height(GlassButtonSize)
            .glass(tint, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleTextButton(stringResource(R.string.reading_font_size_decrease_short),
            stringResource(R.string.reading_font_size_decrease), canDecrease, onDecrease)
        CapsuleTextButton(stringResource(R.string.reading_font_size_increase_short),
            stringResource(R.string.reading_font_size_increase), canIncrease, onIncrease)
    }
}

private val CapsuleButtonWidth = 56.dp

@Composable
private fun CapsuleTextButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
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
            .alpha(if (enabled) 1f else PressFeedback.DISABLED_OPACITY)
            .pressFeedback(interaction, opacity = PressFeedback.BARE_OPACITY, animation = PressAnimation.Instant),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = InabahTheme.palette.onAccent, style = InabahType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}
