package app.inabah.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.feature.azkar.AzkarHomeScreen
import app.inabah.android.feature.hadith.HadithHomeScreen
import app.inabah.android.feature.makharij.MakharijHomeScreen
import app.inabah.android.feature.settings.SettingsScreen

/** Непрозрачность фона панели вкладок (`card` × 0,9; размытие — этап 2). */
private const val TAB_BAR_ALPHA = 0.9f

/** Непрозрачность индикатора выбранной вкладки — цвет вкладки × 0,15. */
private const val TAB_INDICATOR_ALPHA = 0.15f

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
 * Фон раздела до краёв экрана и четыре вкладки. Системная «Назад»: снять экран вкладки,
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
            bottomBar = {
                RootTabBar(
                    selectedTab = selectedTab,
                    onSelect = onSelectTab,
                )
            },
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

@Composable
private fun RootTabBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    NavigationBar(
        modifier = modifier,
        containerColor = palette.card.copy(alpha = TAB_BAR_ALPHA),
    ) {
        AppTab.entries.forEach { tab ->
            val tint = tab.tint(theme)
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = tint,
                    selectedTextColor = tint,
                    indicatorColor = tint.copy(alpha = TAB_INDICATOR_ALPHA),
                    unselectedIconColor = palette.onAccentTertiary,
                    unselectedTextColor = palette.onAccentTertiary,
                ),
            )
        }
    }
}
