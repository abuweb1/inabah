package app.inabah.android.core.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.inabah.android.R

/** Scheherazade New (SIL OFL) — шрифт всего арабского текста. */
val ScheherazadeNew = FontFamily(
    Font(R.font.scheherazade_new_regular),
    Font(R.font.scheherazade_new_bold, FontWeight.Bold),
)

/**
 * Кегль, который не растёт с системным размером шрифта: [points] dp, переведённые в sp через
 * плотность экрана. Не `points / fontScale` — с Android 14 шрифт масштабируется нелинейно
 * (крупные кегли растут меньше), и деление на `fontScale` уменьшало бы текст.
 */
@Composable
internal fun fixedSp(points: Float): TextUnit = with(LocalDensity.current) { points.dp.toSp() }

/**
 * KFGQPC Uthmanic Script HAFS — шрифт Мединского мусхафа для сур и аятов Корана в азкарах (решение пользователя
 * 2026-10-25; лицензия — `assets/licenses/KFGQPC-Hafs-LICENSE.txt`, файл встроен без изменений). Жирного нет.
 */
val MushafHafs = FontFamily(Font(R.font.kfgqpc_uthmanic_hafs))

/** Hafs при том же кегле мельче Scheherazade: ×1,2 — буквы той же высоты (проба `docs/design/mushaf-font.html`). */
const val MUSHAF_SCALE = 1.2f

/** Собственная высота строки Scheherazade New (hhea: (2750 + 1427) / 2048 ≈ 2,04 кегля). */
private const val NATURAL_LINE_HEIGHT = 2.04f

/** Собственная высота строки Hafs (hhea: (2400 + 1200) / 2048 ≈ 1,76 кегля). */
private const val MUSHAF_NATURAL_LINE_HEIGHT = 1.76f

/** Добавочный межстрочный интервал, как `lineSpacing = кегль × 0,3` в iOS. */
private const val EXTRA_LINE_SPACING = 0.3f

/**
 * Арабский текст (iOS `ArabicText`): Scheherazade New, справа налево; [TextAlign.Start] — правый край.
 * [size] — кегль в pt iOS; **не масштабируется** системным размером шрифта (как `fixedSize` в iOS),
 * интервал между строками — собственный шрифта + 0,3 кегля.
 */
@Composable
fun ArabicText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    /** Текст Корана — шрифтом мусхафа ([MushafHafs]), кегль × [MUSHAF_SCALE]. */
    mushaf: Boolean = false,
) = ArabicText(AnnotatedString(text), size, color, modifier, bold, textAlign, maxLines, mushaf)

/** То же с выделением частей текста (цвет буквы в слове-примере). */
@Composable
fun ArabicText(
    text: AnnotatedString,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    mushaf: Boolean = false,
) {
    val style = arabicTextStyle(size, bold, textAlign, mushaf)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
            style = style,
        )
    }
}

/**
 * Стиль [ArabicText] без цвета: им же меряют арабский текст до вёрстки (`TextMeasurer`), чтобы
 * размер совпал с нарисованным (ширина плиток «Читаем слова», 2026-10-16).
 */
@Composable
fun arabicTextStyle(size: Float, bold: Boolean = false, textAlign: TextAlign = TextAlign.Start, mushaf: Boolean = false): TextStyle {
    val points = if (mushaf) size * MUSHAF_SCALE else size
    val naturalLineHeight = if (mushaf) MUSHAF_NATURAL_LINE_HEIGHT else NATURAL_LINE_HEIGHT
    return TextStyle(
        fontFamily = if (mushaf) MushafHafs else ScheherazadeNew,
        fontWeight = if (bold && !mushaf) FontWeight.Bold else FontWeight.Normal,
        fontSize = fixedSp(points),
        lineHeight = fixedSp(points * (naturalLineHeight + EXTRA_LINE_SPACING)),
        textAlign = textAlign,
        textDirection = TextDirection.Rtl,
    )
}

/**
 * Начало арабского текста для свёрнутой строки (зикр, хадис): первые [words] слов и «…»; короче —
 * целиком. Строка показывает одну строку — хватает первых слов (вёрстка длинного текста дороже).
 */
fun arabicPreview(arabic: String, words: Int = PREVIEW_WORDS): String {
    val parts = arabic.split(Whitespace).filter(String::isNotEmpty)
    return if (parts.size > words) parts.take(words).joinToString(" ") + "…" else parts.joinToString(" ")
}

private const val PREVIEW_WORDS = 8
private val Whitespace = Regex("\\s+")
