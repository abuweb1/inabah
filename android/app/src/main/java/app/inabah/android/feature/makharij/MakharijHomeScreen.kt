package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.components.FeaturedVerse
import app.inabah.android.core.designsystem.components.NavCardRing
import app.inabah.android.core.designsystem.components.NavCardRingStyle
import app.inabah.android.core.designsystem.components.SectionBrand
import app.inabah.android.core.designsystem.components.SectionHomeLayout
import app.inabah.android.core.designsystem.components.SectionNavCard
import app.inabah.android.core.settings.ReadingProgress

// Арабский контент главной — не переводится.
private const val BRAND_ARABIC = "مخارج"
private const val VERSE_ARABIC = "وَرَتِّلِ الْقُرْآنَ تَرْتِيلًا"

/**
 * Главная «Махрадж» (iOS `MakharijHomeView`, `docs/android/06-screens.md`, 6.7): бренд, аят
 * аль-Муззаммиль 73:4 в рамке и карточка «Учимся читать» (2026-10-15) → список шагов. У «ج» нижний
 * вынос — подпись бренда не подтягивается к арабскому.
 */
@Composable
fun MakharijHomeScreen(
    store: ReadingStore,
    progress: ReadingProgress,
    onOpenSteps: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    // Обычно курс уже загружен при запуске (AppContainer). Загрузка отменилась (стор вернулся
    // в Idle) - начать снова; после ошибки - ещё попытка при открытии.
    val state by store.course.collectAsStateWithLifecycle()
    LaunchedEffect(store, state is Loadable.Idle) { store.load() }
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
        ReadingCourseCard(store, progress, onOpenSteps)
    }
}

/**
 * «Учимся читать»: подпись - текущий шаг («Шаг 4 из 10 · Огласовки») или «Все шаги пройдены»,
 * кольцо - доля пройденных. Курс ещё не загружен - без подписи и кольца.
 */
@Composable
private fun ReadingCourseCard(store: ReadingStore, progress: ReadingProgress, onClick: () -> Unit) {
    val palette = LocalInabahTheme.current.palette
    val colors = readingColors
    val state by store.course.collectAsStateWithLifecycle()
    val lessonsDone by progress.lessonsDone.collectAsStateWithLifecycle()
    val wordCounts by progress.wordCounts.collectAsStateWithLifecycle()
    val course = (state as? Loadable.Loaded)?.value
    val completed = course?.let { completedSteps(it, lessonsDone, wordCounts) }.orEmpty()
    val meta = course?.let {
        val current = currentStep(it, completed)
        val done = completedCount(it, completed)
        when {
            current != null -> stringResource(R.string.makharij_reading_card_step, current.number, it.steps.size, current.title)
            done == it.steps.size -> stringResource(R.string.makharij_reading_all_done)
            // Уроки с содержанием пройдены, остальные шаги ещё пишутся.
            else -> stringResource(R.string.makharij_reading_card_progress, done, it.steps.size)
        }
    }
    SectionNavCard(
        title = stringResource(R.string.makharij_reading_title),
        icon = painterResource(R.drawable.ic_ain),
        iconColor = palette.gold,
        gradient = colors.card,
        shadow = ShadowToken.navCard(palette.shadow, ShadowToken.Strength.Strong),
        onClick = onClick,
        meta = meta,
        ring = course?.let {
            NavCardRing(
                fraction = completedFraction(it, completed),
                style = NavCardRingStyle(track = palette.track, fill = palette.gold, text = palette.onAccent),
            )
        },
    )
}
