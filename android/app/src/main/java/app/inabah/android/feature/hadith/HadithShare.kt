package app.inabah.android.feature.hadith

import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.share.shareBlocks

/**
 * Текст «Поделиться» хадисом — как на экране: «40 хадисов ан-Навави · Хадис 2», арабский одним
 * абзацем, «Передал: …», перевод, «Приводится: …». Подписи — уже из ресурсов; нет данных — `null`.
 */
fun hadithShareText(hadith: Hadith, header: String, narratorLine: String?, sourceLine: String?): String =
    shareBlocks(header, hadith.arabicDisplayText, narratorLine, hadith.translation?.text, sourceLine)
