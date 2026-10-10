package app.inabah.android.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Средний стоп градиента карточки — по нему сравнивают iOS-тесты (у карточек три стопа). */
private val AngledGradient.mid: Color
    get() {
        check(colors.size == 3) { "у карточки ожидалось три стопа, их ${colors.size}" }
        return colors[1]
    }

/** «Палитры оформления». */
class ThemeStyleTest {
    private val unified = ThemeStyle.entries - ThemeStyle.Sections

    @Test
    fun `«По умолчанию» — прежняя тема без изменений`() {
        assertEquals(Theme.Sections, ThemeStyle.Sections.theme)
    }

    /** Полнота цветов стиля проверяется компилятором (именованные поля `ThemeColors`); здесь — различие. */
    @Test
    fun `У единых стилей есть все цвета, и стили различаются`() {
        val themes = unified.map { it.theme }
        for ((style, theme) in unified.zip(themes)) {
            assertNotEquals(Theme.Sections, theme, "стиль $style")
        }
        assertEquals(unified.size, themes.toSet().size, "стили попарно различаются")
    }

    @Test
    fun `Вторичный текст читается на карточке зикра (≥ 4,5 к 1)`() {
        for (style in ThemeStyle.entries) {
            val palette = style.theme.palette
            val ratio = Contrast.ratio(Contrast.luminance(palette.textSecondary), Contrast.luminance(palette.card))
            assertTrue(ratio >= 4.5, "стиль $style: контраст ${"%.2f".format(ratio)}")
        }
    }

    @Test
    fun `Белый заголовок читается на карточках навигации (≥ 3 к 1, крупный жирный)`() {
        for (style in ThemeStyle.entries) {
            val g = style.theme.gradients
            for ((name, card) in listOf(
                "утренние" to g.morningCard, "вечерние" to g.eveningCard, "ан-Навави" to g.nawawiCard,
                "аль-Къудси" to g.qudsiCard, "аль-Аджурри" to g.ajurriCard,
            )) {
                val ratio = Contrast.ratio(1.0, Contrast.luminance(card.mid))
                assertTrue(ratio >= 3.0, "стиль $style, $name: контраст ${"%.2f".format(ratio)}")
            }
        }
    }

    @Test
    fun `Утренние светлее вечерних, карточки сборников явно различимы`() {
        for (style in ThemeStyle.entries) {
            val g = style.theme.gradients
            assertTrue(
                Contrast.luminance(g.morningCard.mid) > Contrast.luminance(g.eveningCard.mid),
                "стиль $style: утренние не светлее вечерних",
            )
            val collections = listOf(g.nawawiCard.mid, g.qudsiCard.mid, g.ajurriCard.mid)
            for (i in collections.indices) for (j in i + 1 until collections.size) {
                val distance = Contrast.distance(collections[i], collections[j])
                assertTrue(distance >= 0.07, "стиль $style: сборники $i и $j близки (${"%.3f".format(distance)})")
            }
        }
    }

    @Test
    fun `Единый стиль не трогает золото, пергамент, статусы, success и основной текст`() {
        val base = Palette.Sections
        for (style in unified) {
            val p = style.theme.palette
            assertEquals(
                listOf(base.gold, base.goldLight, base.goldDeep, base.sunRays, base.parchmentLight, base.parchmentMid,
                    base.parchmentDeep, base.parchmentInk, base.statusRead, base.statusMemorized, base.success,
                    base.successLight, base.successDeep, base.successDim, base.onAccent, base.textPrimary,
                    base.parchmentHighlight, base.parchmentText, base.parchmentAccent),
                listOf(p.gold, p.goldLight, p.goldDeep, p.sunRays, p.parchmentLight, p.parchmentMid,
                    p.parchmentDeep, p.parchmentInk, p.statusRead, p.statusMemorized, p.success,
                    p.successLight, p.successDeep, p.successDim, p.onAccent, p.textPrimary,
                    p.parchmentHighlight, p.parchmentText, p.parchmentAccent),
                "стиль $style",
            )
            assertEquals(Theme.Sections.gradients.parchment, style.theme.gradients.parchment)
            // Один фон на все разделы.
            val g = style.theme.gradients
            assertEquals(1, setOf(g.azkarBackground, g.eveningBackground, g.hadithBackground,
                g.settingsBackground, g.makharijBackground).size, "стиль $style")
        }
    }
}

