import SwiftUI
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

    @Test("Полоса фигуры: высота от края, пустая при нуле, не выше фигуры", arguments: [
        (VerticalEdge.top, CGFloat(3), CGFloat(0), CGRect(x: 0, y: 0, width: 100, height: 3)),
        (VerticalEdge.bottom, CGFloat(0), CGFloat(0.25), CGRect(x: 0, y: 30, width: 100, height: 10)),
        (VerticalEdge.bottom, CGFloat(0), CGFloat(2), CGRect(x: 0, y: 0, width: 100, height: 40)),
    ])
    func shapeBand(edge: VerticalEdge, points: CGFloat, fraction: CGFloat, expected: CGRect) {
        let band = ShapeBand(base: Rectangle(), edge: edge, points: points, fraction: fraction)
        #expect(band.path(in: CGRect(x: 0, y: 0, width: 100, height: 40)).boundingRect == expected)
    }

    @Test("Полоса фигуры нулевой высоты пуста")
    func emptyShapeBand() {
        let band = ShapeBand(base: RoundedRectangle(cornerRadius: 8), edge: .bottom)
        #expect(band.path(in: CGRect(x: 0, y: 0, width: 100, height: 40)).isEmpty)
    }
}
