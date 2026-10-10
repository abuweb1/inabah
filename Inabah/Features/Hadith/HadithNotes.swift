import SwiftUI

/// Слово сноски взято из перевода — показывается в кавычках; иначе это пояснение ко всему хадису
/// («Различия передач») — без кавычек. Сравнение — как в `check-data` и на Android (`isQuotedTerm`):
/// без регистра, «ё» как «е», знаки — пробелы, только целые слова.
nonisolated enum HadithNoteTerm {
    static func isQuoted(_ term: String, in translation: String) -> Bool {
        let folded = fold(term)
        return !folded.isEmpty && " \(fold(translation)) ".contains(" \(folded) ")
    }

    private static func fold(_ text: String) -> String {
        let lowered = text.lowercased().replacingOccurrences(of: "ё", with: "е")
        let words = lowered.split { !($0.isLetter || $0.isNumber) }
        return words.joined(separator: " ")
    }
}

/// Сноски к переводу хадиса (решения пользователя 2026-10-10, `docs/06-data-structure.md`): справа «N ⌄»,
/// по нажатию плавно раскрывается список — пузыри в стиле перевода азкаров (полоса слева, курсив), но
/// в тон раздела хадисов (`noteStripe` / `noteFill` / `noteText`). Вид — как на Android (`HadithNotes.kt`).
/// Раскрытие — у страницы: при уходе на другой хадис сноски сворачиваются.
struct HadithNotes: View {
    let notes: [HadithNote]
    let translation: String
    let language: ContentLanguage
    @Binding var isExpanded: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HadithNotesToggle(count: notes.count, isExpanded: isExpanded) {
                withAnimation(Motion.collapse) { isExpanded.toggle() }
            }
            VStack(alignment: .leading, spacing: Spacing.s) {
                ForEach(Array(notes.enumerated()), id: \.offset) { _, note in
                    HadithNoteBubble(note: note, isQuoted: HadithNoteTerm.isQuoted(note.term, in: translation))
                }
            }
            .padding(.top, Spacing.xs)
            .environment(\.locale, Locale(identifier: language.rawValue))
            .collapsible(isExpanded: isExpanded)
        }
    }
}

/// Строка во всю ширину: справа число сносок и стрелка. На экране — только число (решение
/// пользователя 2026-10-10); VoiceOver читает «Сноски: N» и «Свёрнуто / Развёрнуто».
private struct HadithNotesToggle: View {
    let count: Int
    let isExpanded: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    /// Стрелка растёт с переводом. На Android значок 20 с полями внутри рамки; SF-шеврон заполняет
    /// кегль целиком — 14 по виду равен ему.
    private static let chevronSize: CGFloat = 14
    private static let halfTurn: Double = 180

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.xxs) {
                Spacer(minLength: 0)
                Text(count, format: .number)
                    .font(.content(.note, scale: scale, weight: .semibold))
                    .monospacedDigit()
                Image(systemName: "chevron.down")
                    .font(.system(size: Self.chevronSize * scale, weight: .medium))
                    .rotationEffect(.degrees(isExpanded ? Self.halfTurn : 0))
            }
            .foregroundStyle(theme.palette.onAccentSecondary)
            .frame(minHeight: Size.minTapTarget)
            .contentShape(.rect)
        }
        .buttonStyle(PressDimButtonStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("hadith.notes.toggle \(count)"))
        .accessibilityValue(Text(isExpanded ? "hadith.notes.expanded" : "hadith.notes.collapsed"))
        .accessibilityAddTraits(.isButton)
    }
}

/// Слово полужирным (из перевода — в кавычках), под ним пояснение — курсив цветом сносок.
private struct HadithNoteBubble: View {
    let note: HadithNote
    let isQuoted: Bool

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Group {
                if isQuoted {
                    Text("hadith.note.quote \(note.term)")
                } else {
                    Text(verbatim: note.term)
                }
            }
            .font(.content(.transliteration, scale: scale, weight: .semibold))
            Text(verbatim: note.text)
                .font(.content(.transliteration, scale: scale))
        }
        .foregroundStyle(theme.palette.noteText)
        .translationBubble(fill: theme.palette.noteFill, stripe: theme.palette.noteStripe)
    }
}
