package app.inabah.android.feature.azkar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.FontSizeControls
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.LinearProgressBar
import app.inabah.android.core.designsystem.components.PrimaryButton
import app.inabah.android.core.designsystem.components.TopBarSubtitle
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.designsystem.monospacedDigits
import app.inabah.android.core.formatting.formatPercent
import app.inabah.android.core.settings.ReadingSettings
import kotlin.math.floor

// Арабский контент оверлея — не переводится.
private const val COMPLETION_TITLE = "مَا شَاءَ اللَّهُ"
private const val COMPLETION_HAMD = "الحمد لله رب العالمين"
private const val COMPLETION_TITLE_SIZE = 52f
private const val COMPLETION_HAMD_SIZE = 24f
private val ErrorIconSize = 44.dp

/** Процент шапки: половина — вверх, как `Math.round` прототипа (2 из 16 → 13 %). */
internal fun headerPercent(progress: SectionProgress): Int = floor(progress.fraction * PERCENT + HALF).toInt()

private const val PERCENT = 100
private const val HALF = 0.5

/**
 * Список раздела (iOS `AzkarListView`): навбар с «А− А+», шапка прогресса, лента карточек
 * и оверлей завершения. Сам экран не читает счётчики зикров — их читают карточки и шапка.
 */
@Composable
fun AzkarListScreen(
    section: AzkarSection,
    store: AzkarStore,
    readingSettings: ReadingSettings,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val state by store.state(section).collectAsStateWithLifecycle()
    val fontSize by readingSettings.arabicFontSize.collectAsStateWithLifecycle()
    // Сохраняется: переход на другую вкладку и пересоздание активности оверлей не прячут
    // (стор уже отметил показ — заново он не появится).
    var showsCompletion by rememberSaveable { mutableStateOf(false) }
    // Повторная попытка живёт, пока экран на месте: ушли — загрузка отменяется (стор вернёт Idle).
    val scope = rememberCoroutineScope()
    LaunchedEffect(section) { store.load(section) }
    CompletionWatcher(store, section, onShow = { showsCompletion = it })

    Column(modifier.fillMaxSize().background(section.background(theme))) {
        InabahTopBar(
            title = stringResource(section.title),
            tint = theme.palette.tabAzkar,
            onBack = onBack,
            subtitle = { TopBarSubtitle(stringResource(section.subtitle)) },
            background = section.headerColor(theme),
        ) {
            FontSizeControls(
                canDecrease = fontSize > ReadingSettings.MIN_SIZE,
                canIncrease = fontSize < ReadingSettings.MAX_SIZE,
                onDecrease = readingSettings::decreaseArabicFontSize,
                onIncrease = readingSettings::increaseArabicFontSize,
                tint = theme.palette.tabAzkar,
            )
        }
        Box(Modifier.fillMaxSize()) {
            when (val current = state) {
                Loadable.Idle, Loadable.Loading -> CircularProgressIndicator(
                    color = InabahTheme.palette.onAccent,
                    modifier = Modifier.align(Alignment.Center),
                )
                is Loadable.Failed -> ContentError(
                    onRetry = { scope.launch { store.load(section) } },
                    modifier = Modifier.align(Alignment.Center),
                )
                // Под оверлеем лента скрыта от TalkBack.
                is Loadable.Loaded -> Column(
                    Modifier
                        .fillMaxSize()
                        .then(if (showsCompletion) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    ProgressHeader(store, section)
                    AzkarFeed(current.value, fontSize.toFloat(), contentPadding)
                }
            }
            // Поверх шапки и ленты, навбар не закрывает (как в iOS). Полное имя — без ColumnScope-варианта.
            androidx.compose.animation.AnimatedVisibility(
                visible = showsCompletion,
                enter = fadeIn(Motion.overlay()),
                exit = fadeOut(Motion.overlay()),
            ) {
                CompletionOverlay(section, onGoHome, contentPadding)
            }
        }
    }
}

/**
 * Оверлей «مَا شَاءَ اللَّهُ» с паузой после выполнения всех зикров (iOS `AzkarCompletionWatcher`):
 * один раз за прохождение; раздел снова не выполнен — следующее завершение покажет снова.
 */
@Composable
private fun CompletionWatcher(store: AzkarStore, section: AzkarSection, onShow: (Boolean) -> Unit) {
    val progress by store.progress(section).collectAsStateWithLifecycle()
    // До загрузки (total = 0) раздел не «не выполнен» — пометку не трогать.
    val isLoaded = progress.total > 0
    val isFinished = progress.isFinished
    LaunchedEffect(isLoaded, isFinished) {
        if (!isLoaded) return@LaunchedEffect
        if (!isFinished) {
            store.resetCompletionAcknowledgement(section)
            onShow(false)
            return@LaunchedEffect
        }
        if (!store.shouldPresentCompletion(section)) return@LaunchedEffect
        // Пауза — чтобы последняя карточка успела показать «готово».
        delay(Motion.COMPLETION_DELAY_MILLIS)
        store.acknowledgeCompletion(section)
        onShow(true)
    }
}

/** «Выполнено: N из M» и процент, полоса под ними; цвет — навбара. */
@Composable
private fun ProgressHeader(store: AzkarStore, section: AzkarSection) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val progress by store.progress(section).collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val label = stringResource(R.string.azkar_progress, progress.completed, progress.total)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(section.headerColor(theme))
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xs, bottom = Spacing.m)
            .clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = palette.onAccentSecondary, style = InabahType.caption)
            Text(formatPercent(headerPercent(progress), locale), color = palette.onAccentSecondary,
                style = InabahType.caption.monospacedDigits())
        }
        val fraction by animateFloatAsState(progress.fraction.toFloat(), Motion.progress(), label = "header")
        LinearProgressBar(fraction, palette.track, theme.gradients.progressFill)
    }
}

