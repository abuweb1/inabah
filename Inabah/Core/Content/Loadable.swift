import Foundation

/// Состояние асинхронно загружаемого значения.
nonisolated enum Loadable<Value: Sendable>: Sendable {
    case idle
    case loading
    case loaded(Value)
    case failed(ContentError)

    var value: Value? {
        if case .loaded(let value) = self { value } else { nil }
    }

    var isLoadingOrLoaded: Bool {
        switch self {
        case .loading, .loaded: true
        case .idle, .failed: false
        }
    }
}
