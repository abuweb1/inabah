import Foundation

/// Новая версия в App Store (решение пользователя 2026-10-10, сделано 2026-10-27): номер последней версии —
/// из iTunes Lookup по номеру приложения, не чаще раза в сутки; после сбоя — не раньше чем через 6 ч
/// (аудит 2026-10-27: без паузы запрос уходил при каждом показе главной). В запросе только номер приложения
/// и регион из настроек телефона; витрина региона не ответила — повтор без страны (`PRIVACY.md`). Сбой сети —
/// прежний ответ: значок не мигает. Актор: хранилище (`UserDefaults`) живёт внутри, запрос — не на главном потоке.
actor AppStoreUpdateSource: UpdateSource {
    /// Номер приложения в App Store (`AboutLinks.appStore`).
    static let appID = "6819638881"
    /// Как часто спрашивать магазин.
    static let checkInterval: TimeInterval = 24 * 60 * 60
    /// Пауза после неудачной попытки.
    static let retryInterval: TimeInterval = 6 * 60 * 60
    private static let lastCheckKey = "update.lastCheck"
    private static let lastAttemptKey = "update.lastAttempt"
    private static let storeVersionKey = "update.storeVersion"
    private static let storeMinimumOSKey = "update.storeMinimumOS"

    private let currentVersion: String
    private let osVersion: String
    private let region: String?
    private let defaults: UserDefaults
    private let now: @Sendable () -> Date
    private let fetch: @Sendable (URL) async throws -> Data
    /// Запрос в полёте: второй вызов (главная показалась снова) ждёт его, а не шлёт свой.
    private var inFlight: Task<LookupResult?, Never>?

    /// - Parameters:
    ///   - region: регион из настроек телефона (2 буквы); `nil` — без страны.
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
        if let inFlight {
            _ = await inFlight.value
        } else if isDue {
            defaults.set(now(), forKey: Self.lastAttemptKey)
            // Ответ запоминается внутри задачи: кто бы из ждущих ни проснулся первым, он прочитает уже записанное.
            let task = Task {
                let fresh = await lookupAnyStorefront()
                if let fresh { remember(fresh) }
                return fresh
            }
            inFlight = task
            _ = await task.value
            inFlight = nil
        }
        return update(
            storeVersion: defaults.string(forKey: Self.storeVersionKey),
            minimumOS: defaults.string(forKey: Self.storeMinimumOSKey)
        )
    }

    /// Прошли сутки с удачного ответа и 6 ч с последней попытки (их не было или часы перевели назад — тоже).
    private var isDue: Bool {
        Self.elapsed(since: defaults.object(forKey: Self.lastCheckKey) as? Date, now: now(), atLeast: Self.checkInterval)
            && Self.elapsed(since: defaults.object(forKey: Self.lastAttemptKey) as? Date, now: now(), atLeast: Self.retryInterval)
    }

    private static func elapsed(since date: Date?, now: Date, atLeast interval: TimeInterval) -> Bool {
        guard let date else { return true }
        let elapsed = now.timeIntervalSince(date)
        return elapsed >= interval || elapsed < 0
    }

    /// Ответ запоминается, но версия не понижается: разные узлы CDN Apple могут отдать прежнюю версию
    /// после новой — кнопка не должна пропадать на сутки (аудит 2026-10-27).
    private func remember(_ fresh: LookupResult) {
        defaults.set(now(), forKey: Self.lastCheckKey)
        if let saved = AppVersionNumber(defaults.string(forKey: Self.storeVersionKey)),
           let incoming = AppVersionNumber(fresh.version), saved > incoming {
            return
        }
        defaults.set(fresh.version, forKey: Self.storeVersionKey)
        defaults.set(fresh.minimumOsVersion, forKey: Self.storeMinimumOSKey)
    }

    /// Витрина региона из настроек; не ответила (нет App Store, нет приложения, ошибка) — витрина по умолчанию.
    private func lookupAnyStorefront() async -> LookupResult? {
        if let region, let result = try? await lookup(country: region) { return result }
        return try? await lookup(country: nil)
    }

    /// Версия магазина новее текущей и встанет на это устройство.
    private func update(storeVersion: String?, minimumOS: String?) -> AvailableUpdate? {
        guard let store = AppVersionNumber(storeVersion), let current = AppVersionNumber(currentVersion),
              store > current, Self.os(osVersion, satisfies: minimumOS)
        else { return nil }
        return AvailableUpdate(version: store.text)
    }

    private func lookup(country: String?) async throws -> LookupResult {
        var components = URLComponents(string: "https://itunes.apple.com/lookup")
        components?.queryItems = [URLQueryItem(name: "id", value: Self.appID)]
            + (country.map { [URLQueryItem(name: "country", value: $0.lowercased())] } ?? [])
        guard let url = components?.url else { throw URLError(.badURL) }
        let response = try JSONDecoder().decode(LookupResponse.self, from: await fetch(url))
        guard let result = response.results.first else { throw URLError(.cannotParseResponse) }
        return result
    }

    private nonisolated struct LookupResponse: Decodable, Sendable {
        let results: [LookupResult]
    }

    private nonisolated struct LookupResult: Decodable, Sendable {
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
