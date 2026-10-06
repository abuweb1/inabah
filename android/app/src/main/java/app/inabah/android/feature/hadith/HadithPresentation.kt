package app.inabah.android.feature.hadith

import app.inabah.android.core.content.model.Hadith

/** Длина превью перевода в строке списка (без многоточия). */
const val HADITH_PREVIEW_LENGTH = 80

private val NarratorIntro = Regex("^От [^:：]+[:：]\\s*")

/**
 * Превью перевода для строки списка: без вступления «От …:», не длиннее
 * [HADITH_PREVIEW_LENGTH] символов + `…`. Нет перевода — пустая строка.
 */
val Hadith.previewText: String
    get() {
        val text = translation?.text ?: return ""
        val body = NarratorIntro.replaceFirst(text, "")
        return if (body.length > HADITH_PREVIEW_LENGTH) {
            body.take(HADITH_PREVIEW_LENGTH).trimEnd() + "…"
        } else {
            body
        }
    }

/** Арабский текст одним абзацем: переносы в данных — строки печатного издания. */
val Hadith.arabicDisplayText: String
    get() = arabic.lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
