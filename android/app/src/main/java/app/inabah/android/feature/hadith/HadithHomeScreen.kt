package app.inabah.android.feature.hadith

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.designsystem.components.SectionPlaceholder

/** Главная «Хадисы» — заглушка этапа 0; полный экран — этап 4, docs/android/06-screens.md. */
@Composable
fun HadithHomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    SectionPlaceholder(
        title = stringResource(R.string.tab_hadith),
        message = stringResource(R.string.placeholder_soon),
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
