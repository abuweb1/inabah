package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.app.SettingsRoute
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.SettingsChevron
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsIcon
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold

/**
 * Корень «Настроек» (iOS `SettingsView`) на графитовом фоне. Пока одна строка «Азкары»:
 * «Хадисы» — этап 4, «Палитра» — 6, «Иконка приложения» — 9 (решение пользователя 2026-10-04).
 */
@Composable
fun SettingsScreen(
    onOpen: (SettingsRoute) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    SettingsScaffold(
        background = theme.gradients.settingsBackground,
        contentPadding = contentPadding,
        largeTitle = stringResource(R.string.settings_title),
        modifier = modifier,
    ) {
        SettingsGroup(
            rows = listOf {
                SettingsRow(onClick = { onOpen(SettingsRoute.Azkar) }, trailing = { SettingsChevron() }) {
                    SettingsIcon(R.drawable.ic_folded_hands, theme.gradients.morningCard)
                    SettingsRowText(
                        title = stringResource(R.string.settings_azkar_title),
                        subtitle = stringResource(R.string.settings_azkar_subtitle),
                    )
                }
            },
        )
    }
}
