import SwiftUI

/// «О сборнике» — из шапки списка хадисов: название, составители, «О книге» и прогресс.
/// Тексты — `data/collections.json` (`HadithStore.loadInfo`), число хадисов — длина файла сборника.
/// Вид — как на Android (`docs/android/06-screens.md`, «„О сборнике“»).
struct CollectionAboutView: View {
    let collection: HadithCollection

    @Environment(HadithStore.self) private var store
    @Environment(\.theme) private var theme

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .audioPlayerInset()
            .background { theme.gradients.hadithBackground.linear.ignoresSafeArea() }
            .navigationTitle(Text("hadith.about.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(theme.palette.hadithHeader, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .glassBackButton(tint: theme.palette.tabHadith, header: theme.palette.hadithHeader)
            .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        switch (store.state(of: collection), store.infoState(of: collection)) {
        case (.loaded(let hadiths), .loaded(let info)):
            CollectionAboutForm(collection: collection, info: info, total: hadiths.count)
        case (.failed, _), (_, .failed):
            ContentUnavailableView {
                Label("content.error.title", systemImage: "exclamationmark.triangle")
            } description: {
                Text("content.error.message")
            } actions: {
                Button("content.error.retry") {
                    Task { await load() }
                }
            }
            .foregroundStyle(theme.palette.onAccent)
        default:
            ProgressView()
                .tint(theme.palette.onAccent)
        }
    }

    /// Сборник и сведения грузятся параллельно; загруженное повторно не грузится.
    private func load() async {
        async let hadiths: Void = store.load(collection)
        async let info: Void = store.loadInfo(collection)
        _ = await (hadiths, info)
    }
}

private struct CollectionAboutForm: View {
    let collection: HadithCollection
    let info: CollectionInfo
    let total: Int

    @Environment(\.theme) private var theme

    var body: some View {
        Form {
            Section {
                CollectionAboutHeader(collection: collection, total: total)
                    .listRowBackground(Color.clear)
            }

            if !info.authors.isEmpty {
                Section {
                    ForEach(info.authors, id: \.self) { author in
                        CollectionAuthorRow(author: author)
                            .settingsRow()
                    }
                } header: {
                    // Каталог не допускает plural без числа в тексте — две строки (на Android — plural).
                    Text(info.authors.count == 1 ? LocalizedStringResource("hadith.about.author") : "hadith.about.authors")
                }
            }

            if !info.about.isEmpty {
                Section {
                    CollectionAboutText(paragraphs: info.about, language: info.language)
                        .settingsRow()
                } header: {
                    Text("hadith.about.book")
                }
            }

            Section {
                CollectionAboutProgress(collection: collection, total: total)
                    .settingsRow()
            } header: {
                Text("hadith.about.hadiths")
            }
        }
        // Шапка — сразу под навбаром, как на Android: без верхнего поля списка.
        .contentMargins(.top, 0, for: .scrollContent)
        .settingsForm(background: theme.gradients.hadithBackground)
    }
}

/// Значок сборника, арабское и русское название, число хадисов — по центру, без карточки.
private struct CollectionAboutHeader: View {
    let collection: HadithCollection
    let total: Int

    @Environment(\.theme) private var theme

    @ScaledMetric(relativeTo: .body) private var iconSize: CGFloat = 44

    private static let arabicTitleSize: Double = 28

    var body: some View {
        VStack(spacing: Spacing.s) {
            Image(systemName: collection.symbolName)
                .resizable()
                .scaledToFit()
                .frame(width: iconSize, height: iconSize)
                .foregroundStyle(collection.iconColor(in: theme))
                .accessibilityHidden(true)
            ArabicText(
                text: collection.arabicTitle,
                size: Self.arabicTitleSize,
                color: theme.palette.onAccent,
                bold: true,
                alignment: .center
            )
            Text(collection.title)
                .font(.headline)
            Text(collection.cardMeta(count: total))
                .font(.caption)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.l)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

private struct CollectionAuthorRow: View {
    let author: CollectionAuthor

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xxxs) {
            Text(verbatim: author.name)
                .font(.body)
            if let years = author.years {
                Text(verbatim: years)
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .padding(.vertical, Spacing.xxs)
        .accessibilityElement(children: .combine)
    }
}

/// «О книге» — шрифтом переводов: растёт с «Размером переводов», а не интерфейса.
private struct CollectionAboutText: View {
    let paragraphs: [String]
    let language: ContentLanguage?

    @Environment(\.contentTextScale) private var scale
    @Environment(\.locale) private var interfaceLocale

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            // По номеру абзаца: одинаковые абзацы не спутаются (А14).
            ForEach(Array(paragraphs.enumerated()), id: \.offset) { _, paragraph in
                Text(verbatim: paragraph)
                    .font(.content(.translation, scale: scale))
                    .lineSpacing(ContentTextStyle.translationLineSpacing * scale)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.vertical, Spacing.xs)
        .environment(\.locale, language.map { Locale(identifier: $0.rawValue) } ?? interfaceLocale)
    }
}

/// «Прочитано: X из N · выучено: M»; отметки читает только эта строка.
private struct CollectionAboutProgress: View {
    let collection: HadithCollection
    let total: Int

    @Environment(HadithProgress.self) private var progress
    @Environment(\.theme) private var theme

    var body: some View {
        let collectionProgress = progress.progress(in: collection, total: total)
        Text("hadith.list.progress \(collectionProgress.read) \(total) \(collectionProgress.memorized)")
            .font(.subheadline)
            .foregroundStyle(theme.palette.onAccentSecondary)
    }
}

#Preview {
    NavigationStack {
        CollectionAboutView(collection: .nawawi)
    }
    .appEnvironment(.preview)
}
