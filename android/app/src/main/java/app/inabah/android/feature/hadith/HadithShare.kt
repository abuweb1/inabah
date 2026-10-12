package app.inabah.android.feature.hadith

import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithNote
import app.inabah.android.core.share.shareBlocks

/**
 * Текст «Поделиться» хадисом — как на экране: «40 хадисов ан-Навави · Хадис 2», арабский одним
 * абзацем, вступление `rawi` как есть, без «Передал:» (решение пользователя 2026-10-11), перевод,
 * «Приводится: …», сноски ([notesBlock]); последним — ссылки на приложение ([storeLinks]).
 * Подписи — уже из ресурсов; нет данных — `null`.
 */
fun hadithShareText(
    hadith: Hadith,
    header: String,
    narratorLine: String?,
    sourceLine: String?,
    notesBlock: String?,
    storeLinks: String?,
): String = shareBlocks(header, hadith.arabicDisplayText, narratorLine, hadith.translation?.text, sourceLine, notesBlock, storeLinks)

/**
 * Сноски в отправке (решения пользователя 2026-10-11): [title] («Комментарии учёных:»), пустая строка, сноски
 * через пустую строку — слово, под ним пояснение. Слово из текста перевода [translation] — в кавычках [quote],
 * как на экране (`isQuotedTerm`); пояснение ко всему хадису («Различия передач») — без. Сносок нет — `null`.
 */
fun hadithNotesBlock(notes: List<HadithNote>, title: String, translation: String, quote: (String) -> String): String? =
    if (notes.isEmpty()) {
        null
    } else {
        (listOf(title) + notes.map { note ->
            val term = if (isQuotedTerm(note.term, translation)) quote(note.term) else note.term
            "$term\n${note.text}"
        }).joinToString("\n\n")
    }
