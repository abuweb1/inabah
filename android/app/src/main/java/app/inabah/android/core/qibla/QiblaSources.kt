package app.inabah.android.core.qibla

import kotlinx.coroutines.flow.Flow

/** Местоположение для экрана «Къибла»: только пока экран собирает поток, без фонового отслеживания. */
interface LocationSource {
    /**
     * Один поиск: [LocationStatus.Off], если геолокация выключена; иначе сразу последние известные координаты
     * (или [LocationStatus.Searching]), затем свежие; не нашлось за отведённое время и старых нет -
     * [LocationStatus.Failed]. Отмена коллектора отменяет поиск.
     */
    fun locate(): Flow<LocationStatus>
}

/**
 * Показание датчика до поправки на склонение.
 *
 * @property magneticAzimuth куда смотрит верх телефона, градусы от магнитного севера.
 * @property timeMillis монотонное время показания.
 */
data class MagneticHeading(
    val magneticAzimuth: Double,
    val accuracyDegrees: Double?,
    val lowAccuracy: Boolean,
    val tiltDegrees: Double,
    val timeMillis: Long,
)

/** Датчик направления: подписка - пока поток собирают, отмена снимает слушатель. */
interface HeadingSource {
    val available: Boolean

    fun headings(): Flow<MagneticHeading>
}

/** Магнитное склонение в точке, градусы (восточное - плюс): истинный азимут = магнитный + склонение. */
fun interface MagneticDeclination {
    fun at(fix: LocationFix): Double
}
