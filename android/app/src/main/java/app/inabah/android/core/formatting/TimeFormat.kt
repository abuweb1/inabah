package app.inabah.android.core.formatting

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Часы системы — 24-часовые? Контекст приложения, не активности: у активности язык подменён на язык
 * интерфейса (`AppLocale`), и без явной настройки 12/24 формат брался бы из него, а не из системы
 * (en-US — 12 часов). Переключение 12/24 конфигурацию не меняет — подхватывается при следующей
 * перерисовке экрана.
 */
@Composable
fun isSystem24HourFormat(): Boolean = DateFormat.is24HourFormat(LocalContext.current.applicationContext)
