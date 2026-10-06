package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.components.SettingsScaffold
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.BuildConfig
import app.inabah.android.R
import app.inabah.android.app.SettingsRoute
import app.inabah.android.core.content.LicenseDocument
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PINNED = "pinned"
private const val ROWS = 40
private const val SWIPES = 8
private val TAB_BAR_HEIGHT = 80.dp

/** «О приложении»: ссылка внизу корня настроек, шапка с версией, лицензии открываются полным текстом. */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun enableChecks() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun footerLinkInSettingsRootOpensAbout() {
        var opened: SettingsRoute? = null
        compose.setContent {
            InabahTheme(theme = ThemeStyle.Sections.theme) {
                SettingsScreen(onOpen = { opened = it }, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithText(context.getString(R.string.settings_about_footer, BuildConfig.VERSION_NAME), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.about_title)).performClick()
        assertEquals(SettingsRoute.About, opened)
    }

    @Test
    fun groupsScrollUnderPinnedLinkButEndAboveIt() {
        compose.setContent {
            InabahTheme(theme = ThemeStyle.Sections.theme) {
                SettingsScaffold(
                    background = SolidColor(Color.Black),
                    contentPadding = PaddingValues(bottom = TAB_BAR_HEIGHT),
                    largeTitle = "Настройки",
                    bottomContent = { Box(Modifier.fillMaxWidth().height(Size.minTapTarget).testTag(PINNED)) },
                ) {
                    repeat(ROWS) { Text("Строка $it", modifier = Modifier.height(Size.minTapTarget)) }
                }
            }
        }
        // До прокрутки нижние строки лежат под закреплённым элементом (как в iOS — список уходит под него)…
        val pinnedTop = compose.onNodeWithTag(PINNED).getUnclippedBoundsInRoot().top
        repeat(SWIPES) { compose.onNodeWithText("Строка 0").onParent().performTouchInput { swipeUp() } }
        // …а в конце прокрутки последняя строка — над ним, а не под ним.
        val lastBottom = compose.onNodeWithText("Строка ${ROWS - 1}").getUnclippedBoundsInRoot().bottom
        assertTrue("последняя строка $lastBottom под закреплённым $pinnedTop", lastBottom <= pinnedTop)
    }

    @Test
    fun aboutShowsVersionAndOpensLicense() {
        var opened: LicenseDocument? = null
        compose.setContent {
            InabahTheme(theme = ThemeStyle.Sections.theme) {
                AboutScreen(onOpenLicense = { opened = it }, onBack = {}, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithText(context.getString(R.string.about_version, appVersion), substring = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_sources_azkar_text), substring = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_link_issues), substring = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(context.getString(R.string.about_license_material_title), substring = true)
            .performScrollTo()
            .performClick()
        assertEquals(LicenseDocument.MaterialSymbols, opened)
    }

    @Test
    fun licenseShowsTitleAndText() {
        compose.setContent {
            InabahTheme(theme = ThemeStyle.Sections.theme) {
                LicenseContent(LicenseDocument.Inter, text = "SIL OPEN FONT LICENSE", onBack = {}, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithText(context.getString(R.string.about_license_inter_title)).assertIsDisplayed()
        compose.onNodeWithText("SIL OPEN FONT LICENSE").assertIsDisplayed()
    }
}
