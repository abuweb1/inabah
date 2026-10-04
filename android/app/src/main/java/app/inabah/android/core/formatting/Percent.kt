package app.inabah.android.core.formatting

import java.text.NumberFormat
import java.util.Locale

/** Процент по правилам локали (`ru`: «13 %» с неразрывным пробелом), как `.percent` в iOS. */
fun formatPercent(percent: Int, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).format(percent / 100.0)
