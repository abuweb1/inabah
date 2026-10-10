import Foundation
import Observation

/// Тексты сборников хадисов. Отметки «прочитан/выучен» — отдельно, в `HadithProgress`:
/// тексты неизменны, а отметки сохраняются между запусками.
@Observable
final class HadithStore {
    @ObservationIgnored private let repository: any ContentRepository
    private(set) var collections: [HadithCollection: Loadable<[Hadith]>] = [:]
    /// Сведения для «О сборнике» (`collections.json`) — грузятся по запросу экрана.
    private(set) var infos: [HadithCollection: Loadable<CollectionInfo>] = [:]

    init(repository: any ContentRepository) {
        self.repository = repository
    }

    func state(of collection: HadithCollection) -> Loadable<[Hadith]> {
        collections[collection] ?? .idle
    }

    func hadiths(in collection: HadithCollection) -> [Hadith] {
        state(of: collection).value ?? []
    }

    /// Загружает сборник, если он ещё не загружен и не загружается. Повторный вызов после ошибки — повторная попытка.
    func load(_ collection: HadithCollection) async {
        guard !state(of: collection).isLoadingOrLoaded else { return }
        collections[collection] = .loading
        do {
            collections[collection] = .loaded(try await repository.hadiths(in: collection))
        } catch let error as ContentError {
            collections[collection] = .failed(error)
        } catch {
            collections[collection] = .failed(.unknown(String(describing: error)))
        }
    }

    func infoState(of collection: HadithCollection) -> Loadable<CollectionInfo> {
        infos[collection] ?? .idle
    }

    /// Загружает сведения о сборнике; повторный вызов после ошибки — повторная попытка.
    func loadInfo(_ collection: HadithCollection) async {
        guard !infoState(of: collection).isLoadingOrLoaded else { return }
        infos[collection] = .loading
        do {
            infos[collection] = .loaded(try await repository.collectionInfo(collection))
        } catch let error as ContentError {
            infos[collection] = .failed(error)
        } catch {
            infos[collection] = .failed(.unknown(String(describing: error)))
        }
    }

    func loadAll() async {
        for collection in HadithCollection.allCases {
            await load(collection)
        }
    }
}
