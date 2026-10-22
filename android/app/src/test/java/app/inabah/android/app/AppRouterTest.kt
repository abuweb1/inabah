package app.inabah.android.app

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** «Навигация». */
class AppRouterTest {
    @Test
    fun `Повторный выбор активной вкладки возвращает к корню`() {
        val router = AppRouter()
        router.open(AzkarRoute.SectionList(AzkarSection.Morning))

        router.select(AppTab.Azkar)

        assertTrue(router.azkarStack.isEmpty())
    }

    @Test
    fun `Выбор другой вкладки не трогает путь`() {
        val router = AppRouter()
        router.open(AzkarRoute.SectionList(AzkarSection.Evening))

        router.select(AppTab.Hadith)

        assertEquals(AppTab.Hadith, router.selectedTab)
        assertEquals(listOf(AzkarRoute.SectionList(AzkarSection.Evening)), router.azkarStack.toList())
    }

    @Test
    fun `openRoot — вкладка азкаров и её стек до корня, другие стеки целы`() {
        val router = AppRouter()
        router.open(AzkarRoute.SectionList(AzkarSection.Evening))
        router.open(HadithRoute.Detail(HadithId(HadithCollection.Nawawi, 3)))

        router.openRoot(AppTab.Azkar)

        assertEquals(AppTab.Azkar, router.selectedTab)
        assertTrue(router.azkarStack.isEmpty())
        assertEquals(2, router.hadithStack.size)
    }

    @Test
    fun `open переключает на вкладку азкаров и открывает экран`() {
        val router = AppRouter()
        router.select(AppTab.Settings)

        router.open(AzkarRoute.SectionList(AzkarSection.Morning))

        assertEquals(AppTab.Azkar, router.selectedTab)
        assertEquals(listOf(AzkarRoute.SectionList(AzkarSection.Morning)), router.azkarStack.toList())
    }

    @Test
    fun `Хадис открывается поверх списка своего сборника`() {
        val router = AppRouter()
        val id = HadithId(HadithCollection.Qudsi, 5)

        router.open(HadithRoute.Detail(id))

        assertEquals(AppTab.Hadith, router.selectedTab)
        assertEquals(listOf(HadithRoute.CollectionList(HadithCollection.Qudsi), HadithRoute.Detail(id)), router.hadithStack.toList())
    }

    @Test
    fun `«О сборнике» открывается поверх списка своего сборника и переживает снимок`() {
        val router = AppRouter()
        val about = HadithRoute.About(HadithCollection.Ajurri)

        router.open(about)

        assertEquals(AppTab.Hadith, router.selectedTab)
        assertEquals(listOf(HadithRoute.CollectionList(HadithCollection.Ajurri), about), router.hadithStack.toList())
        val restored = AppRouter()
        restored.restore(router.snapshot())
        assertEquals(router.hadithStack.toList(), restored.hadithStack.toList())
    }

    @Test
    fun `«Избранное» — отдельный экран вкладки хадисов, хадис из него — поверх, снимок восстанавливает`() {
        val router = AppRouter()
        val id = HadithId(HadithCollection.Qudsi, 2)

        router.open(HadithRoute.Favorites)
        router.push(HadithRoute.Detail(id))

        assertEquals(AppTab.Hadith, router.selectedTab)
        assertEquals(listOf(HadithRoute.Favorites, HadithRoute.Detail(id)), router.hadithStack.toList())
        val restored = AppRouter()
        restored.restore(router.snapshot())
        assertEquals(router.hadithStack.toList(), restored.hadithStack.toList())
    }

    @Test
    fun `Повторный выбор вкладки «Настройки» возвращает к списку разделов`() {
        val paths = listOf(
            listOf(SettingsRoute.Hadith),
            listOf(SettingsRoute.Hadith, SettingsRoute.HadithOrder),
            listOf(SettingsRoute.Makharij),
            listOf(SettingsRoute.About, SettingsRoute.Licenses),
            listOf(SettingsRoute.AppIcon),
            listOf(SettingsRoute.Palette),
        )
        for (path in paths) {
            val router = AppRouter()
            router.select(AppTab.Settings)
            path.forEach(router::push)

            router.select(AppTab.Settings)

            assertTrue(router.settingsStack.isEmpty(), "путь $path")
        }
    }

    @Test
    fun `Вкладка «Махрадж» — выбор и повторный выбор не трогают пути других вкладок`() {
        val router = AppRouter()
        router.open(AzkarRoute.SectionList(AzkarSection.Evening))
        router.push(SettingsRoute.AppIcon)

        router.select(AppTab.Makharij)
        router.select(AppTab.Makharij)

        assertEquals(AppTab.Makharij, router.selectedTab)
        assertEquals(listOf(AzkarRoute.SectionList(AzkarSection.Evening)), router.azkarStack.toList())
        assertEquals(listOf(SettingsRoute.AppIcon), router.settingsStack.toList())
    }

    @Test
    fun `Повторный выбор вкладки «Хадисы» возвращает к главной хадисов`() {
        val router = AppRouter()
        router.open(HadithRoute.CollectionList(HadithCollection.Nawawi))

        router.select(AppTab.Hadith)

        assertTrue(router.hadithStack.isEmpty())
    }

