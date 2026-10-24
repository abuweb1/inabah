package app.inabah.android.core.qibla

/** Доступ к местоположению. */
enum class LocationAccess {
    /** Ещё не проверен. */
    Unknown,

    /** Не дан: можно спросить. */
    NotGranted,

    /** Отказ «больше не спрашивать» - только через настройки телефона. */
    DeniedForever,

    /** Пользователь выбрал «приблизительное» в окне Android: направление верное, пометка в строке. */
    Approximate,

    Precise,
    ;

    val granted: Boolean get() = this == Approximate || this == Precise
}

/** Координаты; [timeMillis] - когда получены, по тем же часам, что `nowMillis` в [qiblaUiState]. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val timeMillis: Long,
)

/** Что известно о местоположении. */
sealed interface LocationStatus {
    /** Геолокация выключена в телефоне. */
    data object Off : LocationStatus

    /** Ищем, координат ещё нет. */
    data object Searching : LocationStatus

    /** Не нашлось за [QiblaTuning.locationTimeoutMillis]. */
    data object Failed : LocationStatus

    data class Found(val fix: LocationFix) : LocationStatus
}

/**
 * Показание датчика направления.
 *
 * @property trueHeading куда смотрит верх телефона, градусы от истинного севера (поправка на склонение уже есть).
 * @property accuracyDegrees оценка точности от датчика; `null` - датчик её не даёт.
 * @property lowAccuracy датчик сам сообщил о низкой точности (нужна калибровка).
 * @property tiltDegrees наклон экрана от горизонтали.
 * @property fieldMicroTesla сила магнитного поля у телефона; `null` - магнитометра нет.
 * @property expectedFieldMicroTesla сила поля Земли в этой точке по мировой модели; `null` - неизвестна.
 */
data class HeadingSample(
    val trueHeading: Double,
    val accuracyDegrees: Double?,
    val lowAccuracy: Boolean,
    val tiltDegrees: Double,
    val timeMillis: Long,
    val fieldMicroTesla: Double? = null,
    val expectedFieldMicroTesla: Double? = null,
)

/** Что известно о направлении телефона. */
sealed interface HeadingStatus {
    /** Датчика направления нет. */
    data object NoSensor : HeadingStatus

    /** Датчик есть, показаний ещё нет. */
    data object Waiting : HeadingStatus

    data class Reading(val sample: HeadingSample) : HeadingStatus
}

/**
 * Пометки плитки местоположения. [nearKaaba] - выбрано «приблизительное», а до Каабы ближе
 * [QiblaTuning.approximateNearKm]: нужно точное.
 */
data class LocationNote(val approximate: Boolean, val stale: Boolean, val nearKaaba: Boolean = false) {
    /** Координатам можно доверить «Направление найдено» (решение пользователя 2026-10-24, (б)+(в)). */
    val confirmable: Boolean get() = !stale && !nearKaaba
}

/** Пометки координат [fix] при доступе [access] на момент [nowMillis]. */
fun locationNote(access: LocationAccess, fix: LocationFix, nowMillis: Long, tuning: QiblaTuning = QiblaTuning.Default): LocationNote {
    val approximate = access == LocationAccess.Approximate
    return LocationNote(
        approximate = approximate,
        stale = nowMillis - fix.timeMillis > tuning.locationStaleMillis,
        nearKaaba = approximate && distanceToKaabaKm(fix.latitude, fix.longitude) <= tuning.approximateNearKm,
    )
}

/** Что подсказать в карточке, когда компас работает. */
sealed interface QiblaGuidance {
    /** Первое показание ещё не пришло. */
    data object Waiting : QiblaGuidance

    data object LowAccuracy : QiblaGuidance

    data class Tilted(val side: TurnSide, val amount: TurnAmount) : QiblaGuidance

    data class Turn(val side: TurnSide, val amount: TurnAmount) : QiblaGuidance

    /** Метка у указателя, но координатам нельзя доверить подтверждение: устарели или приблизительные у Мекки. */
    data object LocationUnconfirmed : QiblaGuidance

    data object Aligned : QiblaGuidance
}

/** Состояние экрана «Къибла» (`docs/android/06-screens.md`, «Къибла»). */
sealed interface QiblaUiState {
    data object Loading : QiblaUiState

    /** Нужен доступ к местоположению; [forever] - спросить уже нельзя, только настройки телефона. */
    data class NeedsPermission(val forever: Boolean) : QiblaUiState

    data object LocationOff : QiblaUiState

    data object Locating : QiblaUiState

    data object LocationFailed : QiblaUiState

    /** Компаса нет: направление - числом от севера. */
    data class NoCompass(val bearing: Double, val location: LocationNote) : QiblaUiState

    /** Кольцо работает; [pointer] - `null`, пока нет показаний. */
    data class Compass(
        val bearing: Double,
        val location: LocationNote,
        val pointer: QiblaPointer?,
        val guidance: QiblaGuidance,
    ) : QiblaUiState
}

/**
 * Состояние экрана по доступу, местоположению и датчику. [pointer] - последний результат [QiblaTracker]
 * для текущего показания (`null`, если показаний нет); наклон и точность - уже с гистерезисом трекера.
 * Подтверждение только при надёжных данных: есть координаты, точность датчика в пределах, телефон лежит ровно.
 */
fun qiblaUiState(
    access: LocationAccess,
    location: LocationStatus,
    heading: HeadingStatus,
    pointer: QiblaPointer?,
    nowMillis: Long,
    tuning: QiblaTuning = QiblaTuning.Default,
): QiblaUiState {
    when (access) {
        LocationAccess.Unknown -> return QiblaUiState.Loading
        LocationAccess.NotGranted -> return QiblaUiState.NeedsPermission(forever = false)
        LocationAccess.DeniedForever -> return QiblaUiState.NeedsPermission(forever = true)
        LocationAccess.Approximate, LocationAccess.Precise -> Unit
    }
    val fix = when (location) {
        LocationStatus.Off -> return QiblaUiState.LocationOff
        LocationStatus.Searching -> return QiblaUiState.Locating
        LocationStatus.Failed -> return QiblaUiState.LocationFailed
        is LocationStatus.Found -> location.fix
    }
    val bearing = qiblaBearing(fix.latitude, fix.longitude)
    val note = locationNote(access, fix, nowMillis, tuning)
    when (heading) {
        HeadingStatus.NoSensor -> return QiblaUiState.NoCompass(bearing, note)
        HeadingStatus.Waiting -> return QiblaUiState.Compass(bearing, note, pointer = null, QiblaGuidance.Waiting)
        is HeadingStatus.Reading -> Unit
    }
    if (pointer == null) return QiblaUiState.Compass(bearing, note, pointer = null, QiblaGuidance.Waiting)
    val guidance = when {
        pointer.lowAccuracy -> QiblaGuidance.LowAccuracy
        pointer.tilted -> QiblaGuidance.Tilted(pointer.side, pointer.amount)
        pointer.aligned -> QiblaGuidance.Aligned
        pointer.heldByLocation -> QiblaGuidance.LocationUnconfirmed
        else -> QiblaGuidance.Turn(pointer.side, pointer.amount)
    }
    return QiblaUiState.Compass(bearing, note, pointer, guidance)
}
