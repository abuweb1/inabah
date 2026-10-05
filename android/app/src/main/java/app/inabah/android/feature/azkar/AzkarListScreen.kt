package app.inabah.android.feature.azkar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import app.inabah.android.core.audio.AudioPlayerController
import app.inabah.android.core.settings.PlaylistSettings
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
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
import app.inabah.android.core.designsystem.components.ContentError
import app.inabah.android.core.designsystem.components.PrimaryButton
import app.inabah.android.core.designsystem.components.ProgressHeader
import app.inabah.android.core.designsystem.components.TopBarSubtitle
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.formatting.formatPercent
import app.inabah.android.core.settings.ReadingSettings
import kotlin.math.floor

// Арабский контент оверлея — не переводится.
private const val COMPLETION_TITLE = "مَا شَاءَ اللَّهُ"
private const val COMPLETION_HAMD = "الحمد لله رب العالمين"
private const val COMPLETION_TITLE_SIZE = 52f
private const val COMPLETION_HAMD_SIZE = 24f

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
    player: AudioPlayerController,
    playlistSettings: PlaylistSettings,
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
            centerTitle = false,
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
                    AzkarProgressHeader(store, section)
                    AzkarFeed(section, current.value, fontSize.toFloat(), player, playlistSettings, contentPadding)
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
private fun AzkarProgressHeader(store: AzkarStore, section: AzkarSection) {
    val theme = LocalInabahTheme.current
    val progress by store.progress(section).collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    ProgressHeader(
        label = stringResource(R.string.azkar_progress, progress.completed, progress.total),
        percent = formatPercent(headerPercent(progress), locale),
        fraction = progress.fraction.toFloat(),
        background = section.headerColor(theme),
        fill = theme.gradients.progressFill,
    )
}

/**
 * Лента — обычная колонка, не ленивая: соседи сдвигаются вместе со сворачиванием карточки. Плеер
 * читает только лента: карточкам — готовое [ZikrAudioState], позицию воспроизведения — никто.
 */
@Composable
private fun AzkarFeed(
    section: AzkarSection,
    sessions: List<ZikrSession>,
    arabicFontSize: Float,
    player: AudioPlayerController,
    playlistSettings: PlaylistSettings,
    contentPadding: PaddingValues,
) {
    val animatedSize by animateFloatAsState(arabicFontSize, Motion.fontSize(), label = "arabicSize")
    val playerState by player.state.collectAsStateWithLifecycle()
    val repeatsByCount by playlistSettings.repeatsByCount.collectAsStateWithLifecycle()
    val pauseSeconds by playlistSettings.pauseBetween.collectAsStateWithLifecycle()
    val rate by playlistSettings.rate.collectAsStateWithLifecycle()
    val playlistId = section.playlistId
    val isPlaylistActive = playerState.isPlaylistActive(playlistId)
    val scrollState = rememberScrollState()
    val cardTops = remember { mutableMapOf<String, Float>() }
    AutoScrollToPlaying(scrollState, cardTops, trackId = playerState.track?.id.takeIf { isPlaylistActive })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.l)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        sessions.forEach { session ->
            key(session.id) {
                val track = session.zikr.toAudioTrack()
                ZikrCard(
                    session = session,
                    arabicFontSize = animatedSize,
                    audio = ZikrAudioState(
                        isActive = playerState.isActive(track.id),
                        isPlaylistCurrent = isPlaylistActive && playerState.track?.id == track.id,
                    ),
                    onPlay = { player.play(track) },
                    modifier = Modifier.onPlaced { cardTops[track.id] = it.positionInParent().y },
                )
            }
        }
        val playlist = sessions.map { it.zikr.toAudioTrack(repeatsByCount) }
        AzkarPlayAllCard(
            isActive = isPlaylistActive,
            repeatsByCount = repeatsByCount,
            pauseSeconds = pauseSeconds,
            rate = rate,
            onRepeatsChange = playlistSettings::setRepeatsByCount,
            onPauseChange = {
                playlistSettings.setPauseBetween(it)
                player.updatePlaylist(playlistId, rate, it)
            },
            onRateChange = {
                playlistSettings.setRate(it)
                player.updatePlaylist(playlistId, it, pauseSeconds)
            },
            onListen = { player.playAll(playlistId, playlist, rate, pauseSeconds) },
            enabled = playlist.isNotEmpty(),
        )
    }
}

/**
 * При смене звучащего зикра «Прослушать все» лента едет к его карточке (`Motion.collapse`) — если
 * пользователь сейчас не листает и не листал последние 2 с (iOS `autoScrollCooldown`).
 */
@Composable
private fun AutoScrollToPlaying(scrollState: ScrollState, cardTops: Map<String, Float>, trackId: String?) {
    val dragged by scrollState.interactionSource.collectIsDraggedAsState()
    var autoScrolling by remember { mutableStateOf(false) }
    // Листание пальцем и его докрутка (не наша прокрутка к карточке).
    val userScrolling by remember { derivedStateOf { dragged || (scrollState.isScrollInProgress && !autoScrolling) } }
    val lastUserScroll = remember { longArrayOf(Long.MIN_VALUE / 2) }
    // Время начала и конца листания: «не листал последние 2 с» считается от конца.
    LaunchedEffect(Unit) { snapshotFlow { userScrolling }.drop(1).collect { lastUserScroll[0] = SystemClock.uptimeMillis() } }
    LaunchedEffect(trackId) {
        val target = trackId?.let(cardTops::get) ?: return@LaunchedEffect
        val quietFor = SystemClock.uptimeMillis() - lastUserScroll[0]
        if (userScrolling || quietFor < AUTO_SCROLL_COOLDOWN_MILLIS) return@LaunchedEffect
        autoScrolling = true
        try {
            scrollState.animateScrollTo(target.toInt(), Motion.collapse())
        } finally {
            autoScrolling = false
        }
    }
}

private const val AUTO_SCROLL_COOLDOWN_MILLIS = 2_000L

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
