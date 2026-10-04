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
    fun `Повторный выбор вкладки «Настройки» возвращает к списку разделов`() {
        val paths = listOf(
            listOf(SettingsRoute.Hadith),
            listOf(SettingsRoute.Hadith, SettingsRoute.HadithOrder),
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
}
