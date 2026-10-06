package app.inabah.android.core.content.model

import app.inabah.android.core.content.ContentLanguage

/** Раздел азкаров; `key` — имя в `azkar.json` и в ключах хранения прогресса. */
enum class AzkarSection(val key: String) {
    Morning("morning"),
    Evening("evening"),
}

/** Номер зикра уникален только внутри раздела. */
data class ZikrId(val section: AzkarSection, val number: Int)

data class Zikr(
    val id: ZikrId,
    val arabic: String,
    /** Сколько раз читать, не меньше 1. */
    val repetitions: Int,
    /** Файл записи в `assets/audio/`; записей пока нет (2026-10-06) — `null`, на экране «Аудио скоро». */
    val audioFileName: String? = null,
    val translation: ZikrTranslation?,
) {
    val section: AzkarSection get() = id.section

    /** Есть запись — ▶ и «Прослушать все» активны; нет — «Аудио скоро» (iOS `hasAudio`). */
    val hasAudio: Boolean get() = audioFileName != null
    val number: Int get() = id.number
}

data class ZikrTranslation(
    val language: ContentLanguage,
    val text: String,
    val transliteration: String?,
    val source: String?,
)
