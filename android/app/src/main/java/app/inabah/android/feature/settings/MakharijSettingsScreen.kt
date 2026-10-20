package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.ConfirmationPopover
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.SwitchRow
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.feature.makharij.ReadingStore
import app.inabah.android.feature.makharij.completedCount
import app.inabah.android.feature.makharij.completedSteps
import app.inabah.android.feature.makharij.hasProgress
import app.inabah.android.feature.makharij.numberedStepCount
import app.inabah.android.feature.makharij.stepTitle

/**
 * Настройки «Махраджа» (решение пользователя 2026-10-19, Android-first): «Уроки» - «Показывать
 * транскрипцию» (то же состояние, что кнопка-глаз на экранах урока) и «Открыть все шаги» (без замков);
 * «Прогресс» - «пройдено N из M», начатые и пройденные шаги со сбросом каждого и «Сбросить весь
 * прогресс». Сброс - с подтверждением над строкой, как в настройках хадисов.
 */
@Composable
fun MakharijSettingsScreen(
    store: ReadingStore,
    progress: ReadingProgress,
    settings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val tint = palette.tabMakharij
    val course = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value
    val transliterationHidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    val allStepsOpen by settings.allStepsOpen.collectAsStateWithLifecycle()
    val lessonsDone by progress.lessonsDone.collectAsStateWithLifecycle()
    val wordCounts by progress.wordCounts.collectAsStateWithLifecycle()
    val zikrs by progress.zikrs.collectAsStateWithLifecycle()
    var pendingStep by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingResetAll by rememberSaveable { mutableStateOf(false) }
    // Курс обычно уже загружен разделом; здесь - если настройки открыли раньше «Махраджа» или после ошибки.
    LaunchedEffect(store) { store.load() }

    SettingsScaffold(
        background = theme.gradients.makharijBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.settings_makharij_title), tint = tint, onBack = onBack) },
    ) {
        SettingsGroup(
            header = stringResource(R.string.settings_makharij_lessons_header),
            rows = listOf(
                {
                    SettingsRow {
                        SwitchRow(
                            label = stringResource(R.string.settings_makharij_translit),
                            subtitle = stringResource(R.string.settings_makharij_translit_subtitle),
                            checked = !transliterationHidden,
                            onCheckedChange = { shown -> settings.setTransliterationHidden(!shown) },
                            tint = tint,
                            labelColor = palette.onAccent,
                        )
                    }
                },
                {
                    SettingsRow {
                        SwitchRow(
                            label = stringResource(R.string.settings_makharij_all_steps),
                            subtitle = stringResource(R.string.settings_makharij_all_steps_subtitle),
                            checked = allStepsOpen,
                            onCheckedChange = settings::setAllStepsOpen,
                            tint = tint,
                            labelColor = palette.onAccent,
                        )
                    }
                },
                {
                    // Волна и подсказки показываются один раз - здесь их можно вернуть (живые ячейки, 2026-10-20).
                    SettingsRow(onClick = settings::resetIntros) {
                        SettingsRowText(
                            title = stringResource(R.string.settings_makharij_hints_reset),
                            subtitle = stringResource(R.string.settings_makharij_hints_reset_subtitle),
                        )
                    }
                },
            ),
        )
        if (course != null) {
            val completed = remember(course, lessonsDone, wordCounts) { completedSteps(course, lessonsDone, wordCounts) }
            val started = remember(course, lessonsDone, wordCounts, zikrs) {
                course.steps.filter { hasProgress(it, lessonsDone, wordCounts, zikrs) }
            }
            SettingsGroup(
                header = stringResource(R.string.settings_makharij_progress_header),
                footer = if (started.isEmpty()) stringResource(R.string.settings_makharij_progress_empty) else null,
                rows = buildList {
                    add {
                        SettingsRow(compact = true) {
                            Text(
                                stringResource(R.string.makharij_reading_card_progress, completedCount(course, completed), numberedStepCount(course)),
                                color = palette.onAccentSecondary,
                                style = InabahType.subheadline,
                            )
                        }
                    }
                    started.forEach { step ->
                        add {
                            StepResetRow(
                                step = step,
                                isDone = step.number in completed,
                                isConfirming = pendingStep == step.number,
                                onReset = { pendingStep = step.number },
                                onConfirm = { progress.resetStep(step.number, step.zikrs.map { it.zikr }) },
                                onDismiss = { pendingStep = null },
                            )
                        }
                    }
                    add {
                        // Якорь - вся строка: подтверждение стоит над ней по центру.
                        Box {
                            SettingsRow(onClick = { pendingResetAll = true }, enabled = started.isNotEmpty(), compact = true) {
                                Text(
                                    stringResource(R.string.settings_makharij_reset_all),
                                    color = palette.onAccent,
                                    style = InabahType.body.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                            ConfirmationPopover(
                                expanded = pendingResetAll,
                                title = stringResource(R.string.settings_makharij_reset_all_confirm_title),
                                message = stringResource(R.string.settings_makharij_reset_all_confirm_message),
                                actionLabel = stringResource(R.string.settings_makharij_reset_confirm_action),
                                tint = tint,
                                onConfirm = progress::reset,
                                onDismiss = { pendingResetAll = false },
                            )
                        }
                    }
                },
            )
        }
    }
}

/** Шаг с прогрессом: «Шаг N · название», пройден или начат, справа «Сбросить»; нажатие - подтверждение. */
@Composable
private fun StepResetRow(
    step: ReadingStep,
    isDone: Boolean,
    isConfirming: Boolean,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalInabahTheme.current.palette
    val title = stepTitle(step)
    Box {
        SettingsRow(
            onClick = onReset,
            compact = true,
            trailing = {
                Text(stringResource(R.string.settings_makharij_step_reset), color = palette.tabMakharij, style = InabahType.body)
            },
        ) {
            SettingsRowText(
                title = title,
                subtitle = stringResource(if (isDone) R.string.settings_makharij_step_done else R.string.settings_makharij_step_started),
            )
        }
        ConfirmationPopover(
            expanded = isConfirming,
            title = stringResource(R.string.settings_makharij_step_reset_confirm_title),
            message = stringResource(R.string.settings_makharij_step_reset_confirm_message, title),
            actionLabel = stringResource(R.string.settings_makharij_reset_confirm_action),
            tint = palette.tabMakharij,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}
