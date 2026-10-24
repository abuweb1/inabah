package app.inabah.android.feature.qibla

import app.inabah.android.core.qibla.spokenTurnDegrees
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.first
import app.inabah.android.core.qibla.AlignmentHaptics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import kotlinx.coroutines.delay
import app.inabah.android.core.settings.OnceFlag
import app.inabah.android.core.qibla.TurnAmount
import app.inabah.android.core.designsystem.components.HintBubble
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.PrimaryButton
import app.inabah.android.core.designsystem.components.SlideUpSheet
import app.inabah.android.core.designsystem.components.TopBarSubtitle
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.qibla.QiblaGuidance
import app.inabah.android.core.qibla.QiblaModel
import app.inabah.android.core.qibla.QiblaUiState
import app.inabah.android.core.qibla.TurnSide
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import app.inabah.android.core.qibla.LocationAccess
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Кольцо - не шире этого и не выше доли высоты экрана: на маленьком экране остаётся место карточке. */
private val DialMaxSize = 360.dp
private const val DIAL_HEIGHT_FRACTION = 0.46f

// Низ экрана (картинка пользователя и набросок `qibla-v2.html`, 2026-10-24): знак поворота, надпись, две плитки, строка.
/** Знак поворота под кольцом: стрелка 104 × 20 (линия, промежуток, уголок), подтянут к кольцу. */
private val MarkHeight = 26.dp
private val MarkLift = 18.dp
private val ArrowWidth = 104.dp
private val ArrowHeight = 20.dp
private val ArrowStroke = 2.4.dp
private val ChevronStroke = 3.dp
private const val ARROW_LINE_END = 0.74f

/** Уголок: ширина и половина высоты - доли высоты знака (как на картинке пользователя: невысокий, широкий изгиб). */
private const val CHEVRON_WIDTH = 0.42f
private const val CHEVRON_HALF = 0.4f

/** Плитки: полупрозрачная поверхность, одна высота. */
private val TileMinHeight = 60.dp
private const val TILE_ALPHA = 0.55f

/** Значки - в `sp`: растут с текстом интерфейса. */
private const val DIALOG_ICON_SP = 34
private const val MARK_ICON_SP = 30
private const val TILE_ICON_SP = 24
private val SpinnerStroke = 2.5.dp

/** Спиннер - доля значка: под знаком и в плитке он визуально крупнее значка той же величины. */
private const val MARK_SPINNER_FRACTION = 0.75f
private const val TILE_SPINNER_FRACTION = 0.85f

/** Подсказка у ⓘ: через 0,6 с после открытия, 4 с - как в «Учимся читать». */
private const val HELP_HINT_DELAY_MILLIS = 600L
private const val HELP_HINT_MILLIS = 4_000L

// Диалог доступа (решение пользователя 2026-10-24): стекло - размыто только то, что под окном.
private val DialogMaxWidth = 340.dp
private val DialogRadius = 28.dp
private const val DIALOG_SCRIM_ALPHA = 0.35f
private const val DIALOG_GLASS_ALPHA = 0.62f
private const val DIALOG_GLASS_ALPHA_NO_BLUR = 0.92f
private const val DIALOG_RIM_ALPHA = 0.35f

/** Доля цвета раздела в стекле окна. */
private const val DIALOG_GLASS_TINT = 0.2f
private val DialogBlur = HazeBlurStyle {
    blurRadius(24.dp)
    noiseFactor(0.08f)
}

/**
 * Экран «Къибла» (`docs/android/06-screens.md`, «Къибла»; макеты `IMG_0230…0233`, набросок `docs/design/qibla.html`).
 * Доступ к местоположению спрашивается при открытии - сначала наше пояснение, потом окно Android.
 */
