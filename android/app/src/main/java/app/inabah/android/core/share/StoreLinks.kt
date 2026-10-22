package app.inabah.android.core.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.inabah.android.R

/**
 * Страницы приложения в магазинах — последним блоком текста «Поделиться». На обеих платформах — обе
 * ссылки (решения пользователя 2026-10-06 и 2026-10-20).
 */
object StoreLinks {
    /** Apple ID из App Store Connect; в iOS — `AboutLinks.appStore`. */
    const val APP_STORE = "https://apps.apple.com/app/id6819638881"

    /**
     * Страница в Google Play по `applicationId` (2026-10-20, решение пользователя: ставить уже сейчас — откроется,
     * когда приложение выйдет из закрытого теста).
     */
    const val GOOGLE_PLAY = "https://play.google.com/store/apps/details?id=app.inabah.android"
}

/**
 * Ссылки под подписями платформ (решение пользователя 2026-10-20): «Для iPhone», под ней App Store, пустая строка,
 * «Для Android», под ней Google Play. Одна ссылка — без подписи.
 */
fun storeLinksBlock(
    iphoneLabel: String,
    androidLabel: String,
    appStore: String? = StoreLinks.APP_STORE,
    googlePlay: String? = StoreLinks.GOOGLE_PLAY,
): String? = when {
    appStore != null && googlePlay != null -> "$iphoneLabel\n$appStore\n\n$androidLabel\n$googlePlay"
    else -> appStore ?: googlePlay
}

/** [storeLinksBlock] с подписями из ресурсов. */
@Composable
fun storeLinksBlock(): String? = storeLinksBlock(
    iphoneLabel = stringResource(R.string.share_text_store_iphone),
    androidLabel = stringResource(R.string.share_text_store_android),
)
