package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.releases.ReleaseNotes
import app.inabah.android.core.releases.ReleaseNotesSource
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** «История версий» (из «О приложении»): все версии из `data/versions.json`, новые сверху. */
@Composable
fun VersionHistoryScreen(
    source: ReleaseNotesSource,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // `null` — ещё читается (доли секунды: файл маленький), пусто — файла нет или он не разобран.
    val versions by produceState<List<ReleaseNotes>?>(null, source) { value = source.all() }
    VersionHistoryContent(versions, onBack, contentPadding, modifier)
}

/** Экран без источника — для превью и UI-тестов. */
@Composable
fun VersionHistoryContent(
    versions: List<ReleaseNotes>?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    SettingsScaffold(
        background = theme.gradients.settingsBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.version_history_title), tint = theme.palette.tabSettings, onBack = onBack) },
    ) {
        when {
            versions == null -> Unit
            versions.isEmpty() -> NoticeCard(
                icon = R.drawable.ic_info,
                title = stringResource(R.string.version_history_empty_title),
                message = stringResource(R.string.version_history_empty_message),
            )
            else -> versions.forEach { notes ->
                SettingsGroup(
                    header = stringResource(R.string.version_history_header, notes.version.text, dateFormatter.format(notes.date)),
                    rows = notes.items.map { item ->
                        @Composable { SettingsRow { SettingsRowText(title = item) } }
                    },
                )
            }
        }
    }
}
