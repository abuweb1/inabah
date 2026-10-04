package app.inabah.android.feature.azkar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.SectionPlaceholder

/** Арабское название приложения — контент, не переводится. */
private const val BRAND_ARABIC = "إنابة"
private const val BRAND_ARABIC_SIZE = 60f

/** Главная «Азкары» — заглушка этапа 0 (бренд); полный экран — этап 3, docs/android/06-screens.md, 6.2. */
@Composable
fun AzkarHomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    SectionPlaceholder(
        title = stringResource(R.string.home_brand_latin),
        message = stringResource(R.string.home_brand_tagline),
        contentPadding = contentPadding,
        modifier = modifier,
        header = {
            ArabicText(
                text = BRAND_ARABIC,
                size = BRAND_ARABIC_SIZE,
                color = InabahTheme.palette.onAccent,
                bold = true,
                textAlign = TextAlign.Center,
            )
        },
    )
}
