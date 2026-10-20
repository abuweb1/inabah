import CoreGraphics

// «Живые» ячейки курса без SwiftUI (docs/android/06-screens.md 6.7 п. 32; как Android `ReadingLiveliness.kt`):
// порядок волны, видимость ячейки, кривые «зова» увеличиваемой плитки. Числа - общие с Android.
nonisolated enum ReadingLiveliness {
    /// Пауза перед волной после открытия экрана.
    static let waveDelay = Duration.milliseconds(500)
    /// Каждая следующая ячейка волны «вдыхает» на столько позже.
    static let waveStagger = 0.055
    /// «Вдох» ячейки: 1 → `waveScale` → 1.
    static let waveBreath = 0.45
    static let waveScale: CGFloat = 1.06
    /// Надпись-подсказка видна столько; появление и уход - `hintFade`.
    static let hintDuration = Duration.seconds(4)
    static let hintFade = 0.3
    /// Тишина без касаний, после которой увеличиваемая плитка «зовёт» (пока слово ни разу не увеличивали).
    static let idleDelay = Duration.seconds(6)
    /// Не больше стольких «зовов» в тишине за заход на экран.
    static let idleCalls = 3
    /// «Зов» - покачивание с наклоном.
    static let wiggleDuration = 0.9
    static let wiggleTimes: [Double] = [0, 0.15, 0.3, 0.45, 0.6, 0.75, 1]
    static let wiggleAngles: [Double] = [0, -4, 4, -3, 2, 0, 0]
    static let wiggleScales: [Double] = [1, 1.06, 1.06, 1.05, 1.03, 1.02, 1]

    /// Длина волны из `count` ячеек, в секундах.
    static func waveLength(count: Int) -> Double {
        count > 0 ? Double(count - 1) * waveStagger + waveBreath : 0
    }

    /// Порядок чтения ячеек: ряд за рядом сверху вниз, в ряду - справа налево. Ряд - ячейки, чей верх ниже
    /// верха первой ячейки ряда меньше чем на полвысоты той. Результат - номер в порядке для каждой ячейки.
    static func readingOrder(_ cells: [CGRect]) -> [Int] {
        var rows: [[Int]] = []
        for index in cells.indices.sorted(by: { cells[$0].minY < cells[$1].minY }) {
            if let first = rows.last?.first, cells[index].minY - cells[first].minY < cells[first].height / 2 {
                rows[rows.count - 1].append(index)
            } else {
                rows.append([index])
            }
        }
        var ranks = Array(repeating: 0, count: cells.count)
        var rank = 0
        for row in rows {
            for index in row.sorted(by: { cells[$0].maxX > cells[$1].maxX }) {
                ranks[index] = rank
                rank += 1
            }
        }
        return ranks
    }

    /// Ячейка видна на экране `host`: уже размечена и её центр внутри (на границе - тоже).
    static func isVisible(_ cell: CGRect, in host: CGRect) -> Bool {
        cell.width > 0
            && (host.minY...host.maxY).contains(cell.midY)
            && (host.minX...host.maxX).contains(cell.midX)
    }

    /// «Вдох» ячейки на доле `progress` своей части волны: 0 → 1 → 0 по `sin`; вне (0, 1) - покой.
    static func waveBump(_ progress: Double) -> CGFloat {
        progress <= 0 || progress >= 1 ? 0 : CGFloat(sin(.pi * progress))
    }

    /// Наклон «зова» в градусах на доле `progress` (0...1).
    static func wiggleRotation(_ progress: Double) -> Double {
        interpolate(progress, values: wiggleAngles)
    }

    /// Масштаб «зова» на доле `progress` (0...1).
    static func wiggleScale(_ progress: Double) -> Double {
        interpolate(progress, values: wiggleScales)
    }

    /// Линейно между ключевыми точками `wiggleTimes`.
    private static func interpolate(_ progress: Double, values: [Double]) -> Double {
        guard progress > wiggleTimes[0] else { return values[0] }
        for index in 1..<wiggleTimes.count where progress <= wiggleTimes[index] {
            let start = wiggleTimes[index - 1]
            let fraction = (progress - start) / (wiggleTimes[index] - start)
            return values[index - 1] + (values[index] - values[index - 1]) * fraction
        }
        return values[values.count - 1]
    }
}
