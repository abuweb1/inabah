package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.designsystem.components.SectionPlaceholder

/** Корень «Настроек» — заглушка этапа 0; полный экран — этапы 3, 4 и 6, docs/android/06-screens.md. */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    SectionPlaceholder(
        title = stringResource(R.string.settings_title),
        message = stringResource(R.string.placeholder_soon),
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
