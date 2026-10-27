import Foundation
import Testing
@testable import Inabah

/// «Обновить»: новая версия в App Store — по запросу к iTunes Lookup не чаще раза в сутки (2026-10-27).
/// Сети в тестах нет: ответ магазина подставляется.
@Suite("Новая версия в App Store")
struct AppStoreUpdateSourceTests {
    private let storage: IsolatedDefaults

    /// Каждому источнику — свой экземпляр того же набора: актор забирает его себе.
    private static func open(_ suiteName: String) -> UserDefaults {
        UserDefaults(suiteName: suiteName) ?? UserDefaults()
    }

    init() throws {
        storage = try IsolatedDefaults("AppStoreUpdateSourceTests")
    }

    /// Магазин-подделка: отдаёт заданный ответ и считает запросы.
    private actor FakeStore {
        var response: Result<Data, any Error>
        private(set) var requests: [URL] = []

        init(_ response: Result<Data, any Error>) { self.response = response }

        func fetch(_ url: URL) throws -> Data {
            requests.append(url)
            return try response.get()
        }

        func respond(_ response: Result<Data, any Error>) { self.response = response }
    }

    private struct Offline: Error {}

    private static func lookup(version: String, minimumOS: String = "18.0") -> Result<Data, any Error> {
        .success(Data("""
        {"resultCount":1,"results":[{"version":"\(version)","minimumOsVersion":"\(minimumOS)","bundleId":"app.inabah"}]}
        """.utf8))
    }

    private static let start = Date(timeIntervalSinceReferenceDate: 800_000_000)

    private func source(
        _ store: FakeStore,
        current: String = "1.2.0",
        osVersion: String = "18.5",
        now: Date = start,
        region: String? = "RU"
    ) -> AppStoreUpdateSource {
        AppStoreUpdateSource(
            currentVersion: current,
            osVersion: osVersion,
            region: region,
            defaults: Self.open(storage.suiteName),
            now: { now },
            fetch: { try await store.fetch($0) }
        )
    }

    @Test("В магазине новее — обновление с номером версии")
    func newerVersion() async {
        let store = FakeStore(Self.lookup(version: "1.3.0"))
        #expect(await source(store).availableUpdate() == AvailableUpdate(version: "1.3.0"))
    }

    @Test("Та же, старше или не версия — обновления нет", arguments: ["1.2.0", "1.1.9", "1.3", "beta"])
    func notNewer(storeVersion: String) async {
        let store = FakeStore(Self.lookup(version: storeVersion))
        #expect(await source(store).availableUpdate() == nil)
    }

    @Test("Новая версия требует iOS новее устройства — не звать")
    func osTooOld() async {
        let store = FakeStore(Self.lookup(version: "1.3.0", minimumOS: "19.0"))
        #expect(await source(store, osVersion: "18.7.1").availableUpdate() == nil)
    }

    @Test("Требование iOS сравнивается по частям", arguments: [
        ("18.7.1", "18.7.1", true), ("18.7.1", "18.0", true), ("18.7", "18.7.1", false),
        ("19.0", "18.10", true), ("18.10", "18.9", true), ("18.0", "19.0", false),
    ])
    func osRequirement(device: String, minimum: String, satisfies: Bool) {
        #expect(AppStoreUpdateSource.os(device, satisfies: minimum) == satisfies)
    }

    @Test("Запрос — номер приложения и страна витрины, без страны — без параметра")
    func requestURL() async throws {
        let store = FakeStore(Self.lookup(version: "1.3.0"))
        _ = await source(store).availableUpdate()
        let url = try #require(await store.requests.first)
        #expect(url.absoluteString == "https://itunes.apple.com/lookup?id=6819638881&country=ru")

        let other = try IsolatedDefaults("AppStoreUpdateSourceTests.noRegion")
        let noRegion = FakeStore(Self.lookup(version: "1.3.0"))
        _ = await AppStoreUpdateSource(
            currentVersion: "1.2.0", osVersion: "18.0", region: nil,
            defaults: Self.open(other.suiteName),
            now: { Self.start }, fetch: { try await noRegion.fetch($0) }
        ).availableUpdate()
        #expect(await noRegion.requests.first?.absoluteString == "https://itunes.apple.com/lookup?id=6819638881")
    }

    @Test("Не чаще раза в сутки: в течение суток — сохранённый ответ без сети, через сутки — запрос")
    func daily() async {
        let store = FakeStore(Self.lookup(version: "1.3.0"))
        #expect(await source(store).availableUpdate() != nil)
        #expect(await source(store, now: Self.start.addingTimeInterval(23 * 3600)).availableUpdate() != nil)
        #expect(await store.requests.count == 1)

        await store.respond(Self.lookup(version: "1.4.0"))
        let later = await source(store, now: Self.start.addingTimeInterval(25 * 3600)).availableUpdate()
        #expect(later == AvailableUpdate(version: "1.4.0"))
        #expect(await store.requests.count == 2)
    }

    @Test("Сбой сети — прежний ответ, а без него — значка нет")
    func offline() async {
        let failing = FakeStore(.failure(Offline()))
        #expect(await source(failing).availableUpdate() == nil)

        let store = FakeStore(Self.lookup(version: "1.3.0"))
        _ = await source(store).availableUpdate()
        await store.respond(.failure(Offline()))
        let next = await source(store, now: Self.start.addingTimeInterval(25 * 3600)).availableUpdate()
        #expect(next == AvailableUpdate(version: "1.3.0"), "значок не мигает от сбоя")
    }

    @Test("Пустой или непонятный ответ — обновления нет", arguments: [
        #"{"resultCount":0,"results":[]}"#,
        "не json",
    ])
    func emptyResponse(body: String) async {
        let store = FakeStore(.success(Data(body.utf8)))
        #expect(await source(store).availableUpdate() == nil)
    }

    @Test("Приложение обновилось до версии магазина — значок пропал без запроса")
    func updatedApp() async {
        let store = FakeStore(Self.lookup(version: "1.3.0"))
        #expect(await source(store, current: "1.2.0").availableUpdate() != nil)
        #expect(await source(store, current: "1.3.0", now: Self.start.addingTimeInterval(3600)).availableUpdate() == nil)
        #expect(await store.requests.count == 1)
    }
}
