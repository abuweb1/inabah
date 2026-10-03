import Foundation

nonisolated extension FormatStyle where Self == IntegerFormatStyle<Int> {
    /// Арабско-индийские цифры без разделителей разрядов: `12` → «١٢» (`toArNum` в прототипе).
    static var arabicIndic: IntegerFormatStyle<Int> {
        // Система счисления задана явно: у локали «ar» цифры по умолчанию латинские.
        IntegerFormatStyle<Int>(locale: Locale(identifier: "ar@numbers=arab")).grouping(.never)
    }
}
