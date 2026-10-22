import Foundation

/// Генератор случайных чисел с зерном (SplitMix64): то же зерно - та же последовательность. Нужен там, где
/// случайный порядок должен повторяться - заход в проверку, страницы «Читаем слова», случайные слоги урока.
/// Последовательность Android (`kotlin.random.Random`) не повторяет: совпадать должны свойства, не порядок.
nonisolated struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
