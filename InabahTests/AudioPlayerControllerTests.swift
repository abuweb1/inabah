import Foundation
import Testing
@testable import Inabah

/// Управляемый движок: длительность задаётся, конец записи вызывается вручную (`finish()`),
/// загрузки можно задержать (`holdsLoads`) и отпускать по одной в любом порядке.
@MainActor
private final class FakeAudioEngine: AudioEngine {
    var onFinish: ((_ successfully: Bool) -> Void)?
    var onError: ((AudioEngineError) -> Void)?
    var currentTime: TimeInterval = 0
    var rate: Float = 1
    var duration: TimeInterval = 30
    var failingFiles: Set<String> = []
    var holdsLoads = false
    var refusesToPlay = false

    private(set) var isPlaying = false
    private(set) var currentURL: URL?
    private(set) var preloadedURL: URL?
    private(set) var playedURLs: [URL] = []
    private(set) var lastStartDelay: TimeInterval?
    private var heldLoads: [(url: URL, continuation: CheckedContinuation<Void, Never>)] = []

    func load(url: URL) async throws -> TimeInterval {
        if holdsLoads {
            await withCheckedContinuation { heldLoads.append((url, $0)) }
        }
        if failingFiles.contains(url.lastPathComponent) {
            throw AudioEngineError.cannotLoad(url.lastPathComponent)
        }
        currentURL = url
        currentTime = 0
        return duration
    }

    func preload(url: URL) async {
        preloadedURL = url
    }

    func startPreloaded(url: URL, after delay: TimeInterval) -> TimeInterval? {
        guard preloadedURL == url else { return nil }
        preloadedURL = nil
        currentURL = url
        currentTime = 0
        play(after: delay)
        return duration
    }

    /// Отказ старта приходит асинхронно — как у настоящего движка.
    func play(after delay: TimeInterval) {
        guard !refusesToPlay, let currentURL else {
            Task { self.onError?(.playbackFailed) }
            return
        }
        isPlaying = true
        lastStartDelay = delay
        playedURLs.append(currentURL)
    }

    func pause() { isPlaying = false }

    func stop() {
        isPlaying = false
        currentTime = 0
        preloadedURL = nil
    }

    /// Запись доиграла до конца.
    func finish(successfully: Bool = true) {
        currentTime = duration
        isPlaying = false
        onFinish?(successfully)
    }

    var heldLoadCount: Int { heldLoads.count }

    /// Отпустить задержанную загрузку этого файла.
    func releaseLoad(_ fileName: String) {
        guard let index = heldLoads.firstIndex(where: { $0.url.lastPathComponent == fileName }) else { return }
        heldLoads.remove(at: index).continuation.resume()
    }
}

@MainActor
@Suite("Аудиоплеер", .timeLimit(.minutes(1)))
struct AudioPlayerControllerTests {
    private let engine = FakeAudioEngine()
    private let player: AudioPlayerController

    init() {
        player = AudioPlayerController(engine: engine)
    }

    private static func track(_ number: Int) -> AudioTrack {
        AudioTrack(
            id: "azkar.morning.\(number)",
            url: URL(fileURLWithPath: "/morning_\(number).mp3"),
            title: "Зикр \(number)",
            subtitle: "Утренние азкары",
            category: "АЗКАРЫ"
        )
    }

    private func playlist(_ repeats: [Int]) -> [AudioQueueItem] {
        repeats.enumerated().map { AudioQueueItem(track: Self.track($0.offset + 1), repeatCount: $0.element) }
    }

    private func play(_ number: Int) async {
        player.play(Self.track(number))
        await player.waitForLoading()
    }

    private func playAll(_ repeats: [Int], rate: Float = 1, pause: Duration = .zero, startAt: Int = 0) async {
        player.playAll(playlist(repeats), id: "test", rate: rate, pauseBetween: pause, startAt: startAt)
        await player.waitForLoading()
    }

    /// Конец записи; если плейлисту пришлось загружать следующую — дождаться загрузки.
    private func finish(successfully: Bool = true) async {
        engine.finish(successfully: successfully)
        await player.waitForLoading()
    }

    /// Дать завершиться задачам на главном акторе (отброшенные загрузки).
    private func settle() async {
        for _ in 0..<10 { await Task.yield() }
    }

