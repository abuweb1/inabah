package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Фон под арабским текстом — «пергамент» азкаров и хадисов (настройка в «Палитре», решение пользователя
 * 2026-10-06: готовый набор, один на оба раздела). Цвета подобраны вручную вместе с текстом — контраст
 * не ниже 7:1 к каждому оттенку проверяет `ParchmentStyleTest`. Рамка, «✦» и значок «N раз» — [ParchmentColors.accent]
 * (у тёмного «Ночного» светлее), полоса сверху — общая (`success*`).
 * HEX вариантов — только здесь (как в [Palette]). На iOS пока нет: docs/07-screen-settings.md.
 */
enum class ParchmentStyle(val key: String, val colors: ParchmentColors) {
    /** Нынешний золотой — совпадает с [Palette.Sections], вид по умолчанию не меняется. */
    Classic(
        "classic",
        ParchmentColors(
            light = Color(0xFFF5D07A), highlight = Color(0xFFECC87B), mid = Color(0xFFF3D080),
            deep = Color(0xFFE8C068), text = Color(0xFF1A1208),
        ),
    ),
    Ivory(
        "ivory",
        ParchmentColors(
            light = Color(0xFFF8F1E1), highlight = Color(0xFFF2E8D2), mid = Color(0xFFEFE4CC),
            deep = Color(0xFFE4D6B8), text = Color(0xFF2A2016),
        ),
    ),
    Sepia(
        "sepia",
        ParchmentColors(
            light = Color(0xFFE9D2AE), highlight = Color(0xFFDEC39A), mid = Color(0xFFD9BD94),
            // Акцент темнее обычного: на самом тёмном оттенке сепии successDeep даёт 4,35 < 4,5.
            deep = Color(0xFFC9A97C), text = Color(0xFF2B1D10), accent = Color(0xFF1F4A2A),
        ),
    ),
    Mint(
        "mint",
        ParchmentColors(
            light = Color(0xFFD9EFDF), highlight = Color(0xFFCBE6D3), mid = Color(0xFFC6E2CE),
            deep = Color(0xFFB2D5BC), text = Color(0xFF0F2A1B),
        ),
    ),
    Pearl(
        "pearl",
        ParchmentColors(
            light = Color(0xFFE6EAF0), highlight = Color(0xFFDCE2EA), mid = Color(0xFFD6DDE6),
            deep = Color(0xFFC5CEDA), text = Color(0xFF161C26),
        ),
    ),

    /** Тёмный фон со светлым текстом — для чтения в темноте без яркого пятна. */
    Night(
        "night",
        ParchmentColors(
            light = Color(0xFF2E2A40), highlight = Color(0xFF352F4C), mid = Color(0xFF2B2640),
            deep = Color(0xFF221E34), text = Color(0xFFF0EAF8), accent = Color(0xFF6DBF7E),
        ),
    ),
    ;

    companion object {
        fun fromKey(key: String): ParchmentStyle? = entries.firstOrNull { it.key == key }
    }
}

/** Тёмно-зелёный акцент пергамента — [Palette.successDeep]. */
private val DEFAULT_ACCENT = Color(0xFF2A5C35)

/** Оттенки градиента пергамента (по порядку стопов) и цвет текста на нём. */
data class ParchmentColors(
    val light: Color,
    val highlight: Color,
    val mid: Color,
    val deep: Color,
    val text: Color,
    /** Рамка, «✦» и значок «N раз»; по умолчанию — successDeep, как раньше. */
    val accent: Color = DEFAULT_ACCENT,
) {
    /** Все оттенки фона — для проверки контраста текста. */
    val backgrounds: List<Color> get() = listOf(light, highlight, mid, deep)
}

/**
 * Тема с фоном [style] под арабским текстом: меняются только пергамент и текст на нём — золото и «чернила»
 * кнопок ([Palette.gold], [Palette.parchmentInk]) остаются. [ParchmentStyle.Classic] — пергамент по умолчанию.
 */
fun Theme.withParchment(style: ParchmentStyle): Theme {
    // Всегда подставлять цвета варианта, и для Classic: тема на входе может уже нести другой фон (превью
    // плиток в «Палитре» строится от темы с выбранным фоном). Результат равен по значению — animateTheme не перезапускается.
    val colors = style.colors
    val parchmentPalette = palette.copy(
        parchmentLight = colors.light,
        parchmentHighlight = colors.highlight,
        parchmentMid = colors.mid,
        parchmentDeep = colors.deep,
        parchmentText = colors.text,
        parchmentAccent = colors.accent,
    )
    return Theme(parchmentPalette, gradients.copy(parchment = ThemeGradients.parchment(parchmentPalette)))
}
