package app.inabah.android.core.qibla

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Кааба: 21°25′21″ с. ш., 39°49′34″ в. д. - Википедия «Kaaba»; совпадает с Kulturenvanteri (21.422500, 39.826167)
 * и LatLong.net (21.422487, 39.826206), расхождение - десятки метров (сверено 2026-10-24).
 */
object Kaaba {
    const val LATITUDE = 21.4225
    const val LONGITUDE = 39.8262
}

/** Средний радиус Земли, км. */
private const val EARTH_RADIUS_KM = 6371.0

private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0

/**
 * Направление на Каабу из точки [latitude], [longitude]: начальный азимут большого круга,
 * градусы по часовой стрелке от истинного севера, 0…360.
 */
fun qiblaBearing(latitude: Double, longitude: Double): Double {
    val from = Math.toRadians(latitude)
    val to = Math.toRadians(Kaaba.LATITUDE)
    val dLon = Math.toRadians(Kaaba.LONGITUDE - longitude)
    val y = sin(dLon) * cos(to)
    val x = cos(from) * sin(to) - sin(from) * cos(to) * cos(dLon)
    return normalizeDegrees(Math.toDegrees(atan2(y, x)))
}

/** Расстояние до Каабы по большому кругу, км (гаверсинус) - для проверки расчёта, на экран не выводится. */
fun distanceToKaabaKm(latitude: Double, longitude: Double): Double {
    val from = Math.toRadians(latitude)
    val to = Math.toRadians(Kaaba.LATITUDE)
    val dLat = to - from
    val dLon = Math.toRadians(Kaaba.LONGITUDE - longitude)
    val a = sin(dLat / 2).let { it * it } + cos(from) * cos(to) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Угол в 0…360 (360 → 0). */
fun normalizeDegrees(degrees: Double): Double {
    val turned = degrees % FULL_TURN
    return if (turned < 0) turned + FULL_TURN else turned
}

/**
 * Кратчайший поворот от [from] к [to]: -180…180, плюс - по часовой стрелке.
 * Ровно противоположное направление - +180.
 */
fun signedDelta(from: Double, to: Double): Double {
    val delta = normalizeDegrees(to - from)
    return if (delta > HALF_TURN) delta - FULL_TURN else delta
}
