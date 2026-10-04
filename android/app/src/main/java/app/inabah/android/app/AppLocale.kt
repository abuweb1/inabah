package app.inabah.android.app

import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

// Язык интерфейса (как в iOS: язык приложения — одна из его локализаций, не обязательно язык системы).
// Строки есть только на русском (`res/values`), но склонение («16 зикров») и формат чисел («13 %») Android
// берёт из языка конфигурации: при английской системе выходило «16 зикра» и «13%». Язык переводов
// азкаров и хадисов выбирается отдельно, по языкам системы (`AppContainer.systemLanguageTags`).

/** Языки, на которые переведён интерфейс. Новый перевод строк — добавить сюда его код. */
internal val SupportedUiLanguages = listOf("ru")

private const val DEFAULT_UI_LANGUAGE = "ru"

/** Первый язык системы из [supported]; ни одного — русский. Регион не нужен: строки без региональных вариантов. */
internal fun uiLanguage(system: List<Locale>, supported: List<String> = SupportedUiLanguages): Locale {
    val language = system.firstOrNull { it.language in supported }?.language ?: DEFAULT_UI_LANGUAGE
    return Locale.forLanguageTag(language)
}

/** Поправка конфигурации экрана: только язык, остальное — как у системы. */
internal fun uiLocaleOverride(system: Configuration): Configuration {
    val locales = system.locales
    val language = uiLanguage((0 until locales.size()).map { locales[it] })
    return Configuration().apply { setLocales(LocaleList(language)) }
}
