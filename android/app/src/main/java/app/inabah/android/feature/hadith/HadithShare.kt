package app.inabah.android.feature.hadith

import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithNote
import app.inabah.android.core.share.shareBlocks

/**
 * Текст «Поделиться» хадисом — как на экране: «40 хадисов ан-Навави · Хадис 2», арабский одним
 * абзацем, «Передал: …», перевод, «Приводится: …», сноски ([notesBlock]); последним — ссылки на
 * приложение ([storeLinks]). Подписи — уже из ресурсов; нет данных — `null`.
 */
fun hadithShareText(
    hadith: Hadith,
    header: String,
    narratorLine: String?,
    sourceLine: String?,
    notesBlock: String?,
    storeLinks: String?,
): String = shareBlocks(header, hadith.arabicDisplayText, narratorLine, hadith.translation?.text, sourceLine, notesBlock, storeLinks)

/** «Сноски:» и по строке на сноску ([line] — «слово — пояснение» из ресурсов); сносок нет — `null`. */
fun hadithNotesBlock(notes: List<HadithNote>, title: String, line: (HadithNote) -> String): String? =
    if (notes.isEmpty()) null else (listOf(title) + notes.map(line)).joinToString("\n")
