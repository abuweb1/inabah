import SwiftUI

/// Кнопки «А− / А+» размера арабского шрифта — для тулбаров экранов чтения.
/// Кегль меняется анимацией: текст и высота карточек перетекают плавно, без скачка ленты.
///
/// `compact` — одна капсула с кнопками своей ширины вместо группы системных элементов
/// (у каждого — поле около 44 pt): рядом с ещё одной кнопкой справа на узких iPhone (390 pt)
/// группа не помещалась и уходила в меню «…».
struct FontSizeControls: ToolbarContent {
    let settings: ReadingSettings
    var compact = false

    private static let compactButtonWidth: CGFloat = 38

    var body: some ToolbarContent {
        if compact {
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 0) {
                    decrease.frame(width: Self.compactButtonWidth, height: Size.minTapTarget)
                    increase.frame(width: Self.compactButtonWidth, height: Size.minTapTarget)
                }
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                decrease
                increase
            }
        }
    }

    private var decrease: some View {
        Button {
            withAnimation(Motion.fontSize) { settings.decreaseArabicFontSize() }
        } label: {
            Text("reading.fontSize.decrease.short")
        }
        .disabled(!settings.canDecreaseArabicFontSize)
        .accessibilityLabel(Text("reading.fontSize.decrease"))
        // Кнопки навбара не меняются с размером интерфейса.
        .fixedTextSize()
    }

    private var increase: some View {
        Button {
            withAnimation(Motion.fontSize) { settings.increaseArabicFontSize() }
        } label: {
            Text("reading.fontSize.increase.short")
        }
        .disabled(!settings.canIncreaseArabicFontSize)
        .accessibilityLabel(Text("reading.fontSize.increase"))
        .fixedTextSize()
    }
}
