package app.inabah.android.core.share

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** «Поделиться» отправляет обычный текст — его принимает любой мессенджер и «Копировать». */
@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @Test
    fun sendsPlainTextWithoutSeparateTitle() {
        val text = "Утренние азкары\n\nسُبْحَانَ اللَّهِ"

        val intent = shareIntent(text)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(text, intent.getStringExtra(Intent.EXTRA_TEXT))
        // Заголовок — первая строка текста; отдельный повторялся бы в превью окна.
        assertNull(intent.getStringExtra(Intent.EXTRA_TITLE))
    }
}
