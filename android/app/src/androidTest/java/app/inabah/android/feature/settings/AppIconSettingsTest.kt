package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.ThemeStyle
import app.inabah.android.core.settings.AppIconOption
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** «Иконка приложения»: четыре плитки-варианта, выбор отмечает плитку; ошибка смены — на месте подписи. */
@RunWith(AndroidJUnit4::class)
class AppIconSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun enableChecks() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun choosingBeadsSelectsItsTile() {
        var selected by mutableStateOf(AppIconOption.Classic)
        compose.setContent {
            InabahTheme(theme = ThemeStyle.Sections.theme) {
                AppIconSettingsContent(
                    selected = selected,
                    failedToChange = false,
                    onSelect = { selected = it },
                    onBack = {},
                    contentPadding = PaddingValues(),
                )
            }
        }
        val classic = compose.onNodeWithText(context.getString(R.string.app_icon_classic))
        val beads = compose.onNodeWithText(context.getString(R.string.app_icon_beads))
        compose.onNodeWithText(context.getString(R.string.app_icon_niche)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.app_icon_dawn)).assertIsDisplayed()
        classic.assertIsSelected()
        beads.assertIsNotSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

        beads.performClick()

        assertEquals(AppIconOption.Beads, selected)
        beads.assertIsSelected()
        classic.assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.settings_app_icon_footer)).assertIsDisplayed()
    }

    @Test
    fun failedChangeShowsErrorInsteadOfFooter() {
        compose.setContent {
            InabahTheme(theme = ThemeStyle.Sections.theme) {
                AppIconSettingsContent(
                    selected = AppIconOption.Classic,
                    failedToChange = true,
                    onSelect = {},
                    onBack = {},
                    contentPadding = PaddingValues(),
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.settings_app_icon_error)).assertIsDisplayed()
    }
}
