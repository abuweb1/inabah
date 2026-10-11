package app.inabah.android.feature.azkar

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.components.GlassCapsuleButton
import app.inabah.android.core.settings.STREAK_DISPLAY_MAX

/**
 * Серия дней под аятом на главной «Азкаров» (решение пользователя 2026-10-11): «✦ 12 дней подряд ›»,
 * без серии — «✦ Начните серию сегодня ›»; нажатие открывает календарь.
 */
@Composable
fun AzkarStreakCapsule(current: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    // Значки рядом с текстом интерфейса — в sp: растут вместе с ним.
    val iconSize = with(LocalDensity.current) { ICON_SIZE.sp.toDp() }
    GlassCapsuleButton(
        onClick = onClick,
        tint = palette.tabAzkar,
        onClickLabel = stringResource(R.string.azkar_calendar_open),
        modifier = modifier,
    ) {
        Icon(painterResource(R.drawable.ic_auto_awesome), contentDescription = null, tint = palette.gold, modifier = Modifier.size(iconSize))
        Text(
            if (current > 0) streakDaysText(current) else stringResource(R.string.azkar_streak_start),
            color = palette.onAccent,
            style = InabahType.subheadline,
        )
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = palette.onAccentSecondary,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** «12 дней подряд»; больше [STREAK_DISPLAY_MAX] — «9999+ дней подряд» (решение пользователя 2026-10-11). */
@Composable
fun streakDaysText(days: Int): String =
    if (days > STREAK_DISPLAY_MAX) stringResource(R.string.azkar_streak_days_max)
    else pluralStringResource(R.plurals.azkar_streak_days, days, days)

/** Число серии в сводке календаря: больше [STREAK_DISPLAY_MAX] — «9999+». */
@Composable
fun streakNumberText(days: Int): String =
    if (days > STREAK_DISPLAY_MAX) stringResource(R.string.azkar_streak_number_max) else days.toString()

private const val ICON_SIZE = 16
