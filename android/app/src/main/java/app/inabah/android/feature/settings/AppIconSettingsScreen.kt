package app.inabah.android.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SelectableTile
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.settings.AppIconOption
import app.inabah.android.core.settings.AppIconSettings
import kotlinx.coroutines.launch

/** Превью иконки — 88, скругление — доля стороны, как у иконок iOS (`AppIconSettingsView`). */
private val IconSize = 88.dp
private const val ICON_CORNER_RATIO = 0.2237f

/** Видимая часть адаптивной иконки — 72 из 108 dp: слои растянуты так, чтобы в превью попала она. */
private const val ADAPTIVE_FULL_TO_VISIBLE = 108f / 72f

/**
 * «Иконка приложения» (iOS `AppIconSettingsView`, этап 9): четыре варианта сеткой 2×2. Выбор применяется
 * сразу (решение пользователя 2026-10-06); работа с `PackageManager` — в [AppIconSettings].
 */
@Composable
fun AppIconSettingsScreen(
    settings: AppIconSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val selected by settings.current.collectAsStateWithLifecycle()
    val failedToChange by settings.failedToChange.collectAsStateWithLifecycle()
    // Смена — короткий вызов платформы; закрыли экран раньше — отметка уже стоит, вызов завершится сам.
    val scope = rememberCoroutineScope()
    AppIconSettingsContent(
        selected = selected,
        failedToChange = failedToChange,
        onSelect = { option -> scope.launch { settings.select(option) } },
        onBack = onBack,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

/** Содержимое экрана без зависимостей — для UI-тестов. Ошибка смены — сообщение на месте подписи. */
@Composable
fun AppIconSettingsContent(
    selected: AppIconOption,
    failedToChange: Boolean,
    onSelect: (AppIconOption) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    SettingsScaffold(
        background = theme.gradients.settingsBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.settings_app_icon_title), tint = theme.palette.tabSettings, onBack = onBack) },
    ) {
        SettingsGroup(
            footer = stringResource(if (failedToChange) R.string.settings_app_icon_error else R.string.settings_app_icon_footer),
            rows = listOf {
                Column(
                    Modifier
                        .selectableGroup()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xlPlus),
                ) {
                    AppIconOption.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                            pair.forEach { option ->
                                SelectableTile(
                                    title = stringResource(option.title),
                                    isSelected = option == selected,
                                    onSelect = { onSelect(option) },
                                    cornerRadius = IconSize * ICON_CORNER_RATIO,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    AppIconPreview(option)
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * Иконка как в лаунчере: слои адаптивной иконки по отдельности (`painterResource` не читает `adaptive-icon`),
 * растянутые на 108/72 — видна центральная часть, края обрезает скругление плитки.
 */
@Composable
private fun AppIconPreview(option: AppIconOption) {
    Box(Modifier.size(IconSize), contentAlignment = Alignment.Center) {
        val layerSize = IconSize * ADAPTIVE_FULL_TO_VISIBLE
        Image(painterResource(option.background), contentDescription = null, modifier = Modifier.requiredSize(layerSize))
        Image(painterResource(option.foreground), contentDescription = null, modifier = Modifier.requiredSize(layerSize))
    }
}

@get:StringRes
private val AppIconOption.title: Int
    get() = when (this) {
        AppIconOption.Classic -> R.string.app_icon_classic
        AppIconOption.Niche -> R.string.app_icon_niche
        AppIconOption.Beads -> R.string.app_icon_beads
        AppIconOption.Dawn -> R.string.app_icon_dawn
    }

@get:DrawableRes
private val AppIconOption.background: Int
    get() = when (this) {
        AppIconOption.Classic -> R.drawable.ic_launcher_background
        AppIconOption.Niche -> R.drawable.ic_launcher_niche_background
        AppIconOption.Beads -> R.drawable.ic_launcher_beads_background
        AppIconOption.Dawn -> R.drawable.ic_launcher_dawn_background
    }

@get:DrawableRes
private val AppIconOption.foreground: Int
    get() = when (this) {
        AppIconOption.Classic -> R.drawable.ic_launcher_foreground
        AppIconOption.Niche -> R.drawable.ic_launcher_niche_foreground
        AppIconOption.Beads -> R.drawable.ic_launcher_beads_foreground
        AppIconOption.Dawn -> R.drawable.ic_launcher_dawn_foreground
    }
