import Testing
@testable import Inabah

/// «Послушать алфавит»: переходы состояния (Android `AlphabetPlayback`).
@Suite("Курс «Учимся читать»: Послушать алфавит")
struct AlphabetPlaybackTests {
    @Test("Пустая сетка - алфавита нет; иначе - с первой буквы, звучит")
    func start() throws {
        #expect(AlphabetPlayback(count: 0) == nil)

        let playback = try #require(AlphabetPlayback(count: 29))

        #expect(playback.index == 0)
        #expect(!playback.isPaused)
        #expect(!playback.hasPrevious)
        #expect(playback.hasNext)
    }

    @Test("Буква отзвучала - следующая; после последней - конец")
    func advance() throws {
        var playback = try #require(AlphabetPlayback(count: 2))

        let first = playback.advance()
        #expect(first)
        #expect(playback.index == 1)
        let last = playback.advance()
        #expect(!last)
        #expect(playback.index == 1)
    }

    @Test("«Продолжить» - та же буква с начала: ключ звучания новый")
    func resumeRestartsLetter() throws {
        var playback = try #require(AlphabetPlayback(count: 29))
        _ = playback.advance()
        let playing = playback.key

        playback.pause()
        #expect(playback.isPaused)
        #expect(playback.key != playing)

        playback.togglePause()
        #expect(!playback.isPaused)
        #expect(playback.index == 1)
        #expect(playback.key != playing)
    }

    @Test("⏮ ⏭ - соседняя буква и снимают паузу; у краёв - ничего")
    func previousNext() throws {
        var playback = try #require(AlphabetPlayback(count: 3))

        playback.previous()
        #expect(playback.index == 0)

        playback.pause()
        playback.next()
        #expect(playback.index == 1)
        #expect(!playback.isPaused)

        playback.next()
        playback.next()
        #expect(playback.index == 2)
        #expect(!playback.hasNext)

        playback.previous()
        #expect(playback.index == 1)
    }

    @Test("Пауза на паузе - ключ не меняется (звук не перезапускается)")
    func pauseTwice() throws {
        var playback = try #require(AlphabetPlayback(count: 29))
        playback.pause()
        let paused = playback.key

        playback.pause()

        #expect(playback.key == paused)
    }
}

@MainActor
@Suite("Курс «Учимся читать»: кнопка «Послушать алфавит»")
struct AlphabetPlayerTests {
    @Test("▶ - с первого элемента, ■ - стоп; «играет» меняется только при старте и стопе")
    func toggle() throws {
        let player = AlphabetPlayer()
        #expect(!player.isActive)

        player.toggle(count: 82)
        #expect(player.isActive)
        #expect(player.playback?.index == 0)

        player.playback?.next()
        player.playback?.pause()
        #expect(player.isActive, "смена слога и пауза - всё ещё играет")

        player.toggle(count: 82)
        #expect(!player.isActive)
        #expect(player.playback == nil)
    }

    @Test("Алфавит доиграл (`playback = nil`) - кнопка снова ▶")
    func finished() {
        let player = AlphabetPlayer()
        player.toggle(count: 1)
        player.playback = nil
        #expect(!player.isActive)
    }
}
