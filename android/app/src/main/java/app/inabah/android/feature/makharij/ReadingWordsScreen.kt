package app.inabah.android.feature.makharij

import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import app.inabah.android.core.designsystem.Motion
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.content.model.ReadingWord
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.LinearProgressBar
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings
import kotlinx.coroutines.launch
import androidx.compose.ui.util.lerp
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import app.inabah.android.core.designsystem.components.FeaturedVerseBox
import app.inabah.android.core.designsystem.components.FeaturedVerse
import kotlinx.coroutines.delay
import app.inabah.android.core.designsystem.components.arabicTextStyle
import app.inabah.android.core.designsystem.components.ParchmentHorizontalPadding
import app.inabah.android.core.content.model.ReadingWords
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf

/**
 * «Читаем слова» (вариант A, решение пользователя 2026-10-15): страницы плиток на пергаменте,
 * нажатие на плитку - «прочитал ещё раз» (в углу «3/10», после 10 - зелёная). Плитки - по 3, по 2 или во
 * всю ширину по измеренной ширине слова, короткие - на первых страницах (`wordPages`, 2026-10-16).
 * Транскрипция и глаз - на первой странице, дальше - лампочка «подсказка» показывает её на миг.
 * Шаг пройден, когда прочитаны все слова (и урок).
 */
