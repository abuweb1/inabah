import SwiftUI

/// Каркас экранов урока «Учимся читать» (Android `ReadingScaffold`): фон раздела, навбар с заголовком,
/// подзаголовком и кнопкой-глазом, загрузка курса, лист буквы поверх содержимого.
struct ReadingScreen<Content: View, Actions: View>: View {
    /// Шаг, к которому строится цепочка листа буквы (пройденные знаки).
    let stepNumber: Int
    let title: (ReadingCourse?) -> Text
    var subtitle: (ReadingCourse?) -> Text? = { _ in nil }
    var showsTransliterationToggle = true
    /// «Живые» ячейки экрана (волна, подсказка); `nil` - экран без них (проверка).
    var intro: ReadingScreenIntro? = nil
    @ViewBuilder var actions: Actions
    @ViewBuilder var content: (ReadingCourse, ReadingColors, LetterSheetOpener) -> Content

    @Environment(ReadingStore.self) private var store
    @Environment(\.theme) private var theme

    /// Номер буквы в листе; `nil` - лист закрыт.
    @State private var sheetLetter: Int?

    var body: some View {
        let colors = ReadingColors(palette: theme.palette)
        ZStack(alignment: .bottom) {
            switch store.course {
            case .idle, .loading:
                ProgressView()
                    .tint(theme.palette.onAccent)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .failed:
                ContentUnavailableView {
                    Label("content.error.title", systemImage: "exclamationmark.triangle")
                } description: {
                    Text("content.error.message")
                } actions: {
                    Button("content.error.retry") {
                        Task { await store.load() }
                    }
                }
                .foregroundStyle(theme.palette.onAccent)
            case .loaded(let course):
                content(course, colors, LetterSheetOpener { sheetLetter = $0.number })
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .tapIntroHost(intro, tint: colors.tint, cellOpened: sheetLetter != nil)
                    // Под открытым листом содержимое для VoiceOver скрыто.
                    .accessibilityHidden(sheetLetter != nil)
                // Лист - соседним слоем, не наложением на прокрутку: `ScrollView` заходит под панель
                // вкладок, а лист должен стоять над ней (своя панель iOS 18-25 отступ не даёт).
                sheet(course: course, colors: colors)
            }
        }
        .audioPlayerInset()
        .background { theme.gradients.makharijBackground.linear.ignoresSafeArea() }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { titleView }
            ToolbarItemGroup(placement: .topBarTrailing) {
                actions
                if showsTransliterationToggle {
                    TransliterationToggle()
                }
            }
        }
        .glassBackButton(tint: theme.palette.tabMakharij, header: nil)
        .task { await store.load() }
    }

    private var titleView: some View {
        VStack(spacing: 0) {
            title(store.course.value)
                .font(.headline.bold())
                .foregroundStyle(theme.palette.onAccent)
            if let subtitle = subtitle(store.course.value) {
                subtitle
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .lineLimit(1)
        .fixedTextSize()
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private func sheet(course: ReadingCourse, colors: ReadingColors) -> some View {
        ZStack(alignment: .bottom) {
            if let number = sheetLetter, let letter = course.letters.first(where: { $0.number == number }) {
                theme.palette.shadow.opacity(LetterSheet.scrimOpacity)
                    .ignoresSafeArea()
                    .contentShape(.rect)
                    .onTapGesture { sheetLetter = nil }
                    .accessibilityHidden(true)
                    .transition(.opacity)
                LetterSheet(course: course, letter: letter, stepNumber: stepNumber, colors: colors) {
                    sheetLetter = nil
                }
                .id(number)
                .transition(.move(edge: .bottom))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .animation(Motion.collapse, value: sheetLetter)
    }
}

/// Ключ надписи-подсказки экрана (`reading.intro.<key>`) и есть ли на нём увеличиваемые плитки.
struct ReadingScreenIntro: Equatable {
    let key: String
    var zoomable = false

    static func lesson(_ step: Int) -> Self { Self(key: "lesson.\(step)") }
    static func alphabet(_ step: Int) -> Self { Self(key: "alphabet.\(step)") }
    static func extra(_ step: Int, topic: Int?) -> Self { Self(key: "extra.\(step).\(topic.map(String.init) ?? "extra")") }
    static let words = Self(key: "words", zoomable: true)
    static let letterCard = Self(key: "letterCard", zoomable: true)
    static let zikr = Self(key: "zikr")
}

/// Открыть лист буквы - передаётся блокам урока (не через окружение: замыкание в окружении не хранится).
struct LetterSheetOpener {
    let open: (ReadingLetter) -> Void

    func callAsFunction(_ letter: ReadingLetter) { open(letter) }
}

/// Кнопка-глаз: скрыть / показать транскрипцию слогов и слов курса (`ReadingSettings.transliterationHidden`).
private struct TransliterationToggle: View {
    @Environment(ReadingSettings.self) private var settings

    var body: some View {
        ReadingHeaderButton(
            symbol: settings.transliterationHidden ? "eye.slash" : "eye",
            label: settings.transliterationHidden ? "makharij.translit.show" : "makharij.translit.hide"
        ) {
            settings.setTransliterationHidden(!settings.transliterationHidden)
        }
    }
}

/// Круглая кнопка-значок в навбаре курса: белый значок на стекле (своё стекло красит значок само).
struct ReadingHeaderButton: View {
    let symbol: String
    let label: LocalizedStringResource
    let action: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.chromeStyle) private var chromeStyle

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .fontWeight(.medium)
                .foregroundStyle(chromeStyle == .custom ? AnyShapeStyle(.foreground) : AnyShapeStyle(theme.palette.onAccent))
        }
        .headerIconButtonStyle(tint: theme.palette.tabMakharij, header: nil)
        .accessibilityLabel(Text(label))
        .fixedTextSize()
    }
}

/// Плитка-пергамент с буквой (Android `ParchmentTile`): лист буквы, карточка буквы.
struct ParchmentLetterTile: View {
    let text: String
    let size: Double
    let box: CGFloat

    @Environment(\.theme) private var theme

    var body: some View {
        Text(verbatim: text)
            .font(.arabic(size: size, bold: true))
            .foregroundStyle(theme.palette.parchmentText)
            .fixedSize()
            .frame(width: box, height: box)
            .background {
                RoundedRectangle(cornerRadius: Radius.card)
                    .fill(theme.gradients.parchment.linear)
            }
            .overlay {
                RoundedRectangle(cornerRadius: Radius.card)
                    .strokeBorder(theme.palette.parchmentAccent, lineWidth: Size.parchmentBorder)
            }
    }
}
