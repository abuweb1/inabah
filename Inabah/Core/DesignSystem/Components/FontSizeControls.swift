import SwiftUI

/// Кнопки «А− / А+» размера арабского шрифта — для тулбаров экранов чтения.
/// Кегль меняется анимацией: текст и высота карточек перетекают плавно, без скачка ленты.
struct FontSizeControls: ToolbarContent {
    let settings: ReadingSettings

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button {
                withAnimation(Motion.fontSize) { settings.decreaseArabicFontSize() }
            } label: {
                Text("reading.fontSize.decrease.short")
            }
            .disabled(!settings.canDecreaseArabicFontSize)
            .accessibilityLabel(Text("reading.fontSize.decrease"))

            Button {
                withAnimation(Motion.fontSize) { settings.increaseArabicFontSize() }
            } label: {
                Text("reading.fontSize.increase.short")
            }
            .disabled(!settings.canIncreaseArabicFontSize)
            .accessibilityLabel(Text("reading.fontSize.increase"))
        }
    }
}