@Composable
fun ReadingWordsScreen(
    stepNumber: Int,
    store: ReadingStore,
    progress: ReadingProgress,
    settings: ReadingSettings,
    onBack: () -> Unit,
    onOpenStep: (Int) -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenSteps: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    val wordCounts by progress.wordCounts.collectAsStateWithLifecycle()
    val lessonsDone by progress.lessonsDone.collectAsStateWithLifecycle()
    val course = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value
    val pages = rememberWordPages(course?.step(stepNumber)?.words)
    val groupCount = pages.size
    // Страницы листаются свайпом в обе стороны и кнопками (просьба пользователя 2026-10-15); страница переживает поворот.
    val pager = rememberPagerState { groupCount }
    val scope = rememberCoroutineScope()
    val group = pager.currentPage
    // Подсказка: транскрипция страницы на миг (просьба пользователя 2026-10-16); другая страница - гаснет.
    var hintPage by remember { mutableStateOf<Int?>(null) }
    var hintRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(hintRequest) {
        if (hintRequest == 0) return@LaunchedEffect
        delay(HINT_MILLIS)
        hintPage = null
    }
    ReadingScaffold(
        store = store,
        title = stringResource(R.string.makharij_words_title),
        subtitle = if (groupCount > 0) stringResource(R.string.makharij_words_group, group + 1, groupCount) else null,
        onBack = onBack,
        modifier = modifier,
        actions = {
            // Глаз - на первой странице (там транскрипция видна), дальше - подсказка.
            if (group == 0) {
                TransliterationToggle(hidden) { settings.setTransliterationHidden(!hidden) }
            } else if (groupCount > 0) {
                TransliterationHint {
                    hintPage = group
                    hintRequest++
                }
            }
        },
    ) { loaded ->
        val step = loaded.step(stepNumber)
        val words = step?.words
        if (step == null || words == null || words.items.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                NoticeCard(
                    icon = R.drawable.ic_hourglass,
                    title = stringResource(R.string.makharij_lesson_soon_title),
                    message = stringResource(R.string.makharij_lesson_soon_message),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
            }
            return@ReadingScaffold
        }
        val counts = wordCounts[step.number].orEmpty()
        val allRead = wordsDone(step, counts)
        val completed = completedSteps(loaded, lessonsDone, wordCounts)
        // Праздник - когда шаг стал пройденным на этом экране (дочитано последнее слово), не при повторном входе.
        val stepDone = step.number in completed
        var wasDone by rememberSaveable { mutableStateOf(stepDone) }
        var celebrate by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(stepDone) {
            if (stepDone && !wasDone) celebrate = true
            wasDone = stepDone
        }
        val next = loaded.steps.firstOrNull { it.number > step.number && it.hasLesson && isUnlocked(loaded, completed, it) }
        // Увеличенная плитка (долгое нажатие, просьба пользователя 2026-10-17): номер слова и где стояла плитка.
        var zoom by remember { mutableStateOf<WordZoom?>(null) }
        Box(Modifier.fillMaxSize()) {
            // Кнопки групп - внизу экрана, вне прокрутки: не прыгают, когда высота группы меняется
            // (последняя группа короче) - просьба пользователя 2026-10-15.
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    WordsProgress(
                        read = wordsReadCount(step, counts),
                        total = words.items.size,
                        repeat = words.repeat,
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                    ReadAloudNote(Modifier.padding(horizontal = Spacing.xl))
                    // Страница - во всю ширину, отступ - внутри: соседние группы не выглядывают по краям.
                    HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                        ParchmentPanel(
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                            topCornerRadius = Radius.card,
                            bottomCornerRadius = Radius.card,
                        ) {
                            WordGrid(
                                rows = pages.getOrElse(page) { emptyList() },
                                counts = { counts.getOrNull(it) ?: 0 },
                                repeat = words.repeat,
                                showTransliteration = (page == 0 && !hidden) || hintPage == page,
                                onRead = { progress.readWord(step.number, it, words.items.size, words.repeat) },
                                zoomed = zoom?.index,
                                onZoom = { index, bounds -> zoom = WordZoom(index, bounds, page) },
                            )
                        }
                    }
                    if (allRead) {
                        WordsFinished(
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                            lessonDone = step.number in lessonsDone,
                            next = next,
                            onOpenStep = onOpenStep,
                            onOpenQuiz = onOpenQuiz,
                            onOpenSteps = onOpenSteps,
                        )
                    }
                }
                GroupNavigation(
                    current = pager.currentPage,
                    count = groupCount,
                    onPrevious = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier
                        .padding(horizontal = Spacing.xl)
                        .padding(top = Spacing.s, bottom = Spacing.m + contentPadding.calculateBottomPadding()),
                )
            }
            zoom?.let { current ->
                val word = words.items.getOrNull(current.index) ?: return@let
                WordZoomOverlay(
                    zoom = current,
                    word = word,
                    count = counts.getOrNull(current.index) ?: 0,
                    repeat = words.repeat,
                    showTransliteration = (current.page == 0 && !hidden) || hintPage == current.page,
                    onRead = { progress.readWord(step.number, current.index, words.items.size, words.repeat) },
                    onDismiss = { zoom = null },
                )
            }
            if (celebrate) {
                StepCompletedOverlay(
                    step = step,
                    next = next,
                    onOpenStep = onOpenStep,
                    onOpenSteps = onOpenSteps,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun WordsProgress(read: Int, total: Int, repeat: Int, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(R.string.makharij_words_progress, read, total, pluralStringResource(R.plurals.zikr_repetitions, repeat, repeat)),
            color = palette.onAccentSecondary,
            style = InabahType.footnote,
        )
        LinearProgressBar(
            fraction = if (total == 0) 0f else read.toFloat() / total,
            track = palette.track,
            fill = readingColors.tint,
        )
    }
}

/**
 * Страницы «Читаем слова» по ширине слов: слово меряется стилем плитки, ряд - по ширине экрана внутри
 * пергамента; плиток в ряду - сколько влезает (3, 2, иначе во всю ширину). Пересчёт - при смене слов или
 * ширины (поворот, другой экран).
 */
@Composable
private fun rememberWordPages(words: ReadingWords?): List<List<WordRow>> {
    val measurer = rememberTextMeasurer()
    val style = arabicTextStyle(WORD_SIZE, bold = true)
    val density = LocalDensity.current
    val screenWidth = LocalWindowInfo.current.containerSize.width
    return remember(words, screenWidth, style, density) {
        if (words == null) return@remember emptyList()
        val rowWidth = screenWidth - with(density) { (Spacing.xl * 2 + ParchmentHorizontalPadding * 2).roundToPx() }
        val gap = with(density) { Spacing.s.roundToPx() }
        val padding = with(density) { (TileTextPadding * 2).roundToPx() }
        val columnsOf = { word: ReadingWord ->
            val width = measurer.measure(word.arabic, style, maxLines = 1, softWrap = false).size.width
            (ReadingWords.MAX_COLUMNS downTo 2).firstOrNull { columns ->
                width <= ((rowWidth - gap * (columns - 1)) / columns - padding) * WORD_FIT
            } ?: 1
        }
        if (words.pairs) pairPages(words.items, words.rowsPerPage) else wordPages(words.items, columnsOf, words.rowsPerPage)
    }
}

/** Ряды страницы справа налево; в неполном ряду - пустые места, чтобы плитки были одной ширины. */
@Composable
private fun WordGrid(
    rows: List<WordRow>,
    counts: (Int) -> Int,
    repeat: Int,
    showTransliteration: Boolean,
    onRead: (Int) -> Unit,
    /** Номер слова, увеличенного сейчас: его плитка на месте не видна (она «вылетела» в центр). */
    zoomed: Int?,
    onZoom: (Int, Rect) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (row in rows) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for ((index, word) in row.words) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            WordTile(
                                word = word,
                                count = counts(index),
                                repeat = repeat,
                                transliteration = word.transliteration,
                                showTransliteration = showTransliteration,
                                onRead = { onRead(index) },
                                onZoom = { bounds -> onZoom(index, bounds) },
                                // В паре словосочетание шире слова.
                                modifier = Modifier
                                    .weight(if (row.isPair && word.isPhrase) PAIR_PHRASE_WEIGHT else 1f)
                                    .fillMaxHeight()
                                    .alpha(if (index == zoomed) 0f else 1f),
                            )
                        }
                    }
                    repeat(row.columns - row.words.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Плитка слова: «3/10» в углу, слово, транскрипция (место под ней есть всегда - плитки одной высоты на
 * всех страницах, видна - [showTransliteration]); прочитано 10 раз - зелёная.
 */
@Composable
private fun WordTile(
    word: ReadingWord,
    count: Int,
    repeat: Int,
    transliteration: String?,
    showTransliteration: Boolean,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
    /** Долгое нажатие - увеличить: где плитка на экране (в координатах корня); нет - не увеличивается. */
    onZoom: ((Rect) -> Unit)? = null,
    wordSize: Float = WORD_SIZE,
    /** Слово и транскрипция - по центру плитки по вертикали (увеличенная плитка), иначе - сверху. */
    centered: Boolean = false,
    /**
     * Масштаб содержимого (слово, счёт) поверх масштаба плитки: в полёте плитка растягивается по ширине
     * и высоте по-разному, а текст - одинаково, без искажений; нет - как у плитки.
     */
    contentScale: (() -> Offset)? = null,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val isDone = count >= repeat
    // Где плитка - для полёта в центр; хранится без состояния Compose: меняется при каждой раскладке.
    val bounds = remember { arrayOf(Rect.Zero) }
    val zoomLabel = stringResource(R.string.makharij_words_zoom_action)
    val initiallyDone = remember(word) { isDone }
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val readLabel = stringResource(R.string.makharij_words_read_action)
    val state = stringResource(R.string.makharij_words_tile_state, count, repeat)
    val description = listOfNotNull(word.arabic, transliteration?.takeIf { showTransliteration }).joinToString(", ")
    val transliterationAlpha by animateFloatAsState(if (showTransliteration) 1f else 0f, label = "wordTransliteration")
    // Живость (просьба пользователя 2026-10-15): плитка наливается золотом снизу вверх с каждым
    // прочтением, толчок и отклик, как у счётчика зикра; на последнем - вспышка ✦ и галочка.
    val fill by animateFloatAsState((count.toFloat() / repeat).coerceIn(0f, 1f), Motion.progress(), label = "wordFill")
    // Цвет раздела, не золото: золото на пергаменте почти не видно.
    val gold = colors.tint.copy(alpha = WORD_FILL_ALPHA)
    val read = {
        haptics.performHapticFeedback(
            if (count + 1 >= repeat) HapticFeedbackType.Confirm else HapticFeedbackType.ContextClick,
        )
        onRead()
    }
    val zoom = onZoom?.let { callback ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            callback(bounds[0])
        }
    }
    Box(modifier.popOn(count).onGloballyPositioned { bounds[0] = it.boundsInRoot() }) {
        Box(
            Modifier
                .fillMaxSize()
                // Прочитанное не считается, но увеличить его можно.
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onLongClick = zoom,
                    onClick = { if (!isDone) read() },
                )
                .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                .surface(
                    if (isDone) palette.success.copy(alpha = DONE_FILL_ALPHA) else colors.parchmentCell,
                    Radius.box,
                    border = if (isDone) palette.parchmentAccent else colors.parchmentCellBorder,
                )
                .clip(RoundedCornerShape(Radius.box))
                .drawBehind {
                    if (!isDone && fill > 0f) {
                        val height = size.height * fill
                        drawRect(gold, topLeft = Offset(0f, size.height - height), size = size.copy(height = height))
                    }
                }
                .clearAndSetSemantics {
                    contentDescription = description
                    stateDescription = state
                    if (!isDone) onClick(readLabel) { read(); true }
                    if (zoom != null) customActions = listOf(CustomAccessibilityAction(zoomLabel) { zoom(); true })
                },
        ) {
            if (isDone) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = palette.parchmentAccent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .contentScale(contentScale, TransformOrigin(0f, 0f))
                        .padding(start = Spacing.xxs, top = Spacing.xxs)
                        .size(DoneIconSize),
                )
            } else {
                Text(
                    stringResource(R.string.makharij_words_count, count, repeat),
                    color = colors.parchmentCaption,
                    style = InabahType.caption2.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .contentScale(contentScale, TransformOrigin(0f, 0f))
                        .padding(start = Spacing.xs, top = Spacing.xxs),
                )
            }
            Column(
                Modifier
                    .align(if (centered) Alignment.Center else Alignment.TopCenter)
                    .contentScale(contentScale, TransformOrigin.Center)
                    .fillMaxWidth()
                    .padding(start = TileTextPadding, end = TileTextPadding, top = Spacing.m, bottom = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Длинное словосочетание (предложение) - до 2 строк, не обрезается.
                ArabicText(word.arabic, wordSize, palette.parchmentText, bold = true, maxLines = if (word.isPhrase) PHRASE_LINES else 1, textAlign = TextAlign.Center)
                if (transliteration != null) {
                    Text(
                        transliteration,
                        color = colors.parchmentCaption,
                        style = InabahType.caption,
                        modifier = Modifier.alpha(transliterationAlpha),
                    )
                }
            }
        }
        // Вспышка - только в момент, когда слово дочитано (не у уже прочитанных при открытии экрана).
        if (isDone && !initiallyDone) SparkleBurst(key = word, color = palette.gold, modifier = Modifier.matchParentSize())
    }
}

