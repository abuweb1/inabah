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
