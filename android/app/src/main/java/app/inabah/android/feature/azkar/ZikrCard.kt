package app.inabah.android.feature.azkar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.ZikrTranslation
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.IconButton
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.ProgressRing
import app.inabah.android.core.designsystem.components.Sparkle
import app.inabah.android.core.designsystem.components.collapsible
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.designsystem.monospacedDigits
import kotlinx.coroutines.delay

/** Свёрнутая строка показывает одну строку — хватает первых слов (вёрстка длинного текста дороже). */
private const val MINI_ROW_WORDS = 8
private const val MINI_ROW_ARABIC_SIZE = 17f
private val MiniCheckSize = 28.dp
private val MiniCheckGlyph = 16.dp
private val BubbleRadius = 8.dp
private val CounterCheckGlyph = 26.dp

/** Ореол вспышки выполненного счётчика — на столько выходит за кнопку. */
private val FlashHaloRadius = 14.dp


/** Первые [words] слов арабского текста и «…»; короче — целиком. */
internal fun miniRowPreview(arabic: String, words: Int = MINI_ROW_WORDS): String {
    val parts = arabic.split(Whitespace).filter(String::isNotEmpty)
    return if (parts.size > words) parts.take(words).joinToString(" ") + "…" else arabic
}

private val Whitespace = Regex("\\s+")

/**
 * Карточка зикра (iOS `ZikrCardView`): пергамент, перевод, действия и счётчик. После выполнения
 * сверху раскрывается заголовок с ✓, а полное содержимое складывается под него; ⌄ раскладывает
 * обратно. Оба блока всегда в иерархии и меняют только высоту ([collapsible]) — соседи по ленте
 * сдвигаются в тех же кадрах. Перерисовывается только по состоянию своего [session] и [audio]
 * (значение от ленты — карточка не читает плеер); звучащий в «Прослушать все» — в золотой рамке.
 */
@Composable
fun ZikrCard(
    session: ZikrSession,
    arabicFontSize: Float,
    modifier: Modifier = Modifier,
    audio: ZikrAudioState = ZikrAudioState.Idle,
    onPlay: () -> Unit = {},
) {
    val palette = InabahTheme.palette
    val state by session.state.collectAsStateWithLifecycle()
    val borderColor by animateColorAsState(
        if (audio.isPlaylistCurrent) palette.gold else palette.hairline, Motion.highlight(), label = "cardBorder",
    )
    val borderWidth by animateDpAsState(
        if (audio.isPlaylistCurrent) PLAYING_BORDER else Size.hairline, Motion.highlight(), label = "cardBorderWidth",
    )
    val total = session.zikr.repetitions
    val isCompleted = state.count >= total

    // Отстаёт от выполнения на Motion.COLLAPSE_DELAY_MILLIS, чтобы была видна вспышка «готово».
    // Начальное значение — одноразовое: при возврате на экран выполненная карточка сразу свёрнута.
    var isCollapsed by remember(session) { mutableStateOf(isCompleted) }
    LaunchedEffect(isCompleted) {
        if (!isCompleted) {
            isCollapsed = false
        } else if (!isCollapsed) {
            delay(Motion.COLLAPSE_DELAY_MILLIS)
            isCollapsed = true
        }
    }
    // Сброс раскрывает сразу, в том же кадре, что и смена счёта: эффект сработал бы кадром позже,
    // и карточка на кадр начала бы сворачиваться.
    val collapsed = isCollapsed && isCompleted
    val showsFullContent = !collapsed || state.isExpanded

    Column(
        modifier
            .fillMaxWidth()
            .surface(palette.card, Radius.card, border = borderColor, lineWidth = borderWidth, shadow = ShadowToken.card(palette))
            .clip(RoundedCornerShape(Radius.card)),
    ) {
        ZikrMiniRow(
            arabic = remember(session) { miniRowPreview(session.zikr.arabic) },
            isExpanded = state.isExpanded,
            onReset = session::reset,
            onToggleExpanded = { session.setExpanded(!state.isExpanded) },
            modifier = Modifier.collapsible(collapsed),
        )
        ZikrFullContent(
            session = session,
            state = state,
            arabicFontSize = arabicFontSize,
            isUnderHeader = collapsed,
            isAudioActive = audio.isActive,
            onPlay = onPlay,
            modifier = Modifier.collapsible(showsFullContent),
        )
    }
}

