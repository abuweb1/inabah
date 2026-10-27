import Foundation

/// Новая версия в App Store (решение пользователя 2026-10-10, сделано 2026-10-27): номер последней версии —
/// из iTunes Lookup по номеру приложения, не чаще раза в сутки. В запросе только номер приложения и страна
/// витрины; о пользователе ничего не передаётся (`PRIVACY.md`). Сбой сети — прежний ответ: значок не мигает.
/// Актор: хранилище (`UserDefaults`) живёт внутри, запрос идёт не на главном потоке.
actor AppStoreUpdateSource: UpdateSource {
    /// Номер приложения в App Store (`AboutLinks.appStore`).
    static let appID = "6819638881"
    /// Как часто спрашивать магазин.
    static let checkInterval: TimeInterval = 24 * 60 * 60
    private static let lastCheckKey = "update.lastCheck"
    private static let storeVersionKey = "update.storeVersion"
    private static let storeMinimumOSKey = "update.storeMinimumOS"

    private let currentVersion: String
    private let osVersion: String
    private let region: String?
    private let defaults: UserDefaults
    private let now: @Sendable () -> Date
    private let fetch: @Sendable (URL) async throws -> Data

    /// - Parameters:
    ///   - region: страна витрины (2 буквы); `nil` — без страны.
    ///   - fetch: загрузка ответа — в тестах подставляется, сети нет.
    init(
        currentVersion: String = AppVersion.marketing,
        osVersion: String = AppStoreUpdateSource.deviceOSVersion,
        region: String? = Locale.current.region?.identifier,
        defaults: UserDefaults = .standard,
        now: @escaping @Sendable () -> Date = Date.init,
        fetch: @escaping @Sendable (URL) async throws -> Data = AppStoreUpdateSource.download
    ) {
        self.currentVersion = currentVersion
        self.osVersion = osVersion
        self.region = region
        self.defaults = defaults
        self.now = now
        self.fetch = fetch
    }

    func availableUpdate() async -> AvailableUpdate? {
        if isDue, let fresh = try? await lookup() {
            defaults.set(now(), forKey: Self.lastCheckKey)
            defaults.set(fresh.version, forKey: Self.storeVersionKey)
            defaults.set(fresh.minimumOsVersion, forKey: Self.storeMinimumOSKey)
        }
        return update(
            storeVersion: defaults.string(forKey: Self.storeVersionKey),
            minimumOS: defaults.string(forKey: Self.storeMinimumOSKey)
        )
    }

    /// Прошли сутки с последнего удачного ответа (или его не было; часы перевели назад — тоже).
    private var isDue: Bool {
        guard let last = defaults.object(forKey: Self.lastCheckKey) as? Date else { return true }
        let elapsed = now().timeIntervalSince(last)
        return elapsed >= Self.checkInterval || elapsed < 0
    }

    /// Версия магазина новее текущей и встанет на это устройство.
    private func update(storeVersion: String?, minimumOS: String?) -> AvailableUpdate? {
        guard let store = AppVersionNumber(storeVersion), let current = AppVersionNumber(currentVersion),
              store > current, Self.os(osVersion, satisfies: minimumOS)
        else { return nil }
        return AvailableUpdate(version: store.text)
    }

    private func lookup() async throws -> LookupResult {
        var components = URLComponents(string: "https://itunes.apple.com/lookup")
        components?.queryItems = [URLQueryItem(name: "id", value: Self.appID)]
            + (region.map { [URLQueryItem(name: "country", value: $0.lowercased())] } ?? [])
        guard let url = components?.url else { throw URLError(.badURL) }
        let response = try JSONDecoder().decode(LookupResponse.self, from: await fetch(url))
        guard let result = response.results.first else { throw URLError(.cannotParseResponse) }
        return result
    }

    private struct LookupResponse: Decodable {
        let results: [LookupResult]
    }

    private struct LookupResult: Decodable {
        let version: String
        let minimumOsVersion: String?
    }

    /// `18.7.1` не ниже `18.0`: части — числами, недостающие — нули. Требования нет — подходит.
    static func os(_ version: String, satisfies minimum: String?) -> Bool {
        guard let minimum else { return true }
        let parts = { (text: String) in text.split(separator: ".").map { Int($0) ?? 0 } }
        var device = parts(version)
        var required = parts(minimum)
        let count = max(device.count, required.count)
        device += Array(repeating: 0, count: count - device.count)
        required += Array(repeating: 0, count: count - required.count)
        return !device.lexicographicallyPrecedes(required)
    }

    static var deviceOSVersion: String {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }

    /// Эфемерная сессия: без кэша и cookie; тайм-аут — 10 с.
    static func download(_ url: URL) async throws -> Data {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: configuration)
        defer { session.finishTasksAndInvalidate() }
        let (data, response) = try await session.data(from: url)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return data
    }
}
