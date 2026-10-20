package app.inabah.android.feature.makharij

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.inabah.android.R
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.IconButton
import app.inabah.android.core.designsystem.components.IconButtonShape
import app.inabah.android.core.designsystem.components.surface

/**
 * «Послушать алфавит» (вариант B, решение пользователя 2026-10-15): ▶ в шапке урока; нажали - на её
 * месте ⏹, под шапкой - панель «⏮ ⏸ ⏹ ⏭», звучащая буква подсвечена в сетке. Стоп - панель исчезает,
 * снова ▶. Владелец - экран урока (переживает поворот).
 */
@Stable
class AlphabetPlayback(index: Int? = null, paused: Boolean = false) {
    /** Номер звучащей буквы; `null` - остановлено (в шапке ▶). */
    var index: Int? by mutableStateOf(index)
        private set
    var isPaused: Boolean by mutableStateOf(paused)
        private set

    val isActive: Boolean get() = index != null

    fun start() {
        index = 0
        isPaused = false
    }

    fun stop() {
        index = null
        isPaused = false
    }

    /** Пауза (уход в фон, зазвучал лист буквы); остановлено - ничего. */
    fun pause() {
        if (isActive) isPaused = true
    }

    fun togglePause() {
        isPaused = !isPaused
    }

    fun previous() {
        index = index?.let { (it - 1).coerceAtLeast(0) }
    }

    /** Следующая буква; после последней - стоп. */
    fun next(count: Int) {
        val current = index ?: return
        if (current >= count - 1) stop() else index = current + 1
    }

    companion object {
        val Saver = Saver<AlphabetPlayback, List<Any?>>(
            save = { listOf(it.index, it.isPaused) },
            restore = { AlphabetPlayback(it[0] as Int?, it[1] as Boolean) },
        )
    }
}

@Composable
fun rememberAlphabetPlayback(): AlphabetPlayback = rememberSaveable(saver = AlphabetPlayback.Saver) { AlphabetPlayback() }

/** Кнопка в шапке: ▶ «Послушать алфавит» или ⏹ «Стоп». */
@Composable
fun AlphabetPlayButton(playback: AlphabetPlayback) {
    val tint = readingColors.tint
    if (playback.isActive) {
        GlassIconButton(onClick = playback::stop, iconRes = R.drawable.ic_stop,
            contentDescription = stringResource(R.string.makharij_alphabet_stop), tint = tint)
    } else {
        GlassIconButton(onClick = playback::start, iconRes = R.drawable.ic_play_arrow,
            contentDescription = stringResource(R.string.makharij_alphabet_play), tint = tint)
    }
}

/**
 * Панель под шапкой поверх урока: выезжает сверху и уезжает обратно (как мини-плеер азкаров), содержимое
 * урока не сдвигает (просьба пользователя 2026-10-20: появлялась резко и толкала элементы вниз).
 */
@Composable
fun AlphabetPlayerHost(playback: AlphabetPlayback, letters: List<ReadingLetter>, modifier: Modifier = Modifier) {
    // Остановили - номера уже нет, а панель ещё уезжает: показывать последнюю букву.
    var shown by remember { mutableStateOf(playback.index) }
    playback.index?.let { shown = it }
    AnimatedVisibility(
        visible = playback.index != null,
        // Выезжает из-под шапки: выше своего верха не рисуется (тень снизу и по бокам - остаётся).
        modifier = modifier.drawWithContent {
            clipRect(left = -size.width, right = size.width * 2, bottom = size.height * 2) { this@drawWithContent.drawContent() }
        },
        enter = slideInVertically(Motion.collapse()) { -it } + fadeIn(Motion.collapse()),
        exit = slideOutVertically(Motion.collapse()) { -it } + fadeOut(Motion.collapse()),
    ) {
        shown?.let { AlphabetPlayerBar(playback, letters, it) }
    }
}

/** Сама панель: какая буква звучит ([index] - от [AlphabetPlayerHost]) и «⏮ ⏸ ⏹ ⏭». */
@Composable
private fun AlphabetPlayerBar(playback: AlphabetPlayback, letters: List<ReadingLetter>, index: Int) {
    val letter = letters.getOrNull(index) ?: return
    val palette = InabahTheme.palette
    val colors = readingColors
    Row(
        Modifier
            .fillMaxWidth()
            .surface(colors.sheet, Radius.box, border = colors.tileBorder, shadow = ShadowToken.floating(palette))
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(letter.name, color = palette.onAccent, style = InabahType.subheadline.copy(fontWeight = FontWeight.SemiBold))
            Text(
                stringResource(R.string.makharij_alphabet_position, index + 1, letters.size),
                color = palette.onAccentSecondary,
                style = InabahType.caption,
            )
        }
        BarButton(R.drawable.ic_skip_previous, stringResource(R.string.makharij_alphabet_previous), playback::previous,
            enabled = index > 0)
        BarButton(
            if (playback.isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause,
            stringResource(if (playback.isPaused) R.string.makharij_alphabet_resume else R.string.makharij_alphabet_pause),
            playback::togglePause,
            prominent = true,
        )
        BarButton(R.drawable.ic_stop, stringResource(R.string.makharij_alphabet_stop), playback::stop)
        BarButton(R.drawable.ic_skip_next, stringResource(R.string.makharij_alphabet_next), { playback.next(letters.size) },
            enabled = index < letters.lastIndex)
    }
}

@Composable
private fun BarButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    prominent: Boolean = false,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    IconButton(
        onClick = onClick,
        icon = painterResource(iconRes),
        contentDescription = description,
        foreground = if (prominent) colors.onTint else palette.onAccent,
        background = if (prominent) colors.tint else colors.tile,
        shape = IconButtonShape.RoundedSquare,
        size = Size.visibleTapTarget,
        border = if (prominent) null else colors.tileBorder,
        enabled = enabled,
    )
}
