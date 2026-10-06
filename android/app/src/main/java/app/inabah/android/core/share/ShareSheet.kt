package app.inabah.android.core.share

import android.content.Context
import android.content.Intent

/**
 * Отправка обычным текстом. Без `EXTRA_TITLE`: заголовок уже первая строка текста — в превью окна
 * он повторялся дважды (эмулятор 2026-10-06).
 */
fun shareIntent(text: String): Intent =
    Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

/** Системное окно «Поделиться» — в нём и «Копировать» (Android 10+); разрешения не нужны. */
fun Context.shareText(text: String) {
    startActivity(Intent.createChooser(shareIntent(text), null))
}