/**
 * Единый стиль собран из цветов своего раздела (как опорные цвета скрипта): ловит перепутанные
 * палитры стилей, неверный токен в `unified()` и потерю стопов/угла в `withColors`.
 */
class UnifiedThemeAnchorTest {
    private val sections = Theme.Sections

    @Test
    fun `Фиолетовая — фон и карточки азкаров, шапка, карточка и акцент «По умолчанию»`() {
        val violet = ThemeStyle.Violet.theme
        assertEquals(sections.gradients.azkarBackground, violet.gradients.hadithBackground)
        assertEquals(sections.gradients.morningCard, violet.gradients.morningCard)
        assertEquals(sections.gradients.eveningCard, violet.gradients.eveningCard)
        assertEquals(sections.palette.header, violet.palette.header)
        assertEquals(sections.palette.card, violet.palette.card)
        assertEquals(sections.palette.accentLight, violet.palette.accentLight)
        assertEquals(sections.palette.tabAzkar, violet.palette.tabSettings)
    }

    @Test
    fun `Изумрудная — фон, шапка и карточки сборников хадисов`() {
        val emerald = ThemeStyle.Emerald.theme
        assertEquals(sections.gradients.hadithBackground, emerald.gradients.azkarBackground)
        assertEquals(sections.gradients.nawawiCard, emerald.gradients.nawawiCard)
        assertEquals(sections.gradients.qudsiCard, emerald.gradients.qudsiCard)
        assertEquals(sections.gradients.ajurriCard, emerald.gradients.ajurriCard)
        assertEquals(sections.palette.hadithHeader, emerald.palette.header)
        assertEquals(sections.palette.tabHadith, emerald.palette.tabAzkar)
    }

    @Test
    fun `Янтарная и Графит — фоны «Махраджа» и настроек`() {
        assertEquals(sections.gradients.makharijBackground, ThemeStyle.Amber.theme.gradients.settingsBackground)
        assertEquals(sections.palette.tabMakharij, ThemeStyle.Amber.theme.palette.tabHadith)
        assertEquals(sections.gradients.settingsBackground, ThemeStyle.Graphite.theme.gradients.makharijBackground)
        assertEquals(sections.palette.tabSettings, ThemeStyle.Graphite.theme.palette.tabMakharij)
    }

    @Test
    fun `Ключи стилей в настройках — как в iOS`() {
        assertEquals(listOf("sections", "violet", "emerald", "amber", "graphite"), ThemeStyle.entries.map { it.key })
    }

    @Test
    fun `withColors сохраняет угол и стопы, требует цвет на каждый стоп`() {
        val card = sections.gradients.morningCard
        assertEquals(card, card.withColors(card.colors))
        kotlin.test.assertFailsWith<IllegalArgumentException> { card.withColors(listOf(Color.Black)) }
    }
}

/** Направление градиентов — «угол CSS», как в прототипе и iOS. */
class AngledGradientTest {
    private fun assertNear(expected: Offset, actual: Offset) {
        assertTrue(abs(expected.x - actual.x) < 1e-3 && abs(expected.y - actual.y) < 1e-3, "ожидалось $expected, получено $actual")
    }

    @Test
    fun `0° — снизу вверх, 90° — слева направо, 168° — почти сверху вниз`() {
        val size = Size(100f, 200f)
        val gradient = { angle: Float -> AngledGradient(angle, listOf(0f to Color.Black, 1f to Color.White)) }

        gradient(0f).endpoints(size).let { (from, to) ->
            assertNear(Offset(50f, 200f), from)
            assertNear(Offset(50f, 0f), to)
        }
        gradient(90f).endpoints(size).let { (from, to) ->
            assertNear(Offset(0f, 100f), from)
            assertNear(Offset(100f, 100f), to)
        }
        // dx = sin(168°)/2 ≈ 0,104, dy = −cos(168°)/2 ≈ 0,489.
        gradient(168f).endpoints(size).let { (from, to) ->
            assertNear(Offset(39.604f, 2.185f), from)
            assertNear(Offset(60.396f, 197.815f), to)
        }
    }
}
