package app.inabah.android.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing

/** Зазор между превью и рамкой выбора (iOS `selectionInset`). */
private val SelectionInset = 4.dp

/** Отметка под подписью — как символ body; в sp, растёт с шагом интерфейса. */
private const val MARK_SIZE = 22f

/**
 * Плитка выбора (iOS `SelectableTile`; палитры и иконки приложения): превью [preview] со скруглением
 * [cornerRadius] в рамке [Size.ringStroke] цвета accentLight, когда выбрана — рамка облегает превью;
 * под ним — [title], [subtitle] (если есть) и отметка ✓ / ○. Нажатие — масштаб 0,97; смена выбора — 300 мс.
 * Для TalkBack — один вариант группы, «выбран».
 */
@Composable
fun SelectableTile(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    cornerRadius: Dp = Radius.box,
    preview: @Composable () -> Unit,
) {
    val palette = InabahTheme.palette
    val interaction = remember { MutableInteractionSource() }
    // Гаснет в прозрачный accentLight, а не в прозрачный чёрный — без тёмной каймы посреди смены.
    val ring by animateColorAsState(
        palette.accentLight.copy(alpha = if (isSelected) 1f else 0f), Motion.highlight(), label = "tileRing",
    )
    val markSize = with(LocalDensity.current) { MARK_SIZE.sp.toDp() }
    Column(
        modifier = modifier
            .selectable(isSelected, interaction, indication = null, role = Role.RadioButton, onClick = onSelect)
            .pressFeedback(interaction, scale = PressFeedback.CARD_SCALE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(
            Modifier
                .border(Size.ringStroke, ring, RoundedCornerShape(cornerRadius + SelectionInset))
                .padding(SelectionInset)
                .clip(RoundedCornerShape(cornerRadius)),
        ) {
            preview()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
            Text(title, color = palette.onAccent, style = InabahType.footnote, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, color = palette.onAccentSecondary, style = InabahType.caption2, textAlign = TextAlign.Center)
            }
        }
        Icon(
            painterResource(if (isSelected) R.drawable.ic_check_circle else R.drawable.ic_radio_button_unchecked),
            contentDescription = null,
            tint = if (isSelected) palette.accentLight else palette.onAccentTertiary,
            modifier = Modifier.size(markSize),
        )
    }
}
