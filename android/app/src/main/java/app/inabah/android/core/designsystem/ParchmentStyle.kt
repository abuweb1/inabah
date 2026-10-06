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
    /** Тёплая старая бумага — к «Янтарной» палитре. */
    Sepia(
        "sepia",
        ParchmentColors(
            light = Color(0xFFE9D2AE), highlight = Color(0xFFDEC39A), mid = Color(0xFFD9BD94),
            // Акцент темнее обычного: на самом тёмном оттенке сепии successDeep даёт 4,35 < 4,5.
            deep = Color(0xFFC9A97C), text = Color(0xFF2B1D10), accent = Color(0xFF1F4A2A),
        ),
    ),

    // Приглушённые средние тона под единые палитры (решение пользователя 2026-10-06: светлые «Слоновая кость»,
    // «Жемчужный», «Мятный» резали глаз и не подходили ни к одной палитре), текст светлый.

    /** Приглушённый лиловый с золотой рамкой (как золото счётчика на фиолетовом) — к «Фиолетовой». */
    Amethyst(
        "amethyst",
        ParchmentColors(
            light = Color(0xFF4A3A6E), highlight = Color(0xFF54427C), mid = Color(0xFF46376A),
            // Золото, а не зелёный: светло-зелёный на самом светлом оттенке давал 4,43 < 4,5.
            deep = Color(0xFF3B2E5C), text = Color(0xFFF3EEFA), accent = Color(0xFFECC87B),
        ),
    ),

    /** Глубокий зелёный с золотой рамкой — к «Изумрудной». */
    Jade(
        "jade",
        ParchmentColors(
            light = Color(0xFF1C4D40), highlight = Color(0xFF21584A), mid = Color(0xFF1B4A3D),
            deep = Color(0xFF153D32), text = Color(0xFFEDF7F2), accent = Color(0xFFECC87B),
        ),
    ),

    /**
     * Приглушённый медово-коричневый с золотой рамкой — к «Янтарной» (2026-10-06: «Пергамент» и «Сепия»
     * для неё слишком светлые, нужен тон в манере остальных).
     */
    Amber(
        "amber",
        ParchmentColors(
            light = Color(0xFF5A3818), highlight = Color(0xFF664020), mid = Color(0xFF553415),
            deep = Color(0xFF472B10), text = Color(0xFFF8EEDF), accent = Color(0xFFECC87B),
        ),
    ),

    /** Дымчатый серо-голубой — к «Графиту». */
    Smoky(
        "smoky",
        ParchmentColors(
            light = Color(0xFF3A414F), highlight = Color(0xFF434B5B), mid = Color(0xFF39404D),
            // Светлее цвета вкладки «Настройки» (8FB4F0 давал 4,15 < 4,5).
            deep = Color(0xFF2F3541), text = Color(0xFFEEF1F6), accent = Color(0xFFB4CBF4),
        ),
    ),

    /** Тёмный фон со светлым текстом — подходит к любой палитре, для чтения в темноте без яркого пятна. */
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
