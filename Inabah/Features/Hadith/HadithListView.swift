import SwiftUI

/// Список хадисов сборника: шапка прогресса и строки со статусами.
///
/// Тело экрана не читает отметки — это делают `HadithFeed` и `HadithListProgressHeader`:
/// отметка одного хадиса не перестраивает весь экран.
struct HadithListView: View {
    let collection: HadithCollection

    @Environment(HadithStore.self) private var store
    @Environment(\.theme) private var theme

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
                ToolbarItem(placement: .principal) { titleView }
            }
            .task { await store.load(collection) }
    }

    @ViewBuilder
    private var content: some View {
        switch store.state(of: collection) {
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
                    Task { await store.load(collection) }
                }
            }
            .foregroundStyle(theme.palette.onAccent)
        case .loaded(let hadiths):
            HadithFeed(hadiths: hadiths)
                .safeAreaInset(edge: .top, spacing: 0) {
                    HadithListProgressHeader(collection: collection, total: hadiths.count)
                }
        }
    }

    private var titleView: some View {
        VStack(spacing: 0) {
            Text(collection.title)
                .font(.headline)
            Text(verbatim: collection.arabicTitle)
                .font(.arabic(size: Layout.subtitleArabicSize))
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .foregroundStyle(theme.palette.onAccent)
        // Заголовок экрана не меняется с размером интерфейса.
        .fixedTextSize()
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private enum Layout {
        static let subtitleArabicSize: Double = 13
    }
}

/// Строки сборника. Единственная часть экрана, которая читает отметки: каждой строке
/// передаётся готовый статус.
private struct HadithFeed: View {
    let hadiths: [Hadith]

    @Environment(HadithProgress.self) private var progress

    var body: some View {
        ScrollView {
            // Высота строк не меняется — ленивый стек не строит десятки строк заранее.
            LazyVStack(spacing: 0) {
                ForEach(hadiths) { hadith in
                    NavigationLink(value: HadithRoute.detail(hadith.id)) {
                        HadithRow(hadith: hadith, status: progress.status(of: hadith.id))
                    }
                    .buttonStyle(PressDimButtonStyle())
                }
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.bottom, Spacing.m)
        }
    }
}

/// «Прочитано X из N · выучено M» и полоса прогресса под навбаром.
private struct HadithListProgressHeader: View {
    let collection: HadithCollection
    let total: Int

    @Environment(HadithProgress.self) private var progress
    @Environment(\.theme) private var theme

    var body: some View {
        let collectionProgress = progress.progress(in: collection, total: total)
        let summary = Text("hadith.list.progress \(collectionProgress.read) \(total) \(collectionProgress.memorized)")
        VStack(spacing: Spacing.xs) {
            HStack {
                summary
                Spacer()
                Text(collectionProgress.readFraction, format: .percent.precision(.fractionLength(0)).rounded(rule: .toNearestOrAwayFromZero))
                    .monospacedDigit()
            }
            .font(.caption)
            .foregroundStyle(theme.palette.onAccentSecondary)

            LinearProgressBar(
                fraction: collectionProgress.readFraction,
                track: theme.palette.track,
                fill: theme.palette.statusRead
            )
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.top, Spacing.xs)
        .padding(.bottom, Spacing.m)
        .background(theme.palette.hadithHeader)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(summary)
    }
}

#Preview {
    NavigationStack {
        HadithListView(collection: .nawawi)
    }
    .appEnvironment(.preview)
}
