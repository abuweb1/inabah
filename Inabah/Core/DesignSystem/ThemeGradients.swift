import SwiftUI

/// Линейный градиент как значение темы: стопы + направление.
nonisolated struct ThemeGradient: Hashable, Sendable {
    var gradient: Gradient
    var startPoint: UnitPoint
    var endPoint: UnitPoint

    init(_ stops: [Gradient.Stop], startPoint: UnitPoint, endPoint: UnitPoint) {
        self.gradient = Gradient(stops: stops)
        self.startPoint = startPoint
        self.endPoint = endPoint
    }

    /// Направление в градусах CSS (`linear-gradient(168deg, …)`): 0° — вверх, 90° — вправо.
    /// Позволяет переносить градиенты из прототипа без ручного пересчёта точек.
    init(_ stops: [Gradient.Stop], cssAngle degrees: Double) {
        let radians = degrees * .pi / 180
        let dx = sin(radians) / 2
        let dy = -cos(radians) / 2
        self.init(
            stops,
            startPoint: UnitPoint(x: 0.5 - dx, y: 0.5 - dy),
            endPoint: UnitPoint(x: 0.5 + dx, y: 0.5 + dy)
        )
    }

    /// Градиент из именованных цветов ассетов — короткая запись для статических тем.
    static func css(stops: [(AssetColor, Double)], cssAngle degrees: Double) -> ThemeGradient {
        let gradientStops = stops.map { asset, location in
            Gradient.Stop(color: Color(asset: asset), location: location)
        }
        return ThemeGradient(gradientStops, cssAngle: degrees)
    }

    var linear: LinearGradient {
        LinearGradient(gradient: gradient, startPoint: startPoint, endPoint: endPoint)
    }
}

/// Градиенты темы: фоны разделов и карточки навигации.
nonisolated struct ThemeGradients: Hashable, Sendable {
    var azkarBackground: ThemeGradient
    /// Фон вечерних азкаров — в цветах карточки «Вечерние азкары» главной.
    var eveningBackground: ThemeGradient
    var hadithBackground: ThemeGradient
    /// Фон раздела «Настройки» — графит, нейтральный к фиолетовым азкарам и изумрудным хадисам.
    var settingsBackground: ThemeGradient
    /// Фон раздела «Махрадж» — янтарно-коричневый (прежний цвет карточки аль-Аджурри, приглушённый).
    var makharijBackground: ThemeGradient
    var morningCard: ThemeGradient
    var eveningCard: ThemeGradient
    var nawawiCard: ThemeGradient
    var qudsiCard: ThemeGradient
    var ajurriCard: ThemeGradient
    var progressFill: ThemeGradient
    var counterButton: ThemeGradient
    var counterButtonDone: ThemeGradient
    var parchment: ThemeGradient
    /// Полоска по верхнему краю пергамента — полупрозрачная, проступает фон.
    var parchmentStripe: ThemeGradient
}

nonisolated extension ThemeGradients {
    static let inabah = ThemeGradients(
        azkarBackground: ThemeGradient.css(stops: [
            (.azkarBackgroundTop, 0),
            (.azkarBackgroundMid, 0.5),
            (.azkarBackgroundBottom, 1),
        ], cssAngle: 168),
        eveningBackground: ThemeGradient.css(stops: [
            (.eveningCardStart, 0),
            (.eveningCardMid, 0.55),
            (.eveningCardEnd, 1),
        ], cssAngle: 168),
        hadithBackground: ThemeGradient.css(stops: [
            (.hadithBackgroundTop, 0),
            (.hadithBackgroundMid, 0.5),
            (.hadithBackgroundBottom, 1),
        ], cssAngle: 168),
        settingsBackground: ThemeGradient.css(stops: [
            (.settingsBackgroundTop, 0),
            (.settingsBackgroundMid, 0.5),
            (.settingsBackgroundBottom, 1),
        ], cssAngle: 168),
        makharijBackground: ThemeGradient.css(stops: [
            (.makharijBackgroundTop, 0),
            (.makharijBackgroundMid, 0.5),
            (.makharijBackgroundBottom, 1),
        ], cssAngle: 168),
        morningCard: ThemeGradient.css(stops: [
            (.morningCardStart, 0),
            (.morningCardMid, 0.6),
            (.morningCardEnd, 1),
        ], cssAngle: 135),
        eveningCard: ThemeGradient.css(stops: [
            (.eveningCardStart, 0),
            (.eveningCardMid, 0.55),
            (.eveningCardEnd, 1),
        ], cssAngle: 135),
        nawawiCard: ThemeGradient.css(stops: [
            (.nawawiCardStart, 0),
            (.nawawiCardMid, 0.6),
            (.nawawiCardEnd, 1),
        ], cssAngle: 135),
        qudsiCard: ThemeGradient.css(stops: [
            (.qudsiCardStart, 0),
            (.qudsiCardMid, 0.55),
            (.qudsiCardEnd, 1),
        ], cssAngle: 135),
        ajurriCard: ThemeGradient.css(stops: [
            (.ajurriCardStart, 0),
            (.ajurriCardMid, 0.55),
            (.ajurriCardEnd, 1),
        ], cssAngle: 135),
        progressFill: ThemeGradient.css(stops: [
            (.successDeep, 0),
            (.success, 1),
        ], cssAngle: 90),
        counterButton: ThemeGradient.css(stops: [
            (.goldLight, 0),
            (.goldDeep, 1),
        ], cssAngle: 160),
        counterButtonDone: ThemeGradient.css(stops: [
            (.successLight, 0),
            (.success, 1),
        ], cssAngle: 160),
        parchment: ThemeGradient.css(stops: [
            (.parchmentLight, 0),
            (.gold, 0.45),
            (.parchmentMid, 0.75),
            (.parchmentDeep, 1),
        ], cssAngle: 150),
        parchmentStripe: ThemeGradient(
            [
                Gradient.Stop(color: Color(asset: .successDeep).opacity(0.6), location: 0),
                Gradient.Stop(color: Color(asset: .success).opacity(0.6), location: 0.5),
                Gradient.Stop(color: Color(asset: .successDeep).opacity(0.6), location: 1),
            ],
            startPoint: .leading,
            endPoint: .trailing
        )
    )
}