/** Увеличенное слово: номер в шаге, где стояла плитка (в координатах корня), страница (для транскрипции). */
private data class WordZoom(val index: Int, val from: Rect, val page: Int)

/**
 * Увеличенная плитка (просьба пользователя 2026-10-17): долгое нажатие - плитка вылетает из своего места в
 * центр и растёт, вокруг - затемнение; касание по ней - «прочитал ещё раз», по затемнению или «Назад» -
 * плитка возвращается на место. Полёт - только трансформации отрисовки (`graphicsLayer`), без перевёрстки.
 */
@Composable
private fun WordZoomOverlay(
    zoom: WordZoom,
    word: ReadingWord,
    count: Int,
    repeat: Int,
    showTransliteration: Boolean,
    onRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    val scope = rememberCoroutineScope()
    val progress = remember(zoom.index) { Animatable(0f) }
    // Словосочетание (предложение) - мельче: до 2 строк во всю ширину.
    val zoomSize = if (word.isPhrase) ZOOM_PHRASE_SIZE else ZOOM_WORD_SIZE
    // Где окажется увеличенная плитка - от неё считается полёт из [WordZoom.from]. Пока не измерена, плитка
    // не видна и полёт не начинается: иначе первый кадр рисовался сразу в центре в полный размер.
    var target by remember { mutableStateOf(Rect.Zero) }
    val measured = target.width > 0f && target.height > 0f
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(zoom.index, measured) {
        if (measured && !closing) progress.animateTo(1f, Motion.zoom())
    }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, Motion.zoom())
                onDismiss()
            }
        }
    }
    BackHandler(onBack = dismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(palette.shadow.copy(alpha = ZOOM_SCRIM_ALPHA))
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        WordTile(
            word = word,
            count = count,
            repeat = repeat,
            transliteration = word.transliteration,
            showTransliteration = showTransliteration,
            onRead = onRead,
            wordSize = zoomSize,
            centered = true,
            // Слово в полёте растёт одинаково по ширине и высоте: от кегля исходной плитки до крупного.
            contentScale = {
                val p = progress.value
                val uniform = lerp(WORD_SIZE / zoomSize, 1f, p)
                val (sx, sy) = tileScale(target, zoom.from, p)
                Offset(uniform / sx, uniform / sy)
            },
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                .height(ZoomTileHeight)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    if (bounds != target) target = bounds
                }
                .graphicsLayer {
                    val to = target
                    val from = zoom.from
                    alpha = if (measured) 1f else 0f
                    if (measured && from.width > 0f) {
                        val p = progress.value
                        val (sx, sy) = tileScale(to, from, p)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = sx
                        scaleY = sy
                        translationX = lerp(from.left - to.left, 0f, p)
                        translationY = lerp(from.top - to.top, 0f, p)
                    }
                }
                // Фон плитки полупрозрачный (рассчитан на пергамент) - под увеличенной подложен пергамент.
                .background(gradients.parchment, RoundedCornerShape(Radius.box)),
        )
    }
}