    // MARK: Одиночный режим

    @Test("▶ запускает запись и открывает плеер")
    func playStartsTrack() async {
        await play(1)

        #expect(player.isPlaying)
        #expect(engine.isPlaying)
        #expect(player.isPanelVisible)
        #expect(player.mode == .single)
        #expect(player.duration == 30)
        #expect(player.isActive("azkar.morning.1"))
    }

    @Test("Плеер появляется сразу, до окончания загрузки файла")
    func panelShowsBeforeLoadCompletes() async {
        player.play(Self.track(1))

        #expect(player.isPanelVisible)
        #expect(player.isPlaying)

        await player.waitForLoading()
        #expect(engine.isPlaying)
    }

    @Test("Пауза во время загрузки отменяет старт звука")
    func pauseWhileLoading() async {
        player.play(Self.track(1))
        player.pause()

        await player.waitForLoading()

        #expect(!player.isPlaying)
        #expect(!engine.isPlaying)
    }

    @Test("Закрыть во время загрузки — звук не начинается")
    func closeWhileLoading() async {
        player.play(Self.track(1))
        player.close()

        await player.waitForLoading()

        #expect(!engine.isPlaying)
        #expect(!player.hasTrack)
    }

    @Test("Гонка загрузок: поздний результат старой записи отбрасывается")
    func staleLoadIsDiscarded() async throws {
        engine.holdsLoads = true
        player.play(Self.track(1))
        await settle()
        player.play(Self.track(2))
        // Обе загрузки должны дойти до задержки в движке, прежде чем их отпускать.
        await settle()
        try #require(engine.heldLoadCount == 2)

        engine.releaseLoad("morning_2.mp3")
        await player.waitForLoading()
        engine.releaseLoad("morning_1.mp3")
        await settle()

        #expect(player.currentTrack?.id == "azkar.morning.2")
        #expect(engine.playedURLs.map(\.lastPathComponent) == ["morning_2.mp3"])
        #expect(player.isPlaying)
    }

    @Test("Пауза и продолжение")
    func pauseAndResume() async {
        await play(1)
        engine.currentTime = 12

        player.togglePlayPause()
        #expect(!player.isPlaying)
        #expect(player.currentTime == 12)

        player.togglePlayPause()
        #expect(player.isPlaying)
        #expect(engine.isPlaying)
    }

    @Test("Повторный ▶ на незаконченной записи только показывает скрытый плеер")
    func replaySameTrackWhilePlayingShowsPanel() async {
        await play(1)
        player.hidePanel()
        engine.currentTime = 7

        await play(1)

        #expect(player.isPanelVisible)
        #expect(engine.playedURLs.count == 1)
        #expect(engine.currentTime == 7)
    }

    @Test("Повторный ▶ после окончания играет запись заново")
    func replaySameTrackAfterFinishRestarts() async {
        await play(1)
        await finish()
        #expect(player.isFinished)
        #expect(!player.isPlaying)
        #expect(!player.isActive("azkar.morning.1"))

        await play(1)

        #expect(player.isPlaying)
        #expect(!player.isFinished)
        #expect(engine.playedURLs.count == 2)
    }

    @Test("▶ на другом зикре заменяет текущую запись")
    func playOtherTrackReplaces() async {
        await play(1)
        await play(2)

        #expect(player.currentTrack?.id == "azkar.morning.2")
        #expect(engine.currentURL?.lastPathComponent == "morning_2.mp3")
    }

    @Test("Перемотка ±10 с не выходит за границы записи", arguments: [
        (5.0, -10.0, 0.0),
        (5.0, 10.0, 15.0),
    ])
    func skipIsClamped(start: Double, delta: Double, expected: Double) async {
        await play(1)
        player.seek(to: start)

        player.skip(by: delta)

        #expect(player.currentTime == expected)
        #expect(engine.currentTime == expected)
    }

    @Test("Перемотка в самый конец во время игры — запись доиграла")
    func seekToEndFinishes() async {
        await play(1)

        player.seek(to: 30)

        #expect(player.isFinished)
        #expect(!player.isPlaying)
    }