/** Лента — обычная колонка, не ленивая: соседи сдвигаются вместе со сворачиванием карточки. */
@Composable
private fun AzkarFeed(sessions: List<ZikrSession>, arabicFontSize: Float, contentPadding: PaddingValues) {
    val animatedSize by animateFloatAsState(arabicFontSize, Motion.fontSize(), label = "arabicSize")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.l)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        sessions.forEach { session ->
            key(session.id) { ZikrCard(session, animatedSize) }
        }
    }
}

@Composable
private fun ContentError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    Column(
        modifier.padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = palette.onAccentSecondary,
            modifier = Modifier.size(ErrorIconSize))
        Text(stringResource(R.string.content_error_title), color = palette.onAccent,
            style = InabahType.title3.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.content_error_message), color = palette.onAccentSecondary,
            style = InabahType.subheadline, textAlign = TextAlign.Center)
        PrimaryButton(onClick = onRetry, text = stringResource(R.string.content_error_retry),
            modifier = Modifier.padding(top = Spacing.m))
    }
}

/** «مَا شَاءَ اللَّهُ» (iOS `AzkarCompletionView`); «← На главную» очищает стек вкладки. */
@Composable
private fun CompletionOverlay(section: AzkarSection, onGoHome: () -> Unit, contentPadding: PaddingValues) {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Касания не проходят к скрытой ленте (иначе невидимый ↺ сбросил бы зикр).
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .background(palette.background)
            .padding(horizontal = Spacing.xxl, vertical = Spacing.section)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
    ) {
        val title = stringResource(R.string.azkar_completion_title)
        ArabicText(
            COMPLETION_TITLE, COMPLETION_TITLE_SIZE, palette.gold, bold = true, textAlign = TextAlign.Center,
            modifier = Modifier
                .pulse()
                .clearAndSetSemantics {
                    contentDescription = title
                    heading()
                },
        )
        val hamd = stringResource(R.string.azkar_completion_hamd)
        ArabicText(
            COMPLETION_HAMD, COMPLETION_HAMD_SIZE, palette.parchmentInk, textAlign = TextAlign.Center,
            modifier = Modifier
                .surface(gradients.parchment, Radius.control, border = palette.successDeep, lineWidth = Size.parchmentBorder)
                .padding(horizontal = Spacing.xlPlus, vertical = Spacing.m)
                .clearAndSetSemantics { contentDescription = hamd },
        )
        Text(stringResource(section.completionMessage), color = palette.textSecondary,
            style = InabahType.subheadline, textAlign = TextAlign.Center)
        PrimaryButton(
            onClick = onGoHome,
            text = stringResource(R.string.azkar_completion_home),
            leadingIcon = painterResource(R.drawable.ic_arrow_back),
        )
    }
}

/** Пульсация 1 ↔ 1,05 на всё время показа (iOS `phaseAnimator`). */
@Composable
private fun Modifier.pulse(): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = Motion.PULSE_SCALE,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE_MILLIS), RepeatMode.Reverse),
        label = "pulseScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
