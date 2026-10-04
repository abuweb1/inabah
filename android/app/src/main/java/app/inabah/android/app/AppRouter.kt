package app.inabah.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Навигация приложения (iOS `AppRouter`): выбранная вкладка и стек экранов каждой вкладки.
 * Состояние — снимки Compose: экраны (Navigation 3, этап 3) перерисовываются по последнему элементу стека.
 * «Махрадж» пока без стека — одна главная. Вызывать с главного потока.
 */
class AppRouter {
    var selectedTab by mutableStateOf(AppTab.Azkar)
        private set

    private val _azkarStack = mutableStateListOf<AzkarRoute>()
    private val _hadithStack = mutableStateListOf<HadithRoute>()
    private val _settingsStack = mutableStateListOf<SettingsRoute>()

    val azkarStack: List<AzkarRoute> get() = _azkarStack
    val hadithStack: List<HadithRoute> get() = _hadithStack
    val settingsStack: List<SettingsRoute> get() = _settingsStack

    /** Выбор вкладки в панели: повторный выбор активной возвращает к её корню, другой — только переключает. */
    fun select(tab: AppTab) {
        if (tab == selectedTab) popToRoot(tab) else selectedTab = tab
    }

    /** Стек меняется, только если есть что снимать: запись того же значения тоже будит наблюдателей. */
    fun popToRoot(tab: AppTab) {
        val stack = when (tab) {
            AppTab.Azkar -> _azkarStack
            AppTab.Hadith -> _hadithStack
            AppTab.Makharij -> return
            AppTab.Settings -> _settingsStack
        }
        if (stack.isNotEmpty()) stack.clear()
    }

    /** Открыть экран азкаров из любого места: вкладка «Азкары», стек — только этот экран. */
    fun open(route: AzkarRoute) {
        selectedTab = AppTab.Azkar
        _azkarStack.replaceWith(listOf(route))
    }

    /** Хадис открывается поверх списка своего сборника — «назад» ведёт в этот список. */
    fun open(route: HadithRoute) {
        selectedTab = AppTab.Hadith
        val stack = when (route) {
            is HadithRoute.CollectionList -> listOf(route)
            is HadithRoute.Detail -> listOf(HadithRoute.CollectionList(route.id.collection), route)
        }
        _hadithStack.replaceWith(stack)
    }

    // Тот же экран поверх себя не кладётся: двойное касание во время перехода (уходящий экран ещё
    // принимает касания) иначе дало бы две записи с одним ключом.

    fun push(route: AzkarRoute) = _azkarStack.pushDistinct(route)

    fun push(route: HadithRoute) = _hadithStack.pushDistinct(route)

    fun push(route: SettingsRoute) = _settingsStack.pushDistinct(route)

    /** «Назад» внутри стека вкладки [tab] (жест или кнопка навбара); корень не снимается. */
    fun pop(tab: AppTab) {
        val stack = when (tab) {
            AppTab.Azkar -> _azkarStack
            AppTab.Hadith -> _hadithStack
            AppTab.Makharij -> return
            AppTab.Settings -> _settingsStack
        }
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Системной «Назад» есть что делать: снять экран или вернуться на «Азкары». */
    val canGoBack: Boolean get() = currentStack().isNotEmpty() || selectedTab != AppTab.Azkar

    /**
     * Системная «Назад»: снять верхний экран вкладки; с корня не «Азкаров» — на «Азкары».
     * С корня «Азкаров» — `false`: выйти из приложения.
     */
    fun goBack(): Boolean {
        val stack = currentStack()
        return when {
            stack.isNotEmpty() -> {
                stack.removeAt(stack.lastIndex)
                true
            }
            selectedTab != AppTab.Azkar -> {
                selectedTab = AppTab.Azkar
                true
            }
            else -> false
        }
    }

    /** Снимок вкладки и стеков — для восстановления после смерти процесса (`rememberSaveable`). */
    fun snapshot(): String = json.encodeToString(
        RouterState.serializer(),
        RouterState(selectedTab, _azkarStack.toList(), _hadithStack.toList(), _settingsStack.toList()),
    )

    /** Восстановить снимок [snapshot]; повреждённый или устаревший формат — остаться в корнях. */
    fun restore(snapshot: String) {
        val state = try {
            json.decodeFromString(RouterState.serializer(), snapshot)
        } catch (_: IllegalArgumentException) {
            // SerializationException — подкласс IllegalArgumentException.
            return
        }
        selectedTab = state.selectedTab
        _azkarStack.replaceWith(state.azkar)
        _hadithStack.replaceWith(state.hadith)
        _settingsStack.replaceWith(state.settings)
    }

    @Serializable
    private data class RouterState(
        val selectedTab: AppTab,
        val azkar: List<AzkarRoute>,
        val hadith: List<HadithRoute>,
        val settings: List<SettingsRoute>,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    private fun currentStack(): MutableList<out Any> = when (selectedTab) {
        AppTab.Azkar -> _azkarStack
        AppTab.Hadith -> _hadithStack
        AppTab.Makharij -> mutableListOf()
        AppTab.Settings -> _settingsStack
    }

    private fun <T> MutableList<T>.pushDistinct(route: T) {
        if (lastOrNull() != route) add(route)
    }

    private fun <T> MutableList<T>.replaceWith(items: List<T>) {
        clear()
        addAll(items)
    }
}
