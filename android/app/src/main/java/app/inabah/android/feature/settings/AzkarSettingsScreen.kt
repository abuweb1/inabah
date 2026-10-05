package app.inabah.android.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.components.ConfirmationPopover
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.SettingsValueChip
import app.inabah.android.core.designsystem.components.TimePickerPopover
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.settings.AzkarResetSettings
import app.inabah.android.core.settings.DayTime
import app.inabah.android.feature.azkar.AzkarStore
import app.inabah.android.feature.azkar.title

/**
 * Настройки азкаров (iOS `AzkarSettingsView`): время ежедневного обнуления — барабан под капсулой
 * времени, применяется сразу; ручной сброс раздела — с подтверждением над строкой.
 */
@Composable
fun AzkarSettingsScreen(
    store: AzkarStore,
    resetSettings: AzkarResetSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val tint = theme.palette.tabAzkar
    val resetTimes by resetSettings.resetTimes.collectAsStateWithLifecycle()
    var editingTime by remember { mutableStateOf<AzkarSection?>(null) }
    var pendingReset by remember { mutableStateOf<AzkarSection?>(null) }
    // hasProgress — не поток: пересчитывается при смене прогресса разделов (в т. ч. isStarted — обнуление
    // по времени при частичном счёте) и после сброса отсюда.
    var resets by remember { mutableIntStateOf(0) }
    val progress = AzkarSection.entries.map { store.progress(it).collectAsStateWithLifecycle().value }

    SettingsScaffold(
        background = theme.gradients.azkarBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.settings_azkar_title), tint = tint, onBack = onBack) },
    ) {
        SettingsGroup(
            header = stringResource(R.string.settings_reset_header),
            footer = stringResource(R.string.settings_reset_footer),
            rows = AzkarSection.entries.map { section ->
                {
                    val time = resetTimes.getValue(section)
                    SettingsRow(
                        trailing = {
                            ResetTimeChip(
                                time = time,
                                isEditing = editingTime == section,
                                tint = tint,
                                onEdit = { editingTime = section },
                                onChange = { resetSettings.setResetTime(it, section) },
                                onDismiss = { editingTime = null },
                            )
                        },
                    ) {
                        SettingsRowText(stringResource(section.resetTimeLabel))
                    }
                }
            },
        )
        SettingsGroup(
            header = stringResource(R.string.settings_reset_now_header),
            footer = stringResource(R.string.settings_reset_now_footer),
            rows = AzkarSection.entries.map { section ->
                {
                    val canReset = remember(resets, progress) { store.hasProgress(section) }
                    // Якорь — вся строка: подтверждение стоит над ней по центру, уголок — на её середину.
                    Box {
                        SettingsRow(onClick = { pendingReset = section }, enabled = canReset) {
                            SettingsRowText(stringResource(section.title))
                        }
                        ConfirmationPopover(
                            expanded = pendingReset == section,
                            title = stringResource(R.string.settings_reset_now_confirm_title),
                            message = stringResource(section.resetConfirmationMessage),
                            actionLabel = stringResource(R.string.settings_reset_now_confirm_action),
                            tint = tint,
                            onConfirm = {
                                store.resetProgress(section)
                                resets++
                            },
                            onDismiss = { pendingReset = null },
                        )
                    }
                }
            },
        )
    }
}

/** Капсула «17:00»; нажатие — барабан под ней. */
@Composable
private fun ResetTimeChip(
    time: DayTime,
    isEditing: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onEdit: () -> Unit,
    onChange: (DayTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(interaction, indication = null, role = Role.Button,
                onClickLabel = stringResource(R.string.settings_reset_time_picker_title), onClick = onEdit)
            .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY),
    ) {
        SettingsValueChip(time.toString(), highlighted = isEditing, tint = tint)
        TimePickerPopover(
            expanded = isEditing,
            hour = time.hour,
            minute = time.minute,
            tint = tint,
            hourDescription = stringResource(R.string.settings_reset_time_hour),
            minuteDescription = stringResource(R.string.settings_reset_time_minute),
            onChange = { hour, minute -> onChange(DayTime.of(hour, minute)) },
            onDismiss = onDismiss,
        )
    }
}

@get:StringRes
private val AzkarSection.resetTimeLabel: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.settings_reset_morning
        AzkarSection.Evening -> R.string.settings_reset_evening
    }

@get:StringRes
private val AzkarSection.resetConfirmationMessage: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.settings_reset_now_confirm_morning
        AzkarSection.Evening -> R.string.settings_reset_now_confirm_evening
    }
