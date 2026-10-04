package app.inabah.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    fun push(route: AzkarRoute) {
        _azkarStack += route
    }

    fun push(route: HadithRoute) {
        _hadithStack += route
    }

    fun push(route: SettingsRoute) {
        _settingsStack += route
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

    private fun currentStack(): MutableList<out Any> = when (selectedTab) {
        AppTab.Azkar -> _azkarStack
        AppTab.Hadith -> _hadithStack
        AppTab.Makharij -> mutableListOf()
        AppTab.Settings -> _settingsStack
    }

    private fun <T> MutableList<T>.replaceWith(items: List<T>) {
        clear()
        addAll(items)
    }
}
