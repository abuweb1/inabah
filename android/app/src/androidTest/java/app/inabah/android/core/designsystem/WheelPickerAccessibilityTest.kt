package app.inabah.android.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inabah.android.core.designsystem.components.TimeWheelPicker
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Барабан времени для TalkBack (хвост этапа 3): один регулируемый элемент, «Больше»/«Меньше» и жест. */
@RunWith(AndroidJUnit4::class)
class WheelPickerAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private var hour by mutableIntStateOf(17)
    private var minute by mutableIntStateOf(0)

    @Before
    fun enableChecks() = compose.enableAccessibilityChecks()

    private fun setPicker() = compose.setContent {
        InabahTheme {
            TimeWheelPicker(hour, minute, onChange = { h, m -> hour = h; minute = m }, hourDescription = "Часы", minuteDescription = "Минуты")
        }
    }

    /** Как TalkBack: действие узла — на UI-потоке, затем Compose доходит до простоя. */
    private fun SemanticsNodeInteraction.performCustomAction(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
    }

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun hourWheelIsOneAdjustableNodeWithCurrentValue() {
        setPicker()
        compose.onNodeWithContentDescription("Часы").assert(stateIs("17"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        // Строки списка скрыты: узел с «18» из барабана TalkBack не видит.
        compose.onNodeWithContentDescription("Минуты").assert(stateIs("00"))
    }

    @Test
    fun increaseActionMovesOneHourAndReportsIt() {
        setPicker()
        compose.onNodeWithContentDescription("Часы").performCustomAction("Больше")
        compose.waitUntil(timeoutMillis = 5_000) { hour == 18 }
        compose.onNodeWithContentDescription("Часы").assert(stateIs("18"))
    }

    // Регрессия (ревью этапа 7): второе действие, пока барабан ещё едет, считалось от недоехавшей середины.
    @Test
    fun twoQuickIncreasesMoveTwoHours() {
        setPicker()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Часы").performCustomAction("Больше")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Часы").performCustomAction("Больше")
        compose.mainClock.autoAdvance = true
        compose.waitUntil(timeoutMillis = 5_000) { hour == 19 }
        compose.onNodeWithContentDescription("Часы").assert(stateIs("19"))
    }

    @Test
    fun decreaseWrapsAroundMidnightForMinutes() {
        setPicker()
        compose.onNodeWithContentDescription("Минуты").performCustomAction("Меньше")
        compose.waitUntil(timeoutMillis = 5_000) { minute == 59 }
        // Часы не тронуты.
        compose.runOnIdle { check(hour == 17) }
    }

    @Test
    fun setProgressJumpsToRequestedHour() {
        setPicker()
        compose.onNodeWithContentDescription("Часы").performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.waitUntil(timeoutMillis = 5_000) { hour == 5 }
    }
}