/** Рамка звучащего в «Прослушать все» зикра (iOS — gold 2 pt). */
private val PLAYING_BORDER = 2.dp

@Composable
private fun ZikrFullContent(
    session: ZikrSession,
    state: ZikrState,
    arabicFontSize: Float,
    isUnderHeader: Boolean,
    isAudioActive: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val zikr = session.zikr
    Column(modifier) {
        // Под свёрнутым заголовком верхние углы прямые — меняются сразу, без анимации.
        ParchmentPanel(topCornerRadius = if (isUnderHeader) 0.dp else Radius.card) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ArabicText(zikr.arabic, arabicFontSize, palette.parchmentInk)
                RepetitionsBadge(zikr.repetitions)
            }
        }
        zikr.translation?.let { translation ->
            ZikrTranslationBlock(
                translation,
                Modifier
                    .collapsible(state.isTranslationVisible)
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.l),
            )
        }
        ZikrActions(
            isAudioActive = isAudioActive,
            onPlay = onPlay,
            isTranslationVisible = state.isTranslationVisible,
            hasTranslation = zikr.translation != null,
            canReset = state.count > 0,
            onToggleTranslation = { session.setTranslationVisible(!state.isTranslationVisible) },
            onReset = session::reset,
            modifier = Modifier.padding(vertical = Spacing.l),
        )
        CardDivider()
        val haptics = LocalHapticFeedback.current
        ZikrCounter(
            count = state.count,
            total = zikr.repetitions,
            onIncrement = {
                when (session.increment()) {
                    IncrementResult.Counted -> haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    IncrementResult.Completed -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    IncrementResult.AlreadyCompleted -> Unit
                }
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Spacing.xlPlus),
        )
    }
}

@Composable
private fun CardDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Size.hairline).background(InabahTheme.palette.divider))
}