    @Test("Перемотка назад после окончания снимает признак «доиграла»")
    func seekAfterFinish() async {
        await play(1)
        await finish()

        player.seek(to: 10)

        #expect(!player.isFinished)
    }

    @Test("Стоп — позиция в начало, плеер остаётся открытым; ▶ продолжает с начала")
    func stopKeepsPanel() async {
        await play(1)
        engine.currentTime = 12

        player.stop()
        #expect(!player.isPlaying)
        #expect(player.currentTime == 0)
        #expect(player.isPanelVisible)
        #expect(player.hasTrack)

        player.resume()
        #expect(player.isPlaying)
        #expect(engine.isPlaying)
    }

    @Test("Стоп сбрасывает номер повтора")
    func stopResetsRepetition() async {
        await playAll([3])
        await finish()
        #expect(player.repetition == 2)

        player.stop()

        #expect(player.repetition == 1)
    }

    @Test("«Сначала» перезапускает запись")
    func restartPlaysFromStart() async {
        await play(1)
        engine.currentTime = 20
        player.togglePlayPause()

        player.restart()
        await player.waitForLoading()

        #expect(player.isPlaying)
        #expect(engine.isPlaying)
        #expect(player.currentTime == 0)
    }

    @Test("Скрыть — звук продолжает играть")
    func hideKeepsPlaying() async {
        await play(1)

        player.hidePanel()

        #expect(!player.isPanelVisible)
        #expect(player.isPlaying)
    }

    @Test("Закрыть — остановка и пустая очередь")
    func closeClears() async {
        await play(1)

        player.close()

        #expect(!player.isPlaying)
        #expect(!player.hasTrack)
        #expect(!player.isPanelVisible)
        #expect(!engine.isPlaying)
    }

    @Test("Фон: одиночная запись на паузе, плейлист продолжает")
    func background() async {
        await play(1)
        player.applicationDidEnterBackground()
        #expect(!player.isPlaying)

        await playAll([1, 1])
        player.applicationDidEnterBackground()
        #expect(player.isPlaying)
    }

    // MARK: Ошибки

    @Test("Ошибка загрузки файла не запускает воспроизведение")
    func loadFailure() async {
        engine.failingFiles = ["morning_1.mp3"]

        await play(1)

        #expect(!player.isPlaying)
        #expect(player.error == .cannotLoad("morning_1.mp3"))
    }

    @Test("После ошибки «продолжить» загружает запись заново")
    func resumeAfterErrorReloads() async {
        engine.failingFiles = ["morning_1.mp3"]
        await play(1)
        engine.failingFiles = []

        player.resume()
        await player.waitForLoading()

        #expect(player.isPlaying)
        #expect(player.error == nil)
        #expect(engine.isPlaying)
    }

    @Test("Запись не запустилась — ошибка, а не «играет» без звука")
    func playbackRefused() async {
        engine.refusesToPlay = true

        await play(1)
        await settle()

        #expect(!player.isPlaying)
        #expect(player.error == .playbackFailed)
    }

    @Test("В плейлисте битая запись пропускается")
    func playlistSkipsBrokenFile() async {
        engine.failingFiles = ["morning_1.mp3"]

        await playAll([1, 1])
        await player.waitForLoading()

        #expect(player.index == 1)
        #expect(player.isPlaying)
        #expect(engine.currentURL?.lastPathComponent == "morning_2.mp3")
    }

    @Test("Ошибка декодирования во время игры — показывается")
    func decodeError() async {
        await play(1)

        engine.onError?(.cannotLoad("morning_1.mp3"))

        #expect(player.error != nil)
        #expect(!player.isPlaying)
    }

    // MARK: Плейлист

    @Test("Запись повторяется по числу раз, затем следующий зикр")
    func playlistRepeatsThenAdvances() async {
        await playAll([3, 1])
        #expect(player.mode == .playlist)
        #expect(player.repetition == 1)

        await finish()
        #expect(player.index == 0)
        #expect(player.repetition == 2)
        #expect(engine.isPlaying)

        await finish()
        #expect(player.repetition == 3)

        await finish()
        #expect(player.index == 1)
        #expect(player.repetition == 1)
        #expect(engine.currentURL?.lastPathComponent == "morning_2.mp3")
        #expect(engine.isPlaying)
    }

