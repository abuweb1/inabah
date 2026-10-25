package app.inabah.android.core.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** С какого дня серии предлагать добавить азкаров (решение пользователя 2026-10-25). */
const val MOTIVATION_FIRST_STREAK = 14

/** Через сколько дней серии предложить снова после «В другой раз» или «ОК» без изменений. */
const val MOTIVATION_REPEAT_DAYS = 7

/** Предложение серии, начавшейся [seriesStart], на её [streak]-м дне ([day]). */
data class MotivationOffer(val seriesStart: LocalDate, val streak: Int) {
    val day: LocalDate get() = seriesStart.plusDays(streak - 1L)
}

/**
 * Предложение на сегодня, если пора предложить добавить азкаров, иначе `null`. Пора, когда в наборе
 * выбраны не все азкары ([canAddMore]), день [today] прочитан полностью, серия не меньше
 * [MOTIVATION_FIRST_STREAK] и этой серии ещё не предлагали или предлагали [MOTIVATION_REPEAT_DAYS] дней назад.
 * Серия узнаётся по дате начала: прервалась — у новой другое начало, и отсчёт снова с [MOTIVATION_FIRST_STREAK].
 */
fun motivationDue(days: Map<LocalDate, AzkarDay>, today: LocalDate, last: MotivationOffer?, canAddMore: Boolean): MotivationOffer? {
    if (!canAddMore || days[today]?.status != AzkarDayStatus.Full) return null
    val streak = azkarStreak(days, today).current
    if (streak < MOTIVATION_FIRST_STREAK) return null
    val offer = MotivationOffer(today.minusDays(streak - 1L), streak)
    if (last != null && last.seriesStart == offer.seriesStart && streak < last.streak + MOTIVATION_REPEAT_DAYS) return null
    return offer
}

/**
 * «Добавьте ещё азкаров» (2026-10-25, `docs/android/03-domain-logic.md`): когда предлагали последний раз.
 * Ключи `azkar.motivation.seriesStart` (`yyyy-MM-dd`) и `azkar.motivation.streak`; испорченные — как не предлагали.
 */
class AzkarMotivation(private val storage: PreferencesStorage) {
    val last: MotivationOffer?
        get() {
            val start = storage.snapshot[startKey] ?: return null
            val streak = storage.snapshot[streakKey] ?: return null
            return try {
                MotivationOffer(LocalDate.parse(start), streak)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /** См. [motivationDue]. */
    fun due(days: Map<LocalDate, AzkarDay>, today: LocalDate, canAddMore: Boolean): MotivationOffer? = motivationDue(days, today, last, canAddMore)

    /** Окно [offer] закрыто («ОК», «В другой раз», «Назад»): запоминается то, что показали, а не день закрытия. */
    fun offered(offer: MotivationOffer) {
        storage.edit {
            it[startKey] = offer.seriesStart.toString()
            it[streakKey] = offer.streak
        }
    }

    private companion object {
        val startKey = stringPreferencesKey("azkar.motivation.seriesStart")
        val streakKey = intPreferencesKey("azkar.motivation.streak")
    }
}
