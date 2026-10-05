package app.inabah.android.feature.azkar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.PrimaryButton
import app.inabah.android.core.designsystem.components.SegmentedControl
import app.inabah.android.core.designsystem.components.SwitchRow
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.PlaylistSettings
import java.text.NumberFormat

/** 🎧 — как символ title2 (22 pt), в sp: растёт с шагом интерфейса. */
private const val HEADPHONES_SIZE = 26f
private const val RATE_FRACTION_DIGITS = 2

/**
 * «Прослушать все азкары» (iOS `AzkarPlayAllCard`) — последней в ленте: повторы по числу раз, пауза
 * между зикрами, скорость и «Слушать» / «Открыть плеер», если плейлист раздела уже активен.
 * Значения — готовые, изменения — колбэками (карточка не читает настройки и плеер).
 */
@Composable
fun AzkarPlayAllCard(
    isActive: Boolean,
    repeatsByCount: Boolean,
    pauseSeconds: Double,
    rate: Float,
    onRepeatsChange: (Boolean) -> Unit,
    onPauseChange: (Double) -> Unit,
    onRateChange: (Float) -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = InabahTheme.palette
    val locale = LocalConfiguration.current.locales[0]
    val rateFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = RATE_FRACTION_DIGITS }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .surface(palette.card, Radius.card, border = palette.goldBorder)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconSize = with(LocalDensity.current) { HEADPHONES_SIZE.sp.toDp() }
            Icon(painterResource(R.drawable.ic_headphones), contentDescription = null, tint = palette.gold,
                modifier = Modifier.size(iconSize))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
                Text(stringResource(R.string.audio_play_all_title), color = palette.textPrimary, style = InabahType.headline)
                Text(stringResource(R.string.audio_play_all_subtitle), color = palette.textSecondary, style = InabahType.footnote)
            }
        }
        SwitchRow(
            label = stringResource(R.string.audio_play_all_repeats),
            checked = repeatsByCount,
            onCheckedChange = onRepeatsChange,
            tint = palette.success,
        )
        Option(stringResource(R.string.audio_play_all_pause)) {
            SegmentedControl(
                options = PlaylistSettings.PAUSE_OPTIONS,
                selected = pauseSeconds,
                onSelect = onPauseChange,
                label = { stringResource(R.string.audio_play_all_pause_seconds, it.toInt()) },
            )
        }
        Option(stringResource(R.string.audio_play_all_rate)) {
            SegmentedControl(
                options = PlaylistSettings.RATE_OPTIONS,
                selected = rate,
                onSelect = onRateChange,
                // Десятичная запятая — по языку интерфейса: «0,75×».
                label = { stringResource(R.string.audio_play_all_rate_value, rateFormat.format(it)) },
            )
        }
        PrimaryButton(
            onClick = onListen,
            text = stringResource(if (isActive) R.string.audio_play_all_open else R.string.audio_play_all_start),
            leadingIcon = painterResource(if (isActive) R.drawable.ic_bottom_panel_open else R.drawable.ic_play_arrow),
            enabled = enabled,
        )
    }
}

@Composable
private fun Option(title: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, color = InabahTheme.palette.textSecondary, style = InabahType.subheadline)
        control()
    }
}
