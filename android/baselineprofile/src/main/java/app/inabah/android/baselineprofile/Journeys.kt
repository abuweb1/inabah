package app.inabah.android.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

// Сценарии для профиля и замеров — по видимым подписям и описаниям TalkBack (строки ресурсов, русский).

internal const val TARGET_PACKAGE = "app.inabah.android"

private const val TIMEOUT_MILLIS = 5_000L
private const val FEED_FLINGS = 4

/** Доля высоты экрана для жестов: выше мини-плеера и панели вкладок. */
private const val SWIPE_FROM = 0.6f
private const val SWIPE_TO = 0.2f
private const val SWIPE_STEPS = 12

private fun MacrobenchmarkScope.waitFor(selector: androidx.test.uiautomator.BySelector) =
    checkNotNull(device.wait(Until.findObject(selector), TIMEOUT_MILLIS)) { "Не найдено на экране: $selector" }

/** С главной «Азкаров» — в утренние: лента карточек со счётчиками. */
internal fun MacrobenchmarkScope.openMorningAzkar() {
    waitFor(By.text("Утренние азкары")).click()
    waitFor(By.desc("Счётчик"))
}

/** Прокрутка ленты вниз — жестами выше мини-плеера, каждый дожидается остановки. */
internal fun MacrobenchmarkScope.scrollFeed(flings: Int = FEED_FLINGS) {
    val x = device.displayWidth / 2
    repeat(flings) {
        device.swipe(x, (device.displayHeight * SWIPE_FROM).toInt(), x, (device.displayHeight * SWIPE_TO).toInt(), SWIPE_STEPS)
        device.waitForIdle()
    }
}

/** ▶ первой карточки: плеер играет, мини-плеер над вкладками. */
internal fun MacrobenchmarkScope.playFirstZikr() {
    waitFor(By.desc("Прослушать")).click()
    waitFor(By.desc("Пауза"))
}

/** Хадисы → ан-Навави → первый хадис → листание страниц. */
internal fun MacrobenchmarkScope.browseHadith() {
    waitFor(By.text("Хадисы")).click()
    waitFor(By.text("40 хадисов ан-Навави")).click()
    waitFor(By.descStartsWith("Хадис 1,")).click()
    repeat(2) {
        waitFor(By.desc("Следующий хадис")).click()
        device.waitForIdle()
    }
    // Текст хадиса — прокрутка вниз (страница пересоздаётся при листании: жест, не найденный элемент).
    scrollFeed(flings = 2)
}
