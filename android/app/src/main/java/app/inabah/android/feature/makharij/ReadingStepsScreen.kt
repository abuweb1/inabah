package app.inabah.android.feature.makharij

import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.BuildConfig
import app.inabah.android.R
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.ScheherazadeNew
import app.inabah.android.core.designsystem.components.Sparkle
import app.inabah.android.core.designsystem.components.LinearProgressBar
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.formatting.toArabicIndicDigits
import app.inabah.android.core.settings.ReadingProgress

/** Состояние строки шага. */
enum class StepState { Done, Current, Open, Locked }

/**
 * Список шагов «Учимся читать» (вариант A, решение пользователя 2026-10-15): «пройдено N из 10»,
 * полоса и строки - номер арабскими цифрами (пройденный - ✓), название, о чём шаг, пример справа.
 * Шаги не запираются: открыть можно любой.
 */
@Composable
fun ReadingStepsScreen(
    store: ReadingStore,
    progress: ReadingProgress,
    onBack: () -> Unit,
    onOpenStep: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lessonsDone by progress.lessonsDone.collectAsStateWithLifecycle()
    val wordCounts by progress.wordCounts.collectAsStateWithLifecycle()
    val unlockSeen by progress.unlockSeen.collectAsStateWithLifecycle()
    ReadingScaffold(
        store = store,
        title = stringResource(R.string.makharij_reading_title),
        onBack = onBack,
        modifier = modifier,
    ) { course ->
        val completed = remember(course, lessonsDone, wordCounts) { completedSteps(course, lessonsDone, wordCounts) }
        val current = remember(course, completed) { currentStep(course, completed) }
        // Новый открытый шаг - замок «отщёлкивается» один раз, потом это запоминается.
        val lastUnlocked = course.steps.lastOrNull { it.hasLesson && isUnlocked(course, completed, it) }?.number ?: 1
        LaunchedEffect(lastUnlocked) {
            if (lastUnlocked > unlockSeen) {
                delay(UNLOCK_ANIMATION_MILLIS)
                progress.markUnlockSeen(lastUnlocked)
            }
        }
        val colors = readingColors
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.s,
                bottom = Spacing.xl + contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item(key = "progress") {
                Column(Modifier.padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        stringResource(R.string.makharij_steps_progress, completedCount(course, completed), numberedStepCount(course)),
                        color = InabahTheme.palette.onAccentSecondary,
                        style = InabahType.footnote,
                    )
                    LinearProgressBar(
                        fraction = completedFraction(course, completed).toFloat(),
                        track = InabahTheme.palette.track,
                        fill = colors.tint,
                    )
                }
            }
            itemsIndexed(course.steps, key = { _, step -> step.number }) { index, step ->
                val state = when {
                    step.number in completed -> StepState.Done
                    step == current -> StepState.Current
                    // Сборка для проверки (`inabah.unlockAll`) - без замков.
                    !BuildConfig.READING_UNLOCK_ALL && !isUnlocked(course, completed, step) -> StepState.Locked
                    else -> StepState.Open
                }
                // Нить между кружками: золотая после пройденного шага, иначе - дорожка.
                val gold = InabahTheme.palette.gold
                val track = InabahTheme.palette.track
                fun segment(after: Int) = if (course.steps[after].number in completed) gold else track
                StepRow(
                    step = step,
                    state = state,
                    threadTop = if (index > 0) segment(index - 1) else null,
                    threadBottom = if (index < course.steps.lastIndex) segment(index) else null,
                    justUnlocked = step.number > unlockSeen && step.number == lastUnlocked && state != StepState.Locked,
                    onClick = { onOpenStep(step.number) },
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    step: ReadingStep,
    state: StepState,
    threadTop: Color?,
    threadBottom: Color?,
    justUnlocked: Boolean,
    onClick: () -> Unit,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val density = LocalDensity.current
    fun scaled(points: Float) = with(density) { points.sp.toDp() }
    val interaction = remember { MutableInteractionSource() }
    val description = listOf(stepTitle(step), step.subtitle)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val stateLabel = when (state) {
        StepState.Done -> stringResource(R.string.makharij_step_state_done)
        StepState.Current -> stringResource(R.string.makharij_step_state_current)
        StepState.Locked -> stringResource(R.string.makharij_step_state_locked)
        StepState.Open -> null
    }
    val isCurrent = state == StepState.Current
    val badgeSize = scaled(BADGE_SIZE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Нить «пути» - поверх карточки, но не через кружок: от середины промежутка до кружка и дальше.
            .drawWithContent {
                drawContent()
                val x = Spacing.m.toPx() + badgeSize.toPx() / 2
                val badgeTop = (size.height - badgeSize.toPx()) / 2
                val halfGap = Spacing.s.toPx() / 2
                val width = ThreadWidth.toPx()
                threadTop?.let { drawLine(it, Offset(x, -halfGap), Offset(x, badgeTop), width) }
                threadBottom?.let { drawLine(it, Offset(x, badgeTop + badgeSize.toPx()), Offset(x, size.height + halfGap), width) }
            }
            // Закрытый шаг (не пройден предыдущий) - не открывается (решение пользователя 2026-10-15).
            .clickable(interaction, indication = null, enabled = state != StepState.Locked, role = Role.Button, onClick = onClick)
            .alpha(if (state == StepState.Locked) LOCKED_ALPHA else 1f)
            .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY)
            .surface(
                if (isCurrent) colors.highlight else colors.tile,
                Radius.box,
                border = if (isCurrent) colors.highlightBorder else colors.tileBorder,
            )
            .padding(horizontal = Spacing.m, vertical = Spacing.m)
            .clearAndSetSemantics {
                contentDescription = description
                stateLabel?.let { stateDescription = it }
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badge = badgeSize
        // Текущий шаг «дышит».
        val badgeMotion = if (isCurrent) Modifier.breathing() else Modifier
        if (state == StepState.Done) {
            Box(Modifier.size(badge).surface(palette.success, badge / 2), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = palette.onAccent,
                    modifier = Modifier.size(scaled(CHECK_SIZE)),
                )
            }
        } else {
            Box(
                badgeMotion.size(badge).surface(colors.sheet, badge / 2, border = colors.tint),
                contentAlignment = Alignment.Center,
            ) {
                // Итог - без номера, ✦ (решение пользователя 2026-10-15).
                if (step.isFinal) Sparkle(colors.tint, scaled(FINAL_SPARKLE_SIZE))
                // Как номер хадиса (HadithRow): строка = кегль, цифры Scheherazade подняты к центру.
                else Text(
                    step.number.toArabicIndicDigits(),
                    color = colors.tint,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer { translationY = -(BADGE_NUMBER_SIZE * DIGIT_LIFT).sp.toPx() },
                    style = InabahType.body.copy(
                        fontFamily = ScheherazadeNew,
                        fontSize = BADGE_NUMBER_SIZE.sp,
                        lineHeight = BADGE_NUMBER_SIZE.sp,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
            Text(step.title, color = palette.onAccent, style = InabahType.subheadline.copy(fontWeight = FontWeight.SemiBold))
            Text(step.subtitle, color = palette.onAccentSecondary, style = InabahType.caption,
                maxLines = SUBTITLE_LINES, overflow = TextOverflow.Ellipsis)
        }
        if (state == StepState.Locked) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = palette.onAccentSecondary,
                modifier = Modifier.size(scaled(LOCK_SIZE)),
            )
        } else if (justUnlocked) {
            UnlockingExample(step.example, scaled(LOCK_SIZE))
        } else {
            step.example?.let { ArabicText(it, EXAMPLE_SIZE, palette.parchmentLight, maxLines = 1) }
        }
    }
}

/** Кружок номера 40, в sp - растёт с шагом интерфейса. */
private const val BADGE_SIZE = 40f
private const val BADGE_NUMBER_SIZE = 20f
private const val DIGIT_LIFT = 0.16f
private const val CHECK_SIZE = 22f
private const val FINAL_SPARKLE_SIZE = 18f
private const val EXAMPLE_SIZE = 22f
private const val SUBTITLE_LINES = 2
private const val LOCK_SIZE = 20f
private const val LOCKED_ALPHA = 0.55f

/** Замок «отщёлкивается» (поворот, рост, исчезает, вспышка ✦) - и проявляется пример шага. */
@Composable
private fun UnlockingExample(example: String?, lockSize: androidx.compose.ui.unit.Dp) {
    val palette = InabahTheme.palette
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(UNLOCK_DELAY_MILLIS)
        progress.animateTo(1f, tween(UNLOCK_ANIMATION_MILLIS.toInt() - UNLOCK_DELAY_MILLIS.toInt()))
    }
    Box(contentAlignment = Alignment.Center) {
        example?.let {
            ArabicText(it, EXAMPLE_SIZE, palette.parchmentLight, maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = ((progress.value - 0.5f) * 2).coerceIn(0f, 1f) })
        }
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = palette.gold,
            modifier = Modifier
                .size(lockSize)
                .graphicsLayer {
                    val t = progress.value
                    alpha = (1 - t * 2).coerceIn(0f, 1f)
                    rotationZ = -UNLOCK_ROTATION * t
                    scaleX = 1 + t
                    scaleY = 1 + t
                },
        )
        if (progress.value > 0f) SparkleBurst(key = example, color = palette.gold, modifier = Modifier.size(lockSize * 3))
    }
}

private const val UNLOCK_DELAY_MILLIS = 400L
private const val UNLOCK_ANIMATION_MILLIS = 1_200L
private const val UNLOCK_ROTATION = 30f
private val ThreadWidth = 2.dp

