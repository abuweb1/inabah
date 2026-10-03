import SwiftUI

/// Главная раздела «Хадисы». Пока заглушка — экраны хадисов делаются на следующем этапе.
struct HadithHomeView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        ComingSoonView(
            symbolName: "book.closed",
            message: "hadith.placeholder.message",
            background: theme.gradients.hadithBackground
        )
    }
}

#Preview {
    HadithHomeView()
}
