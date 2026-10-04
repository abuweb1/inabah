package app.inabah.android.core.formatting

private const val ARABIC_INDIC_ZERO = '٠'

/**
 * Число арабско-индийскими цифрами `٠–٩` без разделителей разрядов (`1000` → `١٠٠٠`).
 * Заменой символов, а не форматированием по локали: локаль окружения подставила бы латинские цифры.
 */
fun Int.toArabicIndicDigits(): String =
    toString().map { char -> if (char in '0'..'9') ARABIC_INDIC_ZERO + (char - '0') else char }
        .joinToString("")
