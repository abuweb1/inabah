package app.inabah.android.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion

// Переключатель и сегменты как в iOS (решение пользователя 2026-10-05, снимок android/docs/player):
// не Material 3 — в одном стиле с остальным приложением.

private val SwitchWidth = 51.dp
private val SwitchHeight = 31.dp
private val SwitchKnobInset = 2.dp
private val SegmentHeight = 32.dp
private val SegmentInset = 2.dp
private const val SELECTED_SEGMENT_ALPHA = 0.28f

/**
 * Строка с переключателем (iOS `Toggle`): подпись [label] слева, капсула справа; нажатие — вся строка,
 * для TalkBack — переключатель. Включён — подложка [tint], белая ручка справа; смена — 300 мс.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val haptics = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) tint else palette.track, Motion.highlight(), label = "switchTrack")
    val knob by animateFloatAsState(if (checked) 1f else 0f, Motion.highlight(), label = "switchKnob")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(checked, remember { MutableInteractionSource() }, indication = null, role = Role.Switch) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onCheckedChange(it)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.textPrimary, style = InabahType.body, modifier = Modifier.weight(1f))
        Box(Modifier.size(SwitchWidth, SwitchHeight).background(track, CircleShape).padding(SwitchKnobInset)) {
            Box(
                Modifier
                    .size(SwitchHeight - SwitchKnobInset * 2)
                    .graphicsLayer { translationX = knob * (SwitchWidth - SwitchHeight).toPx() }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/**
 * Сегменты (iOS `Picker(.segmented)`): равные доли капсулы, выбранный — светлая подложка, которая
 * переезжает к новому выбору сдвигом отрисовки (300 мс); тактильный щелчок на смене. Для TalkBack —
 * группа вариантов, у каждого «выбран / не выбран».
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val haptics = LocalHapticFeedback.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), Motion.highlight(), label = "segment")
    var segmentWidth by remember { mutableIntStateOf(0) }
    val pill = palette.onAccent.copy(alpha = SELECTED_SEGMENT_ALPHA)
    Box(
        modifier
            .fillMaxWidth()
            .height(SegmentHeight)
            .background(palette.track, CircleShape)
            .padding(SegmentInset),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f / options.size.coerceAtLeast(1))
                .onSizeChanged { segmentWidth = it.width }
                .graphicsLayer { translationX = position * segmentWidth }
                .background(pill, CircleShape),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(isSelected, remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(option)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        color = palette.onAccent,
                        maxLines = 1,
                        style = InabahType.subheadline.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    )
                }
            }
        }
    }
}