@Composable
fun QiblaScreen(
    model: QiblaModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Подсказка у ⓘ «Как повысить точность» - один раз; `null` - без неё. */
    helpHint: OnceFlag? = null,
    /** «Доступ уже спрашивали» - отличить «больше не спрашивать» сразу при входе; `null` - не хранить. */
    locationAsked: OnceFlag? = null,
) {
    val permission = rememberLocationPermission(locationAsked)
    // «Перезапустить компас» (решение пользователя 2026-10-24): новая подписка - координаты заново, метка без сглаживания.
    var session by rememberSaveable { mutableIntStateOf(0) }
    val flow = remember(model, permission.access, session) { model.state(permission.access) }
    // Поток показаний (~50 в секунду) здесь не читается: экран перестраивается только по смене состояния (ревью 2026-10-24).
    val state = flow.collectAsStateWithLifecycle(QiblaUiState.Loading)
    var dialogDismissed by rememberSaveable { mutableStateOf(false) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    QiblaHaptics(state)

    val dialogVisible = !dialogDismissed && permission.access == LocationAccess.NotGranted
    var helpHintVisible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Подсказка у ⓘ - один раз, когда на экране ничего поверх: ни нашего окна, ни окна Android (аудит 2026-10-24).
    LaunchedEffect(helpHint, dialogVisible, permission.requesting) {
        if (helpHint == null || helpHint.isSet || dialogVisible || permission.requesting) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        delay(HELP_HINT_DELAY_MILLIS)
        // Пока ждали, ⓘ могли нажать или экран ушёл в паузу.
        if (helpHint.isSet || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        helpHint.set()
        helpHintVisible = true
        delay(HELP_HINT_MILLIS)
        helpHintVisible = false
    }

    val actions = remember(model, permission.access, onBack) {
        QiblaActions(
            onBack = onBack,
            // Наше окно - один раз: после ответа в окне Android спрашивает кнопка.
            onRequestPermission = {
                dialogDismissed = true
                permission.request()
            },
            onDialogLater = { dialogDismissed = true },
            onOpenAppSettings = permission.openAppSettings,
            onOpenLocationSettings = permission.openLocationSettings,
            onRefreshLocation = model::refreshLocation,
            onOpenHelp = {
                helpHintVisible = false
                helpHint?.set()
                helpVisible = true
            },
            onCloseHelp = { helpVisible = false },
            // Отклик - как у счётчика азкаров (решение пользователя 2026-10-24).
            onRestart = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                session++
            },
        )
    }
    QiblaContent(
        state = state,
        dialogVisible = dialogVisible,
        helpVisible = helpVisible,
        actions = actions,
        contentPadding = contentPadding,
        modifier = modifier,
        helpHintVisible = helpHintVisible,
    )
}

/**
 * Один отклик на каждый новый вход в совпадение ([AlignmentHaptics]: счёт входов - внутри одной подписки; после ↻
 * и возврата на экран старое значение отклика не даёт, первый вход новой подписки - даёт).
 */
@Composable
private fun QiblaHaptics(state: State<QiblaUiState>) {
    val haptic = LocalHapticFeedback.current
    val gate = remember { AlignmentHaptics() }
    LaunchedEffect(state) {
        snapshotFlow { (state.value as? QiblaUiState.Compass)?.pointer }
            .filterNotNull()
            .collect { pointer -> if (gate.shouldVibrate(pointer)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}

/** Действия экрана - одним объектом, чтобы содержимое не зависело от модели. */
class QiblaActions(
    val onBack: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onDialogLater: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenLocationSettings: () -> Unit,
    val onRefreshLocation: () -> Unit,
    val onOpenHelp: () -> Unit,
    val onCloseHelp: () -> Unit,
    val onRestart: () -> Unit,
) {
    companion object {
        val None = QiblaActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Содержимое экрана - для превью и UI-тестов. [state] - поток показаний: кольцо читает угол метки
 * только при отрисовке, остальной экран перестраивается лишь при смене подсказки.
 */
@Composable
fun QiblaContent(
    state: State<QiblaUiState>,
    dialogVisible: Boolean,
    helpVisible: Boolean,
    actions: QiblaActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Подсказка «Как повысить точность» у ⓘ (один раз, решение пользователя 2026-10-24). */
    helpHintVisible: Boolean = false,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    // Без угла метки: меняется только при смене состояния или подсказки.
    val screen by remember(state) { derivedStateOf { state.value.withoutPointer() } }
    // Градусы для TalkBack - с запасом у границ шага: озвучка не прыгает и экран не перестраивается от дрожания.
    var spokenTurn by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state) {
        snapshotFlow { (state.value as? QiblaUiState.Compass)?.pointer?.offset }
            .collect { offset -> spokenTurn = offset?.let { spokenTurnDegrees(abs(it), spokenTurn) } }
    }
    val backdrop = rememberHazeState()
    var hostBounds by remember { mutableStateOf(Rect.Zero) }
    var infoBounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { hostBounds = it.boundsInRoot() }) {
        Column(
            Modifier
                .fillMaxSize()
                .background(theme.gradients.azkarBackground)
                .hazeSource(backdrop)
                // Пока открыто окно или справка, TalkBack читает только их - экран под ними скрыт (как у «Что нового»).
                .then(if (dialogVisible || helpVisible) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            InabahTopBar(
                title = stringResource(R.string.qibla_title),
                tint = palette.tabAzkar,
                onBack = actions.onBack,
                subtitle = { TopBarSubtitle(stringResource(R.string.qibla_subtitle)) },
                actions = {
                    GlassIconButton(
                        onClick = actions.onRestart,
                        iconRes = R.drawable.ic_replay,
                        contentDescription = stringResource(R.string.qibla_restart),
                        tint = palette.tabAzkar,
                    )
                },
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val dialSize = min(min(maxWidth - Spacing.xl * 2, maxHeight * DIAL_HEIGHT_FRACTION), DialMaxSize)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.xl)
                        .padding(top = Spacing.xs, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val compass = screen as? QiblaUiState.Compass
                    val headline = headline(screen, actions)
                    QiblaDial(
                        pointer = { (state.value as? QiblaUiState.Compass)?.pointer },
                        aligned = compass?.guidance == QiblaGuidance.Aligned,
                        dimmed = compass?.guidance.let { it == QiblaGuidance.LowAccuracy || it is QiblaGuidance.Tilted },
                        searching = screen == QiblaUiState.Locating || compass?.guidance == QiblaGuidance.Waiting,
                        description = dialDescription(screen, spokenTurn, headline.title),
                        modifier = Modifier.widthIn(max = dialSize),
                    )
                    HeadlineBlock(headline)
                    Column(
                        Modifier.fillMaxWidth().padding(top = Spacing.xlPlus),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        LocationTile(screen, actions)
                        AdviceTile(actions.onOpenHelp, onInfoPlaced = { infoBounds = it })
                    }
                    val tilted = compass?.guidance is QiblaGuidance.Tilted
                    Text(
                        stringResource(if (tilted) R.string.qibla_tilted else R.string.qibla_hold_flat),
                        color = if (tilted) palette.goldLight else palette.onAccentSecondary,
                        style = InabahType.footnote,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xlPlus),
                    )
                }
            }
        }
        AnimatedVisibility(helpHintVisible, enter = fadeIn(), exit = fadeOut()) {
            HintBubble(stringResource(R.string.qibla_help), infoBounds, hostBounds, palette.tabAzkar, palette.background)
        }
        if (dialogVisible) PermissionDialog(backdrop, actions)
        SlideUpSheet(
            visible = helpVisible,
            title = stringResource(R.string.qibla_help),
            onDismiss = actions.onCloseHelp,
            contentPadding = contentPadding,
        ) { HelpText(actions.onCloseHelp) }
    }
}

private fun QiblaUiState.withoutPointer(): QiblaUiState =
    if (this is QiblaUiState.Compass && pointer != null) copy(pointer = null) else this

/** Описание кольца для TalkBack; без компаса - то же, что заголовок карточки ([fallback]). */
@Composable
private fun dialDescription(state: QiblaUiState, turn: Int?, fallback: String): String = when {
    state is QiblaUiState.Compass && state.guidance == QiblaGuidance.Aligned -> stringResource(R.string.qibla_found)
    state is QiblaUiState.Compass && turn != null -> {
        val side = when (val guidance = state.guidance) {
            is QiblaGuidance.Turn -> guidance.side
            is QiblaGuidance.Tilted -> guidance.side
            else -> null
        }
        when (side) {
            TurnSide.Right -> stringResource(R.string.qibla_dial_right, turn)
            TurnSide.Left -> stringResource(R.string.qibla_dial_left, turn)
            null -> fallback
        }
    }
    state is QiblaUiState.Compass -> stringResource(R.string.qibla_dial_waiting)
    else -> fallback
}

/** Знак под кольцом: стрелка в сторону поворота, галочка совпадения, ожидание или ничего (место остаётся). */
private sealed interface Mark {
    data class Arrow(val side: TurnSide) : Mark
    data object Check : Mark
    data object Spinner : Mark
    data object None : Mark
}

/** Цвет надписи: золото - поворот и проблемы компаса, зелёный - найдено, белый - ожидание и доступ. */
private enum class Tone { Gold, Ok, Plain }

private class Headline(
    val mark: Mark,
    val title: String,
    val hint: String,
    val tone: Tone,
    val action: String? = null,
    val onAction: () -> Unit = {},
)

@Composable
private fun headline(state: QiblaUiState, actions: QiblaActions): Headline = when (state) {
    QiblaUiState.Loading -> Headline(Mark.Spinner, stringResource(R.string.qibla_loading), stringResource(R.string.qibla_loading_hint), Tone.Plain)
    is QiblaUiState.NeedsPermission -> if (state.forever) {
        Headline(
            Mark.None, stringResource(R.string.qibla_denied_title), stringResource(R.string.qibla_denied_hint), Tone.Plain,
            stringResource(R.string.qibla_open_settings), actions.onOpenAppSettings,
        )
    } else {
        Headline(
            Mark.None, stringResource(R.string.qibla_permission_title), stringResource(R.string.qibla_permission_hint), Tone.Plain,
            stringResource(R.string.qibla_permission_allow), actions.onRequestPermission,
        )
    }
    QiblaUiState.LocationOff -> Headline(
        Mark.None, stringResource(R.string.qibla_location_off_title), stringResource(R.string.qibla_location_off_hint), Tone.Plain,
        stringResource(R.string.qibla_open_settings), actions.onOpenLocationSettings,
    )
    QiblaUiState.Locating -> Headline(Mark.Spinner, stringResource(R.string.qibla_locating_title), stringResource(R.string.qibla_locating_hint), Tone.Plain)
    QiblaUiState.LocationFailed -> Headline(
        Mark.None, stringResource(R.string.qibla_failed_title), stringResource(R.string.qibla_failed_hint), Tone.Plain,
        stringResource(R.string.qibla_retry), actions.onRefreshLocation,
    )
    is QiblaUiState.NoCompass -> Headline(
        Mark.None, stringResource(R.string.qibla_no_compass_title),
        stringResource(R.string.qibla_no_compass_hint, state.bearing.roundToInt() % FULL_TURN), Tone.Plain,
    )
    is QiblaUiState.Compass -> when (val guidance = state.guidance) {
        QiblaGuidance.Waiting -> Headline(Mark.Spinner, stringResource(R.string.qibla_waiting), stringResource(R.string.qibla_waiting_hint), Tone.Plain)
        QiblaGuidance.LowAccuracy -> Headline(Mark.None, stringResource(R.string.qibla_low_accuracy_title), stringResource(R.string.qibla_low_accuracy_hint), Tone.Gold)
        is QiblaGuidance.Tilted -> Headline(Mark.Arrow(guidance.side), turnTitle(guidance.side, guidance.amount), stringResource(R.string.qibla_tilted_hint), Tone.Gold)
        is QiblaGuidance.Turn -> Headline(Mark.Arrow(guidance.side), turnTitle(guidance.side, guidance.amount), stringResource(R.string.qibla_turn_hint), Tone.Gold)
        QiblaGuidance.Aligned -> Headline(Mark.Check, stringResource(R.string.qibla_found), stringResource(R.string.qibla_found_hint), Tone.Ok)
        // Метка у указателя, но координатам нельзя доверить подтверждение (решение пользователя 2026-10-24, (б)+(в)).
        QiblaGuidance.LocationUnconfirmed -> if (state.location.nearKaaba) {
            Headline(
                Mark.None, stringResource(R.string.qibla_near_kaaba_title), stringResource(R.string.qibla_near_kaaba_hint), Tone.Gold,
                stringResource(R.string.qibla_open_settings), actions.onOpenAppSettings,
            )
        } else {
            Headline(
                Mark.None, stringResource(R.string.qibla_stale_title), stringResource(R.string.qibla_stale_hint), Tone.Gold,
                stringResource(R.string.qibla_location_refresh), actions.onRefreshLocation,
            )
        }
    }
}

private const val FULL_TURN = 360

/** «Поверните направо» (> 45°), «Правее» (15-45°), «Немного правее» (< 15°) - степень с запасом считает трекер. */
@Composable
private fun turnTitle(side: TurnSide, amount: TurnAmount) = stringResource(
    when (amount) {
        TurnAmount.Far -> if (side == TurnSide.Right) R.string.qibla_turn_right else R.string.qibla_turn_left
        TurnAmount.Mid -> if (side == TurnSide.Right) R.string.qibla_turn_right_mid else R.string.qibla_turn_left_mid
        TurnAmount.Near -> if (side == TurnSide.Right) R.string.qibla_turn_right_near else R.string.qibla_turn_left_near
    },
)

/** Знак, крупная надпись, подпись и кнопка действия (если есть). Знак подтянут к кольцу: под кольцом пустое поле. */
@Composable
private fun HeadlineBlock(headline: Headline) {
    val palette = InabahTheme.palette
    val color = when (headline.tone) {
        Tone.Gold -> palette.goldLight
        Tone.Ok -> palette.successLight
        Tone.Plain -> palette.onAccent
    }
    Column(Modifier.fillMaxWidth().pullUp(MarkLift), horizontalAlignment = Alignment.CenterHorizontally) {
        DirectionMark(headline.mark)
        Text(
            headline.title,
            color = color,
            style = InabahType.title2.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xxs).semantics { heading() },
        )
        Text(
            headline.hint,
            color = palette.onAccentSecondary,
            style = InabahType.subheadline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        headline.action?.let {
            PrimaryButton(onClick = headline.onAction, text = it, modifier = Modifier.fillMaxWidth().padding(top = Spacing.l))
        }
    }
}

/** Поднимает элемент на [lift] и забирает это место у раскладки (не просто сдвиг отрисовки). */
private fun Modifier.pullUp(lift: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = lift.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

@Composable
private fun DirectionMark(mark: Mark) {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { MARK_ICON_SP.sp.toDp() }
    Box(Modifier.height(MarkHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (mark) {
            is Mark.Arrow -> {
                val gold = palette.gold
                Canvas(
                    Modifier
                        .size(ArrowWidth, ArrowHeight)
                        .graphicsLayer { if (mark.side == TurnSide.Left) scaleX = -1f },
                ) {
                    // Как на картинке пользователя: линия, промежуток, отдельный уголок - одной линией со скруглённым изгибом.
                    val stroke = ArrowStroke.toPx()
                    val chevron = ChevronStroke.toPx()
                    val midY = size.height / 2
                    drawLine(gold, Offset(stroke, midY), Offset(size.width * ARROW_LINE_END, midY), stroke, StrokeCap.Round)
                    val tipX = size.width - chevron
                    val backX = tipX - size.height * CHEVRON_WIDTH
                    val half = size.height * CHEVRON_HALF
                    val corner = Path().apply {
                        moveTo(backX, midY - half)
                        lineTo(tipX, midY)
                        lineTo(backX, midY + half)
                    }
                    drawPath(corner, gold, style = Stroke(chevron, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Mark.Check -> Icon(painterResource(R.drawable.ic_check_circle), null, tint = palette.successLight, modifier = Modifier.size(iconSize))
            Mark.Spinner -> CircularProgressIndicator(Modifier.size(iconSize * MARK_SPINNER_FRACTION), color = palette.gold, strokeWidth = SpinnerStroke, trackColor = palette.track)
            Mark.None -> Unit
        }
    }
}

/** Плитка: значок (или ожидание) слева, текст, справа - действие. Проблема - текст золотом. */
@Composable
private fun QiblaTile(
    @DrawableRes icon: Int?,
    text: String,
    warn: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { TILE_ICON_SP.sp.toDp() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TileMinHeight)
            .surface(palette.card.copy(alpha = TILE_ALPHA), Radius.card, border = palette.hairline)
            .padding(start = Spacing.xl, end = Spacing.s, top = Spacing.xs, bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == null) {
            CircularProgressIndicator(Modifier.size(iconSize * TILE_SPINNER_FRACTION), color = palette.gold, strokeWidth = SpinnerStroke, trackColor = palette.track)
        } else {
            Icon(painterResource(icon), contentDescription = null, tint = palette.gold, modifier = Modifier.size(iconSize))
        }
        Text(
            text,
            color = if (warn) palette.goldLight else palette.onAccent,
            style = InabahType.subheadline,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.s),
        )
        trailing?.invoke()
    }
}

/** Местоположение: определено / приблизительное / устарело + «Обновить» / определяем / нет доступа. */
@Composable
private fun LocationTile(state: QiblaUiState, actions: QiblaActions) {
    val palette = InabahTheme.palette
    val note = when (state) {
        is QiblaUiState.Compass -> state.location
        is QiblaUiState.NoCompass -> state.location
        else -> null
    }
    when {
        state is QiblaUiState.NeedsPermission -> QiblaTile(R.drawable.ic_location_off, stringResource(R.string.qibla_location_none), warn = true)
        state == QiblaUiState.LocationOff -> QiblaTile(R.drawable.ic_location_off, stringResource(R.string.qibla_location_off_row), warn = true)
        state == QiblaUiState.LocationFailed -> QiblaTile(R.drawable.ic_location_off, stringResource(R.string.qibla_failed_title), warn = true)
        state == QiblaUiState.Loading || state == QiblaUiState.Locating ->
            QiblaTile(null, stringResource(R.string.qibla_location_searching), warn = false)
        note?.stale == true -> QiblaTile(R.drawable.ic_location_on, stringResource(R.string.qibla_location_stale), warn = true) {
            Text(
                stringResource(R.string.qibla_location_refresh),
                color = palette.gold,
                style = InabahType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.control))
                    .clickable(role = Role.Button, onClick = actions.onRefreshLocation)
                    .heightIn(min = Size.minTapTarget)
                    .padding(horizontal = Spacing.s, vertical = Spacing.m),
            )
        }
        // У Мекки «приблизительное» - проблема (решение пользователя 2026-10-24), в остальном мире - только пометка.
        note?.approximate == true -> QiblaTile(R.drawable.ic_location_on, stringResource(R.string.qibla_location_approximate), warn = note.nearKaaba)
        else -> QiblaTile(R.drawable.ic_location_on, stringResource(R.string.qibla_location_found), warn = false)
    }
}

/** Совет «Вдали от металлических предметов»: золотой компас слева, ⓘ «Как повысить точность» справа. */
@Composable
private fun AdviceTile(onOpenHelp: () -> Unit, onInfoPlaced: (Rect) -> Unit) {
    val palette = InabahTheme.palette
    QiblaTile(R.drawable.ic_compass, stringResource(R.string.qibla_advice_metal), warn = false) {
        GlassIconButton(
            onClick = onOpenHelp,
            iconRes = R.drawable.ic_info,
            contentDescription = stringResource(R.string.qibla_help),
            tint = palette.tabAzkar,
            modifier = Modifier.onGloballyPositioned { onInfoPlaced(it.boundsInRoot()) },
        )
    }
}

/**
 * Пояснение перед окном Android (решение 2026-10-24): по центру, стекло - размыто только то, что под окном,
 * экран вокруг лишь чуть притемнён. «Не сейчас» - в карточке остаётся «Разрешить».
 */
@Composable
private fun PermissionDialog(backdrop: HazeState?, actions: QiblaActions) {
    val palette = InabahTheme.palette
    val shape = RoundedCornerShape(DialogRadius)
    val glass = lerp(palette.background, palette.tabAzkar, DIALOG_GLASS_TINT)
        .copy(alpha = if (backdrop != null) DIALOG_GLASS_ALPHA else DIALOG_GLASS_ALPHA_NO_BLUR)
    val iconSize = with(LocalDensity.current) { DIALOG_ICON_SP.sp.toDp() }
    val title = stringResource(R.string.qibla_dialog_title)
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.shadow.copy(alpha = DIALOG_SCRIM_ALPHA))
            // Касание мимо окна ничего не делает: выбор - кнопками или «Назад» (это «Не сейчас»).
            .pointerInput(Unit) { detectTapGestures { } }
            .clearAndSetSemantics {},
    )
    BackHandler(onBack = actions.onDialogLater)
    Box(Modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = DialogMaxWidth)
                .fillMaxWidth()
                .semantics {
                    isTraversalGroup = true
                    paneTitle = title
                },
        ) {
            if (backdrop != null) {
                Box(Modifier.matchParentSize().clip(shape).hazeBlur(HazeInput.Sources(backdrop), DialogBlur))
            }
            Column(
                Modifier
                    .surface(glass, DialogRadius, border = palette.onAccent.copy(alpha = DIALOG_RIM_ALPHA), shadow = ShadowToken.floating(palette))
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(painterResource(R.drawable.ic_location_on), contentDescription = null, tint = palette.gold, modifier = Modifier.size(iconSize))
                Text(
                    title,
                    color = palette.onAccent,
                    style = InabahType.headline.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.qibla_dialog_purpose), color = palette.onAccentSecondary, style = InabahType.footnote, textAlign = TextAlign.Center)
                Text(stringResource(R.string.qibla_dialog_privacy), color = palette.onAccentSecondary, style = InabahType.footnote, textAlign = TextAlign.Center)
                PrimaryButton(
                    onClick = actions.onRequestPermission,
                    text = stringResource(R.string.qibla_permission_allow),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                )
                Text(
                    stringResource(R.string.qibla_dialog_later),
                    color = palette.onAccentSecondary,
                    style = InabahType.subheadline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.control))
                        .clickable(role = Role.Button, onClick = actions.onDialogLater)
                        .heightIn(min = Size.minTapTarget)
                        .padding(horizontal = Spacing.xl, vertical = Spacing.m),
                )
            }
        }
    }
}

