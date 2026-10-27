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
 * @property turnFarDegrees дальше - «Поверните направо», ближе - «Правее»…
 * @property turnNearDegrees …ближе - «Немного правее» (набросок `qibla-v2.html`, решение пользователя 2026-10-24).
 * @property turnHysteresisDegrees запас у этих границ: надпись не мигает от дрожания.
 * @property interferenceEnterRatio сила магнитного поля отличается от ожидаемой в этой точке (мировая модель) больше
 *   этой доли - металл или магнит рядом, «Компас неточен» (аудит 2026-10-24: абсолютные 22-70 мкТл не годились
 *   для мест со слабым полем - Южная Атлантика ~22-25 мкТл)…
 * @property interferenceExitRatio …и снова чисто - меньше этой.
 * @property approximateNearKm ближе к Каабе «приблизительное» местоположение (размытие 1-3 км) сдвигает направление
 *   на десятки градусов - подтверждения нет, просим точное (решение пользователя 2026-10-24).
 * @property locationStaleMillis координаты старше - «Местоположение устарело» с кнопкой «Обновить»; направление не
 *   подтверждается, пока не обновятся (решение пользователя 2026-10-24).
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
    val turnFarDegrees: Double = 45.0,
    val turnNearDegrees: Double = 15.0,
    val turnHysteresisDegrees: Double = 2.0,
    val interferenceEnterRatio: Double = 0.35,
    val interferenceExitRatio: Double = 0.25,
    val approximateNearKm: Double = 50.0,
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
