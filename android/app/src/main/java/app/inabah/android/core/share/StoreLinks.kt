package app.inabah.android.core.share

/**
 * Страницы приложения в магазинах — последним блоком текста «Поделиться». На обеих платформах — обе
 * ссылки (решение пользователя 2026-10-06; Google Play пока нет — сейчас только App Store, и на Android).
 */
object StoreLinks {
    /** Apple ID из App Store Connect; в iOS — `AboutLinks.appStore`. */
    const val APP_STORE = "https://apps.apple.com/app/id6819638881"

    /** Google Play — пока нет (APK ставится вручную); появится — вписать сюда, строка добавится сама. */
    val GOOGLE_PLAY: String? = null
}

/**
 * Одна ссылка — без подписи (как в iOS, решение пользователя); две — по строке с названием магазина
 * («App Store: …», «Google Play: …» — бренды, не переводятся), иначе не понять, какая чья.
 */
fun storeLinksBlock(appStore: String? = StoreLinks.APP_STORE, googlePlay: String? = StoreLinks.GOOGLE_PLAY): String? =
    when {
        appStore != null && googlePlay != null -> "App Store: $appStore\nGoogle Play: $googlePlay"
        else -> appStore ?: googlePlay
    }
