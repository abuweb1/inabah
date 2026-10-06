package app.inabah.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Профиль частых путей: запуск, лента утренних азкаров (с плеером и без), хадисы с листанием.
 * Запуск: ./gradlew :app:generateReleaseBaselineProfile — результат в app/src/release/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        openMorningAzkar()
        scrollFeed()
        playFirstZikr()
        scrollFeed()
        device.pressBack()
        browseHadith()
    }
}
