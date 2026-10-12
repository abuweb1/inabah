package app.inabah.android.feature.whatsnew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.ThemeStyle
import app.inabah.android.core.releases.AppVersion
import app.inabah.android.core.releases.ReleaseNotes
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Окно «Что нового»: заголовок с версией, пункты; «ОК» и касание мимо закрывают, касание по карточке — нет. */
@RunWith(AndroidJUnit4::class)
class WhatsNewOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notes = ReleaseNotes(AppVersion.parse("1.1.1")!!, LocalDate.of(2026, 10, 12), listOf("Напоминания", "Избранные хадисы"))
    private var dismissed = 0

    @Before
    fun setUp() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            var shown by remember { mutableStateOf(true) }
            InabahTheme(theme = ThemeStyle.Sections.theme) {
                if (shown) {
                    WhatsNewOverlay(notes, tint = InabahTheme.palette.tabAzkar, backdrop = null, onDismiss = {
                        dismissed++
                        shown = false
                    })
                }
            }
        }
    }

    private val title get() = context.getString(R.string.whats_new_title, "1.1.1")

    @Test
    fun showsVersionAndItemsAndOkDismisses() {
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Напоминания").assertIsDisplayed()
        compose.onNodeWithText("Избранные хадисы").assertIsDisplayed()

        compose.onNodeWithText(context.getString(R.string.whats_new_ok)).performClick()

        assertEquals(1, dismissed)
        compose.onAllNodesWithText(title).assertCountEquals(0)
    }

    @Test
    fun tapOnCardKeepsItTapOutsideDismisses() {
        compose.onNodeWithText("Избранные хадисы").performClick()
        compose.waitForIdle()
        assertEquals("касание по карточке окно не закрывает", 0, dismissed)

        // Верхний левый угол экрана — затемнение, мимо карточки.
        compose.onRoot().performTouchInput { click(Offset(SIDE_TAP, SIDE_TAP)) }

        assertEquals(1, dismissed)
    }

    private companion object {
        const val SIDE_TAP = 10f
    }
}
