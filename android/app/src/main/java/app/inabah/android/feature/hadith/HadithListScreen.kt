package app.inabah.android.feature.hadith

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.ContentError
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.ProgressHeader
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.formatting.formatPercent
import app.inabah.android.core.settings.HadithCollectionProgress
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.HadithStatus
import kotlin.math.floor
import kotlinx.coroutines.launch

/** Кегль арабского названия сборника под заголовком навбара. */
private const val SUBTITLE_ARABIC_SIZE = 13f

/** Процент шапки: половина — вверх (1 из 40 = 2,5 % → 3 %), как у азкаров. */
internal fun headerPercent(progress: HadithCollectionProgress): Int = floor(progress.readFraction * PERCENT + HALF).toInt()

private const val PERCENT = 100
private const val HALF = 0.5

/**
 * Список хадисов сборника (iOS `HadithListView`): навбар с арабским названием, шапка прогресса
 * и строки со статусами. Отметки читают только шапка и лента — строке приходит готовый статус.
 */
@Composable
fun HadithListScreen(
    collection: HadithCollection,
    store: HadithStore,
    progress: HadithProgress,
    onBack: () -> Unit,
    onOpenHadith: (HadithId) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val states by store.collections.collectAsStateWithLifecycle()
    // Повторная попытка живёт, пока экран на месте: ушли — загрузка отменяется (стор вернёт Idle).
    val scope = rememberCoroutineScope()
    LaunchedEffect(collection) { store.load(collection) }

    Column(modifier.fillMaxSize().background(theme.gradients.hadithBackground)) {
        InabahTopBar(
            title = stringResource(collection.title),
            tint = palette.tabHadith,
            onBack = onBack,
            subtitle = { ArabicText(collection.arabicTitle, SUBTITLE_ARABIC_SIZE, palette.onAccentSecondary, maxLines = 1) },
            background = palette.hadithHeader,
        )
        Box(Modifier.fillMaxSize()) {
            when (val current = states[collection] ?: Loadable.Idle) {
                Loadable.Idle, Loadable.Loading -> CircularProgressIndicator(
                    color = palette.onAccent,
                    modifier = Modifier.align(Alignment.Center),
                )
                is Loadable.Failed -> ContentError(
                    onRetry = { scope.launch { store.load(collection) } },
                    modifier = Modifier.align(Alignment.Center),
                )
                is Loadable.Loaded -> Column(Modifier.fillMaxSize()) {
                    HadithListProgressHeader(progress, collection, total = current.value.size)
                    HadithFeed(current.value, progress, onOpenHadith, contentPadding)
                }
            }
        }
    }
}

/** «Прочитано: X из N · выучено: M» и процент, полоса — цветом «прочитан». */
@Composable
private fun HadithListProgressHeader(progress: HadithProgress, collection: HadithCollection, total: Int) {
    val palette = InabahTheme.palette
    val statuses by progress.statuses.collectAsStateWithLifecycle()
    val counts = remember(statuses, total) { progress.progress(collection, total) }
    val locale = LocalConfiguration.current.locales[0]
    ProgressHeader(
        label = stringResource(R.string.hadith_list_progress, counts.read, total, counts.memorized),
        percent = formatPercent(headerPercent(counts), locale),
        fraction = counts.readFraction.toFloat(),
        background = palette.hadithHeader,
        fill = SolidColor(palette.statusRead),
    )
}

/** Строки сборника — ленивый список: высота строк не меняется. Единственная часть, читающая отметки. */
@Composable
private fun HadithFeed(
    hadiths: List<Hadith>,
    progress: HadithProgress,
    onOpenHadith: (HadithId) -> Unit,
    contentPadding: PaddingValues,
) {
    val statuses by progress.statuses.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.xl,
            end = Spacing.xl,
            bottom = Spacing.m + contentPadding.calculateBottomPadding(),
        ),
    ) {
        items(hadiths, key = { it.number }) { hadith ->
            val interaction = remember { MutableInteractionSource() }
            HadithRow(
                hadith = hadith,
                status = statuses[hadith.id] ?: HadithStatus.None,
                modifier = Modifier
                    .clickable(interaction, indication = null, role = Role.Button) { onOpenHadith(hadith.id) }
                    .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY),
            )
        }
    }
}
