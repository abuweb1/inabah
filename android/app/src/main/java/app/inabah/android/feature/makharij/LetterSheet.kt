package app.inabah.android.feature.makharij

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.audio.ClipPlayback
import app.inabah.android.core.audio.playToEnd
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.BareIconButton
import app.inabah.android.core.designsystem.components.IconButton
import app.inabah.android.core.designsystem.components.IconButtonShape
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Лист буквы снизу (1A, решение пользователя 2026-10-15; набросок `docs/design/makharij-audio-themes.html`):
 * буква на пергаменте, название, номер, цепочка справа налево - название → слоги → закрытые слоги; слог с ещё не
 * пройденным знаком - пунктиром. Закрывается смахиванием вниз, нажатием на затемнение, «×» и «Назад».
 * Озвучка (набросок 1A): есть записи цепочки - справа в шапке круглая ▶ «Прослушать» цвета раздела;
 * при открытии листа цепочка звучит сама, по очереди, звучащий элемент подсвечен, во время цепочки - ⏹;
 * любой элемент с записью - отдельно по нажатию. Закрыли лист, ушли с экрана или в фон - звук остановлен.
 * Записей нет - ни кнопки, ни нажатий: лист как без звука.
 * У буквы с руководством по произношению (ث ذ ظ ض, 2026-10-21) внизу - кнопка с его названием.
 */
@Composable
fun BoxScope.LetterSheet(
    course: ReadingCourse,
    letter: ReadingLetter?,
    stepNumber: Int,
    transliterationHidden: Boolean,
    onDismiss: () -> Unit,
    contentPadding: PaddingValues,
    clips: ClipPlayback = ClipPlayback.None,
    /** Открыть руководство по [id][app.inabah.android.core.content.model.ReadingGuide.id]; `null` - без кнопки. */
    onOpenGuide: ((String) -> Unit)? = null,
) {
    // Пока лист уезжает, показывать последнюю букву, а не пустоту.
    var shown by remember { mutableStateOf(letter) }
    if (letter != null) shown = letter
    BackHandler(enabled = letter != null, onBack = onDismiss)

    AnimatedVisibility(letter != null, enter = fadeIn(Motion.collapse()), exit = fadeOut(Motion.collapse())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(InabahTheme.palette.shadow.copy(alpha = SCRIM_ALPHA))
                // Затемнение для TalkBack не нужно: закрыть можно «×» (семантика убрана до clickable).
                .clearAndSetSemantics {}
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = letter != null,
        enter = slideInVertically(Motion.collapse()) { it },
        exit = slideOutVertically(Motion.collapse()) { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        shown?.let { SheetPanel(course, it, stepNumber, transliterationHidden, letter != null, clips, onDismiss, contentPadding, onOpenGuide) }
    }
}

/** Что играть: [files] `null` - вся цепочка (⏹ в шапке), иначе - нажатый элемент. Каждый запрос - новый объект. */
private class ChainRequest(val files: List<String>?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetPanel(
    course: ReadingCourse,
    letter: ReadingLetter,
    stepNumber: Int,
    hidden: Boolean,
    /** Лист открыт (не уезжает). */
    active: Boolean,
    clips: ClipPlayback,
    onDismiss: () -> Unit,
    contentPadding: PaddingValues,
    onOpenGuide: ((String) -> Unit)?,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val chain = remember(course, letter, stepNumber) { letterChain(course, letter, stepNumber) }
    val available by clips.available.collectAsStateWithLifecycle()
    val playing by clips.playing.collectAsStateWithLifecycle()
    val files = remember(chain, available) { chain.playableFiles(available) }
    // Открыли лист - цепочка сразу (набросок 1A: «звук сразу начинается»).
    var request by remember(letter, active) { mutableStateOf(if (active) ChainRequest(null) else null) }
    LaunchedEffect(request, active, files) {
        val queue = request?.files ?: files
        if (!active || request == null || queue.isEmpty()) return@LaunchedEffect
        for ((index, file) in queue.withIndex()) {
            if (index > 0) delay(CHAIN_GAP_MILLIS)
            // Пока молчали, зазвучало другое - не перебивать.
            if (index > 0 && clips.playing.value != null) break
            // Прервали (наушники, другой звук) или зазвучало другое - цепочку не продолжать.
            if (!clips.playToEnd(file)) break
        }
        request = null
    }
    // Закрыли лист, ушли с экрана или в фон - не доигрывать.
    val currentFiles by rememberUpdatedState(files)
    val stopOwn = { if (clips.playing.value in currentFiles) clips.stop() }
    LaunchedEffect(active) { if (!active) stopOwn() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        request = null
        stopOwn()
    }
    DisposableEffect(clips) { onDispose { stopOwn() } }
    val chainPlaying = active && request != null && request?.files == null && files.isNotEmpty()
    // Смахивание вниз: лист едет за пальцем, дальше порога - закрыть, иначе вернуть на место.
    val offset = remember(letter) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissDistance = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = offset.value }
            .surface(
                SolidColor(colors.sheet),
                RoundedCornerShape(topStart = Radius.panel, topEnd = Radius.panel),
                border = colors.tileBorder,
                shadow = ShadowToken.floating(palette),
            )
            // Касания внутри листа не доходят до затемнения (без семантики кнопки).
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(letter) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offset.value > dismissDistance) onDismiss() else offset.animateTo(0f, Motion.collapse())
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, Motion.collapse()) } },
                ) { _, delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) } }
            }
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(Size.grabberWidth, Size.grabberHeight)
                .background(palette.track, RoundedCornerShape(Size.grabberHeight)),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ParchmentTile(letter.letter, LETTER_SIZE, Modifier.size(LETTER_BOX))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(letter.name, color = palette.onAccent, style = InabahType.title3.copy(fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.makharij_letter_number, letter.number), color = palette.onAccentSecondary, style = InabahType.subheadline)
                    ArabicText(letter.arabicName, NAME_SIZE, palette.onAccentSecondary, maxLines = 1)
                }
            }
            if (files.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (chainPlaying) {
                            request = null
                            stopOwn()
                        } else {
                            request = ChainRequest(null)
                        }
                    },
                    icon = painterResource(if (chainPlaying) R.drawable.ic_stop else R.drawable.ic_play_arrow),
                    contentDescription = stringResource(if (chainPlaying) R.string.makharij_letter_stop else R.string.makharij_letter_play),
                    foreground = colors.onTint,
                    background = colors.tint,
                    shape = IconButtonShape.Circle,
                    size = Size.visibleTapTarget,
                )
            }
            BareIconButton(
                onClick = onDismiss,
                icon = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.makharij_letter_close),
                foreground = palette.onAccentSecondary,
            )
        }
        letter.note?.let {
            Text(
                it,
                color = palette.onAccentSecondary,
                style = InabahType.footnote,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
            )
        }
        // Справа налево: название - первым справа.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                for (item in chain) {
                    val file = item.audioFile?.takeIf { it in available }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ChainChip(
                            item = item,
                            hidden = hidden,
                            isPlaying = file != null && file == playing,
                            onPlay = file?.let { { request = ChainRequest(listOf(it)) } },
                        )
                    }
                }
            }
        }
        val guide = onOpenGuide?.let { course.guideFor(letter) }
        if (guide != null) {
            ReadingOutlineButton(guide.title, onClick = { onOpenGuide(guide.id) }, modifier = Modifier.padding(top = Spacing.xl))
        }
    }
}

