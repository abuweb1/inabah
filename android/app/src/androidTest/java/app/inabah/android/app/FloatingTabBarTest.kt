package app.inabah.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inabah.android.core.designsystem.InabahTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Панель вкладок: полная на главных, компактная (×0,7, без подписей) на внутренних - вкладки те же для касания и TalkBack. */
@RunWith(AndroidJUnit4::class)
class FloatingTabBarTest {
    @get:Rule
    val compose = createComposeRule()

    private var selected by mutableStateOf(AppTab.Azkar)

    @Before
    fun enableChecks() = compose.enableAccessibilityChecks()

    private fun setBar(compactness: Float) = compose.setContent {
        InabahTheme {
            FloatingTabBar(selectedTab = selected, onSelect = { selected = it }, compactness = { compactness })
        }
    }

    @Test
    fun fullBarSelectsTab() {
        setBar(compactness = 0f)
        compose.onNodeWithContentDescription("Хадисы").performClick()
        compose.onNodeWithContentDescription("Хадисы").assertIsSelected()
    }

    @Test
    fun compactBarKeepsLabelsForTalkBackAndSelectsTab() {
        setBar(compactness = 1f)
        // Подпись погашена только на экране - TalkBack читает «Махрадж», зона касания - во всю высоту панели.
        compose.onNodeWithContentDescription("Махрадж").performClick()
        compose.onNodeWithContentDescription("Махрадж").assertIsSelected()
    }
}