/** Масштаб плитки в полёте по ширине и высоте: из [from] (исходная плитка) в [to] (увеличенная). */
private fun tileScale(to: Rect, from: Rect, progress: Float): Pair<Float, Float> =
    if (to.width > 0f && to.height > 0f && from.width > 0f) {
        lerp(from.width / to.width, 1f, progress) to lerp(from.height / to.height, 1f, progress)
    } else {
        1f to 1f
    }

/** Поправка масштаба содержимого плитки ([scale] - по ширине и высоте), вокруг [origin]; нет - без неё. */
private fun Modifier.contentScale(scale: (() -> Offset)?, origin: TransformOrigin): Modifier =
    if (scale == null) this else graphicsLayer {
        val (x, y) = scale()
        transformOrigin = origin
        scaleX = x
        scaleY = y
    }

/**
 * Мотивация над словами (просьба пользователя 2026-10-16: читать нужно много, и только вслух): «Читайте
 * каждое слово вслух…» сразу под прогрессом, по ⓘ - хадис о двойной награде тому, кому чтение даётся
 * с трудом (как пояснения урока). На каждой странице.
 */
@Composable
private fun ReadAloudNote(modifier: Modifier = Modifier) {
    Box(modifier) {
        InfoTextCard(stringResource(R.string.makharij_words_read_aloud)) {
            FeaturedVerseBox(
                FeaturedVerse(
                    arabic = HADITH_ARABIC,
                    translation = stringResource(R.string.makharij_words_hadith_translation),
                    reference = stringResource(R.string.makharij_words_hadith_reference),
                    isFramed = false,
                ),
                modifier = Modifier.padding(vertical = Spacing.xl),
            )
        }
    }
}

