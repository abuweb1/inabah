import Testing
import UIKit
@testable import Inabah

@Suite("Дизайн-система")
struct DesignSystemTests {
    @Test("Каждый токен AssetColor есть в каталоге ассетов", arguments: AssetColor.allCases)
    func assetColorExists(_ color: AssetColor) {
        #expect(UIColor(named: color.rawValue, in: .main, compatibleWith: nil) != nil)
    }

    @Test("Шрифт Scheherazade New регистрируется из Bundle", arguments: [AppFont.arabicRegular, AppFont.arabicBold])
    func arabicFontIsAvailable(_ postScriptName: String) {
        AppFont.registerBundledFonts()
        #expect(UIFont(name: postScriptName, size: 21) != nil)
    }

    @Test("Арабско-индийские цифры без разделителей", arguments: [
        (1, "١"),
        (12, "١٢"),
        (40, "٤٠"),
        (1000, "١٠٠٠"),
    ])
    func arabicIndicDigits(number: Int, expected: String) {
        #expect(number.formatted(.arabicIndic) == expected)
    }
}
