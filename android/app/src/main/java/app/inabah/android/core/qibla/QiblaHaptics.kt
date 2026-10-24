package app.inabah.android.core.qibla

/**
 * Решает, давать ли виброотклик: один на каждый новый вход в совпадение (аудит 2026-10-24). Счёт входов растёт
 * в [QiblaTracker]; у новой подписки (↻, возврат на экран) - свой номер и счёт с нуля, поэтому сравнение - только
 * внутри одного номера: старое значение после ↻ отклика не даёт, первый вход новой подписки - даёт.
 */
class AlignmentHaptics {
    private var generation: Int? = null
    private var heard = 0

    fun shouldVibrate(pointer: QiblaPointer): Boolean {
        if (pointer.generation != generation) {
            generation = pointer.generation
            heard = 0
        }
        val vibrate = pointer.alignedEntries > heard
        heard = pointer.alignedEntries
        return vibrate
    }
}

/** Шаг градусов для TalkBack: точнее датчик не знает, и озвучка не меняется на каждый градус. */
const val SPOKEN_TURN_STEP = 5

/**
 * Сколько градусов назвать TalkBack («поверните на 40°»): шагом [SPOKEN_TURN_STEP], не меньше одного шага (не «на 0°»);
 * прежнее значение [previous] держится, пока отклонение не уйдёт от него на три четверти шага (аудит 2026-10-24:
 * у 12,5° озвучка прыгала 10°/15°).
 */
fun spokenTurnDegrees(distance: Double, previous: Int?): Int {
    if (previous != null && kotlin.math.abs(distance - previous) < SPOKEN_TURN_STEP * HOLD_FRACTION) return previous
    return (kotlin.math.round(distance / SPOKEN_TURN_STEP).toInt() * SPOKEN_TURN_STEP).coerceAtLeast(SPOKEN_TURN_STEP)
}

private const val HOLD_FRACTION = 0.75
