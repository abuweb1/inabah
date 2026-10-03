import SwiftUI

/// Экран хадиса: страницы сборника листаются свайпом или кнопками ‹ › в навбаре.
///
/// Страницы — ленивый горизонтальный стек с постраничной прокруткой: строятся только видимая
/// и соседние. Номер текущей страницы — позиция прокрутки (`scrollPosition`).
struct HadithDetailView: View {
    let id: HadithID

    @Environment(HadithStore.self) private var store
    @Environment(ReadingSettings.self) private var settings
    @Environment(\.theme) private var theme

    @State private var currentNumber: Int?

    init(id: HadithID) {
        self.id = id
        // Начальная страница — открытый хадис; дальше позицию ведёт прокрутка.
        _currentNumber = State(initialValue: id.number)
    }

    var body: some View {
        let hadiths = store.hadiths(in: id.collection)
        pages(hadiths)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .audioPlayerInset()
            .background { theme.gradients.hadithBackground.linear.ignoresSafeArea() }
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(theme.palette.hadithHeader, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .principal) { pager(count: hadiths.count) }
                FontSizeControls(settings: settings)
            }
            .task { await store.load(id.collection) }
    }

    private func pages(_ hadiths: [Hadith]) -> some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(hadiths) { hadith in
                    HadithPage(hadith: hadith)
                        .containerRelativeFrame(.horizontal)
                        .id(hadith.number)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $currentNumber)
        .scrollIndicators(.hidden)
    }

    /// ‹ «Хадис 3 из 50» ›
    private func pager(count: Int) -> some View {
        let number = currentNumber ?? id.number
        return HStack(spacing: Spacing.s) {
            Button {
                go(to: number - 1)
            } label: {
                Image(systemName: "chevron.left")
            }
            .disabled(number <= 1)
            .accessibilityLabel(Text("hadith.detail.previous"))

            Text("hadith.detail.counter \(number) \(count)")
                .font(.footnote.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(theme.palette.onAccent)
                .padding(.horizontal, Spacing.m)
                .padding(.vertical, Spacing.xxs)
                .background(theme.palette.track, in: .capsule)
                .contentTransition(.numericText(value: Double(number)))

            Button {
                go(to: number + 1)
            } label: {
                Image(systemName: "chevron.right")
            }
            .disabled(number >= count)
            .accessibilityLabel(Text("hadith.detail.next"))
        }
        .fontWeight(.semibold)
        .tint(theme.palette.onAccent)
    }

    private func go(to number: Int) {
        withAnimation(Motion.collapse) {
            currentNumber = number
        }
    }
}

#Preview {
    NavigationStack {
        HadithDetailView(id: HadithID(collection: .nawawi, number: 1))
    }
    .appEnvironment(.preview)
}
