package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.monospacedDigits

// Экраны настроек — как сгруппированные списки iOS 26 (снимки пользователя android/docs/settings):
// группы на subtleFill с крупным скруглением, жирный заголовок группы над ней, подпись под ней,
// разделители между строками с отступом слева.

private val GroupRadius = 26.dp
private val RowMinHeight = 56.dp
private val RowHorizontalPadding = 22.dp
private val GroupSpacing = 24.dp
private val SettingsIconSize = 32.dp
private val SettingsIconGlyph = 20.dp
private val ChevronSize = 22.dp

/**
 * Каркас экрана настроек: градиент раздела до краёв, сверху — [topBar] (навбар с «‹») или крупный
 * заголовок [largeTitle] (корень), группы прокручиваются; снизу — [contentPadding] (панель вкладок).
 */
@Composable
fun SettingsScaffold(
    background: Brush,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    largeTitle: String? = null,
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(background)) {
        topBar?.invoke()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (topBar == null) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(horizontal = Spacing.xl)
                .padding(bottom = contentPadding.calculateBottomPadding() + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(GroupSpacing),
        ) {
            largeTitle?.let {
                Text(
                    it,
                    color = InabahTheme.palette.onAccent,
                    style = InabahType.largeTitle.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = Spacing.xxxl).semantics { heading() },
                )
            }
            content()
        }
    }
}

/** Группа строк с жирным заголовком [header] и подписью [footer]; строки — [rows], разделители — сами. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    rows: List<@Composable () -> Unit>,
) {
    val palette = InabahTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        header?.let {
            Text(
                it,
                color = palette.onAccent,
                style = InabahType.headline.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = RowHorizontalPadding).semantics { heading() },
            )
        }
        Column(Modifier.fillMaxWidth().surface(palette.subtleFill, GroupRadius)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = RowHorizontalPadding)
                            .fillMaxWidth()
                            .height(Size.hairline)
                            .background(palette.hairline),
                    )
                }
                row()
            }
        }
        footer?.let {
            Text(it, color = palette.onAccentSecondary, style = InabahType.footnote,
                modifier = Modifier.padding(horizontal = RowHorizontalPadding))
        }
    }
}

/**
 * Строка настроек: [content] слева, [trailing] справа; нажатие — [onClick] (затемнение строки,
 * как в списках iOS), без него — строка только показывает значение.
 */
@Composable
fun SettingsRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                        .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY)
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else PressFeedback.DISABLED_OPACITY)
            .padding(horizontal = RowHorizontalPadding, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            content()
        }
        trailing()
    }
}

/** Текст строки: заголовок body и необязательная подпись caption. */
@Composable
fun SettingsRowText(title: String, subtitle: String? = null, color: Color = InabahTheme.palette.onAccent) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
        Text(title, color = color, style = InabahType.body)
        subtitle?.let { Text(it, color = InabahTheme.palette.onAccentSecondary, style = InabahType.caption) }
    }
}

/** Значок строки корня настроек: символ на градиенте раздела, 32 dp, скругление 5. */
@Composable
fun SettingsIcon(iconRes: Int, gradient: Brush, tint: Color = InabahTheme.palette.onAccent) {
    Box(Modifier.size(SettingsIconSize).surface(gradient, Radius.small), contentAlignment = Alignment.Center) {
        Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(SettingsIconGlyph))
    }
}

/** «›» строки-перехода. */
@Composable
fun SettingsChevron() {
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
        tint = InabahTheme.palette.onAccentTertiary, modifier = Modifier.size(ChevronSize))
}

/** Капсула значения справа в строке (время «17:00»); [highlighted] — открыт выбор. */
@Composable
fun SettingsValueChip(text: String, highlighted: Boolean, tint: Color) {
    val palette = InabahTheme.palette
    Text(
        text,
        color = if (highlighted) tint else palette.onAccent,
        style = InabahType.body.monospacedDigits(),
        modifier = Modifier
            .surface(SolidColor(palette.onAccent.copy(alpha = CHIP_ALPHA)), RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.l, vertical = Spacing.xs),
    )
}

private const val CHIP_ALPHA = 0.1f

/** Отступ содержимого вкладки снизу без верхнего (верх даёт навбар или крупный заголовок). */
fun PaddingValues.bottomOnly(): PaddingValues = PaddingValues(bottom = calculateBottomPadding())
