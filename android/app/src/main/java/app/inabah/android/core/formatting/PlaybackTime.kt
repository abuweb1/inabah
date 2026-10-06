package app.inabah.android.core.formatting

import kotlin.math.roundToLong

// Время плеера `м:сс` (docs/android/03-domain-logic.md, 3.7): прошедшее — вниз до секунды,
// длительность — до ближайшей (запись 2,98 с — «0:03», а не «0:02»).

private const val MILLIS_IN_SECOND = 1000L
private const val SECONDS_IN_MINUTE = 60

fun formatElapsed(ms: Long): String = minutesSeconds(ms.coerceAtLeast(0) / MILLIS_IN_SECOND)

fun formatDuration(ms: Long): String = minutesSeconds((ms.coerceAtLeast(0) / MILLIS_IN_SECOND.toDouble()).roundToLong())

private fun minutesSeconds(totalSeconds: Long): String =
    "%d:%02d".format(totalSeconds / SECONDS_IN_MINUTE, totalSeconds % SECONDS_IN_MINUTE)
