package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.inabah.android.core.content.LicenseDocument
import app.inabah.android.core.content.LicenseTexts
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsScaffold

/** Полный текст лицензии (iOS `LicenseView`); текст читается из ассетов при открытии. */
@Composable
fun LicenseScreen(
    document: LicenseDocument,
    texts: LicenseTexts,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val text by produceState<String?>(initialValue = null, document) { value = texts.text(document) }
    LicenseContent(document, text, onBack, contentPadding, modifier)
}

/**
 * Текст лицензии с выделением для копирования (iOS `textSelection`). Не моноширинным, как в iOS: на Android
 * только встроенные шрифты (Inter, Scheherazade) — caption Inter. [text] `null` — пока читается или нет файла.
 */
@Composable
fun LicenseContent(
    document: LicenseDocument,
    text: String?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    SettingsScaffold(
        background = theme.gradients.settingsBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(document.title), tint = theme.palette.tabSettings, onBack = onBack) },
    ) {
        SelectionContainer {
            Text(text.orEmpty(), color = theme.palette.onAccentStrong, style = InabahType.caption)
        }
    }
}
