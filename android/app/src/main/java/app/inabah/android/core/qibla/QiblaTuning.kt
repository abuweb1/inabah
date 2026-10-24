package app.inabah.android.core.qibla

/**
 * Допуски экрана «Къибла» (решения 2026-10-24, обоснование - `docs/android/03-domain-logic.md`, «Къибла»).
 * Компас телефона в обычных условиях ошибается на 5-15°, рядом с металлом - на десятки градусов,
 * поэтому значения не должны создавать ощущения точности выше этой.
 *
 * @property alignEnterDegrees «Направление найдено» загорается, когда метка ближе к указателю.
 * @property alignExitDegrees и гаснет, только когда отходит дальше: разрыв гасит дрожание датчика у границы.
 * @property smoothingMillis постоянная времени сглаживания метки: дрожание датчика гасится, поворот не запаздывает.
 * @property flipZoneDegrees около противоположного направления сторона поворота держится прежней,
 *   пока метка не отойдёт от 180° дальше этого.
 * @property snapAfterMillis пауза между показаниями, после которой метка встаёт сразу, без сглаживания
 *   (возврат на экран).
 * @property alignRearmMillis новый вход в совпадение даёт виброотклик, только если метка была вне совпадения
 *   хотя бы столько: короткие провалы (дрожание, миг ненадёжного показания) отклик не повторяют.
 * @property tiltMaxDegrees наклон экрана от горизонтали, после которого направление не подтверждается…
 * @property tiltExitDegrees …и меньше которого телефон снова «лежит ровно» (гистерезис: держат в руке около 30°).
 * @property headingAccuracyMaxDegrees оценка точности датчика, хуже которой - «Компас неточен»…
 * @property headingAccuracyExitDegrees …и лучше которой компас снова точен.
 * @property locationStaleMillis координаты старше - «Местоположение устарело» с кнопкой «Обновить».
 * @property locationTimeoutMillis сколько ждать свежих координат, прежде чем показать ошибку.
 */
data class QiblaTuning(
    val alignEnterDegrees: Double = 5.0,
    val alignExitDegrees: Double = 8.0,
    val smoothingMillis: Long = 150,
    val flipZoneDegrees: Double = 10.0,
    val snapAfterMillis: Long = 1_000,
    val alignRearmMillis: Long = 1_000,
    val tiltMaxDegrees: Double = 30.0,
    val tiltExitDegrees: Double = 25.0,
    val headingAccuracyMaxDegrees: Double = 20.0,
    val headingAccuracyExitDegrees: Double = 15.0,
    val locationStaleMillis: Long = 30 * 60 * 1_000L,
    val locationTimeoutMillis: Long = 30 * 1_000L,
) {
    init {
        require(alignEnterDegrees in 0.0..alignExitDegrees) { "Порог входа не больше порога выхода" }
        require(smoothingMillis >= 0 && flipZoneDegrees in 0.0..180.0)
        require(tiltExitDegrees <= tiltMaxDegrees && headingAccuracyExitDegrees <= headingAccuracyMaxDegrees)
    }

    companion object {
        val Default = QiblaTuning()
    }
}