@Composable
private fun RepetitionsBadge(repetitions: Int) {
    val palette = InabahTheme.palette
    Row(
        modifier = Modifier
            .surface(palette.successTint, Radius.small, border = palette.successBorder)
            .padding(horizontal = Spacing.s, vertical = Spacing.xxxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sparkle(palette.successDeep, Size.ornament * SPARKLE_IN_BADGE)
        Text(
            pluralStringResource(R.plurals.zikr_repetitions, repetitions, repetitions),
            color = palette.successDeep,
            style = InabahType.caption2.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

private const val SPARKLE_IN_BADGE = 0.8f

/**
 * Транскрипция, перевод на подложке с полосой и источник (iOS `ZikrTranslationView`); язык текста —
 * язык перевода (переносы). Стили переводов — растут с шагом «Размера переводов»; также образец
 * на экране «Размер текста».
 */
@Composable
fun ZikrTranslationBlock(translation: ZikrTranslation, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    val locale = LocaleList(translation.language.code)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        translation.transliteration?.let {
            Text(it, color = palette.textSecondary, style = InabahType.contentTransliteration.copy(localeList = locale))
        }
        val stripeColor = palette.accent
        Text(
            translation.text,
            color = palette.textPrimary,
            style = InabahType.contentTranslation.copy(localeList = locale),
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.accentDim, RoundedCornerShape(topEnd = BubbleRadius, bottomEnd = BubbleRadius))
                .drawBehind {
                    val stripe = Size.accentStripe.toPx()
                    val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 0f else size.width - stripe
                    drawRect(stripeColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(stripe, size.height))
                }
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
        translation.source?.let {
            Text(it, color = palette.textTertiary, style = InabahType.contentNote.copy(localeList = locale))
        }
    }
}

/**
 * ▶, «Аа», ↺ — по центру. ▶ — запись зикра; выбрана в плеере и не доиграла — статичная волна
 * на зелёном, «Открыть плеер» (нажатие показывает плеер, не ставит паузу).
 */
@Composable
private fun ZikrActions(
    isAudioActive: Boolean,
    onPlay: () -> Unit,
    isTranslationVisible: Boolean,
    hasTranslation: Boolean,
    canReset: Boolean,
    onToggleTranslation: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPlay,
            icon = painterResource(if (isAudioActive) R.drawable.ic_graphic_eq else R.drawable.ic_play_arrow),
            contentDescription = stringResource(if (isAudioActive) R.string.audio_zikr_open_player else R.string.audio_zikr_listen),
            foreground = if (isAudioActive) palette.onAccent else palette.textSecondary,
            background = if (isAudioActive) palette.success else palette.actionBackground,
            border = if (isAudioActive) palette.successLight else palette.hairline,
        )
        IconButton(
            onClick = onToggleTranslation,
            contentDescription = stringResource(R.string.zikr_translation_toggle),
            background = if (isTranslationVisible) palette.accent else palette.actionBackground,
            border = if (isTranslationVisible) palette.accentLight else palette.hairline,
            enabled = hasTranslation,
            modifier = Modifier.semantics { selected = isTranslationVisible },
        ) {
            Text(
                stringResource(R.string.zikr_translation_toggle_short),
                color = if (isTranslationVisible) palette.onAccent else palette.textSecondary,
                style = InabahType.subheadline.copy(fontWeight = FontWeight.Bold),
            )
        }
        IconButton(
            onClick = onReset,
            icon = painterResource(R.drawable.ic_replay),
            contentDescription = stringResource(R.string.zikr_reset),
            foreground = palette.textSecondary,
            background = palette.actionBackground,
            border = palette.hairline,
            enabled = canReset,
        )
    }
}

/**
 * Счётчик (iOS `ZikrCounterButton`): кольцо и кнопка с числом; по заполнении — зелёная галочка
 * и вспышка. Выполненный неактивен. Для TalkBack — одна кнопка «Счётчик, 2 из 3». Закреплён —
 * не растёт с шагом интерфейса, как и остальные кнопки.
 */
@Composable
private fun ZikrCounter(count: Int, total: Int, onIncrement: () -> Unit, modifier: Modifier = Modifier) = FixedTextSize {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    val isCompleted = count >= total
    val fraction = count.toFloat() / total.coerceAtLeast(1)

    // Вспышка — только при выполнении нажатием, не при появлении уже выполненного счётчика.
    val glow = remember { Animatable(0f) }
    val wasCompleted = remember { booleanArrayOf(isCompleted) }
    LaunchedEffect(isCompleted) {
        val justCompleted = isCompleted && !wasCompleted[0]
        wasCompleted[0] = isCompleted
        // Сброс посреди вспышки отменяет её — ореол не должен остаться.
        glow.snapTo(0f)
        if (justCompleted) {
            glow.animateTo(1f, tween(Motion.FLASH_IN_MILLIS, easing = LinearEasing))
            glow.animateTo(0f, tween(Motion.FLASH_OUT_MILLIS, easing = LinearEasing))
        }
    }

    val label = stringResource(R.string.zikr_counter_label)
    val value = stringResource(R.string.zikr_counter_value, count, total)
    val hint = stringResource(if (isCompleted) R.string.zikr_completed else R.string.zikr_counter_hint)
    val interaction = remember { MutableInteractionSource() }
    val successColor = palette.success
    Box(
        modifier = modifier
            .size(Size.counter)
            .drawBehind {
                val g = glow.value
                if (g > 0f) {
                    val halo = FlashHaloRadius.toPx() * g
                    val radius = size.minDimension / 2
                    drawCircle(
                        Brush.radialGradient(
                            0f to successColor.copy(alpha = g),
                            radius / (radius + halo) to successColor.copy(alpha = g),
                            1f to Color.Transparent,
                            radius = radius + halo,
                        ),
                        radius = radius + halo,
                    )
                }
            }
            .clickable(interaction, indication = null, enabled = !isCompleted, role = Role.Button,
                onClickLabel = hint, onClick = onIncrement)
            .pressFeedback(interaction, scale = PressFeedback.COUNTER_SCALE)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = value
            },
        contentAlignment = Alignment.Center,
    ) {
        ProgressRing(
            fraction = fraction,
            trackColor = palette.goldTrack,
            fillColor = if (isCompleted) palette.success else palette.gold,
            lineWidth = Size.ringStroke,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .padding(Spacing.xs)
                .fillMaxSize()
                .surface(
                    if (isCompleted) gradients.counterButtonDone else gradients.counterButton,
                    CircleShape,
                    shadow = if (isCompleted) null else ShadowToken.goldButton(palette),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isCompleted,
                transitionSpec = { (scaleIn(Motion.highlight()) + fadeIn(Motion.highlight())) togetherWith fadeOut(Motion.press()) },
                label = "counterFace",
            ) { completed ->
                if (completed) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = palette.onAccent,
                        modifier = Modifier.size(CounterCheckGlyph))
                } else {
                    CounterDigits(count, total)
                }
            }
        }
    }
}

