package app.inabah.android.feature.azkar

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.AzkarDay
import app.inabah.android.core.settings.AzkarDayRecord
import java.time.LocalDate
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** «Календарь азкаров» и капсула серии на главной (решения пользователя 2026-10-11). */
@RunWith(AndroidJUnit4::class)
class AzkarCalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 11)
    private val full = AzkarDay(AzkarDayRecord(16, 16), AzkarDayRecord(16, 16))

    @Before
    fun enableChecks() {
        compose.enableAccessibilityChecks()
    }

    private fun showCalendar(days: Map<LocalDate, AzkarDay>) {
        compose.setContent {
            // Приложение подставляет русский интерфейс (uiLocaleOverride в MainActivity); тестовая активность — нет.
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag("ru")) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                InabahTheme { AzkarCalendarContent(days, today, onBack = {}, contentPadding = PaddingValues()) }
            }
        }
    }

    @Test
    fun summaryCountsStreakAndDayShowsDetails() {
        showCalendar(
            mapOf(
                today.minusDays(2) to full,
                today.minusDays(1) to full,
                today to AzkarDay(morning = AzkarDayRecord(16, 16), evening = AzkarDayRecord(3, 16)),
            ),
        )

        // Сегодня не дочитаны вечерние — серия до вчера не рвётся.
        compose.onNode(hasText("подряд") and hasText("2")).assertIsDisplayed()
        compose.onNode(hasText("всего дней") and hasText("2")).assertIsDisplayed()

        compose.onNodeWithContentDescription("11 октября: прочитаны утренние").performClick()
        compose.onNodeWithText("16 из 16").assertIsDisplayed()
        compose.onNodeWithText("3 из 16").assertIsDisplayed()
    }

    @Test
    fun monthsPageBackToHistoryButNotIntoFuture() {
        showCalendar(mapOf(LocalDate.of(2026, 9, 20) to full))

        compose.onNodeWithText("Октябрь 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Следующий месяц").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Предыдущий месяц").assertIsEnabled().performClick()
        compose.onNodeWithText("Сентябрь 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Предыдущий месяц").assertIsNotEnabled()
    }

    @Test
    fun emptyHistoryShowsZerosAndHint() {
        showCalendar(emptyMap())

        compose.onNode(hasText("лучшая серия") and hasText("0")).assertIsDisplayed()
        compose.onNodeWithText("начнётся серия", substring = true).assertIsDisplayed()
    }

    @Test
    fun capsuleShowsStreakOrInvitationAndOpensCalendar() {
        var current by mutableIntStateOf(0)
        var opened = 0
        compose.setContent {
            InabahTheme { AzkarStreakCapsule(current, onClick = { opened++ }) }
        }

        compose.onNodeWithText("Начните серию сегодня").assertIsDisplayed().performClick()
        compose.runOnIdle { check(opened == 1) }

        current = 2
        compose.onNodeWithText("2 дня подряд").assertIsDisplayed()
        compose.onAllNodesWithText("Начните серию сегодня").assertCountEquals(0)
    }
}
