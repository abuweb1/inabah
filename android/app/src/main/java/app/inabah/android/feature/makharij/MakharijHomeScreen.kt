package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.FeaturedVerse
import app.inabah.android.core.designsystem.components.SectionBrand
import app.inabah.android.core.designsystem.components.SectionHomeLayout
import app.inabah.android.core.designsystem.components.surface

// Арабский контент главной — не переводится.
private const val BRAND_ARABIC = "مخارج"
private const val VERSE_ARABIC = "وَرَتِّلِ الْقُرْآنَ تَرْتِيلًا"

/** Песочные часы «Скоро»: значок 24 — размер символа title3 (кегль 20) с полями Material; в sp, растёт с шагом. */
private const val HOURGLASS_SIZE = 24f

/**
 * Главная «Махрадж» (iOS `MakharijHomeView`, `docs/android/06-screens.md`, 6.7): бренд, аят
 * аль-Муззаммиль 73:4 в рамке и карточка «Скоро» вместо разделов. У «ج» нижний вынос — подпись
 * бренда не подтягивается к арабскому.
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
        ComingSoonCard()
    }
}

/** «Скоро» (iOS `ComingSoonCard`, компактная — `10-typography.md`, 10.3): для TalkBack — одним элементом. */
@Composable
private fun ComingSoonCard() {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { HOURGLASS_SIZE.sp.toDp() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .surface(palette.subtleFill, Radius.navCard, border = palette.hairline)
            .padding(vertical = Spacing.xxl, horizontal = Spacing.xlPlus)
            // Одним элементом и заголовком: TalkBack находит раздел «Скоро» в навигации по заголовкам.
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(painterResource(R.drawable.ic_hourglass), contentDescription = null, tint = palette.gold,
            modifier = Modifier.size(iconSize))
        Text(stringResource(R.string.makharij_soon_title), color = palette.onAccent,
            style = InabahType.headline.copy(fontWeight = FontWeight.Bold))
        Text(stringResource(R.string.makharij_soon_message), color = palette.onAccentSecondary,
            style = InabahType.caption, textAlign = TextAlign.Center)
    }
}