@Composable
private fun CounterDigits(count: Int, total: Int) {
    val palette = InabahTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                // Как .numericText: растёт — новая цифра снизу, уменьшается — сверху.
                val direction = if (targetState > initialState) 1 else -1
                (slideInVertically(Motion.digits()) { it * direction } + fadeIn(Motion.press())) togetherWith
                    (slideOutVertically(Motion.digits()) { -it * direction } + fadeOut(Motion.press()))
            },
            label = "counterDigits",
        ) { value ->
            Text(value.toString(), color = palette.parchmentInk,
                style = InabahType.title3.copy(fontWeight = FontWeight.Bold).monospacedDigits())
        }
        Text(stringResource(R.string.zikr_counter_of, total), color = palette.parchmentInk, style = InabahType.caption2)
    }
}

/** Заголовок выполненной карточки (iOS `ZikrMiniRow`): точка, начало текста, ✓, ↺ и ⌄. */
@Composable
private fun ZikrMiniRow(
    arabic: String,
    isExpanded: Boolean,
    onReset: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    // Поворот ⌄ — той же кривой и длительности, что и раскрытие под ним.
    val rotation by animateFloatAsState(if (isExpanded) HALF_TURN else 0f, Motion.collapse(), label = "chevron")
    val dividerAlpha by animateFloatAsState(if (isExpanded) 1f else 0f, Motion.collapse(), label = "divider")
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s)
                .padding(start = Spacing.l, end = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Size.statusDot).background(palette.success, CircleShape))
            ArabicText(arabic, MINI_ROW_ARABIC_SIZE, palette.textSecondary, modifier = Modifier.weight(1f), maxLines = 1)
            val completed = stringResource(R.string.zikr_completed)
            Box(
                Modifier
                    .size(MiniCheckSize)
                    .background(palette.successDim, CircleShape)
                    .semantics { contentDescription = completed },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = palette.success,
                    modifier = Modifier.size(MiniCheckGlyph))
            }
            IconButton(
                onClick = onReset,
                icon = painterResource(R.drawable.ic_replay),
                contentDescription = stringResource(R.string.zikr_reset),
                foreground = palette.textSecondary,
                background = palette.actionBackground,
                border = palette.hairline,
                size = Size.miniButton,
            )
            IconButton(
                onClick = onToggleExpanded,
                icon = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(if (isExpanded) R.string.zikr_collapse else R.string.zikr_expand),
                foreground = palette.textSecondary,
                background = palette.actionBackground,
                border = palette.hairline,
                size = Size.miniButton,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
        CardDivider(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = dividerAlpha })
    }
}

private const val HALF_TURN = 180f
