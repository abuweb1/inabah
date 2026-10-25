package app.inabah.android.feature.azkar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.InabahTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** «Добавьте ещё азкаров»: число серии, хадис, плашки; «ОК» — своё действие, «В другой раз», мимо и «Назад» — закрыть. */
@RunWith(AndroidJUnit4::class)
class AzkarMotivationDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var ok = 0
    private var later = 0

    @Before
    fun setUp() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            var shown by remember { mutableStateOf(true) }
            InabahTheme {
                if (shown) {
                    AzkarMotivationDialog(
                        streak = 21,
                        counts = listOf(CompositionCount(AzkarSection.Morning, 16, 21)),
                        backdrop = null,
                        onOk = {
                            ok++
                            shown = false
                        },
                        onLater = {
                            later++
                            shown = false
                        },
                    )
                }
            }
        }
    }

    @Test
    fun showsStreakHadithAndCounts() {
        // Число и подпись для TalkBack — одна фраза (форма слова — по локали устройства, как у окна).
        val spoken = context.resources.getQuantityString(R.plurals.azkar_streak_days, 21, 21)
        compose.onNodeWithContentDescription(spoken).assertExists()
        compose.onNodeWithText("Вы держите серию!").assertExists()
        compose.onNodeWithText("«Самые любимые Аллахӏом дела - постоянные, даже если их немного»").assertExists()
        compose.onNodeWithText("Утренние 16 из 21").assertExists()
        compose.onAllNodesWithText("Вечерние", substring = true).assertCountEquals(0)
    }

    @Test
    fun okRunsItsAction() {
        compose.onNodeWithText("ОК").performClick()
        compose.runOnIdle { assertEquals(1 to 0, ok to later) }
    }

    @Test
    fun laterCloses() {
        compose.onNodeWithText("В другой раз").performClick()
        compose.runOnIdle { assertEquals(0 to 1, ok to later) }
    }

    @Test
    fun tapOutsideCloses() {
        compose.onRoot().performTouchInput { click(Offset(centerX, 4f)) }
        compose.runOnIdle { assertEquals(0 to 1, ok to later) }
    }

    @Test
    fun backCloses() {
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(0 to 1, ok to later) }
    }
}
