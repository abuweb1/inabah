package app.inabah.android.feature.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SwitchRow
import app.inabah.android.core.reminders.ReminderChannels
import app.inabah.android.core.settings.AzkarWindow
import app.inabah.android.core.settings.DayTime
import app.inabah.android.core.settings.ReminderSettings
import app.inabah.android.feature.azkar.rememberDayTimeFormatter
import app.inabah.android.feature.azkar.title

/**
 * «Напоминания» в настройках азкаров (решения пользователя 2026-10-10): в начале времени — утренние
 * и вечерние, «если ещё не прочитаны» — за 2 часа до конца, для включённых разделов. Переключатели
 * сохраняются сразу (расписание пересчитывает `ReminderService`); время в подписи — из черновика окна.
 */
@Composable
internal fun AzkarRemindersGroup(settings: ReminderSettings, windows: Map<AzkarSection, AzkarWindow>, tint: Color) {
    val preferences by settings.preferences.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermission(ReminderChannels.AZKAR)
    var denied by remember { mutableStateOf(false) }
    val format = rememberDayTimeFormatter()
    val palette = InabahTheme.palette
    // Включение — только с разрешением; выключение — сразу.
    val toggle = { enabled: Boolean, apply: (Boolean) -> Unit ->
        if (!enabled) {
            apply(false)
        } else {
            permission.request { granted -> if (granted) apply(true) else denied = true }
        }
    }
    val blocked = !permission.allowed && (denied || preferences.azkarStart.isNotEmpty() || preferences.azkarStreak)
    SettingsGroup(
        header = stringResource(R.string.settings_reminders_header),
        notice = if (blocked) stringResource(R.string.settings_reminders_blocked) else null,
        rows = buildList {
            AzkarSection.entries.forEach { section ->
                add {
                    SettingsRow {
                        SwitchRow(
                            label = stringResource(section.title),
                            subtitle = stringResource(R.string.settings_reminders_start_subtitle, format(windows.getValue(section).start)),
                            checked = section in preferences.azkarStart && permission.allowed,
                            onCheckedChange = { toggle(it) { on -> settings.setAzkarStart(section, on) } },
                            tint = tint,
                            labelColor = palette.onAccent,
                        )
                    }
                }
            }
            add {
                SettingsRow {
                    SwitchRow(
                        label = stringResource(R.string.settings_reminders_unfinished),
                        subtitle = stringResource(R.string.settings_reminders_unfinished_subtitle),
                        checked = preferences.azkarUnfinished && permission.allowed,
                        onCheckedChange = { toggle(it, settings::setAzkarUnfinished) },
                        tint = tint,
                        enabled = preferences.azkarStart.isNotEmpty(),
                        labelColor = palette.onAccent,
                    )
                }
            }
            // Независимо от напоминаний в начале (решение пользователя 2026-10-11).
            add {
                SettingsRow {
                    SwitchRow(
                        label = stringResource(R.string.settings_reminders_streak),
                        subtitle = stringResource(R.string.settings_reminders_streak_subtitle),
                        checked = preferences.azkarStreak && permission.allowed,
                        onCheckedChange = { toggle(it, settings::setAzkarStreak) },
                        tint = tint,
                        labelColor = palette.onAccent,
                    )
                }
            }
            if (blocked) add { OpenNotificationSettingsRow(permission) }
        },
    )
}

/**
 * «Хадис дня» в настройках хадисов: переключатель и время. Время правится черновиком и сохраняется при
 * уходе с экрана ([timeDraft] / [onEditTime] — у экрана, как окна азкаров).
 */
@Composable
internal fun HadithOfDayGroup(
    settings: ReminderSettings,
    timeDraft: DayTime,
    isEditingTime: Boolean,
    onEditTime: (Boolean) -> Unit,
    onTimeChange: (DayTime) -> Unit,
    tint: Color,
) {
    val preferences by settings.preferences.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermission(ReminderChannels.HADITH)
    var denied by remember { mutableStateOf(false) }
    val palette = InabahTheme.palette
    val enabled = preferences.hadithOfDay && permission.allowed
    val blocked = !permission.allowed && (denied || preferences.hadithOfDay)
    SettingsGroup(
        header = stringResource(R.string.settings_hadith_of_day_header),
        footer = stringResource(R.string.settings_hadith_of_day_footer),
        notice = if (blocked) stringResource(R.string.settings_reminders_blocked) else null,
        rows = buildList {
            add {
                SettingsRow {
                    SwitchRow(
                        label = stringResource(R.string.settings_hadith_of_day_toggle),
                        checked = enabled,
                        onCheckedChange = { on ->
                            if (!on) settings.setHadithOfDay(false)
                            else permission.request { granted -> if (granted) settings.setHadithOfDay(true) else denied = true }
                        },
                        tint = tint,
                        labelColor = palette.onAccent,
                    )
                }
            }
            if (enabled) {
                add {
                    SettingsRow(trailing = {
                        WindowTimeChip(
                            time = timeDraft,
                            description = stringResource(R.string.settings_hadith_of_day_time_description),
                            isEditing = isEditingTime,
                            tint = tint,
                            onEdit = { onEditTime(true) },
                            onChange = onTimeChange,
                            onDismiss = { onEditTime(false) },
                        )
                    }) {
                        SettingsRowText(stringResource(R.string.settings_hadith_of_day_time))
                    }
                }
            }
            if (blocked) add { OpenNotificationSettingsRow(permission) }
        },
    )
}

/** «Открыть настройки» — уведомления (или канал раздела) выключены в системе или в разрешении отказано. */
@Composable
private fun OpenNotificationSettingsRow(permission: NotificationPermissionState) {
    SettingsRow(onClick = permission.openSettings, compact = true) {
        Text(
            stringResource(R.string.settings_reminders_open_settings),
            color = InabahTheme.palette.onAccent,
            style = InabahType.body.copy(fontWeight = FontWeight.Bold),
        )
    }
}
