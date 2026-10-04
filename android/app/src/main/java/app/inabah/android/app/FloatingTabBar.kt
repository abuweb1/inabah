package app.inabah.android.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ScheherazadeNew
import app.inabah.android.core.designsystem.components.fixedSp
import app.inabah.android.core.designsystem.components.surface

// Размеры — по снимкам iOS (системная плавающая панель вкладок, pt → dp один к одному).
private val BarHeight = 62.dp
private val BarHorizontalMargin = 21.dp
private val BarBottomMargin = 4.dp
private val PillInset = 4.dp
private val TabIconSize = 26.dp

/** Кегль подписи и буквы «ع»; панель iOS не растёт с системным шрифтом. */
private const val LABEL_SIZE = 11f
private const val GLYPH_SIZE = 21f

/**
 * Подложка капсулы — нейтральное затемнение (тень палитры): сквозь него виден цвет раздела под панелью,
 * как у стекла iOS; содержимое проходит под панелью.
 */
private const val BAR_FILL_ALPHA = 0.14f

/** Буква «ع» в насхе сидит низко (большая нижняя дуга) — поднять к центру значка. */
private val GlyphLift = (-5).dp

/** Обводка капсулы и «пилюля» выбранной вкладки — цвет вкладки с прозрачностью. */
private const val BAR_RIM_ALPHA = 0.45f
private const val PILL_ALPHA = 0.3f

/**
 * Плавающая панель вкладок, как системная панель iOS: капсула над нижним краем, выбранная вкладка —
 * на «пилюле» и в цвет своего раздела, остальные — белые. Пилюля переезжает к выбранной вкладке
 * (сдвиг отрисовки, не раскладки). Для TalkBack — группа вкладок.
 */
@Composable
fun FloatingTabBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val tint by animateColorAsState(selectedTab.tint(theme), Motion.highlight(), label = "tabTint")
    val pillIndex by animateFloatAsState(selectedTab.ordinal.toFloat(), Motion.collapse(), label = "tabPill")
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = BarHorizontalMargin, end = BarHorizontalMargin, bottom = BarBottomMargin)
            .height(BarHeight)
            .surface(
                palette.shadow.copy(alpha = BAR_FILL_ALPHA),
                BarHeight / 2,
                border = tint.copy(alpha = BAR_RIM_ALPHA),
                shadow = ShadowToken.card(palette),
            )
            .padding(PillInset),
    ) {
        val itemWidth = maxWidth / AppTab.entries.size
        val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
        Box(
            Modifier
                .width(itemWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = pillIndex * itemWidthPx }
                .surface(tint.copy(alpha = PILL_ALPHA), (BarHeight - PillInset * 2) / 2),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            AppTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    color = if (tab == selectedTab) tint else palette.onAccent,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: AppTab,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxxs, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(TabIconSize), contentAlignment = Alignment.Center) {
            when (val icon = tab.icon) {
                is TabIcon.Drawable -> Icon(painterResource(icon.resId), contentDescription = null, tint = color,
                    modifier = Modifier.size(TabIconSize))
                // Буква — встроенным Scheherazade, без увеличенной строки ArabicText: значок, не абзац.
                // Высокая строка шрифта не должна сжимать букву в рамке значка — без ограничения по высоте.
                is TabIcon.Glyph -> Text(
                    icon.text,
                    color = color,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .graphicsLayer { translationY = GlyphLift.toPx() },
                    style = TextStyle(fontFamily = ScheherazadeNew, fontWeight = FontWeight.Bold, fontSize = fixedSp(GLYPH_SIZE)),
                )
            }
        }
        Text(
            stringResource(tab.label),
            color = color,
            maxLines = 1,
            style = InabahType.caption2.copy(fontSize = fixedSp(LABEL_SIZE), fontWeight = FontWeight.SemiBold),
        )
    }
}
