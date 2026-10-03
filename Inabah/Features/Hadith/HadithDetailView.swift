import SwiftUI

/// Экран хадиса: страницы сборника листаются свайпом или кнопками ‹ › в навбаре.
///
/// Страницы — ленивый горизонтальный стек с постраничной прокруткой: строятся только видимая
/// и соседние. Номер текущей страницы — позиция прокрутки (`scrollPosition`). Лента строится
/// только для загруженного сборника — тогда начальная позиция применяется с первой раскладки.
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
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .audioPlayerInset()
            .background { theme.gradients.hadithBackground.linear.ignoresSafeArea() }
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(theme.palette.hadithHeader, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HadithPager(number: $currentNumber, count: store.hadiths(in: id.collection).count)
                }
                FontSizeControls(settings: settings)
            }
            .task { await store.load(id.collection) }
    }

    @ViewBuilder
    private var content: some View {
        switch store.state(of: id.collection) {
        case .idle, .loading:
            ProgressView()
                .tint(theme.palette.onAccent)
        case .failed:
            ContentUnavailableView {
                Label("content.error.title", systemImage: "exclamationmark.triangle")
            } description: {
                Text("content.error.message")
            } actions: {
                Button("content.error.retry") {
                    Task { await store.load(id.collection) }
                }
            }
            .foregroundStyle(theme.palette.onAccent)
        case .loaded(let hadiths):
            pages(hadiths)
        }
    }

    private func pages(_ hadiths: [Hadith]) -> some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(hadiths, id: \.number) { hadith in
                    HadithPageContainer(hadith: hadith)
                        .containerRelativeFrame(.horizontal)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $currentNumber)
        .scrollIndicators(.hidden)
    }
}

/// Страница с готовым статусом: только эта обёртка читает отметки, сама страница получает значение.
private struct HadithPageContainer: View {
    let hadith: Hadith

    @Environment(HadithProgress.self) private var progress

    var body: some View {
        HadithPage(hadith: hadith, status: progress.status(of: hadith.id))
    }
}

/// ‹ «Хадис 3 из 50» › в навбаре. Отдельная вьюха: смена страницы пересчитывает только её.
/// Пока сборник не загружен (`count == 0`), пейджер не показывается.
private struct HadithPager: View {
    @Binding var number: Int?
    let count: Int

    @Environment(\.theme) private var theme

    /// Шевроны визуально мельче, зона нажатия — полные 44 pt (`BareIconButtonStyle`).
    private static let chevronFont = Font.callout.weight(.semibold)

    var body: some View {
        if count > 0 {
            let current = min(max(number ?? 1, 1), count)
            HStack(spacing: 0) {
                Button {
                    go(to: current - 1)
                } label: {
                    Image(systemName: "chevron.backward")
                        .font(Self.chevronFont)
                }
                .disabled(current <= 1)
                .accessibilityLabel(Text("hadith.detail.previous"))

                counter(current)

                Button {
                    go(to: current + 1)
                } label: {
                    Image(systemName: "chevron.forward")
                        .font(Self.chevronFont)
                }
                .disabled(current >= count)
                .accessibilityLabel(Text("hadith.detail.next"))
            }
            .buttonStyle(BareIconButtonStyle(foreground: theme.palette.onAccent))
        }
    }

    /// «3 из 50». Если в навбаре тесно (узкий экран, крупный шрифт) — шрифт немного ужимается.
    /// `ViewThatFits` здесь не годится: навбар предлагает ширину нестабильно, и подпись
    /// переключалась на короткую форму даже там, где полная помещается.
    private func counter(_ current: Int) -> some View {
        Text("hadith.detail.counter \(current) \(count)")
            .minimumScaleFactor(0.75)
            .font(.footnote.weight(.semibold))
        .monospacedDigit()
        .lineLimit(1)
        .foregroundStyle(theme.palette.onAccent)
        .padding(.horizontal, Spacing.m)
        .padding(.vertical, Spacing.xxs)
        .background(theme.palette.track, in: .capsule)
        .contentTransition(.numericText(value: Double(current)))
        .animation(Motion.highlight, value: current)
    }

    private func go(to target: Int) {
        withAnimation(Motion.collapse) {
            number = target
        }
    }
}

#Preview {
    NavigationStack {
        HadithDetailView(id: HadithID(collection: .nawawi, number: 1))
    }
    .appEnvironment(.preview)
}
