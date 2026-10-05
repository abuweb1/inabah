package app.inabah.android.core.audio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.audio.AudioPlayerController
import app.inabah.android.core.audio.PlaybackMode
import app.inabah.android.core.audio.PlayerState
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.InterfaceTextScale
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.Tracking
import app.inabah.android.core.designsystem.components.BareIconButton
import app.inabah.android.core.designsystem.components.IconButton
import app.inabah.android.core.designsystem.components.IconButtonShape
import app.inabah.android.core.designsystem.components.ProminentRoundButton
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.designsystem.monospacedDigits
import app.inabah.android.core.formatting.formatDuration
import app.inabah.android.core.formatting.formatElapsed
import app.inabah.android.core.settings.InterfaceTextSize
import kotlinx.coroutines.flow.StateFlow

/** Свайп вниз по шапке плеера длиннее этого — скрыть (звук играет). */
private val HideSwipeDistance = 40.dp
private val SkipIconSize = 30.dp
private const val SKIP_MILLIS = 10_000L
private const val ERROR_ICON_SIZE = 14f

/**
 * Мини-плеер над панелью вкладок (iOS `AudioPlayerPanel`, снимки android/docs/player): ручка, раздел и
 * зикр, кнопки скрыть / ⌃ ⌄ / закрыть, полоса с временем, ↺ −10 ⏯ +10 ■. Весь закреплён на шаге
 * «Мельче» (`docs/android/10-typography.md`, «Мини-плеер»): компактный на любом размере интерфейса.
 */
@Composable
fun AudioPlayerHost(player: AudioPlayerController, modifier: Modifier = Modifier) {
    val state by player.state.collectAsStateWithLifecycle()
    // Закрыли ✕ — очередь уже пуста, а панель ещё уезжает: показывать последнее, что играло.
    var shown by remember { mutableStateOf(state) }
    if (state.track != null) shown = state
    AnimatedVisibility(
        visible = state.isPanelVisible && state.track != null,
        modifier = modifier,
        // Уезжает вниз — под панель вкладок (она рисуется поверх), как в iOS (снимок android/docs/player).
        enter = slideInVertically(Motion.collapse()) { it } + fadeIn(Motion.collapse()),
        exit = slideOutVertically(Motion.collapse()) { it } + fadeOut(Motion.collapse()),
    ) {
        AudioPlayerPanel(shown, player)
    }
}

/** Сама панель; [state] — от [AudioPlayerHost] (на время скрытия — последнее с записью). */
@Composable
private fun AudioPlayerPanel(state: PlayerState, player: AudioPlayerController, modifier: Modifier = Modifier) =
    InterfaceTextScale(InterfaceTextSize.Smaller.fontScale) {
        val track = state.track ?: return@InterfaceTextScale
        val palette = InabahTheme.palette
        val shape = RoundedCornerShape(Radius.panel)
        val stripe = palette.gold
        val resources = LocalResources.current
        var scrubMs by remember { mutableStateOf<Long?>(null) }
        Column(
            modifier = modifier
                .padding(horizontal = Spacing.m)
                .padding(bottom = Spacing.s)
                .fillMaxWidth()
                .surface(SolidColor(palette.card), shape, border = palette.goldBorder, shadow = ShadowToken.floating(palette))
                .clip(shape)
                // Золотая полоса у левого края, по скруглению панели; для TalkBack её нет.
                .drawBehind { drawRect(stripe, size = size.copy(width = Size.accentStripe.toPx())) }
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.s, bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            PanelHeader(state, track.category, track.title, player)
            ScrubBar(
                position = player.positionMs,
                durationMs = state.durationMs,
                onSeek = player::seek,
                onScrub = { scrubMs = it },
                label = stringResource(R.string.audio_player_position),
                // «0:11 из 0:45» — по текущей позиции, когда TalkBack читает полосу.
                value = { positionMs -> resources.getString(R.string.audio_player_time, formatElapsed(positionMs), formatDuration(state.durationMs)) },
            )
            TimeRow(player.positionMs, scrubMs, state.durationMs)
            Controls(state, player)
        }
    }