    @Test
    fun `Системная «Назад» — снять экран, с корня вкладки на «Азкары», с корня «Азкаров» выйти`() {
        val router = AppRouter()
        router.open(HadithRoute.Detail(HadithId(HadithCollection.Nawawi, 1)))

        assertTrue(router.goBack())
        assertEquals(listOf(HadithRoute.CollectionList(HadithCollection.Nawawi)), router.hadithStack.toList())
        assertTrue(router.goBack())
        assertTrue(router.hadithStack.isEmpty())
        assertTrue(router.canGoBack)
        assertTrue(router.goBack())
        assertEquals(AppTab.Azkar, router.selectedTab)
        assertFalse(router.canGoBack)
        assertFalse(router.goBack())
    }

    @Test
    fun `pop снимает верхний экран своей вкладки, с корня ничего не делает`() {
        val router = AppRouter()
        router.push(SettingsRoute.Azkar)
        router.open(AzkarRoute.SectionList(AzkarSection.Morning))

        router.pop(AppTab.Settings)
        router.pop(AppTab.Settings)

        assertTrue(router.settingsStack.isEmpty())
        assertEquals(listOf(AzkarRoute.SectionList(AzkarSection.Morning)), router.azkarStack.toList())
        assertEquals(AppTab.Azkar, router.selectedTab)
    }

    @Test
    fun `Двойное открытие того же экрана кладёт его один раз`() {
        val router = AppRouter()

        router.push(AzkarRoute.SectionList(AzkarSection.Morning))
        router.push(AzkarRoute.SectionList(AzkarSection.Morning))
        router.push(SettingsRoute.Azkar)
        router.push(SettingsRoute.Azkar)

        assertEquals(listOf(AzkarRoute.SectionList(AzkarSection.Morning)), router.azkarStack.toList())
        assertEquals(listOf<SettingsRoute>(SettingsRoute.Azkar), router.settingsStack.toList())
    }

    @Test
    fun `Снимок восстанавливает вкладку и стеки всех вкладок`() {
        val source = AppRouter()
        source.open(AzkarRoute.SectionList(AzkarSection.Evening))
        source.open(HadithRoute.Detail(HadithId(HadithCollection.Qudsi, 7)))
        source.select(AppTab.Settings)
        source.push(SettingsRoute.Azkar)

        val restored = AppRouter()
        restored.restore(source.snapshot())

        assertEquals(AppTab.Settings, restored.selectedTab)
        assertEquals(source.azkarStack.toList(), restored.azkarStack.toList())
        assertEquals(source.hadithStack.toList(), restored.hadithStack.toList())
        assertEquals(listOf<SettingsRoute>(SettingsRoute.Azkar), restored.settingsStack.toList())
    }

    @Test
    fun `Повреждённый снимок оставляет корни`() {
        val router = AppRouter()

        router.restore("{\"selectedTab\":\"Settings\",\"azkar\":[{\"type\":\"unknown\"}]}")
        router.restore("не JSON")

        assertEquals(AppTab.Azkar, router.selectedTab)
        assertTrue(router.azkarStack.isEmpty())
        assertTrue(router.settingsStack.isEmpty())
    }

    @Test
    fun `«Учимся читать» — урок поверх списка шагов, проверка поверх своего урока, «Следующий шаг» заменяет их`() {
        val router = AppRouter()

        router.open(MakharijRoute.Quiz(4))
        assertEquals(AppTab.Makharij, router.selectedTab)
        assertEquals(
            listOf(MakharijRoute.Steps, MakharijRoute.Lesson(4), MakharijRoute.Quiz(4)),
            router.makharijStack.toList(),
        )

        router.open(MakharijRoute.Lesson(5))
        assertEquals(listOf(MakharijRoute.Steps, MakharijRoute.Lesson(5)), router.makharijStack.toList())

        router.select(AppTab.Makharij)
        assertTrue(router.makharijStack.isEmpty(), "повторный выбор вкладки — к главной")
    }

    @Test
    fun `Снимок восстанавливает стек «Махраджа», снимок без него читается`() {
        val source = AppRouter()
        source.open(MakharijRoute.Lesson(1))

        val restored = AppRouter()
        restored.restore(source.snapshot())
        assertEquals(listOf(MakharijRoute.Steps, MakharijRoute.Lesson(1)), restored.makharijStack.toList())
        assertTrue(restored.goBack())
        assertEquals(listOf<MakharijRoute>(MakharijRoute.Steps), restored.makharijStack.toList())

        val old = AppRouter()
        old.restore("{\"selectedTab\":\"Makharij\",\"azkar\":[],\"hadith\":[],\"settings\":[]}")
        assertEquals(AppTab.Makharij, old.selectedTab)
        assertTrue(old.makharijStack.isEmpty())
    }

    @Test
    fun `Экраны урока - тема, справка, буква, зикр, слова, алфавит - поверх урока и переживают смерть процесса`() {
        val routes = listOf(
            12 to MakharijRoute.Extra(12, topic = 2),
            10 to MakharijRoute.Extra(10),
            3 to MakharijRoute.LetterForms(3, "ب"),
            13 to MakharijRoute.Zikr(13, 3),
            5 to MakharijRoute.Words(5),
            4 to MakharijRoute.Alphabet(4),
        )
        for ((step, route) in routes) {
            val source = AppRouter()
            source.open(route)
            val stack = listOf(MakharijRoute.Steps, MakharijRoute.Lesson(step), route)
            assertEquals(stack, source.makharijStack.toList(), "$route")

            val restored = AppRouter()
            restored.restore(source.snapshot())
            assertEquals(stack, restored.makharijStack.toList(), "$route после восстановления")
        }
    }
}
