package app.inabah.android.feature.hadith

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.BareIconButton
import app.inabah.android.core.designsystem.components.ContentError
import app.inabah.android.core.designsystem.components.FontSizeControls
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.designsystem.monospacedDigits
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.HadithStatus
import app.inabah.android.core.settings.ReadingSettings
import kotlinx.coroutines.launch

/**
 * Экран хадиса (iOS `HadithDetailView`): страницы сборника листаются свайпом или ‹ › в навбаре,
 * начальная — открытый хадис. Пейджер строится для загруженного сборника — начальная страница
 * применяется с первой раскладки; позиция сохраняется (`rememberPagerState`).
 */
@Composable
fun HadithDetailScreen(
    id: HadithId,
    store: HadithStore,
    progress: HadithProgress,
    readingSettings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val states by store.collections.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(id.collection) { store.load(id.collection) }

    Box(modifier.fillMaxSize().background(theme.gradients.hadithBackground)) {
        when (val current = states[id.collection] ?: Loadable.Idle) {
            is Loadable.Loaded -> HadithPages(current.value, id, progress, readingSettings, onBack, contentPadding)
            Loadable.Idle, Loadable.Loading, is Loadable.Failed -> Column(Modifier.fillMaxSize()) {
                HadithTopBar(readingSettings, onBack, pager = null)
                Box(Modifier.fillMaxSize()) {
                    if (current is Loadable.Failed) {
                        ContentError(
                            onRetry = { scope.launch { store.load(id.collection) } },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        CircularProgressIndicator(color = theme.palette.onAccent, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

@Composable
private fun HadithPages(
    hadiths: List<Hadith>,
    id: HadithId,
    progress: HadithProgress,
    readingSettings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val initialPage = hadiths.indexOfFirst { it.number == id.number }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage) { hadiths.size }
    val statuses by progress.statuses.collectAsStateWithLifecycle()
    val fontSize by readingSettings.arabicFontSize.collectAsStateWithLifecycle()
    val animatedSize by animateFloatAsState(fontSize.toFloat(), Motion.fontSize(), label = "arabicSize")
    Column(Modifier.fillMaxSize()) {
        HadithTopBar(readingSettings, onBack, pagerState)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { hadiths[it].number },
        ) { page ->
            val hadith = hadiths[page]
            HadithPage(
                hadith = hadith,
                status = statuses[hadith.id] ?: HadithStatus.None,
                arabicFontSize = animatedSize,
                onToggleRead = { progress.toggleRead(hadith.id) },
                onToggleMemorized = { progress.toggleMemorized(hadith.id) },
                contentPadding = contentPadding,
            )
        }
    }
}

/** Навбар: ‹ «3 из 50» › по центру, справа «А− А+»; до загрузки — без пейджера. */
@Composable
private fun HadithTopBar(readingSettings: ReadingSettings, onBack: () -> Unit, pager: PagerState?) {
    val palette = InabahTheme.palette
    val fontSize by readingSettings.arabicFontSize.collectAsStateWithLifecycle()
    InabahTopBar(
        tint = palette.tabHadith,
        onBack = onBack,
        background = palette.hadithHeader,
        // По центру навбара, как в iOS (решение пользователя 2026-10-04).
        centerTitle = true,
        actions = {
            FontSizeControls(
                canDecrease = ReadingSettings.canDecrease(fontSize),
                canIncrease = ReadingSettings.canIncrease(fontSize),
                onDecrease = readingSettings::decreaseArabicFontSize,
                onIncrease = readingSettings::increaseArabicFontSize,
                tint = palette.tabHadith,
            )
        },
    ) {
        pager?.let { HadithPagerControls(it) }
    }
}

/** ‹ «3 из 50» ›: кнопки листают с кривой `Motion.collapse`; номер меняется анимацией по цифрам. */
@Composable
private fun HadithPagerControls(pager: PagerState) {
    val palette = InabahTheme.palette
    val scope = rememberCoroutineScope()
    val current = pager.currentPage
    val count = pager.pageCount
    // Направление смены цифр — куда листали: вперёд — цифры уходят вверх.
    val previous = remember { intArrayOf(current) }
    val increasing = current >= previous[0]
    SideEffect { previous[0] = current }
    // От страницы, куда уже листаем: быстрое второе нажатие во время анимации — ещё на страницу дальше.
    fun go(by: Int) {
        val page = (pager.targetPage + by).coerceIn(0, count - 1)
        scope.launch { pager.animateScrollToPage(page, animationSpec = Motion.collapse()) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BareIconButton(
            onClick = { go(-1) },
            icon = painterResource(R.drawable.ic_chevron_left),
            contentDescription = stringResource(R.string.hadith_detail_previous),
            foreground = palette.onAccent,
            enabled = current > 0,
        )
        val counter = stringResource(R.string.hadith_detail_counter, current + 1, count)
        Box(
            Modifier
                .surface(SolidColor(palette.track), CircleShape)
                .padding(horizontal = Spacing.m, vertical = Spacing.xxs)
                // Листание свайпом TalkBack озвучит само: «3 из 50» — вежливая живая область.
                .clearAndSetSemantics {
                    contentDescription = counter
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            AnimatedDigits(counter, increasing)
        }
        BareIconButton(
            onClick = { go(1) },
            icon = painterResource(R.drawable.ic_chevron_right),
            contentDescription = stringResource(R.string.hadith_detail_next),
            foreground = palette.onAccent,
            enabled = current < count - 1,
        )
    }
}

/**
 * Текст, у которого меняющиеся символы сменяются по одному (iOS `.numericText`): цифра уходит вверх,
 * новая приходит снизу, 300 мс. Символы сопоставляются с конца — «9 из 50» → «10 из 50» меняет только номер.
 */
@Composable
private fun AnimatedDigits(text: String, increasing: Boolean) {
    val style = InabahType.footnote.monospacedDigits().copy(fontWeight = FontWeight.SemiBold)
    val color = InabahTheme.palette.onAccent
    Row {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val direction = if (increasing) 1 else -1
                        (slideInVertically(Motion.highlight()) { it * direction } + fadeIn(Motion.highlight())) togetherWith
                            (slideOutVertically(Motion.highlight()) { -it * direction } + fadeOut(Motion.highlight()))
                    },
                    label = "digit",
                ) { Text(it.toString(), color = color, style = style, maxLines = 1) }
            }
        }
    }
}
