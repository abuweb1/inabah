import SwiftUI

/// Группа «Избранного»: сборник и его отмеченные хадисы (или состояние загрузки).
nonisolated struct FavoritesGroup: Hashable, Sendable {
    nonisolated enum Content: Hashable, Sendable {
        case loading
        case failed
        case hadiths([Hadith])
    }

    let collection: HadithCollection
    let content: Content

    /// Группы «Избранного» — как на Android (`favoritesGroups`): сборники в порядке главной и только
    /// с закладками, хадисы — по номеру; закладки на номера, которых в сборнике нет, скрыты, и
    /// сборник без видимых хадисов группы не получает.
    static func make(
        order: [HadithCollection],
        favorites: [HadithCollection: Set<Int>],
        states: [HadithCollection: Loadable<[Hadith]>]
    ) -> [FavoritesGroup] {
        order.compactMap { collection in
            guard let numbers = favorites[collection], !numbers.isEmpty else { return nil }
            switch states[collection] ?? .idle {
            case .idle, .loading:
                return FavoritesGroup(collection: collection, content: .loading)
            case .failed:
                return FavoritesGroup(collection: collection, content: .failed)
            case .loaded(let hadiths):
                let marked = hadiths.filter { numbers.contains($0.number) }.sorted { $0.number < $1.number }
                return marked.isEmpty ? nil : FavoritesGroup(collection: collection, content: .hadiths(marked))
            }
        }
    }
}

/// «Избранное» — хадисы с закладкой из всех сборников, группами по сборникам. Убрать хадис
/// можно только закладкой на его экране; хадис открывается поверх, «назад» ведёт сюда.
struct FavoritesView: View {
    @Environment(HadithStore.self) private var store
    @Environment(HadithFavorites.self) private var favorites
    @Environment(HadithCollectionOrder.self) private var order
    @Environment(\.theme) private var theme

    var body: some View {
        let groups = FavoritesGroup.make(order: order.collections, favorites: favorites.numbers, states: store.collections)
        content(groups)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .audioPlayerInset()
            .background { theme.gradients.hadithBackground.linear.ignoresSafeArea() }
            .navigationTitle(Text("hadith.favorites.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(theme.palette.hadithHeader, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .task(id: favorites.numbers.keys.sorted { $0.rawValue < $1.rawValue }) { await load() }
    }

    @ViewBuilder
    private func content(_ groups: [FavoritesGroup]) -> some View {
        if groups.isEmpty {
            NoticeCard(
                symbol: "bookmark",
                title: "hadith.favorites.empty.title",
                message: "hadith.favorites.empty.message"
            )
            .padding(.horizontal, Spacing.xl)
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(groups, id: \.collection) { group in
                        FavoritesGroupView(group: group)
                    }
                }
                .padding(.horizontal, Spacing.xl)
                .padding(.bottom, Spacing.m)
            }
        }
    }

    /// Грузятся только сборники с закладками; загруженные повторно не грузятся.
    private func load() async {
        for collection in order.collections where !(favorites.numbers[collection]?.isEmpty ?? true) {
            await store.load(collection)
        }
    }
}

private struct FavoritesGroupView: View {
    let group: FavoritesGroup

    @Environment(HadithStore.self) private var store
    @Environment(HadithProgress.self) private var progress
    @Environment(\.theme) private var theme

    var body: some View {
        Text(group.collection.title)
            .font(.headline)
            .foregroundStyle(theme.palette.onAccent)
            .padding(.top, Spacing.xl)
            .padding(.bottom, Spacing.xs)
            .accessibilityAddTraits(.isHeader)
        switch group.content {
        case .loading:
            ProgressView()
                .tint(theme.palette.onAccent)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.xl)
        case .failed:
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("content.error.title")
                    .font(.subheadline.weight(.semibold))
                Button("content.error.retry") {
                    Task { await store.load(group.collection) }
                }
            }
            .foregroundStyle(theme.palette.onAccent)
            .padding(.vertical, Spacing.s)
        case .hadiths(let hadiths):
            ForEach(hadiths) { hadith in
                NavigationLink(value: HadithRoute.detail(hadith.id)) {
                    HadithRow(hadith: hadith, status: progress.status(of: hadith.id))
                }
                .buttonStyle(PressDimButtonStyle())
            }
        }
    }
}

#Preview {
    NavigationStack {
        FavoritesView()
    }
    .appEnvironment(.preview)
}
