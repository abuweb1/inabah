package app.inabah.android.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing

/** Значок 24 — размер символа title3 (кегль 20) с полями Material; в sp, растёт с шагом интерфейса. */
private const val NOTICE_ICON_SIZE = 24f

/**
 * Карточка-заглушка (iOS `ComingSoonCard`, компактная — `10-typography.md`, 10.3): значок золотом,
 * [title] и [message] по центру — «Скоро» в «Махрадже», пустое «Избранное», предложение напоминаний
 * (с кнопками [actions] под текстом). Для TalkBack — одним элементом и заголовком: находится в навигации
 * по заголовкам; кнопки остаются отдельными элементами.
 */
@Composable
fun NoticeCard(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { NOTICE_ICON_SIZE.sp.toDp() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .surface(palette.subtleFill, Radius.navCard, border = palette.hairline)
            .padding(vertical = Spacing.xxl, horizontal = Spacing.xlPlus)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = palette.gold, modifier = Modifier.size(iconSize))
        Text(title, color = palette.onAccent, style = InabahType.headline.copy(fontWeight = FontWeight.Bold))
        Text(message, color = palette.onAccentSecondary, style = InabahType.caption, textAlign = TextAlign.Center)
        if (actions != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                content = actions,
            )
        }
    }
}
