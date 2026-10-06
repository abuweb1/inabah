package app.inabah.android.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ConfirmationPopover
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.SettingsValueChip
import app.inabah.android.core.designsystem.components.TimePickerPopover
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.DayTime
import app.inabah.android.feature.azkar.AzkarStore
import app.inabah.android.feature.azkar.rememberDayTimeFormatter
import app.inabah.android.feature.azkar.title

/**
 * Настройки азкаров (iOS `AzkarSettingsView`): время азкаров «с — до» — барабан под капсулой времени,
 * сохраняется сразу, а к прогрессу применяется при уходе с экрана; ручной сброс раздела —
 * с подтверждением над строкой.
 */
@Composable
fun AzkarSettingsScreen(
    store: AzkarStore,
    windowSettings: AzkarWindowSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val tint = theme.palette.tabAzkar
    val windows by windowSettings.windows.collectAsStateWithLifecycle()
    var editingTime by remember { mutableStateOf<WindowEdge?>(null) }
    var pendingReset by remember { mutableStateOf<AzkarSection?>(null) }
    // hasProgress — не поток: пересчитывается при смене прогресса разделов (в т. ч. isStarted — обнуление
    // по времени при частичном счёте) и после сброса отсюда.
    var resets by remember { mutableIntStateOf(0) }
    val progress = AzkarSection.entries.map { store.progress(it).collectAsStateWithLifecycle().value }
    // Как iOS `.onDisappear`: пока крутят колесо, прочитанное не стирается — важно только итоговое время.
    DisposableEffect(store) { onDispose { store.reconcile() } }

    SettingsScaffold(
        background = theme.gradients.azkarBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.settings_azkar_title), tint = tint, onBack = onBack) },
    ) {
        SettingsGroup(
            header = stringResource(R.string.settings_window_header),
            footer = stringResource(R.string.settings_window_footer),
            rows = AzkarSection.entries.map { section ->
                {
                    val window = windows.getValue(section)
                    // Время — второй строкой под названием: две капсулы рядом с ним не помещаются
                    // (iOS `ViewThatFits` уходит в ту же раскладку уже на обычном размере).
                    SettingsRow {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            SettingsRowText(stringResource(section.title))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WindowEdgeLabel(stringResource(R.string.settings_window_from))
                                WindowTimeChip(
                                    time = window.start,
                                    description = stringResource(section.startDescription),
                                    isEditing = editingTime == WindowEdge(section, isStart = true),
                                    tint = tint,
                                    onEdit = { editingTime = WindowEdge(section, isStart = true) },
                                    onChange = { windowSettings.setStart(it, section) },
                                    onDismiss = { editingTime = null },
                                )
                                WindowEdgeLabel(stringResource(R.string.settings_window_to))
                                WindowTimeChip(
                                    time = window.end,
                                    description = stringResource(section.endDescription),
                                    isEditing = editingTime == WindowEdge(section, isStart = false),
                                    tint = tint,
                                    onEdit = { editingTime = WindowEdge(section, isStart = false) },
                                    onChange = { windowSettings.setEnd(it, section) },
                                    onDismiss = { editingTime = null },
                                )
                            }
                        }
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

/** Какую капсулу правят: начало или конец окна раздела. */
private data class WindowEdge(val section: AzkarSection, val isStart: Boolean)

/** «с» / «до» между капсулами. */
@Composable
private fun WindowEdgeLabel(text: String) {
    Text(text, color = InabahTheme.palette.onAccentSecondary, style = InabahType.body)
}

/** Капсула «05:00» (формат системы); нажатие — барабан под ней. TalkBack: «Начало утренних азкаров, 05:00». */
@Composable
private fun WindowTimeChip(
    time: DayTime,
    description: String,
    isEditing: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onEdit: () -> Unit,
    onChange: (DayTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val text = rememberDayTimeFormatter()(time)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(interaction, indication = null, role = Role.Button,
                onClickLabel = stringResource(R.string.settings_reset_time_picker_title), onClick = onEdit)
            // Описание заменяет текст капсулы, время читается значением.
            .semantics {
                contentDescription = description
                stateDescription = text
            }
            .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY),
    ) {
        SettingsValueChip(text, highlighted = isEditing, tint = tint)
        TimePickerPopover(
            expanded = isEditing,
            hour = time.hour,
            minute = time.minute,
            tint = tint,
            hourDescription = stringResource(R.string.settings_reset_time_hour),
            minuteDescription = stringResource(R.string.settings_reset_time_minute),
            dayPeriodDescription = stringResource(R.string.settings_reset_time_day_period),
            onChange = { hour, minute -> onChange(DayTime.of(hour, minute)) },
            onDismiss = onDismiss,
        )
    }
}

@get:StringRes
private val AzkarSection.startDescription: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.settings_window_morning_start
        AzkarSection.Evening -> R.string.settings_window_evening_start
    }

@get:StringRes
private val AzkarSection.endDescription: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.settings_window_morning_end
        AzkarSection.Evening -> R.string.settings_window_evening_end
    }

@get:StringRes
private val AzkarSection.resetConfirmationMessage: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.settings_reset_now_confirm_morning
        AzkarSection.Evening -> R.string.settings_reset_now_confirm_evening
    }
