package app.inabah.android.core.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.inabah.android.R

/** Scheherazade New (SIL OFL) — шрифт всего арабского текста. */
val ScheherazadeNew = FontFamily(
    Font(R.font.scheherazade_new_regular),
    Font(R.font.scheherazade_new_bold, FontWeight.Bold),
)

/**
 * Арабский текст: Scheherazade New, справа налево.
 * [size] — кегль в pt iOS; не масштабируется системным размером шрифта (как `fixedSize` в iOS).
 * Межстрочный интервал и прочие правила docs/android/05-design-system.md, 5.4 — этап 2.
 */
@Composable
fun ArabicText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    textAlign: TextAlign = TextAlign.Center,
) {
    val fontScale = LocalDensity.current.fontScale
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = TextStyle(
                fontFamily = ScheherazadeNew,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontSize = (size / fontScale).sp,
                textAlign = textAlign,
                textDirection = TextDirection.Rtl,
            ),
        )
    }
}
