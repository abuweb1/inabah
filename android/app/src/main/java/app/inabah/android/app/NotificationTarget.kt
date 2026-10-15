package app.inabah.android.app

import android.content.Context
import android.content.Intent
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.reminders.ReminderKind

/** Куда ведёт нажатие на уведомление приложения. Интент собирает [intent], разбирает [notificationTarget]. */
sealed interface NotificationTarget {
    /** Медиауведомление: раздел звучащей записи с открытым плеером. */
    data object Player : NotificationTarget

    /** «Серия под угрозой»: главная «Азкаров» (решение пользователя 2026-10-13). */
    data object AzkarHome : NotificationTarget

    /** Напоминание раздела: его список. */
    data class AzkarList(val section: AzkarSection) : NotificationTarget

    /** Хадис дня: этот хадис поверх списка своего сборника. */
    data class Hadith(val id: HadithId) : NotificationTarget
}

/** Куда ведёт напоминание азкаров вида [kind] раздела [section]. */
fun azkarReminderTarget(kind: ReminderKind, section: AzkarSection): NotificationTarget =
    if (kind.isStreak) NotificationTarget.AzkarHome else NotificationTarget.AzkarList(section)

/** Явный интент на [MainActivity] (не на алиасы иконок: правило проекта). */
fun NotificationTarget.intent(context: Context): Intent {
    val intent = Intent(context, MainActivity::class.java)
    return when (this) {
        NotificationTarget.Player -> intent.setAction(MainActivity.ACTION_OPEN_PLAYER)
        NotificationTarget.AzkarHome -> intent.setAction(MainActivity.ACTION_OPEN_AZKAR_HOME)
        is NotificationTarget.AzkarList -> intent.setAction(MainActivity.ACTION_OPEN_AZKAR)
            .putExtra(MainActivity.EXTRA_SECTION, section.key)
        is NotificationTarget.Hadith -> intent.setAction(MainActivity.ACTION_OPEN_HADITH)
            .putExtra(MainActivity.EXTRA_COLLECTION, id.collection.key)
            .putExtra(MainActivity.EXTRA_NUMBER, id.number)
    }
}

/** Цель по действию и дополнениям интента; чужое действие или неполные данные — `null` (ничего не открывать). */
fun notificationTarget(action: String?, section: String?, collection: String?, number: Int): NotificationTarget? =
    when (action) {
        MainActivity.ACTION_OPEN_PLAYER -> NotificationTarget.Player
        MainActivity.ACTION_OPEN_AZKAR_HOME -> NotificationTarget.AzkarHome
        MainActivity.ACTION_OPEN_AZKAR -> AzkarSection.entries.firstOrNull { it.key == section }
            ?.let(NotificationTarget::AzkarList)
        MainActivity.ACTION_OPEN_HADITH -> HadithCollection.fromKey(collection.orEmpty())
            ?.takeIf { number >= 1 }
            ?.let { NotificationTarget.Hadith(HadithId(it, number)) }
        else -> null
    }

/** [notificationTarget] из интента активности. */
fun Intent.notificationTarget(): NotificationTarget? = notificationTarget(
    action = action,
    section = getStringExtra(MainActivity.EXTRA_SECTION),
    collection = getStringExtra(MainActivity.EXTRA_COLLECTION),
    number = getIntExtra(MainActivity.EXTRA_NUMBER, 0),
)
