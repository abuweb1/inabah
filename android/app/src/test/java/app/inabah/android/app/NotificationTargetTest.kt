package app.inabah.android.app

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.reminders.ReminderKind
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Нажатие на уведомление (TASKS §15): куда ведёт напоминание и как разбирается интент. */
class NotificationTargetTest {
    @Test
    fun `Серия под угрозой — главная азкаров, остальные напоминания — список раздела`() {
        assertEquals(NotificationTarget.AzkarHome, azkarReminderTarget(ReminderKind.MorningStreak, AzkarSection.Morning))
        assertEquals(NotificationTarget.AzkarHome, azkarReminderTarget(ReminderKind.EveningStreak, AzkarSection.Evening))
        for (kind in ReminderKind.entries.filter { it.section != null && !it.isStreak }) {
            val section = kind.section!!
            assertEquals(NotificationTarget.AzkarList(section), azkarReminderTarget(kind, section), "$kind")
        }
    }

    @Test
    fun `Разбор действий интента`() {
        assertEquals(NotificationTarget.Player, notificationTarget(MainActivity.ACTION_OPEN_PLAYER, null, null, 0))
        assertEquals(NotificationTarget.AzkarHome, notificationTarget(MainActivity.ACTION_OPEN_AZKAR_HOME, null, null, 0))
        assertEquals(
            NotificationTarget.AzkarList(AzkarSection.Evening),
            notificationTarget(MainActivity.ACTION_OPEN_AZKAR, AzkarSection.Evening.key, null, 0),
        )
        assertEquals(
            NotificationTarget.Hadith(HadithId(HadithCollection.Qudsi, 7)),
            notificationTarget(MainActivity.ACTION_OPEN_HADITH, null, HadithCollection.Qudsi.key, 7),
        )
    }

    @Test
    fun `Чужое действие и неполные данные — ничего не открывать`() {
        assertNull(notificationTarget(null, null, null, 0))
        assertNull(notificationTarget("android.intent.action.MAIN", null, null, 0))
        assertNull(notificationTarget(MainActivity.ACTION_OPEN_AZKAR, "night", null, 0))
        assertNull(notificationTarget(MainActivity.ACTION_OPEN_AZKAR, null, null, 0))
        assertNull(notificationTarget(MainActivity.ACTION_OPEN_HADITH, null, "unknown", 3))
        assertNull(notificationTarget(MainActivity.ACTION_OPEN_HADITH, null, HadithCollection.Nawawi.key, 0))
    }
}
