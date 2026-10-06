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
 * Корень «Настроек» (iOS `SettingsView`) на графитовом фоне: «Азкары», «Хадисы» и «Оформление» —
 * «Палитра», «Размер текста», «Иконка приложения».
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
            rows = listOf(
                {
                    SettingsRow(onClick = { onOpen(SettingsRoute.Azkar) }, trailing = { SettingsChevron() }) {
                        SettingsIcon(R.drawable.ic_auto_awesome, theme.gradients.morningCard)
                        SettingsRowText(
                            title = stringResource(R.string.settings_azkar_title),
                            subtitle = stringResource(R.string.settings_azkar_subtitle),
                        )
                    }
                },
                {
                    SettingsRow(onClick = { onOpen(SettingsRoute.Hadith) }, trailing = { SettingsChevron() }) {
                        SettingsIcon(R.drawable.ic_book, theme.gradients.nawawiCard)
                        SettingsRowText(
                            title = stringResource(R.string.settings_hadith_title),
                            subtitle = stringResource(R.string.settings_hadith_subtitle),
                        )
                    }
                },
            ),
        )
        // «Оформление» — порядок iOS: «Палитра», «Размер текста», «Иконка приложения».
        SettingsGroup(
            header = stringResource(R.string.settings_appearance_header),
            rows = listOf(
                {
                    SettingsRow(onClick = { onOpen(SettingsRoute.Palette) }, trailing = { SettingsChevron() }) {
                        SettingsIcon(R.drawable.ic_palette, theme.gradients.qudsiCard)
                        SettingsRowText(
                            title = stringResource(R.string.settings_palette_title),
                            subtitle = stringResource(R.string.settings_palette_subtitle),
                        )
                    }
                },
                {
                    SettingsRow(onClick = { onOpen(SettingsRoute.TextSize) }, trailing = { SettingsChevron() }) {
                        SettingsIcon(R.drawable.ic_format_size, theme.gradients.ajurriCard)
                        SettingsRowText(
                            title = stringResource(R.string.settings_text_size_title),
                            subtitle = stringResource(R.string.settings_text_size_subtitle),
                        )
                    }
                },
                {
                    SettingsRow(onClick = { onOpen(SettingsRoute.AppIcon) }, trailing = { SettingsChevron() }) {
                        SettingsIcon(R.drawable.ic_apps, theme.gradients.eveningCard)
                        SettingsRowText(
                            title = stringResource(R.string.settings_app_icon_title),
                            subtitle = stringResource(R.string.settings_app_icon_subtitle),
                        )
                    }
                },
            ),
        )
    }
}
