package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.monospacedDigits

private val ErrorIconSize = 44.dp

/**
 * Шапка прогресса под навбаром списка (азкары, хадисы): [label] и [percent] над полосой [fraction],
 * фон — цвет навбара [background]. Для TalkBack — одна фраза [label].
 */
@Composable
fun ProgressHeader(
    label: String,
    percent: String,
    fraction: Float,
    background: Color,
    fill: Brush,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xs, bottom = Spacing.m)
            .clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = palette.onAccentSecondary, style = InabahType.caption)
            Text(percent, color = palette.onAccentSecondary, style = InabahType.caption.monospacedDigits())
        }
        val animated by animateFloatAsState(fraction, Motion.progress(), label = "header")
        LinearProgressBar(animated, palette.track, fill)
    }
}

/** Контент не загрузился (iOS `ContentUnavailableView`): значок, «Не удалось загрузить», «Повторить». */
@Composable
fun ContentError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    Column(
        modifier.padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = palette.onAccentSecondary,
            modifier = Modifier.size(ErrorIconSize))
        Text(stringResource(R.string.content_error_title), color = palette.onAccent,
            style = InabahType.title3.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.content_error_message), color = palette.onAccentSecondary,
            style = InabahType.subheadline, textAlign = TextAlign.Center)
        PrimaryButton(onClick = onRetry, text = stringResource(R.string.content_error_retry),
            modifier = Modifier.padding(top = Spacing.m))
    }
}
