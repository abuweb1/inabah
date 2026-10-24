package app.inabah.android.feature.azkar

import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.share.shareBlocks

/**
 * Текст «Поделиться» зикром — всё, что на карточке, в её порядке: раздел, арабский, «N раз»,
 * вступление `rawi` ([narratorLine], когда есть; без «Передал:» — 2026-10-11), транскрипция, перевод, источник; последним — ссылки на
 * приложение ([storeLinks], `storeLinksBlock`). Подписи — уже из ресурсов.
 */
fun zikrShareText(zikr: Zikr, sectionTitle: String, repetitions: String, narratorLine: String?, storeLinks: String?): String {
    val translation = zikr.translation
    return shareBlocks(
        sectionTitle, zikr.arabic, repetitions,
        // Как на экране: транскрипция, «Передаётся от…» сразу перед переводом.
        translation?.transliteration, narratorLine, translation?.text, translation?.source,
        storeLinks,
    )
}
