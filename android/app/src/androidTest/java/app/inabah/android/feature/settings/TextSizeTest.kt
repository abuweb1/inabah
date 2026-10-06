package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InterfaceTextScale
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.ContentTextSize
import app.inabah.android.core.settings.InterfaceTextSize as InterfaceStep
import app.inabah.android.core.settings.PreferencesStorage
import app.inabah.android.core.settings.TextSizeSettings
import app.inabah.android.feature.azkar.AzkarStore
import app.inabah.android.feature.hadith.HadithStore
import java.io.File
import java.time.Instant
import java.time.ZoneId
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

private const val SETTLE_MILLIS = 300L

/** «Размер текста»: ползунки и закреплённый навбар. */
@RunWith(AndroidJUnit4::class)
class TextSizeTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var directory: File
    private lateinit var storage: PreferencesStorage

    @Before
    fun openStorage() {
        // Этап 7: каждое действие теста заодно проверяет экран на доступность (ATF).
        compose.enableAccessibilityChecks()
        directory = File(context.cacheDir, "text-size-test-${System.nanoTime()}").apply { mkdirs() }
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
    fun tapAtTrackEndSelectsLargestAndAccessibilityStepSelectsSmaller() {
        val settings = TextSizeSettings(storage)
        val repository = EmptyRepository()
        val azkar = AzkarStore(repository, storage, AzkarWindowSettings(storage), AzkarHistory(storage),{ Instant.EPOCH }, { ZoneId.of("UTC") }, { _, e -> throw AssertionError(e) })
        compose.setContent {
            InabahTheme {
                TextSizeSettingsScreen(settings, azkar, HadithStore(repository), onBack = {}, contentPadding = PaddingValues())
            }
        }

        // Ведение с середины дорожки за её правый край — бегунок прилипает к последнему шагу.
        compose.onNodeWithContentDescription("Размер переводов").performTouchInput {
            down(center)
            moveTo(centerRight + androidx.compose.ui.geometry.Offset(width.toFloat(), 0f))
            up()
        }
        compose.runOnIdle { check(settings.content.value == ContentTextSize.Largest) }
        // Название шага озвучивается значением ползунка (подпись над ним скрыта от TalkBack).
        compose.onNodeWithContentDescription("Размер переводов")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Очень крупный"))

        // TalkBack: смахивание меняет шаг через setProgress.
        compose.onNodeWithContentDescription("Размер интерфейса")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.runOnIdle { check(settings.interfaceSize.value == InterfaceStep.Smaller) }
    }

    @Test
    fun draggingInterfaceSliderUnderItsOwnScaleReachesEveryStep() {
        val settings = TextSizeSettings(storage)
        val repository = EmptyRepository()
        val azkar = AzkarStore(repository, storage, AzkarWindowSettings(storage), AzkarHistory(storage),{ Instant.EPOCH }, { ZoneId.of("UTC") }, { _, e -> throw AssertionError(e) })
        compose.setContent {
            InabahTheme {
                // Как в приложении: экран под шагом интерфейса — смена шага меняет плотность под пальцем.
                val step by settings.interfaceSize.collectAsState()
                InterfaceTextScale(step.fontScale) {
                    TextSizeSettingsScreen(settings, azkar, HadithStore(repository), onBack = {}, contentPadding = PaddingValues())
                }
            }
        }
        val seen = mutableListOf<InterfaceStep>()

        compose.onNodeWithContentDescription("Размер интерфейса").performTouchInput {
            down(centerLeft + androidx.compose.ui.geometry.Offset(-width.toFloat(), 0f))
            // Одним жестом слева направо, с паузами — после каждой смены шага.
            for (fraction in listOf(0.5f, 1f, 1.5f)) {
                moveTo(centerLeft + androidx.compose.ui.geometry.Offset(width * fraction, 0f))
                advanceEventTime(SETTLE_MILLIS)
                compose.runOnIdle { seen += settings.interfaceSize.value }
            }
            up()
        }

        compose.runOnIdle { check(settings.interfaceSize.value == InterfaceStep.Larger) { "дошли до ${settings.interfaceSize.value}, по пути $seen" } }
    }

    @Test
    fun navBarTitleIgnoresInterfaceStepAndSystemFontScale() {
        var scaled by mutableStateOf(false)
        compose.setContent {
            InabahTheme {
                // Как в приложении: системный ×2 под закреплённым корнем, поверх — шаг «Крупнее».
                val density = LocalDensity.current
                val system = if (scaled) 2f else 1f
                CompositionLocalProvider(LocalDensity provides Density(density.density, system)) {
                    FixedTextSize {
                        InterfaceTextScale(if (scaled) InterfaceStep.Larger.fontScale else 1f) {
                            InabahTopBar(title = "Заголовок", tint = Color.White, onBack = {})
                        }
                    }
                }
            }
        }
        val standard = compose.onNodeWithText("Заголовок").getUnclippedBoundsInRoot()

        scaled = true
        compose.onNodeWithText("Заголовок").assertHeightIsEqualTo(standard.bottom - standard.top)
    }
}

private class EmptyRepository : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> = emptyList()

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> = emptyList()
}
