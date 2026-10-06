package app.inabah.android.core.content

import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LicenseTextsTest {
    private val assetsDir = File(requireNotNull(System.getProperty("inabah.assetsDir")))

    /** Как `AssetManager.open` в APK: Scheherazade копирует сборка, Inter и Material Symbols — в `src/main/assets`. */
    private val apkAssets = ContentSource { path ->
        listOf(File(assetsDir, path), File("src/main/assets", path)).firstOrNull { it.exists() }?.inputStream()
            ?: throw FileNotFoundException(path)
    }

    @Test
    fun `Строки абзаца склеиваются через пробел`() {
        assertEquals("Permission is hereby granted, free of charge.", reflowLicense("Permission is hereby\n  granted, free of charge."))
    }

    @Test
    fun `Пустая строка — граница абзаца`() {
        assertEquals("Первый абзац.\n\nВторой абзац.", reflowLicense("Первый\nабзац.\n\nВторой\nабзац."))
    }

    @Test
    fun `Пустая строка в начале абзаца не даёт пробела`() {
        // Так начинается Apache License: перевод строки, затем отступ.
        assertEquals("Apache License Version 2.0", reflowLicense("\n                Apache License\n      Version 2.0"))
    }

    @Test
    fun `Переводы строк Windows обрабатываются как обычные`() {
        assertEquals("a b\n\nc", reflowLicense("a\r\nb\r\n\r\nc"))
    }

    @Test
    fun `Абзац с линейкой из дефисов не склеивается`() {
        val rule = "PREAMBLE\n-----------------------------------------------------------"
        assertEquals("$rule\n\nThe goals", reflowLicense("$rule\n\nThe\ngoals"))
    }

    @Test
    fun `Каждая лицензия есть в ассетах и читается`() = runTest {
        val texts = LicenseTexts(apkAssets, StandardTestDispatcher(testScheduler))
        for (document in LicenseDocument.entries) {
            val text = assertNotNull(texts.text(document), document.assetName)
            assertTrue(text.length > MIN_LICENSE_LENGTH, document.assetName)
            // Склеено: в файлах строки по ~70 символов, на экране — абзацами.
            assertFalse(text.lines().all { it.length <= LINE_WIDTH }, document.assetName)
        }
    }

    @Test
    fun `Нет файла — пустой экран, без падения`() = runTest {
        val texts = LicenseTexts({ throw FileNotFoundException(it) }, StandardTestDispatcher(testScheduler))
        assertNull(texts.text(LicenseDocument.Inter))
    }

    private companion object {
        const val MIN_LICENSE_LENGTH = 1_000
        const val LINE_WIDTH = 100
    }
}
