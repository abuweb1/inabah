import SwiftUI
import UIKit

/// Руководство по произношению «Учимся читать» (Android `ReadingGuideScreen.kt`, 2026-10-21): заголовок -
/// название руководства и «Учимся читать»; лента карточек - текст с заголовком и рисунки на светлом фоне
/// `parchmentIllustration` с подписью и легендой. Буквы руководства на экране не открываются - листа нет.
struct ReadingGuideView: View {
    let guideID: String

    var body: some View {
        ReadingScreen(
            // Лист буквы здесь не открывается - шаг для его цепочки не нужен.
            stepNumber: 0,
            title: { course in Text(verbatim: course?.guide(id: guideID)?.title ?? "") },
            subtitle: { _ in Text("makharij.reading.title") },
            showsTransliterationToggle: false,
            actions: { EmptyView() },
            content: { course, colors, _ in
                ScrollView {
                    VStack(spacing: Spacing.m) {
                        let blocks = course.guide(id: guideID)?.blocks ?? []
                        ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                            switch block {
                            case .text(let text):
                                GuideTextCard(block: text, colors: colors)
                            case .image(let image):
                                GuideImageCard(block: image, colors: colors)
                            default:
                                EmptyView()
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, Spacing.s)
                    .padding(.bottom, Spacing.xl)
                }
            }
        )
    }
}

/// Кнопка руководства под уроком, в листе буквы и на карточке буквы (Android `ReadingOutlineButton`).
struct GuideButton: View {
    let guide: ReadingGuide
    let colors: ReadingColors

    @Environment(AppRouter.self) private var router

    var body: some View {
        Button { router.makharijPath.append(.guide(id: guide.id)) } label: {
            Text(verbatim: guide.title)
        }
        .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
    }
}

/// Заголовок карточки руководства.
private struct GuideTitle: View {
    let text: String

    @Environment(\.theme) private var theme

    var body: some View {
        Text(ReadingText.withArabicRuns(text, baseSize: ContentTextStyle.translation.baseSize))
            .font(.body.weight(.semibold))
            .foregroundStyle(theme.palette.onAccent)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Карточка текста: заголовок и абзац (арабские вставки - Scheherazade).
private struct GuideTextCard: View {
    let block: ReadingBlock.Text
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            if let title = block.title { GuideTitle(text: title) }
            LessonParagraph(text: block.text, color: theme.palette.onAccent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.m)
        .readingTile(colors)
    }
}

/// Карточка рисунка: рисунок на `parchmentIllustration` (смысл - в подписи, VoiceOver его не читает),
/// под ним заголовок, подпись и легенда.
private struct GuideImageCard: View {
    let block: ReadingBlock.Image
    let colors: ReadingColors

    @Environment(\.theme) private var theme
    /// Квадрат легенды растёт вместе с текстом (Android - 18 sp).
    @ScaledMetric(relativeTo: .body) private var swatchSize: CGFloat = 18

    @State private var image: UIImage?

    /// Пропорции до загрузки рисунка (Android - 1,15).
    private static let placeholderAspect: CGFloat = 1.15

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            illustration
            if block.title != nil || block.text != nil || !block.legend.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    if let title = block.title { GuideTitle(text: title) }
                    if let text = block.text { LessonParagraph(text: text, color: theme.palette.onAccent) }
                    ForEach(block.legend, id: \.self) { item in
                        legendRow(item)
                    }
                }
                .padding(.horizontal, Spacing.xs)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.m)
        .readingTile(colors)
        .task(id: block.image) { image = await GuideImageLoader.load(block.image) }
    }

    /// Фон - заливкой формы, рисунок поверх (без обрезки формой: правило CLAUDE.md о масках).
    private var illustration: some View {
        let aspect = image.map { $0.size.width / max($0.size.height, 1) } ?? Self.placeholderAspect
        return ZStack {
            RoundedRectangle(cornerRadius: Radius.box, style: .continuous)
                .fill(theme.palette.parchmentIllustration)
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .padding(Spacing.xs)
            }
        }
        .aspectRatio(aspect, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }

    private func legendRow(_ item: ReadingLegendItem) -> some View {
        HStack(spacing: Spacing.s) {
            RoundedRectangle(cornerRadius: Radius.small, style: .continuous)
                .fill(Self.color(item.rgb))
                .strokeBorder(colors.tileBorder, lineWidth: Size.hairline)
                .frame(width: swatchSize, height: swatchSize)
                .accessibilityHidden(true)
            LessonParagraph(text: item.text, color: theme.palette.onAccent)
        }
    }

    /// Цвет метки на рисунке - из данных (`#RRGGBB`), не токен темы.
    private static func color(_ rgb: UInt32) -> Color {
        SRGBColor(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        ).color
    }
}

/// Рисунки руководств - `images/reading/<имя>.webp`, в Bundle плоско. Чтение и расшифровка - не на главном
/// акторе: иначе первый кадр экрана ждал бы декодер WebP.
nonisolated enum GuideImageLoader {
    static let fileExtension = "webp"

    static func url(_ name: String, bundle: Bundle = .main) -> URL? {
        bundle.url(forResource: name, withExtension: fileExtension)
    }

    @concurrent
    static func load(_ name: String) async -> UIImage? {
        guard let url = url(name), let image = UIImage(contentsOfFile: url.path) else { return nil }
        return await image.byPreparingForDisplay() ?? image
    }
}

#Preview {
    NavigationStack {
        ReadingGuideView(guideID: "articulation")
    }
    .appEnvironment(.preview)
}
