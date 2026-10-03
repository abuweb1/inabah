import Foundation

nonisolated extension Comparable {
    /// Значение, ограниченное диапазоном.
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
