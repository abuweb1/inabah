package app.inabah.android.feature.azkar

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.share.shareBlocks
import app.inabah.android.core.share.shareText
import app.inabah.android.core.share.storeLinksBlock

// «Сделать напоминание» (Android-first, 2026-10-06; для iOS — docs/02-screen-azkar.md): та же отправка,
// что «Поделиться», но заготовленный текст — как рассылка пользователя в WhatsApp, отдельно для утренних
// и вечерних. Без звёздочек (жирный WhatsApp в Telegram остался бы символами — решение пользователя).

/** Слова Абу ад-Дарды по-арабски (Ахмад в «аз-Зухд», 726) — текст пользователя, не переводится. */
private const val ABU_AD_DARDA_ARABIC = "إِنَّ الَّذِينَ أَلْسِنَتُهُمْ رَطْبَةٌ بِذِكْرِ اللَّهِ يَدْخُلُ الْجَنَّةَ وَهُوَ يَضْحَكُ"

/** Черта перед временем чтения — как в рассылке. */
private const val SEPARATOR = "______________________"

/**
 * Текст напоминания: заголовок, призыв, слова Абу ад-Дарды (вступление, арабский, перевод, источник —
 * по строке), черта и лучшее время чтения, последним — ссылки на приложение. Подписи — уже из ресурсов.
 */
fun azkarReminderText(
    title: String,
    call: String,
    quoteIntro: String,
    quoteTranslation: String,
    quoteSource: String,
    bestTime: String,
    storeLinks: String?,
): String = shareBlocks(
    title,
    call,
    listOf(quoteIntro, ABU_AD_DARDA_ARABIC, quoteTranslation, quoteSource).joinToString("\n"),
    "$SEPARATOR\n$bestTime",
    storeLinks,
)

/** «Сделать напоминание» раздела: подписи — из ресурсов в композиции, отправка — по нажатию. */
@Composable
fun azkarReminderAction(section: AzkarSection): () -> Unit {
    val context = LocalContext.current
    val text = azkarReminderText(
        title = stringResource(section.reminderTitle),
        call = stringResource(section.reminderCall),
        quoteIntro = stringResource(R.string.share_text_reminder_quote_intro),
        quoteTranslation = stringResource(R.string.share_text_reminder_quote_translation),
        quoteSource = stringResource(R.string.share_text_reminder_quote_source),
        bestTime = stringResource(section.reminderBestTime),
        storeLinks = storeLinksBlock(),
    )
    return { context.shareText(text) }
}

@get:StringRes
private val AzkarSection.reminderTitle: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.share_text_reminder_morning_title
        AzkarSection.Evening -> R.string.share_text_reminder_evening_title
    }

@get:StringRes
private val AzkarSection.reminderCall: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.share_text_reminder_morning_call
        AzkarSection.Evening -> R.string.share_text_reminder_evening_call
    }

@get:StringRes
private val AzkarSection.reminderBestTime: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.share_text_reminder_morning_time
        AzkarSection.Evening -> R.string.share_text_reminder_evening_time
    }
