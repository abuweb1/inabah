package app.inabah.android.core.content

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Лицензия стороннего ресурса, встроенного в приложение (iOS `LicenseDocument`): шрифты и значки.
 * [assetName] — файл в `assets/licenses/`. Порядок — как в iOS, Inter (шрифт интерфейса, только Android) — последним.
 */
@Serializable
enum class LicenseDocument(val assetName: String) {
    /** Арабский шрифт Scheherazade New — SIL Open Font License 1.1 (копирует `copySharedContent` из iOS). */
    Scheherazade("ScheherazadeNew-OFL.txt"),

    /** Значки Material Symbols — Apache License 2.0. */
    MaterialSymbols("MaterialSymbols-LICENSE.txt"),

    /** Шрифт интерфейса Inter — SIL Open Font License 1.1. */
    Inter("Inter-OFL.txt"),
}

/** Тексты лицензий из ассетов — для экрана лицензии; чтение не с главного потока. */
class LicenseTexts(
    private val source: ContentSource,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Текст для экрана (абзацы склеены, [reflowLicense]); файла нет или не читается — `null`, экран пустой,
     * как в iOS (`try?`). Наличие всех файлов в ассетах проверяет JVM-тест.
     */
    suspend fun text(document: LicenseDocument): String? = withContext(ioDispatcher) {
        try {
            source.open("$LICENSES_DIR/${document.assetName}").use { reflowLicense(it.bufferedReader().readText()) }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val LICENSES_DIR = "licenses"
    }
}

/**
 * Текст лицензии для экрана (iOS `LicenseDocument.reflowed`): строки внутри абзаца склеены — в файле они
 * разбиты по ~70 символов, и на узком экране получалась «лесенка». Пустая строка — граница абзаца;
 * абзацы с линейкой из «-» не трогаются.
 */
fun reflowLicense(text: String): String =
    text.replace("\r\n", "\n")
        .split("\n\n")
        .joinToString("\n\n") { paragraph ->
            val lines = paragraph.split("\n")
            val isRule = lines.any { line -> line.trim().let { it.isNotEmpty() && it.all { char -> char == '-' } } }
            // Пустые строки внутри абзаца (файл начинается с «\n», три перевода подряд) — без лишнего пробела в начале.
            if (isRule) paragraph else lines.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        }
