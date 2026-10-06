package app.inabah.android.feature.azkar

import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.share.shareBlocks

/**
 * Текст «Поделиться» зикром — всё, что на карточке, в её порядке: раздел, арабский, «N раз»,
 * транскрипция, перевод, источник. Подписи ([sectionTitle], [repetitions]) — уже из ресурсов.
 */
fun zikrShareText(zikr: Zikr, sectionTitle: String, repetitions: String): String {
    val translation = zikr.translation
    return shareBlocks(sectionTitle, zikr.arabic, repetitions, translation?.transliteration, translation?.text, translation?.source)
}
