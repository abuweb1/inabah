import Foundation
import Testing
@testable import Inabah

/// «Живые» ячейки курса - как Android `ReadingLivelinessTest`.
@Suite("Учимся читать: волна, видимость и «зов» ячеек")
struct ReadingLivelinessTests {
    private let host = CGRect(x: 0, y: 100, width: 400, height: 800)

    @Test("Волна - по порядку чтения, ряд за рядом сверху вниз, в ряду справа налево")
    func readingOrder() {
        let cells = [
            CGRect(x: 0, y: 100, width: 100, height: 60),
            CGRect(x: 150, y: 100, width: 100, height: 60),
            CGRect(x: 300, y: 100, width: 100, height: 60),
            // Второй ряд: одна ячейка на 1 pt выше соседних - ряд тот же.
            CGRect(x: 0, y: 171, width: 100, height: 60),
            CGRect(x: 150, y: 170, width: 100, height: 60),
            CGRect(x: 300, y: 170, width: 100, height: 60),
        ]
        #expect(ReadingLiveliness.readingOrder(cells) == [2, 1, 0, 5, 4, 3])
        #expect(ReadingLiveliness.readingOrder([]).isEmpty)
    }

    @Test("В волну и подсказку - только видимые ячейки", arguments: [
        (CGRect(x: 10, y: 120, width: 80, height: 60), true),
        (CGRect(x: 10, y: 1000, width: 80, height: 60), false),  // ниже экрана
        (CGRect(x: 450, y: 120, width: 80, height: 60), false),  // следующая страница
        (CGRect.zero, false),  // ещё не размечена
    ])
    func visibility(cell: CGRect, isVisible: Bool) {
        #expect(ReadingLiveliness.isVisible(cell, in: host) == isVisible)
    }

    @Test("«Зов» - покачивание с наклоном, начинается и кончается в покое")
    func wiggle() {
        #expect(ReadingLiveliness.wiggleRotation(0) == 0)
        #expect(ReadingLiveliness.wiggleRotation(1) == 0)
        #expect(ReadingLiveliness.wiggleRotation(0.15) == -4)
        #expect(ReadingLiveliness.wiggleRotation(0.3) == 4)
        #expect(ReadingLiveliness.wiggleScale(0) == 1)
        #expect(ReadingLiveliness.wiggleScale(1) == 1)
        #expect(ReadingLiveliness.wiggleScale(0.3) > 1)
    }

    @Test("«Вдох» волны - 0 → 1 → 0, вне своей доли - покой")
    func waveBump() {
        #expect(ReadingLiveliness.waveBump(0) == 0)
        #expect(ReadingLiveliness.waveBump(1) == 0)
        #expect(abs(ReadingLiveliness.waveBump(0.5) - 1) < 1e-9)
        #expect(ReadingLiveliness.waveLength(count: 0) == 0)
        #expect(abs(ReadingLiveliness.waveLength(count: 3) - (2 * 0.055 + 0.45)) < 1e-9)
    }
}

@MainActor
@Suite("Учимся читать: состояние вступления экрана")
struct TapIntroTests {
    private let host = CGRect(x: 0, y: 100, width: 400, height: 800)

    private func intro() -> TapIntro {
        let intro = TapIntro()
        intro.hostFrame = host
        return intro
    }

    @Test("Волна ранжирует только видимые ячейки, по порядку чтения")
    func waveRanksVisible() {
        let intro = intro()
        let left = UUID(), right = UUID(), hidden = UUID()
        intro.register(left, frame: CGRect(x: 0, y: 120, width: 100, height: 60), hint: "makharij.hint.letter", zoomable: false)
        intro.register(right, frame: CGRect(x: 300, y: 120, width: 100, height: 60), hint: "makharij.hint.letter", zoomable: false)
        intro.register(hidden, frame: CGRect(x: 0, y: 2000, width: 100, height: 60), hint: "makharij.hint.letter", zoomable: false)

        let length = intro.startWave()

        #expect(intro.waveOrder == [right: 0, left: 1])
        #expect(intro.wave == 1)
        #expect(length == ReadingLiveliness.waveLength(count: 2))
    }

    @Test("Подсказка у первой по порядку видимой ячейки, у увеличиваемых экранов - у первой увеличиваемой")
    func firstCell() {
        let intro = intro()
        let letter = UUID(), word = UUID()
        intro.register(letter, frame: CGRect(x: 300, y: 120, width: 100, height: 60), hint: "makharij.hint.letter", zoomable: false)
        intro.register(word, frame: CGRect(x: 0, y: 300, width: 100, height: 60), hint: "makharij.hint.zoom", zoomable: true)

        #expect(intro.firstCell(onlyZoomable: false)?.id == letter)
        #expect(intro.firstCell(onlyZoomable: true)?.id == word)
    }

    @Test("Тап находит ячейку под пальцем: вспышка в её рамке и «нажали ячейку»; между ячейками - ничего")
    func tapFindsCell() {
        let intro = intro()
        var seen = 0
        intro.beginVisit { seen += 1 }
        let cell = UUID()
        let frame = CGRect(x: 10, y: 120, width: 80, height: 60)
        intro.register(cell, frame: frame, hint: "makharij.hint.letter", zoomable: false)

        intro.tapped(at: CGPoint(x: 200, y: 140))
        #expect(intro.flash == nil)
        #expect(seen == 0)

        intro.tapped(at: CGPoint(x: 30, y: 150))
        #expect(intro.cell(at: CGPoint(x: 30, y: 150))?.id == cell)
        #expect(intro.flash?.frame == frame)
        #expect(intro.flash?.point == CGPoint(x: 30, y: 150))
        #expect(seen == 1)
    }

    @Test("Нажатие ячейки прячет подсказку и отмечает её показанной один раз; касание экрана - только прячет")
    func tapMarksSeenOnce() {
        let intro = intro()
        var seen = 0
        intro.beginVisit { seen += 1 }
        intro.hint = TapHint(target: .zero, text: "makharij.hint.letter")

        intro.touched()
        #expect(intro.hint == nil)
        #expect(seen == 0)

        intro.tappedCell()
        intro.tappedCell()
        #expect(seen == 1)

        // Новый заход - нажатие снова считается.
        intro.beginVisit { seen += 1 }
        intro.tappedCell()
        #expect(seen == 2)
    }

    @Test("Уход с экрана во время вступления - надпись не отмечается показанной")
    func cancelledIntroIsNotSeen() async {
        let intro = intro()
        intro.register(UUID(), frame: CGRect(x: 0, y: 120, width: 100, height: 60), hint: "makharij.hint.letter", zoomable: false)
        var seen = 0
        let run = Task { await intro.runIntro(seen: false, zoomable: false) { seen += 1 } }

        run.cancel()
        await run.value

        #expect(seen == 0)
        #expect(intro.hint == nil)
        #expect(intro.introFinished)
    }

    @Test("Повторное открытие экрана: волна снова, надписи нет")
    func seenScreenHasNoHint() async {
        let intro = intro()
        intro.register(UUID(), frame: CGRect(x: 0, y: 120, width: 100, height: 60), hint: "makharij.hint.letter", zoomable: false)
        var seen = 0

        await intro.runIntro(seen: true, zoomable: false) { seen += 1 }

        #expect(intro.wave == 1)
        #expect(intro.hint == nil)
        #expect(seen == 0)
    }
}
