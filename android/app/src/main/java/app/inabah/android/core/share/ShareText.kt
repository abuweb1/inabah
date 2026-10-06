package app.inabah.android.core.share

// «Поделиться» зикром и хадисом (Android-first, 2026-10-06; для iOS — docs/02-screen-azkar.md,
// docs/05-screen-hadith-detail.md): только текст, «Копировать» — в системном окне (ShareSheet.kt).

/**
 * Текст для отправки: блоки через пустую строку в порядке экрана; отсутствующие и пустые пропускаются.
 * Последний блок — ссылки на приложение (`storeLinksBlock`).
 */
fun shareBlocks(vararg blocks: String?): String =
    blocks.mapNotNull { block -> block?.trim()?.takeIf(String::isNotEmpty) }.joinToString(BLOCK_SEPARATOR)

private const val BLOCK_SEPARATOR = "\n\n"
