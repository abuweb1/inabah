import SwiftUI

/// Кнопки «А− / А+» размера арабского шрифта — для тулбаров экранов чтения.
struct FontSizeControls: ToolbarContent {
    let settings: ReadingSettings

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button {
                settings.decreaseArabicFontSize()
            } label: {
                Text("reading.fontSize.decrease.short")
            }
            .disabled(!settings.canDecreaseArabicFontSize)
            .accessibilityLabel(Text("reading.fontSize.decrease"))

            Button {
                settings.increaseArabicFontSize()
            } label: {
                Text("reading.fontSize.increase.short")
            }
            .disabled(!settings.canIncreaseArabicFontSize)
            .accessibilityLabel(Text("reading.fontSize.increase"))
        }
    }
}