/** Справка «Как повысить точность» (набросок 2026-10-24): советы для Android, без обещания точности. */
@Composable
private fun HelpText(onClose: () -> Unit) {
    val palette = InabahTheme.palette
    Text(
        stringResource(R.string.qibla_help),
        color = palette.onAccent,
        style = InabahType.title3,
        modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
    )
    HelpSection(R.string.qibla_help_hold_title, listOf(R.string.qibla_help_hold_text))
    HelpSection(R.string.qibla_help_interference_title, listOf(R.string.qibla_help_interference_text))
    HelpSection(
        R.string.qibla_help_unstable_title,
        listOf(R.string.qibla_help_unstable_calibrate, R.string.qibla_help_unstable_precise, R.string.qibla_help_unstable_restart),
        bullets = true,
    )
    HelpSection(R.string.qibla_help_error_title, listOf(R.string.qibla_help_error_text))
    PrimaryButton(onClick = onClose, text = stringResource(R.string.qibla_help_close), modifier = Modifier.fillMaxWidth().padding(top = Spacing.l))
}

@Composable
private fun HelpSection(title: Int, paragraphs: List<Int>, bullets: Boolean = false) {
    val palette = InabahTheme.palette
    Text(
        stringResource(title),
        color = palette.goldLight,
        style = InabahType.callout.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
    )
    for (paragraph in paragraphs) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (bullets) Text(BULLET, color = palette.gold, style = InabahType.subheadline, modifier = Modifier.clearAndSetSemantics {})
            Text(stringResource(paragraph), color = palette.onAccentSecondary, style = InabahType.subheadline)
        }
    }
}

private const val BULLET = "•"
