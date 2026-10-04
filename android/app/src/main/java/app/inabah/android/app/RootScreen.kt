package app.inabah.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.core.designsystem.InterfaceTextScale
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.bottomOnly
import app.inabah.android.feature.azkar.AzkarHomeScreen
import app.inabah.android.feature.azkar.AzkarListScreen
import app.inabah.android.feature.hadith.HadithHomeScreen
import app.inabah.android.feature.makharij.MakharijHomeScreen
import app.inabah.android.feature.settings.AzkarSettingsScreen
import app.inabah.android.feature.settings.SettingsScreen
import app.inabah.android.feature.settings.TextSizeSettingsScreen

/**
 * Корень (iOS `RootTabView`): вкладки, у «Азкаров» и «Настроек» — свой стек экранов (Navigation 3)
 * над стеком [AppRouter]. Стеки и вкладка переживают смерть процесса (снимок роутера в
 * сохранённом состоянии). Плеер над вкладками — этап 5; стек «Хадисов» — этап 4.
 */
@Composable
fun RootScreen(services: AppServices, modifier: Modifier = Modifier) {
    val router = services.router
    // Сохраняется при уходе в фон, восстанавливается до первого кадра (после смерти процесса).
    rememberSaveable(saver = Saver<Unit, String>(save = { router.snapshot() }, restore = { router.restore(it) })) {}
    RootContent(
        selectedTab = router.selectedTab,
        canGoBack = router.canGoBack,
        onSelectTab = router::select,
        onBack = { router.goBack() },
        modifier = modifier,
    ) { tab, contentPadding ->
        TabContent(tab, services, contentPadding)
    }
}

/**
 * Фон раздела до краёв экрана и плавающая панель вкладок, как в iOS: содержимое прокручивается
 * под панелью (нижний отступ содержимого — высота панели). Системная «Назад»: экран вкладки
 * снимает её `NavDisplay`, с корня вкладки — на «Азкары», с корня «Азкаров» — выход (docs/android/02).
 * Вкладки переключаются без анимации; состояние экранов невыбранной вкладки сохраняется.
 */
@Composable
fun RootContent(
    selectedTab: AppTab,
    canGoBack: Boolean,
    onSelectTab: (AppTab) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (tab: AppTab, contentPadding: PaddingValues) -> Unit,
) {
    BackHandler(enabled = canGoBack, onBack = onBack)

    val theme = LocalInabahTheme.current
    val tabStates = rememberSaveableStateHolder()
    // Экран вкладки (вместе с фоном) — источник размытия под панелью вкладок, как стекло iOS.
    val backdrop = rememberHazeState()
    Box(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { FloatingTabBar(selectedTab = selectedTab, onSelect = onSelectTab, backdrop = backdrop) },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(backdrop)
                    .background(selectedTab.background(theme)),
            ) {
                tabStates.SaveableStateProvider(selectedTab.name) {
                    content(selectedTab, innerPadding)
                }
            }
        }
    }
}

/** Корень стека вкладки — её главный экран; дальше — маршруты из [AppRouter]. */
private data object TabRoot

@Composable
private fun TabContent(tab: AppTab, services: AppServices, contentPadding: PaddingValues) {
    // Шаг интерфейса — на экраны вкладки (панель вкладок — вне его); навбар, кнопки и всплывающие
    // окна внутри экранов закреплены сами (docs/android/10-typography.md, 10.1).
    val interfaceSize by services.textSizeSettings.interfaceSize.collectAsStateWithLifecycle()
    InterfaceTextScale(interfaceSize.fontScale) {
        TabScreens(tab, services, contentPadding)
    }
}

@Composable
private fun TabScreens(tab: AppTab, services: AppServices, contentPadding: PaddingValues) {
    val router = services.router
    when (tab) {
        AppTab.Azkar -> TabNavDisplay(router.azkarStack, onBack = { router.pop(AppTab.Azkar) }) {
            entry<TabRoot> {
                AzkarHomeScreen(services.azkarStore, onOpenSection = { router.push(AzkarRoute.SectionList(it)) }, contentPadding)
            }
            entry<AzkarRoute.SectionList> { route ->
                AzkarListScreen(
                    section = route.section,
                    store = services.azkarStore,
                    readingSettings = services.readingSettings,
                    onBack = { router.pop(AppTab.Azkar) },
                    onGoHome = { router.popToRoot(AppTab.Azkar) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
        }
        AppTab.Hadith -> HadithHomeScreen(contentPadding)
        AppTab.Makharij -> MakharijHomeScreen(contentPadding)
        AppTab.Settings -> TabNavDisplay(router.settingsStack, onBack = { router.pop(AppTab.Settings) }) {
            entry<TabRoot> {
                SettingsScreen(onOpen = router::push, contentPadding = contentPadding.bottomOnly())
            }
            entry<SettingsRoute.Azkar> {
                AzkarSettingsScreen(
                    store = services.azkarStore,
                    resetSettings = services.azkarResetSettings,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.TextSize> {
                TextSizeSettingsScreen(
                    settings = services.textSizeSettings,
                    azkarStore = services.azkarStore,
                    hadithStore = services.hadithStore,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
        }
    }
}

/** Стек вкладки: корень [TabRoot] и маршруты [stack]; переходы — как в iOS (docs/android/05, «Плавность»). */
@Composable
private fun TabNavDisplay(
    stack: List<Any>,
    onBack: () -> Unit,
    entries: androidx.navigation3.runtime.EntryProviderScope<Any>.() -> Unit,
) {
    NavDisplay(
        backStack = listOf<Any>(TabRoot) + stack,
        onBack = onBack,
        transitionSpec = { push() },
        popTransitionSpec = { pop() },
        predictivePopTransitionSpec = { pop() },
        entryProvider = entryProvider(builder = entries),
    )
}

private const val NAVIGATION_MILLIS = 350

/** Уходящий под новый экран сдвигается на 30 % ширины и притемняется. */
private const val UNDERLAP_FRACTION = 0.3f
private const val UNDERLAP_ALPHA = 0.7f

private fun <T> navigationSpec() = tween<T>(NAVIGATION_MILLIS, easing = FastOutSlowInEasing)

private fun underlapOffset(width: Int) = -(width * UNDERLAP_FRACTION).toInt()

/** Вперёд: новый экран справа на всю ширину, старый — влево на 30 % и тускнеет. */
private fun AnimatedContentTransitionScope<Scene<Any>>.push(): ContentTransform =
    slideInHorizontally(navigationSpec()) { it } togetherWith
        (slideOutHorizontally(navigationSpec(), ::underlapOffset) + fadeOut(navigationSpec(), UNDERLAP_ALPHA))

/** Назад — зеркально; уходящий экран остаётся сверху. */
private fun AnimatedContentTransitionScope<Scene<Any>>.pop(): ContentTransform =
    ((slideInHorizontally(navigationSpec(), ::underlapOffset) + fadeIn(navigationSpec(), UNDERLAP_ALPHA)) togetherWith
        slideOutHorizontally(navigationSpec()) { it })
        .apply { targetContentZIndex = -1f }
