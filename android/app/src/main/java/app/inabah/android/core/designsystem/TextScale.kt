package app.inabah.android.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Размер текста (docs/android/10-typography.md, 10.1): у каждого текста одна категория —
// арабский (только А−/А+), перевод (шаг переводов), интерфейс (шаг интерфейса), закреплённое
// и плеер (ничего). Системный размер шрифта не влияет ни на что: корень — [FixedTextSize].

/** Множитель переводов — шаг «Размера переводов»; ставит корень приложения. */
val LocalContentTextScale = staticCompositionLocalOf { 1f }

/** Содержимое — со стандартным масштабом шрифта: закреплённое (навбар, кнопки, всплывающие окна) и корень. */
@Composable
fun FixedTextSize(content: @Composable () -> Unit) = ScaledText(1f, content)

/**
 * Содержимое экрана вкладки — с шагом интерфейса [fontScale]: всё в `sp` растёт само.
 * Ставить на экран, а не на навигацию и панель вкладок; закреплённые компоненты внутри
 * экрана сами возвращают стандартный масштаб.
 */
@Composable
fun InterfaceTextScale(fontScale: Float, content: @Composable () -> Unit) = ScaledText(fontScale, content)

@Composable
private fun ScaledText(fontScale: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current.density
    // Всегда свой класс: смена объекта плотности под жестом сбрасывает `pointerInput` (ползунок
    // интерфейса терял перетаскивание), а равные значения — нет.
    val density = remember(base, fontScale) { LinearTextDensity(base, fontScale) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/**
 * Плотность с **линейным** масштабом шрифта: стандартная `Density(d, fontScale)` от 1,03 включает
 * нелинейные таблицы Android — крупный текст и значки в `sp` росли бы меньше мелкого, а шаг
 * интерфейса — один множитель на всё (как Dynamic Type для body).
 */
private data class LinearTextDensity(override val density: Float, override val fontScale: Float) : Density {
    override fun Dp.toSp(): TextUnit = (value / fontScale).sp

    override fun TextUnit.toDp(): Dp = (value * fontScale).dp
}
