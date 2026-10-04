package app.inabah.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.feature.azkar.AzkarHomeScreen
import app.inabah.android.feature.hadith.HadithHomeScreen
import app.inabah.android.feature.makharij.MakharijHomeScreen
import app.inabah.android.feature.settings.SettingsScreen

/**
 * Корень (iOS `RootTabView`): вкладки и системная «Назад» по [AppRouter].
 * Экраны стеков вкладок (Navigation 3) — этап 3; плеер над вкладками — этап 5.
 */
@Composable
fun RootScreen(router: AppRouter, modifier: Modifier = Modifier) {
    RootContent(
        selectedTab = router.selectedTab,
        canGoBack = router.canGoBack,
        onSelectTab = router::select,
        onBack = { router.goBack() },
        modifier = modifier,
    )
}

/**
 * Фон раздела до краёв экрана и плавающая панель вкладок, как в iOS: содержимое прокручивается
 * под панелью (нижний отступ содержимого — высота панели). Системная «Назад»: снять экран вкладки,
 * с корня вкладки — на «Азкары», с корня «Азкаров» — выход (docs/android/02).
 */
@Composable
fun RootContent(
    selectedTab: AppTab,
    canGoBack: Boolean,
    onSelectTab: (AppTab) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = canGoBack, onBack = onBack)

    val theme = LocalInabahTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(selectedTab.background(theme)),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { FloatingTabBar(selectedTab = selectedTab, onSelect = onSelectTab) },
        ) { innerPadding ->
            TabContent(tab = selectedTab, contentPadding = innerPadding)
        }
    }
}

@Composable
private fun TabContent(tab: AppTab, contentPadding: PaddingValues) {
    when (tab) {
        AppTab.Azkar -> AzkarHomeScreen(contentPadding)
        AppTab.Hadith -> HadithHomeScreen(contentPadding)
        AppTab.Makharij -> MakharijHomeScreen(contentPadding)
        AppTab.Settings -> SettingsScreen(contentPadding)
    }
}