/** ‹ группа › под пергаментом. */
@Composable
private fun GroupNavigation(
    current: Int,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (current > 0) {
            ReadingOutlineButton(stringResource(R.string.makharij_words_previous), onClick = onPrevious, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (current < count - 1) {
            ReadingButton(stringResource(R.string.makharij_words_next), onClick = onNext, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Все слова прочитаны: шаг пройден (если и урок пройден) - следующий шаг или список. */
@Composable
private fun WordsFinished(
    lessonDone: Boolean,
    next: ReadingStep?,
    onOpenStep: (Int) -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenSteps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Spacer(Modifier.height(Spacing.s))
        NoticeCard(
            icon = R.drawable.ic_check_circle,
            title = stringResource(R.string.makharij_words_done_title),
            message = stringResource(
                if (lessonDone) R.string.makharij_words_done_message else R.string.makharij_words_done_quiz,
            ),
        )
        if (!lessonDone) {
            ReadingButton(stringResource(R.string.makharij_lesson_check), onClick = onOpenQuiz)
        } else {
            next?.let { ReadingButton(stringResource(R.string.makharij_quiz_next_step, it.title), onClick = { onOpenStep(it.number) }) }
        }
        ReadingOutlineButton(stringResource(R.string.makharij_quiz_to_steps), onClick = onOpenSteps)
    }
}

private const val WORD_SIZE = 28f
/** Увеличенная плитка: кегль слова, высота, затемнение вокруг. */
private const val ZOOM_WORD_SIZE = 60f
private const val ZOOM_PHRASE_SIZE = 44f
private val ZoomTileHeight = 240.dp
private const val ZOOM_SCRIM_ALPHA = 0.6f
private const val PAIR_PHRASE_WEIGHT = 2f
private const val PHRASE_LINES = 2
// Арабский контент - не переводится (Муслим, 798).
private const val HADITH_ARABIC = "الْمَاهِرُ بِالْقُرْآنِ مَعَ السَّفَرَةِ الْكِرَامِ الْبَرَرَةِ، وَالَّذِي يَقْرَأُ الْقُرْآنَ وَيَتَتَعْتَعُ فِيهِ وَهُوَ عَلَيْهِ شَاقٌّ لَهُ أَجْرَانِ"
/** Поле слова в плитке слева и справа - и в расчёте ширины ([rememberWordPages]). */
private val TileTextPadding = Spacing.xs
/** Слово «влезает» в плитку, если занимает не больше этой доли её ширины: впритык читать тесно. */
private const val WORD_FIT = 0.8f
/** Сколько видна транскрипция по подсказке. */
private const val HINT_MILLIS = 700L
private const val DONE_FILL_ALPHA = 0.22f
private const val WORD_FILL_ALPHA = 0.5f
private val DoneIconSize = 14.dp
