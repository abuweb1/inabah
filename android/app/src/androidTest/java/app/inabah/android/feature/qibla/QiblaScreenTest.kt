package app.inabah.android.feature.qibla

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.qibla.LocationNote
import app.inabah.android.core.qibla.QiblaGuidance
import app.inabah.android.core.qibla.QiblaPointer
import app.inabah.android.core.qibla.QiblaUiState
import app.inabah.android.core.qibla.TurnAmount
import app.inabah.android.core.qibla.TurnSide
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Экран «Къибла» (1.3.0): состояния, действия карточки, диалог доступа, справка. */
@RunWith(AndroidJUnit4::class)
class QiblaScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = QiblaActions(
        onBack = { calls += "back" },
        onRequestPermission = { calls += "request" },
        onDialogLater = { calls += "later" },
        onOpenAppSettings = { calls += "appSettings" },
        onOpenLocationSettings = { calls += "locationSettings" },
        onRefreshLocation = { calls += "refresh" },
        onOpenHelp = { calls += "help" },
        onCloseHelp = { calls += "closeHelp" },
        onRestart = { calls += "restart" },
    )
    private val fine = LocationNote(approximate = false, stale = false)

    @Before
    fun enableChecks() {
        compose.enableAccessibilityChecks()
    }

    private fun show(state: QiblaUiState, dialog: Boolean = false, help: Boolean = false, helpHint: Boolean = false) {
        compose.setContent {
            // Приложение подставляет русский интерфейс (uiLocaleOverride в MainActivity); тестовая активность - нет.
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag("ru")) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                InabahTheme { QiblaContent(remember { mutableStateOf(state) }, dialog, help, actions, PaddingValues(), helpHintVisible = helpHint) }
            }
        }
    }

    private fun compass(offset: Double, guidance: QiblaGuidance, note: LocationNote = fine) = QiblaUiState.Compass(
        bearing = 194.0,
        location = note,
        pointer = QiblaPointer(offset, offset, if (offset < 0) TurnSide.Left else TurnSide.Right, guidance == QiblaGuidance.Aligned, 1),
        guidance = guidance,
    )

    @Test
    fun permissionDialogAllowsOrPostpones() {
        show(QiblaUiState.NeedsPermission(forever = false), dialog = true)
        compose.onNodeWithText("Доступ к местоположению").assertIsDisplayed()
        compose.onNodeWithText("Координаты остаются на телефоне и никуда не отправляются.").assertIsDisplayed()
        compose.onNodeWithText("Не сейчас").performClick()
        compose.runOnIdle { check(calls == listOf("later")) }
        // Под окном экран для TalkBack скрыт: кнопка «Разрешить» одна - в окне.
        compose.onAllNodesWithText("Разрешить").assertCountEquals(1)
    }

    @Test
    fun deniedForeverOffersAppSettings() {
        show(QiblaUiState.NeedsPermission(forever = true))
        compose.onNodeWithText("Доступ не разрешён").assertIsDisplayed()
        compose.onNodeWithText("Открыть настройки").performClick()
        compose.runOnIdle { check(calls == listOf("appSettings")) }
    }

    @Test
    fun locationOffOffersLocationSettings() {
        show(QiblaUiState.LocationOff)
        compose.onNodeWithText("Открыть настройки").performClick()
        compose.runOnIdle { check(calls == listOf("locationSettings")) }
    }

    @Test
    fun turnStateDescribesSideAndDegreesForTalkBack() {
        show(compass(-42.0, QiblaGuidance.Turn(TurnSide.Left, TurnAmount.Far)))
        compose.onNodeWithText("Поверните налево").assertIsDisplayed()
        compose.onNodeWithContentDescription("Къибла слева, поверните на 40°").assertIsDisplayed()
    }

    @Test
    fun restartButtonRestartsCompass() {
        show(compass(20.0, QiblaGuidance.Turn(TurnSide.Right, TurnAmount.Far)))
        compose.onNodeWithContentDescription("Перезапустить компас").performClick()
        compose.runOnIdle { check(calls == listOf("restart")) }
    }

    @Test
    fun alignedShowsConfirmation() {
        show(compass(1.0, QiblaGuidance.Aligned))
        compose.onNodeWithText("Вы направлены к Къибле").assertIsDisplayed()
        compose.onNodeWithContentDescription("Направление найдено").assertIsDisplayed()
    }

    @Test
    fun staleLocationOffersRefresh() {
        show(compass(20.0, QiblaGuidance.Turn(TurnSide.Right, TurnAmount.Far), LocationNote(approximate = false, stale = true)))
        compose.onNodeWithText("Местоположение устарело").assertIsDisplayed()
        compose.onNodeWithText("Обновить").performClick()
        compose.runOnIdle { check(calls == listOf("refresh")) }
    }

    @Test
    fun lowAccuracyAndInfoButtonOpensHelp() {
        show(compass(20.0, QiblaGuidance.LowAccuracy))
        compose.onNodeWithText("Компас неточен").assertIsDisplayed()
        compose.onNodeWithText("Вдали от металлических предметов").assertIsDisplayed()
        compose.onNodeWithContentDescription("Как повысить точность").performScrollTo().performClick()
        compose.runOnIdle { check(calls == listOf("help")) }
    }

    @Test
    fun turnWordingFollowsAmount() {
        show(compass(-30.0, QiblaGuidance.Turn(TurnSide.Left, TurnAmount.Mid)))
        compose.onNodeWithText("Левее").assertIsDisplayed()
    }

    @Test
    fun nearTurnAndHelpHintBubble() {
        show(compass(8.0, QiblaGuidance.Turn(TurnSide.Right, TurnAmount.Near)), helpHint = true)
        compose.onNodeWithText("Немного правее").assertIsDisplayed()
        compose.onNodeWithText("Как повысить точность").assertIsDisplayed() // пузырь у ⓘ
    }

    @Test
    fun helpSheetReadsAndCloses() {
        show(compass(20.0, QiblaGuidance.LowAccuracy), help = true)
        compose.onNodeWithText("Уберите помехи").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Понятно").performScrollTo().performClick()
        compose.runOnIdle { check(calls.last() == "closeHelp") }
    }
}
