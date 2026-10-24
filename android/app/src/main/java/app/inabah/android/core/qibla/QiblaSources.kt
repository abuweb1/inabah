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
 * @property lowAccuracy датчик сам сообщил о низкой точности ([sensorReportsLowAccuracy]).
 * @property fieldMicroTesla сила поля у телефона (магнитометр); `null` - магнитометра нет.
 * @property timeMillis монотонное время показания.
 */
data class MagneticHeading(
    val magneticAzimuth: Double,
    val accuracyDegrees: Double?,
    val lowAccuracy: Boolean,
    val tiltDegrees: Double,
    val timeMillis: Long,
    val fieldMicroTesla: Double? = null,
)

/**
 * Датчик направления: подписка - пока поток собирают, отмена снимает слушатель. Не удалось подписаться - поток
 * завершается ошибкой (экран покажет «Компаса нет», а не вечное «Настраиваем компас»).
 */
interface HeadingSource {
    val available: Boolean

    fun headings(): Flow<MagneticHeading>
}

/** Магнитное поле Земли в точке по мировой модели (без сети). */
interface MagneticModel {
    /** Склонение, градусы (восточное - плюс): истинный азимут = магнитный + склонение. */
    fun declination(fix: LocationFix): Double

    /** Сила поля, мкТл - с ней сравнивается магнитометр (помехи). */
    fun fieldMicroTesla(fix: LocationFix): Double
}
