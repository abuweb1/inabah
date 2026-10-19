package app.inabah.android.feature.makharij

import app.inabah.android.core.designsystem.Motion
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingQuestion
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.ReadingProgress
import kotlin.random.Random

/**
 * Проверка после урока (набросок `docs/design/makharij-reading.html`, «Как читается?»): 3 варианта,
 * без оценок и таймера - ошибся, показываем правильный ответ и почему. Шаг пройден, когда даны ответы
 * на все вопросы (правильность не важна), затем - «Шаг пройден»: следующий шаг или список.
 */
@Composable
fun ReadingQuizScreen(
    stepNumber: Int,
    store: ReadingStore,
    progress: ReadingProgress,
    onBack: () -> Unit,
    onOpenStep: (Int) -> Unit,
    onOpenSteps: () -> Unit,
    onOpenWords: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Зерно захода: новый заход - новые вопросы (просьба пользователя 2026-10-19); поворот экрана - тот же. */
    seed: Long = rememberSaveable { Random.nextLong() },
) {
    // Номер вопроса и выбранный ответ переживают поворот; после последнего - «Шаг пройден».
    var index by rememberSaveable { mutableIntStateOf(0) }
    // Неверные попытки текущего вопроса - битами по номеру варианта; верный найден - можно дальше.
    var wrongMask by rememberSaveable { mutableIntStateOf(0) }
    var solved by rememberSaveable { mutableStateOf(false) }
    val course = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value
    val round = remember(course, stepNumber, seed) {
        course?.step(stepNumber)?.let { quizRound(course, it, seed) }.orEmpty()
    }
    val total = round.size
    val isFinished = total > 0 && index >= total
    ReadingScaffold(
        store = store,
        title = stringResource(R.string.makharij_quiz_title),
        subtitle = if (total > 0 && !isFinished) stringResource(R.string.makharij_quiz_counter, index + 1, total) else null,
        onBack = onBack,
        modifier = modifier,
    ) { loaded ->
        val step = loaded.step(stepNumber)
        when {
            step == null || round.isEmpty() -> Box(Modifier.fillMaxSize()) {
                NoticeCard(
                    icon = R.drawable.ic_hourglass,
                    title = stringResource(R.string.makharij_lesson_soon_title),
                    message = stringResource(R.string.makharij_lesson_soon_message),
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
            }
            // Шаг без слов пройден проверкой - праздник; со словами - «Осталось прочитать слова».
            index >= round.size && step.words == null -> StepCompletedOverlay(
                step = step,
                next = loaded.steps.firstOrNull { it.number > step.number && it.hasLesson },
                onOpenStep = onOpenStep,
                onOpenSteps = onOpenSteps,
                contentPadding = contentPadding,
            )
            index >= round.size -> QuizFinished(
                // У шага есть слова - сначала «Читаем слова»: без них следующий шаг закрыт.
                hasWords = step.words != null,
                onOpenWords = onOpenWords,
                // Следующий шаг с готовым уроком; дальше уроков нет - только список.
                next = loaded.steps.firstOrNull { it.number > step.number && it.hasLesson },
                onOpenStep = onOpenStep,
                onOpenSteps = onOpenSteps,
                contentPadding = contentPadding,
            )
            else -> QuizQuestion(
                round = round,
                index = index,
                wrongMask = wrongMask,
                solved = solved,
                onAnswer = { i, isCorrect -> if (isCorrect) solved = true else wrongMask = wrongMask or (1 shl i) },
                onNext = {
                    if (index == round.lastIndex) progress.markLessonDone(step.number)
                    wrongMask = 0
                    solved = false
                    index += 1
                },
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun QuizQuestion(
    round: List<QuizItem>,
    index: Int,
    wrongMask: Int,
    solved: Boolean,
    onAnswer: (index: Int, isCorrect: Boolean) -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
) {
    val palette = InabahTheme.palette
    val (question, options) = round[index]
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressDots(count = round.size, current = index)
        Text(
            (question as? ReadingQuestion.Word)?.prompt ?: stringResource(
                when (question) {
                    is ReadingQuestion.Syllable, is ReadingQuestion.ClosedSyllable -> R.string.makharij_quiz_syllable
                    is ReadingQuestion.LetterName, is ReadingQuestion.LetterInForm -> R.string.makharij_quiz_letter
                    is ReadingQuestion.Word -> R.string.makharij_quiz_word
                },
            ),
            color = palette.onAccentSecondary,
            style = InabahType.footnote,
            modifier = Modifier.padding(top = Spacing.xxl),
        )
        val arabic = when (question) {
            is ReadingQuestion.Syllable -> question.letter.syllable(question.mark)
            is ReadingQuestion.ClosedSyllable -> question.arabic
            is ReadingQuestion.LetterName -> question.letter.letter
            is ReadingQuestion.LetterInForm -> question.letter.form(question.form)
            is ReadingQuestion.Word -> question.arabic
        }
        // Слово - в широкой плитке и мельче: в квадрат буквы длинное слово не влезает.
        if (question is ReadingQuestion.Word) {
            ParchmentTile(arabic, QUESTION_WORD_SIZE, Modifier.padding(top = Spacing.m).fillMaxWidth().height(QUESTION_BOX), cornerRadius = Radius.panel)
        } else {
            ParchmentTile(arabic, QUESTION_SIZE, Modifier.padding(top = Spacing.m).size(QUESTION_BOX), cornerRadius = Radius.panel)
        }
        val option: @Composable (Int, QuizOption, Modifier) -> Unit = { i, option, modifier ->
            OptionButton(
                text = option.text,
                state = when {
                    solved && option.isCorrect -> OptionState.Correct
                    wrongMask and (1 shl i) != 0 -> OptionState.Wrong
                    solved -> OptionState.Other
                    else -> OptionState.Open
                },
                onClick = {
                    // Отклик: верно - подтверждение, неверно - «отказ» (просьба пользователя: живость).
                    haptics.performHapticFeedback(
                        if (option.isCorrect) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                    )
                    onAnswer(i, option.isCorrect)
                },
                modifier = modifier,
            )
        }
        // Транскрипция слова длинная (таӏаджджальа) - варианты столбцом во всю ширину; слоги и буквы - в ряд.
        if (question is ReadingQuestion.Word) {
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                options.forEachIndexed { i, it -> option(i, it, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                options.forEachIndexed { i, it -> option(i, it, Modifier.weight(1f)) }
            }
        }
        // Ошибся - «неверно», выбирать дальше (верный сам не подсвечивается - решение пользователя
        // 2026-10-15); нашёл верный - «Верно!», объяснение и «Дальше».
        if (solved) {
            Feedback(isCorrect = true, explanation = explanation(question))
        } else if (wrongMask != 0) {
            Feedback(isCorrect = false, explanation = null)
        }
        if (solved) {
            Spacer(Modifier.height(Spacing.xl))
            ReadingButton(
                text = stringResource(if (index == round.lastIndex) R.string.makharij_quiz_finish else R.string.makharij_quiz_next),
                onClick = onNext,
            )
        }
    }
}

/** Почему ответ такой: знак и его звук или звук буквы. */
@Composable
private fun explanation(question: ReadingQuestion): String = when (question) {
    is ReadingQuestion.Syllable -> stringResource(
        R.string.makharij_quiz_explain_syllable,
        question.mark.where.replaceFirstChar { it.uppercase() },
        question.mark.name,
        question.mark.sound,
    )
    is ReadingQuestion.Word -> question.explanation
        ?: stringResource(R.string.makharij_quiz_explain_word, question.options.getOrElse(question.answer) { "" })
    is ReadingQuestion.ClosedSyllable -> stringResource(
        R.string.makharij_quiz_explain_closed,
        question.letter.name,
        question.transliteration(question.vowel.id),
    )
    is ReadingQuestion.LetterName -> stringResource(
        R.string.makharij_quiz_explain_letter,
        question.letter.name,
        question.letter.sound,
    )
    is ReadingQuestion.LetterInForm -> stringResource(
        R.string.makharij_quiz_explain_form,
        question.letter.name,
        stringResource(question.form.place),
    )
}

private enum class OptionState { Open, Correct, Wrong, Other }

@Composable
private fun OptionButton(text: String, state: OptionState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val interaction = remember { MutableInteractionSource() }
    val stateLabel = when (state) {
        OptionState.Correct -> stringResource(R.string.makharij_quiz_option_correct)
        OptionState.Wrong -> stringResource(R.string.makharij_quiz_option_wrong)
        OptionState.Open, OptionState.Other -> null
    }
    val (fill, border) = when (state) {
        OptionState.Correct -> palette.success.copy(alpha = CORRECT_FILL_ALPHA) to palette.success
        OptionState.Wrong -> palette.destructive.copy(alpha = WRONG_FILL_ALPHA) to palette.destructive
        OptionState.Open, OptionState.Other -> colors.tile to colors.tileBorder
    }
    // Неверный - трясётся «нет», верный - толчок и вспышка ✦.
    Box(modifier.shakeOn(state == OptionState.Wrong).popOn(state == OptionState.Correct)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = OPTION_HEIGHT)
                .clickable(interaction, indication = null, enabled = state == OptionState.Open, role = Role.Button, onClick = onClick)
                .pressFeedback(interaction, scale = PressFeedback.WIDE_SCALE)
                // После верного ответа остальные варианты приглушены.
                .alpha(
                    when (state) {
                        OptionState.Open, OptionState.Correct, OptionState.Wrong -> 1f
                        OptionState.Other -> OTHER_ALPHA
                    },
                )
                .surface(fill, Radius.card, border = border, lineWidth = OPTION_BORDER)
                .padding(horizontal = Spacing.xs)
                .semantics { stateLabel?.let { stateDescription = it } },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = palette.onAccent,
                style = InabahType.title3.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        if (state == OptionState.Correct) SparkleBurst(key = text, color = palette.success, modifier = Modifier.matchParentSize())
    }
}

/** «Верно!» и объяснение или «Неверно, попробуйте ещё раз»; TalkBack читает сразу после ответа. */
@Composable
private fun Feedback(isCorrect: Boolean, explanation: String?) {
    val palette = InabahTheme.palette
    val title = stringResource(if (isCorrect) R.string.makharij_quiz_correct else R.string.makharij_quiz_wrong)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl)
            .surface(
                if (isCorrect) palette.successTint else palette.destructive.copy(alpha = WRONG_FILL_ALPHA),
                Radius.box,
                border = if (isCorrect) palette.successBorder else palette.destructive,
            )
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(title, color = palette.onAccent, style = InabahType.subheadline.copy(fontWeight = FontWeight.Bold))
        explanation?.let { Text(it, color = palette.onAccentStrong, style = InabahType.subheadline) }
    }
}

/** Точки вопросов: пройденные - зелёные, текущая - цветом раздела. Для TalkBack - счётчик в навбаре. */
@Composable
private fun ProgressDots(count: Int, current: Int) {
    val palette = InabahTheme.palette
    val colors = readingColors
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(count) { i ->
            // Точка наливается цветом, текущая - вытянута в капсулу.
            val color by animateColorAsState(
                when {
                    i < current -> palette.success
                    i == current -> colors.tint
                    else -> palette.track
                },
                Motion.collapse(),
                label = "dotColor",
            )
            val width by animateDpAsState(if (i == current) Size.statusDot * CURRENT_DOT_STRETCH else Size.statusDot, Motion.collapse(), label = "dotWidth")
            Box(Modifier.size(width, Size.statusDot).background(color, CircleShape))
        }
    }
}

/** «Шаг пройден»: следующий шаг (если есть) и список. */
@Composable
private fun QuizFinished(
    hasWords: Boolean,
    onOpenWords: () -> Unit,
    next: ReadingStep?,
    onOpenStep: (Int) -> Unit,
    onOpenSteps: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        NoticeCard(
            icon = R.drawable.ic_check_circle,
            title = stringResource(R.string.makharij_quiz_done_title),
            message = stringResource(if (hasWords) R.string.makharij_quiz_done_words else R.string.makharij_quiz_done_message),
        )
        if (hasWords) {
            ReadingButton(stringResource(R.string.makharij_words_open), onClick = onOpenWords)
        } else next?.let { ReadingButton(stringResource(R.string.makharij_quiz_next_step, it.title), onClick = { onOpenStep(it.number) }) }
        ReadingOutlineButton(stringResource(R.string.makharij_quiz_to_steps), onClick = onOpenSteps)
    }
}

private val QUESTION_BOX = 180.dp
private const val CURRENT_DOT_STRETCH = 2.5f
private const val QUESTION_SIZE = 72f
private const val QUESTION_WORD_SIZE = 52f
private val OPTION_HEIGHT = 60.dp
private val OPTION_BORDER = 1.5.dp
private const val CORRECT_FILL_ALPHA = 0.25f
private const val WRONG_FILL_ALPHA = 0.22f
private const val OTHER_ALPHA = 0.3f
