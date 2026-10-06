@file:OptIn(ExperimentalCoroutinesApi::class)

package app.inabah.android.core.settings

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppIconSettingsTest {
    private class FakeSwitcher(var enabled: AppIconOption = AppIconOption.Classic) : AppIconSwitcher {
        val selected = mutableListOf<AppIconOption>()
        var failure: RuntimeException? = null
        var readFailure: RuntimeException? = null

        override fun current(): AppIconOption {
            readFailure?.let { throw it }
            return enabled
        }

        override fun select(option: AppIconOption) {
            failure?.let { throw it }
            selected += option
            enabled = option
        }
    }

    // Отдельный экземпляр диспетчера на общем планировщике: withContext действительно переключается,
    // и между выбором и вызовом платформы видно промежуточное состояние.
    private val unreadable = mutableListOf<RuntimeException>()

    private fun TestScope.settings(switcher: FakeSwitcher) =
        AppIconSettings(switcher, StandardTestDispatcher(testScheduler), onUnreadable = { unreadable += it })

    @Test
    fun refreshStartedBeforeSelectDoesNotOverwriteChoice() = runTest {
        val switcher = FakeSwitcher()
        val settings = settings(switcher)
        // Чтение при запуске ещё в пути (вернёт «Классическую»), а пользователь уже выбрал «Нишу».
        launch { settings.refresh() }
        launch { settings.select(AppIconOption.Niche) }
        advanceUntilIdle()
        assertEquals(AppIconOption.Niche, settings.current.value)
    }

    @Test
    fun unreadablePlatformKeepsClassicAndReportsError() = runTest {
        val switcher = FakeSwitcher(enabled = AppIconOption.Dawn).apply { readFailure = IllegalArgumentException("нет алиаса") }
        val settings = settings(switcher)
        settings.refresh()
        assertEquals(AppIconOption.Classic, settings.current.value)
        assertEquals(1, unreadable.size)
    }

    @Test
    fun startsWithClassicUntilRefreshed() = runTest {
        val settings = settings(FakeSwitcher(enabled = AppIconOption.Dawn))
        assertEquals(AppIconOption.Classic, settings.current.value)
    }

    @Test
    fun refreshReadsEnabledIconFromPlatform() = runTest {
        val settings = settings(FakeSwitcher(enabled = AppIconOption.Beads))
        settings.refresh()
        assertEquals(AppIconOption.Beads, settings.current.value)
    }

    @Test
    fun selectMarksChoiceAtOnceAndSwitchesPlatform() = runTest {
        val switcher = FakeSwitcher()
        val settings = settings(switcher)
        launch { settings.select(AppIconOption.Niche) }
        runCurrent()
        assertEquals(AppIconOption.Niche, settings.current.value)
        advanceUntilIdle()
        assertEquals(listOf(AppIconOption.Niche), switcher.selected)
        assertFalse(settings.failedToChange.value)
    }

    @Test
    fun selectingCurrentIconDoesNothing() = runTest {
        val switcher = FakeSwitcher()
        val settings = settings(switcher)
        settings.select(AppIconOption.Classic)
        assertEquals(emptyList(), switcher.selected)
    }

    @Test
    fun secondSelectWhileChangingIsIgnored() = runTest {
        val switcher = FakeSwitcher()
        val settings = settings(switcher)
        // Второй выбор выполняется, пока вызов платформы для первого ещё в очереди диспетчера.
        launch { settings.select(AppIconOption.Niche) }
        launch { settings.select(AppIconOption.Dawn) }
        advanceUntilIdle()
        assertEquals(listOf(AppIconOption.Niche), switcher.selected)
        assertEquals(AppIconOption.Niche, settings.current.value)
    }

    @Test
    fun failureRollsBackChoiceAndReportsError() = runTest {
        val switcher = FakeSwitcher().apply { failure = IllegalArgumentException("компонент не найден") }
        val settings = settings(switcher)
        settings.select(AppIconOption.Beads)
        assertEquals(AppIconOption.Classic, settings.current.value)
        assertTrue(settings.failedToChange.value)
    }

    @Test
    fun nextSuccessfulSelectClearsError() = runTest {
        val switcher = FakeSwitcher().apply { failure = IllegalArgumentException() }
        val settings = settings(switcher)
        settings.select(AppIconOption.Beads)
        switcher.failure = null
        settings.select(AppIconOption.Beads)
        assertFalse(settings.failedToChange.value)
        assertEquals(AppIconOption.Beads, settings.current.value)
    }
}
