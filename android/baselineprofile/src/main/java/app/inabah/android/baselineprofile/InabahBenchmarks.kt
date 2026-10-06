package app.inabah.android.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ITERATIONS = 5

/**
 * Замеры этапа 7 (критерий — без рывков ленты при игре плеера): холодный запуск и кадры прокрутки
 * утренних азкаров с играющим плеером — без профиля и с ним. Запуск:
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class InabahBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun feedWithPlayerWithoutProfile() = feedWithPlayer(CompilationMode.None())

    @Test
    fun feedWithPlayerWithProfile() = feedWithPlayer(CompilationMode.Partial(BaselineProfileMode.Require))

    /** Та же прокрутка без плеера — чтобы отделить цену плеера от цены самой ленты. */
    @Test
    fun feedWithoutPlayerWithProfile() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = null,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openMorningAzkar()
        },
    ) {
        scrollFeed()
    }

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private fun feedWithPlayer(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // Процесс живёт между проходами: замер — только кадры прокрутки (холодный старт на эмуляторе API 37
        // не давал кадров в трассе).
        startupMode = null,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openMorningAzkar()
            playFirstZikr()
        },
    ) {
        scrollFeed()
    }
}
