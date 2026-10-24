package app.inabah.android.feature.qibla

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
import androidx.compose.ui.graphics.Color
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
import app.inabah.android.core.designsystem.components.GlassCapsuleButton
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
import androidx.lifecycle.compose.LifecycleStartEffect
import app.inabah.android.core.qibla.LocationAccess
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Кольцо - не шире этого и не выше доли высоты экрана: на маленьком экране остаётся место карточке. */
private val DialMaxSize = 360.dp
private const val DIAL_HEIGHT_FRACTION = 0.46f

/** Карточка состояния одной высоты во всех состояниях без кнопки - экран не прыгает. */
private val CardMinHeight = 88.dp
private const val CARD_ALPHA = 0.82f

/** Значок карточки и строк - в `sp`: растёт с текстом интерфейса. */
private const val CARD_ICON_SP = 34
private const val ROW_ICON_SP = 20
private val SpinnerStroke = 3.dp
private val RowSpinnerStroke = 2.dp

/** Шаг округления градусов для TalkBack: точнее датчик не знает, и озвучка не меняется на каждый градус. */
private const val SPOKEN_STEP = 5

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
fun QiblaScreen(model: QiblaModel, onBack: () -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val permission = rememberLocationPermission()
    // «Перезапустить компас» (решение пользователя 2026-10-24): новая подписка - координаты заново, метка без сглаживания.
    var session by rememberSaveable { mutableIntStateOf(0) }
    val flow = remember(model, permission.access, session) { model.state(permission.access) }
    // Поток показаний (~50 в секунду) здесь не читается: экран перестраивается только по смене состояния (ревью 2026-10-24).
    val state = flow.collectAsStateWithLifecycle(QiblaUiState.Loading)
    var dialogDismissed by rememberSaveable { mutableStateOf(false) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }

    QiblaHaptics(state, session)

    val actions = remember(model, permission.access, onBack) {
        QiblaActions(
            onBack = onBack,
            // Наше окно - один раз: после ответа в окне Android спрашивает кнопка карточки.
            onRequestPermission = {
                dialogDismissed = true
                permission.request()
            },
            onDialogLater = { dialogDismissed = true },
            onOpenAppSettings = permission.openAppSettings,
            onOpenLocationSettings = permission.openLocationSettings,
            onRefreshLocation = model::refreshLocation,
            onOpenHelp = { helpVisible = true },
            onCloseHelp = { helpVisible = false },
            onRestart = { session++ },
        )
    }
    QiblaContent(
        state = state,
        dialogVisible = !dialogDismissed && permission.access == LocationAccess.NotGranted,
        helpVisible = helpVisible,
        actions = actions,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

/**
 * Один лёгкий отклик на каждый новый вход в совпадение: счёт входов растёт в [QiblaTracker] (дрожание и короткие
 * провалы его не увеличивают). Состояния без метки (поиск координат) счёт не сбрасывают; новая подписка
 * после возврата на экран начинает трекер с нуля - и запомненный счёт тоже.
 */
@Composable
private fun QiblaHaptics(state: State<QiblaUiState>, session: Int) {
    val haptic = LocalHapticFeedback.current
    var heard by remember { mutableIntStateOf(0) }
    LifecycleStartEffect(Unit) {
        heard = 0
        onStopOrDispose {}
    }
    LaunchedEffect(state, session) {
        heard = 0
        snapshotFlow { (state.value as? QiblaUiState.Compass)?.pointer?.alignedEntries }
            .filterNotNull()
            .collect { entries ->
                if (entries > heard) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                heard = entries
            }
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
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    // Без угла метки: меняется только при смене состояния или подсказки.
    val screen by remember(state) { derivedStateOf { state.value.withoutPointer() } }
    val spokenTurn by remember(state) {
        derivedStateOf {
            (state.value as? QiblaUiState.Compass)?.pointer?.let { roundTo(abs(it.offset), SPOKEN_STEP) }
        }
    }
    val backdrop = rememberHazeState()
    Box(modifier.fillMaxSize()) {
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
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val compass = screen as? QiblaUiState.Compass
                    val card = cardText(screen, actions)
                    QiblaDial(
                        pointer = { (state.value as? QiblaUiState.Compass)?.pointer },
                        aligned = compass?.guidance == QiblaGuidance.Aligned,
                        dimmed = compass?.guidance.let { it == QiblaGuidance.LowAccuracy || it is QiblaGuidance.Tilted },
                        searching = screen == QiblaUiState.Locating || compass?.guidance == QiblaGuidance.Waiting,
                        description = dialDescription(screen, spokenTurn, card.title),
                        modifier = Modifier.widthIn(max = dialSize),
                    )
                    StatusCard(card)
                    InfoRows(screen, actions)
                    HelpButton(actions.onOpenHelp)
                }
            }
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

private fun roundTo(value: Double, step: Int): Int = (value / step).roundToInt() * step

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

/** Значок карточки: картинка цвета [tint] или крутящийся индикатор. */
private sealed interface CardIcon {
    data class Picture(@DrawableRes val res: Int, val tint: Color, val mirrored: Boolean = false) : CardIcon
    data object Spinner : CardIcon
}

private class CardText(val icon: CardIcon, val title: String, val hint: String, val action: String? = null, val onAction: () -> Unit = {})

@Composable
private fun cardText(state: QiblaUiState, actions: QiblaActions): CardText {
    val palette = InabahTheme.palette
    val gold = palette.gold
    fun picture(res: Int, mirrored: Boolean = false) = CardIcon.Picture(res, gold, mirrored)
    return when (state) {
        QiblaUiState.Loading -> CardText(CardIcon.Spinner, stringResource(R.string.qibla_loading), stringResource(R.string.qibla_loading_hint))
        is QiblaUiState.NeedsPermission -> if (state.forever) {
            CardText(
                picture(R.drawable.ic_location_off), stringResource(R.string.qibla_denied_title), stringResource(R.string.qibla_denied_hint),
                stringResource(R.string.qibla_open_settings), actions.onOpenAppSettings,
            )
        } else {
            CardText(
                picture(R.drawable.ic_location_off), stringResource(R.string.qibla_permission_title), stringResource(R.string.qibla_permission_hint),
                stringResource(R.string.qibla_permission_allow), actions.onRequestPermission,
            )
        }
        QiblaUiState.LocationOff -> CardText(
            picture(R.drawable.ic_location_off), stringResource(R.string.qibla_location_off_title), stringResource(R.string.qibla_location_off_hint),
            stringResource(R.string.qibla_open_settings), actions.onOpenLocationSettings,
        )
        QiblaUiState.Locating -> CardText(picture(R.drawable.ic_location_on), stringResource(R.string.qibla_locating_title), stringResource(R.string.qibla_locating_hint))
        QiblaUiState.LocationFailed -> CardText(
            picture(R.drawable.ic_location_off), stringResource(R.string.qibla_failed_title), stringResource(R.string.qibla_failed_hint),
            stringResource(R.string.qibla_retry), actions.onRefreshLocation,
        )
        is QiblaUiState.NoCompass -> CardText(
            picture(R.drawable.ic_explore_off), stringResource(R.string.qibla_no_compass_title),
            stringResource(R.string.qibla_no_compass_hint, state.bearing.roundToInt() % 360),
            stringResource(R.string.qibla_help), actions.onOpenHelp,
        )
        is QiblaUiState.Compass -> when (val guidance = state.guidance) {
            QiblaGuidance.Waiting -> CardText(CardIcon.Spinner, stringResource(R.string.qibla_waiting), stringResource(R.string.qibla_waiting_hint))
            QiblaGuidance.LowAccuracy -> CardText(
                picture(R.drawable.ic_all_inclusive), stringResource(R.string.qibla_low_accuracy_title), stringResource(R.string.qibla_low_accuracy_hint),
                stringResource(R.string.qibla_help), actions.onOpenHelp,
            )
            is QiblaGuidance.Tilted -> CardText(picture(R.drawable.ic_turn_right, guidance.side == TurnSide.Left), turnTitle(guidance.side), stringResource(R.string.qibla_tilted_hint))
            is QiblaGuidance.Turn -> CardText(picture(R.drawable.ic_turn_right, guidance.side == TurnSide.Left), turnTitle(guidance.side), stringResource(R.string.qibla_turn_hint))
            QiblaGuidance.Aligned -> CardText(
                CardIcon.Picture(R.drawable.ic_check_circle, palette.successLight), stringResource(R.string.qibla_found), stringResource(R.string.qibla_found_hint),
            )
        }
    }
}

@Composable
private fun turnTitle(side: TurnSide) =
    stringResource(if (side == TurnSide.Right) R.string.qibla_turn_right else R.string.qibla_turn_left)

/** Карточка состояния: значок и заголовок в ряд; кнопка - во всю ширину, в одну строку (набросок 2026-10-24). */
@Composable
private fun StatusCard(text: CardText) {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { CARD_ICON_SP.sp.toDp() }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .surface(palette.card.copy(alpha = CARD_ALPHA), Radius.navCard, border = palette.hairline)
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                when (val icon = text.icon) {
                    CardIcon.Spinner -> CircularProgressIndicator(Modifier.size(iconSize), color = palette.gold, strokeWidth = SpinnerStroke, trackColor = palette.track)
                    is CardIcon.Picture -> Icon(
                        painterResource(icon.res),
                        contentDescription = null,
                        tint = icon.tint,
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer { if (icon.mirrored) scaleX = -1f },
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(text.title, color = palette.onAccent, style = InabahType.headline.copy(fontWeight = FontWeight.Bold), modifier = Modifier.semantics { heading() })
                Text(text.hint, color = palette.onAccentSecondary, style = InabahType.footnote)
            }
        }
        text.action?.let { PrimaryButton(onClick = text.onAction, text = it, modifier = Modifier.fillMaxWidth()) }
    }
}

/** Строки под карточкой: местоположение и «держите ровно»; проблема - золотом. */
@Composable
private fun InfoRows(state: QiblaUiState, actions: QiblaActions) {
    val palette = InabahTheme.palette
    val note = when (state) {
        is QiblaUiState.Compass -> state.location
        is QiblaUiState.NoCompass -> state.location
        else -> null
    }
    Column(Modifier.fillMaxWidth()) {
        when {
            state is QiblaUiState.NeedsPermission -> InfoRow(R.drawable.ic_location_off, stringResource(R.string.qibla_location_none), warn = true)
            state == QiblaUiState.LocationOff -> InfoRow(R.drawable.ic_location_off, stringResource(R.string.qibla_location_off_row), warn = true)
            state == QiblaUiState.LocationFailed -> InfoRow(R.drawable.ic_location_off, stringResource(R.string.qibla_failed_title), warn = true)
            state == QiblaUiState.Loading || state == QiblaUiState.Locating ->
                InfoRow(null, stringResource(R.string.qibla_location_searching), warn = false)
            note?.stale == true -> InfoRow(
                R.drawable.ic_location_on, stringResource(R.string.qibla_location_stale), warn = true,
                action = stringResource(R.string.qibla_location_refresh), onAction = actions.onRefreshLocation,
            )
            note?.approximate == true -> InfoRow(R.drawable.ic_location_on, stringResource(R.string.qibla_location_approximate), warn = false)
            else -> InfoRow(R.drawable.ic_location_on, stringResource(R.string.qibla_location_found), warn = false)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs).heightIn(min = Size.hairline).background(palette.divider))
        val tilted = (state as? QiblaUiState.Compass)?.guidance is QiblaGuidance.Tilted
        InfoRow(
            R.drawable.ic_smartphone,
            stringResource(if (tilted) R.string.qibla_tilted else R.string.qibla_hold_flat),
            warn = tilted,
        )
    }
}

/** [icon] `null` - крутящийся индикатор («Определяем местоположение…»). */
@Composable
private fun InfoRow(@DrawableRes icon: Int?, text: String, warn: Boolean, action: String? = null, onAction: () -> Unit = {}) {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { ROW_ICON_SP.sp.toDp() }
    val color = if (warn) palette.goldLight else palette.onAccent
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Size.minTapTarget)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == null) {
            CircularProgressIndicator(Modifier.size(iconSize), color = palette.gold, strokeWidth = RowSpinnerStroke, trackColor = palette.track)
        } else {
            Icon(painterResource(icon), contentDescription = null, tint = if (warn) palette.gold else palette.tabAzkar, modifier = Modifier.size(iconSize))
        }
        Text(text, color = color, style = InabahType.subheadline, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = palette.gold,
                style = InabahType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.control))
                    .clickable(onClick = onAction)
                    .heightIn(min = Size.minTapTarget)
                    .padding(horizontal = Spacing.s, vertical = Spacing.m),
            )
        }
    }
}

@Composable
private fun HelpButton(onClick: () -> Unit) {
    val palette = InabahTheme.palette
    GlassCapsuleButton(onClick = onClick, tint = palette.tabAzkar, modifier = Modifier.fillMaxWidth(), fillWidth = true) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(with(LocalDensity.current) { ROW_ICON_SP.sp.toDp() }))
        Text(stringResource(R.string.qibla_help), color = palette.onAccent, style = InabahType.subheadline)
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
    val iconSize = with(LocalDensity.current) { CARD_ICON_SP.sp.toDp() }
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
                        .clickable(onClick = actions.onDialogLater)
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
