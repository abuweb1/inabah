package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

// Ползунок «Фон перевода» (решение пользователя 2026-10-10, docs/analysis/2026-10-10-shared-translation-block.md):
// −1 — темнее, 0 — как задумано, +1 — светлее до предела читаемости в этой палитре.

/** Доля чёрного у фона перевода при −1 — «почти чёрный», текст не теряется. */
private const val DARK_MAX = 0.8f

/** Перевод — как основной текст (WCAG AAA), остальные строки — AA. */
private const val TEXT_MIN = 7.0
private const val LABEL_MIN = 4.5

/** Источник зикра (`textTertiary`) — третьестепенный и уже сейчас ниже AA (~3,4:1 «По умолчанию»): не ниже 3:1. */
private const val SOURCE_MIN = 3.0

/**
 * Во сколько раз быстрее фона светлеет источник зикра: к `textSecondary` он приходит при доле осветления 1/4
 * (решение пользователя 2026-10-10 — иначе бледный источник не давал осветлить карточку зикра).
 */
private const val SOURCE_LIFT = 4f

/** Шагов двоичного поиска предела осветления — точность 1/4096 доли, считается раз на палитру. */
private const val SEARCH_STEPS = 12

/** Наименьшая сдвинутая доля, которая уже меняет цвет; меньше — тема без изменений. */
private const val NO_SHADE = 1e-4f

/**
 * Тема со сдвинутым фоном перевода: карточка и пузырь зикра (`zikrCard`, `accentDim`) и карточка хадиса
 * (`hadithCard`); светлее — и источник зикра (`zikrSource`), чтобы оставался читаемым. [shade] — от −1 (темнее, к чёрному) до +1 (светлее, к цвету раздела: `accentLight` у азкаров,
 * `tabHadith` у хадисов — в единых стилях это цвета стиля); около 0 — та же тема. Светлее — не дальше
 * [translationLightLimit]: текст на краю ползунка читается так же, как требуют тесты палитр.
 * Применять к теме **без** сдвига (`MainActivity` — после `withParchment`; образец в «Палитре» — так же).
 */
fun Theme.withTranslationShade(shade: Float): Theme {
    val value = shade.coerceIn(-1f, 1f)
    if (value > -NO_SHADE && value < NO_SHADE) return this
    val p = palette
    val shaded = if (value < 0f) {
        val t = -value * DARK_MAX
        p.copy(
            zikrCard = lerp(p.zikrCard, Color.Black, t),
            accentDim = lerp(p.accentDim, Color.Black, t),
            hadithCard = lerp(p.hadithCard, Color.Black, t),
        )
    } else {
        val azkar = value * translationLightLimit(p, TranslationCard.Azkar)
        val hadith = value * translationLightLimit(p, TranslationCard.Hadith)
        p.copy(
            zikrCard = lerp(p.zikrCard, p.accentLight, azkar),
            accentDim = lerp(p.accentDim, p.accentLight, azkar),
            zikrSource = zikrSource(p, azkar),
            hadithCard = lerp(p.hadithCard, p.tabHadith, hadith),
        )
    }
    return copy(palette = shaded)
}

/** Карточка перевода, к которой относится предел. */
internal enum class TranslationCard { Azkar, Hadith }

/**
 * Наибольшая доля осветления (0…1) фона перевода [card] в палитре [palette], при которой строки на нём
 * читаются: перевод ≥ 7:1, «Передал», транскрипция, источник хадиса и сноски ≥ 4,5:1, источник зикра ≥ 3:1 — а если в палитре уже меньше
 * порога, то не хуже, чем сейчас (предел не уходит ниже 0).
 */
internal fun translationLightLimit(palette: Palette, card: TranslationCard): Float {
    val required = readability(palette, card, 0f).map { (ratio, minimum) -> minOf(ratio, minimum) }
    fun readable(t: Float) = readability(palette, card, t).zip(required).all { (check, need) -> check.first >= need }
    if (readable(1f)) return 1f
    var low = 0f
    var high = 1f
    repeat(SEARCH_STEPS) {
        val mid = (low + high) / 2
        if (readable(mid)) low = mid else high = mid
    }
    return low
}

/** Источник зикра на фоне, осветлённом на долю [t]: от `zikrSource` к `textSecondary`, в [SOURCE_LIFT] раз быстрее фона. */
private fun zikrSource(p: Palette, t: Float): Color = lerp(p.zikrSource, p.textSecondary, (t * SOURCE_LIFT).coerceAtMost(1f))

/** Контраст каждой строки на фоне, осветлённом на долю [t], — пары (контраст, порог). */
private fun readability(p: Palette, card: TranslationCard, t: Float): List<Pair<Double, Double>> = when (card) {
    TranslationCard.Azkar -> {
        val background = lerp(p.zikrCard, p.accentLight, t)
        val bubble = lerp(p.accentDim, p.accentLight, t)
        listOf(
            contrast(p.textPrimary, bubble) to TEXT_MIN,
            contrast(p.textSecondary, background) to LABEL_MIN,
            contrast(zikrSource(p, t), background) to SOURCE_MIN,
        )
    }
    TranslationCard.Hadith -> {
        val background = lerp(p.hadithCard, p.tabHadith, t)
        listOf(
            contrast(p.textPrimary, background) to TEXT_MIN,
            contrast(p.noteText, background) to LABEL_MIN,
            contrast(p.onAccentSecondary, background) to LABEL_MIN,
            contrast(p.noteText, p.noteFill.compositeOver(background)) to LABEL_MIN,
        )
    }
}

/** Контраст с учётом прозрачности текста (`onAccentSecondary` — белый 60 %). */
private fun contrast(foreground: Color, background: Color): Double =
    Contrast.ratio(foreground.compositeOver(background), background)
