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
            if (current > 0) pluralStringResource(R.plurals.azkar_streak_days, current, current) else stringResource(R.string.azkar_streak_start),
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

private const val ICON_SIZE = 16
