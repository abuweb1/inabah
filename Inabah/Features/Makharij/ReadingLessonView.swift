import SwiftUI

/// Урок шага «Учимся читать». Пока - заготовка: навбар шага и «Урок готовится» (как Android у шага без
/// урока); содержимое урока - следующий этап переноса (docs/ios-port.md, «Урок»).
struct ReadingLessonView: View {
    let stepNumber: Int

    @Environment(ReadingStore.self) private var store
    @Environment(\.theme) private var theme

    var body: some View {
        let step = store.course.value?.step(stepNumber)
        NoticeCard(symbol: "hourglass", title: "makharij.lesson.soon.title", message: "makharij.lesson.soon.message")
            .padding(Spacing.xl)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .audioPlayerInset()
            .background { theme.gradients.makharijBackground.linear.ignoresSafeArea() }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    if let step { titleView(step) }
                }
            }
            .glassBackButton(tint: theme.palette.tabMakharij, header: nil)
            .task { await store.load() }
    }

    private func titleView(_ step: ReadingStep) -> some View {
        VStack(spacing: 0) {
            Text(step.displayTitle)
                .font(.headline.bold())
                .foregroundStyle(theme.palette.onAccent)
            if !step.subtitle.isEmpty {
                Text(verbatim: step.subtitle)
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .lineLimit(1)
        .fixedTextSize()
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

#Preview {
    NavigationStack {
        ReadingLessonView(stepNumber: 2)
    }
    .appEnvironment(.preview)
}
