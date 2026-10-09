package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.FeaturedVerse
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.SectionBrand
import app.inabah.android.core.designsystem.components.SectionHomeLayout

// Арабский контент главной — не переводится.
private const val BRAND_ARABIC = "مخارج"
private const val VERSE_ARABIC = "وَرَتِّلِ الْقُرْآنَ تَرْتِيلًا"

/**
 * Главная «Махрадж» (iOS `MakharijHomeView`, `docs/android/06-screens.md`, 6.7): бренд, аят
 * аль-Муззаммиль 73:4 в рамке и карточка «Скоро» (`NoticeCard`) вместо разделов. У «ج» нижний
 * вынос — подпись бренда не подтягивается к арабскому.
 */
@Composable
fun MakharijHomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    SectionHomeLayout(
        background = theme.gradients.makharijBackground,
        brand = SectionBrand(
            arabicName = BRAND_ARABIC,
            tagline = stringResource(R.string.makharij_home_brand_tagline),
            arabicNameHasDescender = true,
        ),
        verse = FeaturedVerse(
            arabic = VERSE_ARABIC,
            translation = stringResource(R.string.makharij_home_verse_translation),
            reference = stringResource(R.string.makharij_home_verse_reference),
        ),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        NoticeCard(
            icon = R.drawable.ic_hourglass,
            title = stringResource(R.string.makharij_soon_title),
            message = stringResource(R.string.makharij_soon_message),
        )
    }
}
