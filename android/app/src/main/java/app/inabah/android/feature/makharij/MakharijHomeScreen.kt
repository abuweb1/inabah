package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.designsystem.components.SectionPlaceholder

/** Главная «Махрадж» — заглушка этапа 0; экран «Скоро» с брендом и аятом — этап 6, docs/android/06-screens.md. */
@Composable
fun MakharijHomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    SectionPlaceholder(
        title = stringResource(R.string.makharij_soon_title),
        message = stringResource(R.string.makharij_soon_message),
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
