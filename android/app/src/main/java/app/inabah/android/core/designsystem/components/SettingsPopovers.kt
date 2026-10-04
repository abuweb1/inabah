package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing

// Всплывающие карточки экранов настроек — как iOS 26 (android/docs/settings, IMG_9744 и IMG_9747):
// подтверждение сброса над строкой и барабан времени под капсулой значения.

private val ConfirmationWidth = 240.dp
private val ConfirmationPadding = 20.dp
private const val ACTION_FILL_ALPHA = 0.1f

/**
 * Подтверждение разрушающего действия (iOS `confirmationDialog` в виде popover): заголовок,
 * сообщение, красная капсула [actionLabel]. Отмена — касание мимо или «Назад», как в iOS.
 * Вызывать внутри строки-якоря.
 */
@Composable
fun ConfirmationPopover(
    title: String,
    message: String,
    actionLabel: String,
    tint: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = InabahTheme.palette
    AnchoredPopover(PopoverPlacement.AboveCenter, tint, onDismiss) {
        Column(
            Modifier.width(ConfirmationWidth).padding(ConfirmationPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(title, color = palette.onAccent, style = InabahType.headline.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() })
            Text(message, color = palette.onAccentSecondary, style = InabahType.subheadline)
            val interaction = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = Spacing.m)
                    .fillMaxWidth()
                    .heightIn(min = Size.primaryButtonHeight)
                    .surface(SolidColor(palette.onAccent.copy(alpha = ACTION_FILL_ALPHA)), RoundedCornerShape(percent = 50))
                    .clickable(interaction, indication = null, role = Role.Button) {
                        onConfirm()
                        onDismiss()
                    }
                    .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY),
            ) {
                Text(actionLabel, color = palette.destructive, style = InabahType.body.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/**
 * Барабан времени под капсулой значения (iOS DatePicker `.wheel` в popover): выбранное время
 * применяется сразу, закрытие — касание мимо или «Назад». Вызывать внутри капсулы-якоря.
 */
@Composable
fun TimePickerPopover(
    hour: Int,
    minute: Int,
    tint: Color,
    hourDescription: String,
    minuteDescription: String,
    onChange: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AnchoredPopover(PopoverPlacement.BelowEnd, tint, onDismiss) {
        TimeWheelPicker(hour, minute, onChange, hourDescription, minuteDescription)
    }
}
