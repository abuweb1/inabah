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

/**
 * «Читаем слова» (вариант A, решение пользователя 2026-10-15): группы по 12 слов плитками на пергаменте,
 * нажатие на плитку - «прочитал ещё раз» (в углу «3/10», после 10 - зелёная). Транскрипция и глаз -
 * только в первой группе, дальше - без подсказок. Шаг пройден, когда прочитаны все слова (и урок).
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
    val groupCount = course?.step(stepNumber)?.words?.groups?.size ?: 0
    // Группы листаются свайпом в обе стороны и кнопками (просьба пользователя 2026-10-15); страница переживает поворот.
    val pager = rememberPagerState { groupCount }
    val scope = rememberCoroutineScope()
    val group = pager.currentPage
    ReadingScaffold(
        store = store,
        title = stringResource(R.string.makharij_words_title),
        subtitle = if (groupCount > 0) stringResource(R.string.makharij_words_group, group + 1, groupCount) else null,
        onBack = onBack,
        modifier = modifier,
        actions = {
            // Глаз - только там, где есть транскрипция (первая группа: первые 12 слов).
            if (group == 0) TransliterationToggle(hidden) { settings.setTransliterationHidden(!hidden) }
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
        val groups = words.groups
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
                    // Страница - во всю ширину, отступ - внутри: соседние группы не выглядывают по краям.
                    HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                        val offset = page * words.groupSize
                        ParchmentPanel(
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                            topCornerRadius = Radius.card,
                            bottomCornerRadius = Radius.card,
                        ) {
                            WordGrid(
                                words = groups[page],
                                counts = { counts.getOrNull(offset + it) ?: 0 },
                                repeat = words.repeat,
                                hidden = hidden,
                                onRead = { progress.readWord(step.number, offset + it, words.items.size, words.repeat) },
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
                    count = groups.size,
                    onPrevious = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier
                        .padding(horizontal = Spacing.xl)
                        .padding(top = Spacing.s, bottom = Spacing.m + contentPadding.calculateBottomPadding()),
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
            stringResource(R.string.makharij_words_progress, read, total, repeat),
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

/** Слова группы по 3 в ряд справа налево. */
@Composable
private fun WordGrid(
    words: List<ReadingWord>,
    counts: (Int) -> Int,
    repeat: Int,
    hidden: Boolean,
    onRead: (Int) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            words.withIndex().chunked(WORD_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for ((index, word) in row) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            WordTile(
                                word = word,
                                count = counts(index),
                                repeat = repeat,
                                // Транскрипция - у первых 12 слов, дальше без подсказок (решение пользователя).
                                transliteration = word.transliteration,
                                hidden = hidden,
                                onRead = { onRead(index) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(WORD_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Плитка слова: «3/10» в углу, слово, транскрипция (первая группа); прочитано 10 раз - зелёная. */
@Composable
private fun WordTile(
    word: ReadingWord,
    count: Int,
    repeat: Int,
    transliteration: String?,
    hidden: Boolean,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val isDone = count >= repeat
    val initiallyDone = remember(word) { isDone }
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val readLabel = stringResource(R.string.makharij_words_read_action)
    val state = stringResource(R.string.makharij_words_tile_state, count, repeat)
    val description = listOfNotNull(word.arabic, transliteration?.takeIf { !hidden }).joinToString(", ")
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
    Box(modifier.popOn(count)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(interaction, indication = null, enabled = !isDone, role = Role.Button, onClick = read)
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
                },
        ) {
            if (isDone) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = palette.parchmentAccent,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = Spacing.xxs, top = Spacing.xxs).size(DoneIconSize),
                )
            } else {
                Text(
                    stringResource(R.string.makharij_words_count, count, repeat),
                    color = colors.parchmentCaption,
                    style = InabahType.caption2.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = Spacing.xs, top = Spacing.xxs),
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.m, bottom = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArabicText(word.arabic, WORD_SIZE, palette.parchmentText, bold = true, maxLines = 1, textAlign = TextAlign.Center)
                if (transliteration != null) {
                    Text(
                        transliteration,
                        color = colors.parchmentCaption,
                        style = InabahType.caption,
                        modifier = Modifier.alpha(transliterationAlpha(hidden)),
                    )
                }
            }
        }
        // Вспышка - только в момент, когда слово дочитано (не у уже прочитанных при открытии экрана).
        if (isDone && !initiallyDone) SparkleBurst(key = word, color = palette.gold, modifier = Modifier.matchParentSize())
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

private const val WORD_COLUMNS = 3
private const val WORD_SIZE = 28f
private const val DONE_FILL_ALPHA = 0.22f
private const val WORD_FILL_ALPHA = 0.5f
private val DoneIconSize = 14.dp
