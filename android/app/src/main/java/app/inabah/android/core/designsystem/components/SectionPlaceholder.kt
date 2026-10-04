package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Spacing

/**
 * Временный экран раздела (этап 0): заголовок и подпись по центру.
 * Фон раздела рисует корень (под строкой состояния и вкладками), [contentPadding] — отступы от системных панелей.
 */
@Composable
fun SectionPlaceholder(
    title: String,
    message: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    val palette = InabahTheme.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            header()
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = palette.onAccent,
                style = InabahType.headline,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = palette.onAccentSecondary,
                style = InabahType.caption,
                textAlign = TextAlign.Center,
            )
        }
    }
}
