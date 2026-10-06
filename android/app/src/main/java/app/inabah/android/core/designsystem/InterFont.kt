package app.inabah.android.core.designsystem

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface as AndroidTypeface
import android.graphics.fonts.Font as AndroidFontFile
import android.graphics.fonts.FontFamily as AndroidFontFamily
import android.graphics.fonts.FontStyle as AndroidFontStyle
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.inabah.android.R
import java.io.IOException

// Шрифт интерфейса — Inter (SIL OFL, rsms/inter 4.1): ближайший открытый аналог SF Pro, системного
// шрифта iOS (сам SF Pro по лицензии Apple вне платформ Apple использовать нельзя; решение
// пользователя 2026-10-04). Переменный шрифт: вес (wght 100–900) и оптический размер (opsz 14–32);
// как SF Pro Text / Display: мелкий текст — текстовый вариант, от 20 pt — Display.
//
// Только встроенные шрифты (решение пользователя): выбранный в системе шрифт (OEM-темы, «Шрифты»
// в настройках) на приложение не влияет. Символы, которых нет в Inter (арабские слова и «ﷺ»
// в русском тексте), берутся из встроенного Scheherazade New — своей цепочкой подстановки
// (Typeface.CustomFallbackBuilder), а не из системных шрифтов. Покрытие текстов — FontCoverageTest.

private const val TAG = "InabahFonts"

/** Оптический размер: текстовый (мелкий текст) и Display (заголовки от 20 pt). */
private const val TEXT_OPTICAL_SIZE = 14f
private const val DISPLAY_OPTICAL_SIZE = 32f

/** Веса, которые использует интерфейс. */
private val Weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
private val Styles = listOf(FontStyle.Normal, FontStyle.Italic)

/** Шрифты интерфейса: текстовый и Display. Создаются один раз в [InabahTheme]. */
@Immutable
class InabahFonts(val text: FontFamily, val display: FontFamily) {
    companion object {
        fun from(resources: Resources): InabahFonts = try {
            InabahFonts(
                text = interWithArabicFallback(resources, TEXT_OPTICAL_SIZE),
                display = interWithArabicFallback(resources, DISPLAY_OPTICAL_SIZE),
            )
        } catch (error: IOException) {
            // Файл шрифта в APK не читается — дефект сборки; без своей подстановки, но всё равно Inter.
            Log.e(TAG, "Не удалось собрать шрифт с подстановкой", error)
            Fallback
        }

        /** Inter без своей подстановки — превью и запасной вариант. */
        val Fallback = InabahFonts(text = composeInter(TEXT_OPTICAL_SIZE), display = composeInter(DISPLAY_OPTICAL_SIZE))
    }
}

val LocalInabahFonts = staticCompositionLocalOf { InabahFonts.Fallback }

/**
 * Семейство Compose из готовых Typeface: на каждый вес и начертание — свой Typeface
 * «Inter нужного веса → Scheherazade New». Compose выбирает начертание по весу и курсиву сам.
 */
@Throws(IOException::class)
private fun interWithArabicFallback(resources: Resources, opticalSize: Float): FontFamily {
    val arabicRegular = AndroidFontFile.Builder(resources, R.font.scheherazade_new_regular).build()
    val arabicBold = AndroidFontFile.Builder(resources, R.font.scheherazade_new_bold)
        .setWeight(FontWeight.Bold.weight).build()
    val fonts = Weights.flatMap { weight ->
        Styles.map { style ->
            val italic = style == FontStyle.Italic
            val slant = if (italic) AndroidFontStyle.FONT_SLANT_ITALIC else AndroidFontStyle.FONT_SLANT_UPRIGHT
            val inter = AndroidFontFile.Builder(resources, if (italic) R.font.inter_variable_italic else R.font.inter_variable)
                .setWeight(weight.weight)
                .setSlant(slant)
                .setFontVariationSettings("'wght' ${weight.weight}, 'opsz' $opticalSize")
                .build()
            val arabic = if (weight.weight >= FontWeight.SemiBold.weight) arabicBold else arabicRegular
            val typeface = AndroidTypeface.CustomFallbackBuilder(AndroidFontFamily.Builder(inter).build())
                .addCustomFallback(AndroidFontFamily.Builder(arabic).build())
                .setStyle(AndroidFontStyle(weight.weight, slant))
                .build()
            PrebuiltFont(typeface, weight, style)
        }
    }
    return FontFamily(fonts)
}

/** Шрифт Compose поверх уже собранного Typeface (с подстановкой): загрузка — сразу, без файлов. */
private class PrebuiltFont(
    val typeface: AndroidTypeface,
    override val weight: FontWeight,
    override val style: FontStyle,
) : AndroidFont(FontLoadingStrategy.Blocking, Loader, FontVariation.Settings(weight, style)) {
    private object Loader : TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): AndroidTypeface = (font as PrebuiltFont).typeface

        override suspend fun awaitLoad(context: Context, font: AndroidFont): AndroidTypeface = (font as PrebuiltFont).typeface
    }
}

private fun composeInter(opticalSize: Float): FontFamily = FontFamily(
    Weights.flatMap { weight ->
        Styles.map { style ->
            Font(
                resId = if (style == FontStyle.Italic) R.font.inter_variable_italic else R.font.inter_variable,
                weight = weight,
                style = style,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight.weight),
                    FontVariation.Setting("opsz", opticalSize),
                ),
            )
        }
    },
)