/** Ручка и заголовок; свайп вниз по ним скрывает плеер (полоса и кнопки свайп не ловят). */
@Composable
private fun PanelHeader(state: PlayerState, category: String, title: String, player: AudioPlayerController) {
    val palette = InabahTheme.palette
    val threshold = with(LocalDensity.current) { HideSwipeDistance.toPx() }
    Column(
        modifier = Modifier.pointerInput(Unit) {
            var travelled = 0f
            detectVerticalDragGestures(
                onDragStart = { travelled = 0f },
                onDragEnd = { if (travelled > threshold) player.hidePanel() },
                onVerticalDrag = { change, amount ->
                    travelled += amount
                    change.consume()
                },
            )
        },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(Size.grabberWidth, Size.grabberHeight)
                .background(palette.goldMuted, CircleShape),
        )
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // Подпись — в колонке под названием: зона касания кнопок справа (48) выше текста и
            // отодвигала бы её отдельной строкой.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
                    Text(category, color = palette.textSecondary, maxLines = 1,
                        style = InabahType.caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = Tracking.caption))
                    Text(title, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = InabahType.subheadline.copy(fontWeight = FontWeight.SemiBold))
                }
                Subtitle(state)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SquareButton(R.drawable.ic_visibility_off, stringResource(R.string.audio_player_hide), player::hidePanel)
                if (state.mode == PlaybackMode.Playlist) {
                    SquareButton(R.drawable.ic_keyboard_arrow_up, stringResource(R.string.audio_player_previous),
                        player::previous, enabled = state.canGoPrevious)
                    SquareButton(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.audio_player_next),
                        player::next, enabled = state.canGoNext)
                }
                SquareButton(R.drawable.ic_close, stringResource(R.string.audio_player_close), player::close)
            }
        }
    }
}

/** Раздел (одиночная запись), «Зикр 3 из 16 · повтор 2 из 100» (плейлист) или ошибка — золотом. */
@Composable
private fun Subtitle(state: PlayerState) {
    val palette = InabahTheme.palette
    val style = InabahType.caption
    if (state.hasError) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            val iconSize = with(LocalDensity.current) { ERROR_ICON_SIZE.sp.toDp() }
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = palette.gold,
                modifier = Modifier.size(iconSize))
            Text(stringResource(R.string.audio_player_error), color = palette.gold, style = style, maxLines = 1)
        }
        return
    }
    val text = when (state.mode) {
        PlaybackMode.Single -> state.track?.subtitle.orEmpty()
        PlaybackMode.Playlist -> {
            val position = stringResource(R.string.audio_playlist_position, state.zikrIndex + 1, state.count)
            val repeats = state.track?.repeats ?: 1
            if (repeats > 1) {
                "$position · " + stringResource(R.string.audio_playlist_repetition, state.repetition, repeats)
            } else {
                position
            }
        }
    }
    Text(text, color = palette.textSecondary, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Прошедшее и длительность; во время перетаскивания слева — время под пальцем. Для TalkBack — в полосе. */
@Composable
private fun TimeRow(position: StateFlow<Long>, scrubMs: Long?, durationMs: Long) {
    val palette = InabahTheme.palette
    val positionMs by position.collectAsStateWithLifecycle()
    val style = InabahType.caption.monospacedDigits()
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatElapsed(scrubMs ?: positionMs), color = palette.textSecondary, style = style)
        Text(formatDuration(durationMs), color = palette.textSecondary, style = style)
    }
}

@Composable
private fun Controls(state: PlayerState, player: AudioPlayerController) {
    val palette = InabahTheme.palette
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareButton(R.drawable.ic_replay, stringResource(R.string.audio_player_restart), player::restart)
        BareIconButton({ player.skip(-SKIP_MILLIS) }, painterResource(R.drawable.ic_replay_10),
            stringResource(R.string.audio_player_back10), palette.textPrimary, iconSize = SkipIconSize)
        ProminentRoundButton(
            onClick = player::togglePlayPause,
            icon = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
            contentDescription = stringResource(if (state.isPlaying) R.string.audio_player_pause else R.string.audio_player_play),
        )
        BareIconButton({ player.skip(SKIP_MILLIS) }, painterResource(R.drawable.ic_forward_10),
            stringResource(R.string.audio_player_forward10), palette.textPrimary, iconSize = SkipIconSize)
        SquareButton(R.drawable.ic_stop, stringResource(R.string.audio_player_stop), player::stop)
    }
}

/** Квадратная кнопка плеера (iOS `IconButtonStyle(.square)`): 34, радиус 12, рамка goldBorder. */
@Composable
private fun SquareButton(iconRes: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val palette = InabahTheme.palette
    IconButton(
        onClick = onClick,
        icon = painterResource(iconRes),
        contentDescription = description,
        foreground = palette.textPrimary,
        background = palette.actionBackground,
        shape = IconButtonShape.RoundedSquare,
        size = Size.playerButton,
        border = palette.goldBorder,
        enabled = enabled,
    )
}
