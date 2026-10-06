package app.inabah.android.feature.azkar

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.formatting.isSystem24HourFormat
import app.inabah.android.core.settings.AzkarWindow
import app.inabah.android.core.settings.DayTime
import java.time.format.DateTimeFormatter

// Время азкаров на экранах (iOS `AzkarWindow+Presentation`): формат системы — 12 или 24 часа и локаль.

/** Форматирует время суток как часы системы: в ru — «05:00», в 12-часовом формате — «5:00 AM». */
@Composable
fun rememberDayTimeFormatter(): (DayTime) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = isSystem24HourFormat()
    return remember(locale, is24Hour) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        val format: (DayTime) -> String = { time -> formatter.format(time.localTime) }
        format
    }
}

/** «05:00–12:00» — на карточке раздела вне времени азкаров. */
@Composable
fun AzkarWindow.rangeText(): String {
    val format = rememberDayTimeFormatter()
    return stringResource(R.string.azkar_window_range, format(start), format(end))
}

/** «Время утренних азкаров — с 05:00 до 12:00» — на экране раздела вне времени азкаров. */
@Composable
fun AzkarSection.windowNotice(window: AzkarWindow): String {
    val format = rememberDayTimeFormatter()
    val res = when (this) {
        AzkarSection.Morning -> R.string.azkar_window_notice_morning
        AzkarSection.Evening -> R.string.azkar_window_notice_evening
    }
    return stringResource(res, format(window.start), format(window.end))
}