    @Test("Следующая запись готовится заранее, пауза между зикрами — в аудиодорожке")
    func pauseBetweenItemsIsScheduledInEngine() async {
        await playAll([1, 1], pause: .seconds(3))
        #expect(engine.preloadedURL?.lastPathComponent == "morning_2.mp3")

        await finish()

        #expect(player.index == 1)
        #expect(player.isPlaying)
        #expect(engine.lastStartDelay == 3)
    }

    @Test("Запись доиграла на паузе — следующий шаг только после «продолжить»")
    func finishWhilePausedWaitsForResume() async {
        await playAll([2, 1])
        player.pause()

        engine.finish()
        #expect(player.repetition == 1)
        #expect(!engine.isPlaying)

        player.resume()
        #expect(player.repetition == 2)
        #expect(engine.isPlaying)
    }

    @Test("Конец очереди — плейлист завершён")
    func playlistFinishes() async {
        await playAll([1, 1])

        await finish()
        await finish()

        #expect(player.isFinished)
        #expect(!player.isPlaying)
        #expect(player.index == 1)
    }

    @Test("Следующий/предыдущий в границах очереди")
    func nextPrevious() async {
        await playAll([1, 1, 1])
        #expect(!player.canGoPrevious)

        player.next()
        player.next()
        await player.waitForLoading()
        #expect(player.index == 2)
        #expect(!player.canGoNext)

        player.next()
        #expect(player.index == 2)

        player.previous()
        await player.waitForLoading()
        #expect(player.index == 1)
        #expect(player.repetition == 1)
        #expect(engine.isPlaying)
    }

    @Test("В одиночном режиме нет перехода к соседним записям")
    func singleHasNoNavigation() async {
        await play(1)
        #expect(!player.canGoNext)
        #expect(!player.canGoPrevious)
    }

    @Test("Скорость передаётся движку; одиночная запись — всегда 1×")
    func rate() async {
        await playAll([1], rate: 1.25)
        #expect(engine.rate == 1.25)

        player.updatePlaylist(rate: 1.5, pauseBetween: .zero)
        #expect(engine.rate == 1.5)
        #expect(player.playbackRate == 1.5)

        await play(5)
        #expect(engine.rate == 1)
    }

    @Test("Плейлист распознаётся по идентификатору")
    func playlistIdentity() async {
        await playAll([1, 1])
        #expect(player.isPlaylistActive("test"))
        #expect(!player.isPlaylistActive("other"))

        await play(1)
        #expect(!player.isPlaylistActive("test"))
    }

    @Test("Повторы не ограничены снизу: repeatCount < 1 считается как 1")
    func queueItemClampsRepeatCount() {
        #expect(AudioQueueItem(track: Self.track(1), repeatCount: 0).repeatCount == 1)
    }
}

@MainActor
@Suite("Настройки «Прослушать все»")
struct PlaylistSettingsTests {
    private let defaults: UserDefaults

    init() throws {
        defaults = try #require(UserDefaults(suiteName: "PlaylistSettingsTests.\(UUID().uuidString)"))
    }

    @Test("Значения по умолчанию")
    func defaultValues() {
        let settings = PlaylistSettings(defaults: defaults)
        #expect(settings.repeatsByCount)
        #expect(settings.pauseBetween == 1)
        #expect(settings.rate == 1)
    }

    @Test("Настройки сохраняются между запусками")
    func persists() {
        let settings = PlaylistSettings(defaults: defaults)
        settings.repeatsByCount = false
        settings.pauseBetween = 3
        settings.rate = 1.5

        let restored = PlaylistSettings(defaults: defaults)
        #expect(!restored.repeatsByCount)
        #expect(restored.pauseBetween == 3)
        #expect(restored.rate == 1.5)
    }

    @Test("Недопустимое сохранённое значение — значение по умолчанию")
    func invalidStoredValues() {
        defaults.set(7.0, forKey: "playlist.pauseBetween")
        defaults.set(3.0, forKey: "playlist.rate")

        let settings = PlaylistSettings(defaults: defaults)
        #expect(settings.pauseBetween == 1)
        #expect(settings.rate == 1)
    }
}
