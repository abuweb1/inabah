package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingJoin
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.core.settings.ZikrProgress

/**
 * Зикр итога «Читаем зикр» (решения пользователя 2026-10-17): «По словам» - плитки по порядку зикра,
 * каждое слово как оно есть (отдельно, если начать с него), касание - «прочитал» (один раз); «Слитно» -
 * места, где слова сливаются, строкой с подписью; «Целиком» - вся строка с паузой в конце, ▶ - запись
 * зикра из «Азкаров» (один раз), «Прочитал целиком», «Открыть в азкарах». Прочитаны все зикры - урок
 * итога пройден и праздник, как в конце шага.
 */
@Composable
fun ReadingZikrScreen(
    stepNumber: Int,
    zikrIndex: Int,
    store: ReadingStore,
    progress: ReadingProgress,
    settings: ReadingSettings,
    onBack: () -> Unit,
    onOpenAzkar: (AzkarSection) -> Unit,
    onOpenStep: (Int) -> Unit,
    onOpenSteps: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Записи в сборке и их проигрывание (`ClipPlayer`): запись зикра. */
    clips: Set<String> = emptySet(),
    onPlayClip: (String) -> Unit = {},
    onStopClip: () -> Unit = {},
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    val zikrs by progress.zikrs.collectAsStateWithLifecycle()
    val step = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value?.step(stepNumber)
    val zikr = step?.zikrs?.getOrNull(zikrIndex)
    // Запись длинная - ушли с экрана, звук не продолжается.
    val stop by rememberUpdatedState(onStopClip)
    DisposableEffect(Unit) { onDispose { stop() } }
    ReadingScaffold(
        store = store,
        title = step?.let { stringResource(R.string.makharij_zikr_title, zikrIndex + 1, it.zikrs.size) }.orEmpty(),
        subtitle = zikr?.let { stringResource(R.string.makharij_zikr_source, stringResource(it.zikr.section.titleRes), it.zikr.number) },
        onBack = onBack,
        modifier = modifier,
        actions = { TransliterationToggle(hidden) { settings.setTransliterationHidden(!hidden) } },
    ) { course ->
        val current = course.step(stepNumber) ?: return@ReadingScaffold
        val block = current.zikrs.getOrNull(zikrIndex) ?: return@ReadingScaffold
        val state = zikrs[zikrIndex] ?: ZikrProgress()
        // Последний зикр прочитан - урок итога пройден (кнопки «Готово» у итога нет) и праздник.
        val allDone = allZikrsDone(current, zikrs)
        var celebrate by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(allDone) {
            if (allDone && stepNumber !in progress.lessonsDone.value) {
                progress.markLessonDone(stepNumber)
                celebrate = true
            }
        }
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                SectionTitle(stringResource(R.string.makharij_zikr_words_progress, block.words.indices.count { it in state.words }, block.words.size))
                ZikrWords(block, state, hidden) { progress.readZikrWord(zikrIndex, it) }
                if (block.joins.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.makharij_zikr_joins))
                    ZikrJoins(block.joins, hidden)
                }
                SectionTitle(stringResource(R.string.makharij_zikr_whole))
                ZikrWhole(block, hidden)
                block.audio?.takeIf { it in clips }?.let { audio ->
                    ReadingOutlineButton(stringResource(R.string.makharij_zikr_play), onClick = { onPlayClip(audio) })
                }
                if (!state.isRead) {
                    ReadingButton(stringResource(R.string.makharij_zikr_read), onClick = { progress.markZikrRead(zikrIndex) })
                }
                ReadingOutlineButton(stringResource(R.string.makharij_zikr_open_azkar), onClick = { onOpenAzkar(block.zikr.section) })
            }
            if (celebrate) {
                StepCompletedOverlay(
                    step = current,
                    next = null,
                    onOpenStep = onOpenStep,
                    onOpenSteps = onOpenSteps,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

/** Название части экрана - цветом раздела, заголовок для TalkBack. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = readingColors.tint,
        style = InabahType.caption.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs).semantics { heading() },
    )
}

/** Плитки слов справа налево, по порядку зикра; ряд - во всю ширину, плитки ряда одной ширины. */
@Composable
private fun ZikrWords(zikr: ReadingBlock.Zikr, state: ZikrProgress, hidden: Boolean, onRead: (Int) -> Unit) {
    val columnsOf = rememberWordColumns()
    val rows = remember(zikr, columnsOf) { zikrRows(zikr.words, columnsOf) }
    ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                for (row in rows) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        for ((index, word) in row.words) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                WordTile(
                                    word = word,
                                    count = if (index in state.words) 1 else 0,
                                    repeat = 1,
                                    transliteration = word.transliteration,
                                    showTransliteration = !hidden,
                                    onRead = { onRead(index) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Места слитного чтения: слова строкой, под ними - как читаются вместе и почему. */
@Composable
private fun ZikrJoins(joins: List<ReadingJoin>, hidden: Boolean) {
    val palette = InabahTheme.palette
    val colors = readingColors
    ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (join in joins) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(join.arabic, join.transliteration.takeIf { !hidden }, join.note).joinToString(", ")
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArabicText(join.arabic, JOIN_SIZE, palette.parchmentText, bold = true, textAlign = TextAlign.Center, maxLines = JOIN_LINES)
                    Text(
                        join.transliteration,
                        color = colors.parchmentCaption,
                        style = InabahType.caption.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.alpha(transliterationAlpha(hidden)),
                    )
                    Text(withArabicRuns(join.note), color = colors.parchmentCaption, style = InabahType.caption, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Вся строка зикра на пергаменте и её транскрипция (с паузой в конце). */
@Composable
private fun ZikrWhole(zikr: ReadingBlock.Zikr, hidden: Boolean) {
    val palette = InabahTheme.palette
    val colors = readingColors
    ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = listOfNotNull(zikr.arabic, zikr.transliteration.takeIf { !hidden }).joinToString(", ")
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ArabicText(zikr.arabic, WHOLE_SIZE, palette.parchmentText, bold = true, textAlign = TextAlign.Center)
            Text(
                zikr.transliteration,
                color = colors.parchmentCaption,
                style = InabahType.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(transliterationAlpha(hidden)),
            )
        }
    }
}

/** Название раздела азкаров - в подзаголовке экрана зикра. */
private val AzkarSection.titleRes: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.azkar_morning_title
        AzkarSection.Evening -> R.string.azkar_evening_title
    }

private const val JOIN_SIZE = 30f
private const val JOIN_LINES = 2
private const val WHOLE_SIZE = 30f
