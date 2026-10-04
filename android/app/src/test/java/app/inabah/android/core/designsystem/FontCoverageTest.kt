package app.inabah.android.core.designsystem

import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * Каждый символ текстов приложения есть во встроенных шрифтах: иначе Android подставил бы
 * системный шрифт (а его пользователь мог заменить), и вид зависел бы от устройства.
 * Интерфейс — Inter + подстановка Scheherazade (InterFont.kt), арабский текст — Scheherazade.
 */
class FontCoverageTest {
    private val fontDir = File("src/main/res/font")
    private val assetsDir = File(requireNotNull(System.getProperty("inabah.assetsDir")))

    private val inter = codepoints(File(fontDir, "inter_variable.ttf")) intersect codepoints(File(fontDir, "inter_variable_italic.ttf"))
    private val scheherazade = codepoints(File(fontDir, "scheherazade_new_regular.ttf")) intersect
        codepoints(File(fontDir, "scheherazade_new_bold.ttf"))

    @Test
    fun `Тексты интерфейса и переводы покрыты Inter и Scheherazade`() {
        val text = buildString {
            appendData { key -> key != "arabic" && key != "audio" }
            append(File("src/main/res/values/strings.xml").readText().replace(Regex("<[^>]+>"), ""))
        }
        assertEquals(emptyList(), missing(text, inter + scheherazade))
    }

    @Test
    fun `Арабский текст покрыт Scheherazade`() {
        val text = buildString { appendData { key -> key == "arabic" } }
        assertTrue(text.isNotEmpty())
        assertEquals(emptyList(), missing(text, scheherazade))
    }

    /** Символы без глифа: «U+XXXX символ». Пробелы и управляющие не считаются. */
    private fun missing(text: String, covered: Set<Int>): List<String> =
        text.codePoints().toArray().toSortedSet()
            .filter { it > ' '.code && it !in covered && Character.getType(it) != Character.FORMAT.toInt() }
            .map { "U+%04X %s".format(it, String(Character.toChars(it))) }

    private fun StringBuilder.appendData(include: (key: String) -> Boolean) {
        File(assetsDir, "data").listFiles { file -> file.extension == "json" }.orEmpty().forEach { file ->
            collect(Json.parseToJsonElement(file.readText()), key = "", include = include)
        }
    }

    private fun StringBuilder.collect(element: JsonElement, key: String, include: (String) -> Boolean) {
        when (element) {
            is JsonObject -> element.forEach { (k, v) -> collect(v, k, include) }
            is JsonArray -> element.forEach { collect(it, key, include) }
            is JsonPrimitive -> if (element.isString && include(key)) append(element.content)
        }
    }

    /** Кодовые точки из таблицы `cmap` (форматы 4 и 12). */
    private fun codepoints(file: File): Set<Int> {
        val buffer = ByteBuffer.wrap(file.readBytes())
        val tables = (0 until buffer.getShort(4).toUShortInt()).associate { i ->
            val record = 12 + 16 * i
            String(ByteArray(4) { buffer.get(record + it) }) to buffer.getInt(record + 8)
        }
        val cmap = requireNotNull(tables["cmap"]) { "нет cmap в ${file.name}" }
        val result = mutableSetOf<Int>()
        for (i in 0 until buffer.getShort(cmap + 2).toUShortInt()) {
            val subtable = cmap + buffer.getInt(cmap + 4 + 8 * i + 4)
            when (buffer.getShort(subtable).toUShortInt()) {
                4 -> {
                    val segments = buffer.getShort(subtable + 6).toUShortInt() / 2
                    for (s in 0 until segments) {
                        val end = buffer.getShort(subtable + 14 + 2 * s).toUShortInt()
                        val start = buffer.getShort(subtable + 16 + 2 * segments + 2 * s).toUShortInt()
                        if (start != 0xFFFF) result.addAll(start..end)
                    }
                }
                12 -> {
                    val groups = buffer.getInt(subtable + 12)
                    for (g in 0 until groups) {
                        val group = subtable + 16 + 12 * g
                        result.addAll(buffer.getInt(group)..buffer.getInt(group + 4))
                    }
                }
            }
        }
        return result
    }

    private fun Short.toUShortInt(): Int = toInt() and 0xFFFF
}