/**
 * Элемент цепочки: подпись сверху («название», знак, у закрытого слога - «сукун»), арабское и транскрипция. Звучит - подсвечен цветом
 * раздела, как буква «Послушать алфавит»; [onPlay] - есть запись: нажатие проигрывает только его.
 */
@Composable
private fun ChainChip(item: LetterChainItem, hidden: Boolean, isPlaying: Boolean, onPlay: (() -> Unit)?) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val listen = stringResource(R.string.makharij_word_listen)
    val play: (() -> Unit)? = onPlay?.let {
        {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            it()
        }
    }
    val label = when (item.kind) {
        LetterChainItem.Kind.Name -> stringResource(R.string.makharij_chain_name)
        LetterChainItem.Kind.Syllable, LetterChainItem.Kind.Closed -> item.markName.orEmpty()
    }
    val description = if (item.isLearned) {
        "$label, ${item.transliteration}"
    } else {
        stringResource(R.string.makharij_chain_locked, label, item.transliteration)
    }
    val border = if (isPlaying) colors.highlightBorder else colors.tileBorder
    Box(
        Modifier
            .then(
                if (play != null) {
                    Modifier
                        .clickable(interaction, indication = null, role = Role.Button, onClick = play)
                        .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = description
                if (play != null) {
                    role = Role.Button
                    onClick(listen) { play(); true }
                }
            },
    ) {
        Column(
            Modifier
                .padding(top = LABEL_OVERLAP)
                .widthIn(min = CHIP_MIN_WIDTH)
                .alpha(if (item.isLearned) 1f else LOCKED_ALPHA)
                .then(
                    if (item.isLearned) {
                        Modifier.surface(if (isPlaying) colors.highlight else colors.tile, Radius.box, border = border, lineWidth = ReadingTileBorder)
                    } else {
                        Modifier.dashedBorder(border, Radius.box)
                    },
                )
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArabicText(item.arabic, CHIP_ARABIC_SIZE, palette.onAccent, maxLines = 1)
            Text(
                item.transliteration,
                color = palette.onAccentSecondary,
                style = InabahType.caption,
                modifier = Modifier.alpha(transliterationAlpha(hidden)),
            )
        }
        Text(
            label,
            color = palette.onAccentSecondary,
            style = InabahType.caption2,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .surface(colors.sheet, Radius.small)
                .padding(horizontal = Spacing.xs),
        )
    }
}

/** Пунктирная рамка - элемент ещё не пройден. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = Size.hairline.toPx() * DASH_STROKE
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))),
    )
}

/** Пауза между элементами цепочки: слышно, где кончился один и начался другой. */
private const val CHAIN_GAP_MILLIS = 400L
private const val SCRIM_ALPHA = 0.45f
private const val LOCKED_ALPHA = 0.45f
private val DISMISS_DISTANCE = 96.dp
private val LETTER_BOX = 84.dp
private const val LETTER_SIZE = 48f
private const val NAME_SIZE = 18f
private const val CHIP_ARABIC_SIZE = 26f
private val CHIP_MIN_WIDTH = 60.dp
/** Подпись наполовину выходит за верх элемента. */
private val LABEL_OVERLAP = 8.dp
private const val DASH_STROKE = 1.5f
private const val DASH = 10f
