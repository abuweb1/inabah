package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.ThemeGradients
import app.inabah.android.core.designsystem.ThemeStyle
import app.inabah.android.core.designsystem.animateTheme
import app.inabah.android.core.settings.AppearanceSettings
import app.inabah.android.core.settings.PreferencesStorage
import app.inabah.android.feature.makharij.MakharijHomeScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val HALF_TRANSITION_MILLIS = 150L

/** «Палитра»: выбор сохраняется и перекрашивает приложение; «Махрадж» — карточка «Скоро» одним элементом. */
@RunWith(AndroidJUnit4::class)
class PaletteSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var directory: File
    private lateinit var storage: PreferencesStorage

    @Before
    fun openStorage() {
        directory = File(context.cacheDir, "palette-test-${System.nanoTime()}").apply { mkdirs() }
        val dataStore = PreferenceDataStoreFactory.create(scope = ioScope) { File(directory, "test.preferences_pb") }
        storage = PreferencesStorage(dataStore) { throw AssertionError("Ошибка хранилища", it) }
        runBlocking { storage.load() }
        ioScope.launch { storage.runWriter() }
    }

    @After
    fun closeStorage() {
        ioScope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun choosingEmeraldSavesItAndRepaintsHomeBackground() {
        val settings = AppearanceSettings(storage)
        var shownGradients: ThemeGradients? = null
        compose.setContent {
            // Как в MainActivity: тема — из настройки, с анимацией смены.
            val style by settings.style.collectAsState()
            InabahTheme(theme = animateTheme(style.theme)) {
                Column {
                    shownGradients = LocalInabahTheme.current.gradients
                    PaletteSettingsScreen(settings, onBack = {}, contentPadding = PaddingValues())
                }
            }
        }

        compose.onNodeWithText("Изумрудная").performScrollTo().performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            check(settings.style.value == ThemeStyle.Emerald)
            // Критерий этапа 6: фон главной («Азкары») — уже изумрудный.
            check(shownGradients?.azkarBackground == ThemeStyle.Emerald.theme.gradients.azkarBackground)
        }
        compose.onNodeWithText("Изумрудная")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    /**
     * Регрессия (запись экрана 2026-10-05): в кадре смены палитры на кадр мигала новая. Первый кадр —
     * прежний вид; смена посреди перехода продолжается от видимого кадра, без скачка; в конце — последний стиль.
     */
    @Test
    fun paletteChangeStartsFromShownFrameWithoutJumps() {
        val settings = AppearanceSettings(storage)
        var shown: ThemeGradients? = null
        compose.setContent {
            val style by settings.style.collectAsState()
            InabahTheme(theme = animateTheme(style.theme)) { shown = LocalInabahTheme.current.gradients }
        }
        compose.waitForIdle()
        val sections = ThemeStyle.Sections.theme.gradients.azkarBackground
        check(shown?.azkarBackground == sections)

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { settings.select(ThemeStyle.Emerald) }
        compose.mainClock.advanceTimeByFrame()
        check(shown?.azkarBackground == sections) { "первый кадр смены — уже не прежняя палитра" }

        compose.mainClock.advanceTimeBy(HALF_TRANSITION_MILLIS)
        val middle = shown?.azkarBackground
        check(middle != sections && middle != ThemeStyle.Emerald.theme.gradients.azkarBackground) { "середина перехода" }

        compose.runOnIdle { settings.select(ThemeStyle.Amber) }
        compose.mainClock.advanceTimeByFrame()
        check(shown?.azkarBackground == middle) { "смена посреди перехода прыгнула" }

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        check(shown?.azkarBackground == ThemeStyle.Amber.theme.gradients.azkarBackground)
    }

    @Test
    fun makharijComingSoonIsOneAccessibilityElement() {
        compose.setContent { InabahTheme { MakharijHomeScreen(contentPadding = PaddingValues()) } }
        // Заголовок и текст — в одном узле (объединены).
        compose.onNodeWithText("Скоро", substring = false, useUnmergedTree = false)
            .assert(SemanticsMatcher("содержит и текст") { node ->
                node.config.getOrElseNullable(SemanticsProperties.Text) { null }
                    ?.any { text -> text.text.startsWith("Здесь появятся") } == true
            })
    }
}
