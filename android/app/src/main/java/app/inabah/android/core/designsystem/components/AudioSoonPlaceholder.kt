package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing

/** ▶ — как символ headline, в sp (растёт с шагом интерфейса); круг 44 — постоянный, как в iOS. */
private const val PLAY_ICON_SIZE = 20f

/**
 * Записей пока нет — неактивная карточка «[title] / Аудио скоро» (iOS `AudioSoonPlaceholder`): у хадиса
 * («Аудио · Хадис 2») и вместо «Прослушать все азкары». Для TalkBack — одна строка из подписей.
 */
@Composable
fun AudioSoonPlaceholder(title: String, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .surface(palette.subtleFill, Radius.box)
            .padding(vertical = Spacing.m, horizontal = Spacing.l)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Неактивный значок — не кнопка «Воспроизвести»: TalkBack читает только подписи.
        Box(Modifier.size(Size.visibleTapTarget).background(palette.track, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = palette.onAccentTertiary,
                modifier = Modifier.size(with(LocalDensity.current) { PLAY_ICON_SIZE.sp.toDp() }))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
            Text(title, color = palette.onAccentSecondary, style = InabahType.caption)
            Text(stringResource(R.string.audio_soon), color = palette.onAccentTertiary, style = InabahType.caption2)
        }
    }
}
