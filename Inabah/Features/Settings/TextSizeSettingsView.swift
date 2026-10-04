import SwiftUI

/// Размер текста: переводы (5 шагов) и интерфейс (3 шага), у каждого — живой образец.
/// Системный размер текста iOS на эти части приложения не влияет.
struct TextSizeSettingsView: View {
    @Environment(TextSizeSettings.self) private var settings
    @Environment(AzkarStore.self) private var azkarStore
    @Environment(HadithStore.self) private var hadithStore
    @Environment(\.theme) private var theme

    var body: some View {
        Form {
            Section {
                StepSlider(
                    label: "settings.textSize.content.label",
                    steps: ContentTextSize.allCases,
                    selection: settings.content,
                    title: \.title
                ) { size in
                    withAnimation(Motion.fontSize) { settings.select(content: size) }
                }
                .settingsRow()
                if let translation = sampleZikr?.translation {
                    ZikrTranslationView(translation: translation)
                        .padding(Spacing.l)
                        .surface(theme.palette.card, cornerRadius: Radius.box)
                        .padding(.vertical, Spacing.xs)
                        .settingsRow()
                }
            } header: {
                Text("settings.textSize.content.header")
            } footer: {
                Text("settings.textSize.content.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }

            Section {
                // Смена размера интерфейса перестраивает всё дерево — без анимации.
                StepSlider(
                    label: "settings.textSize.interface.label",
                    steps: InterfaceTextSize.allCases,
                    selection: settings.interface,
                    title: \.title
                ) { settings.select(interface: $0) }
                .settingsRow()
                if let hadith = sampleHadith {
                    HadithRow(hadith: hadith, status: .read)
                        .settingsRow()
                }
            } header: {
                Text("settings.textSize.interface.header")
            } footer: {
                Text("settings.textSize.interface.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .navigationTitle(Text("settings.textSize.title"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await azkarStore.load(.morning) }
        .task { await hadithStore.load(.nawawi) }
        .audioPlayerInset()
    }

    /// Образцы — настоящие тексты; пока раздел не загружен — из набора превью. Зикр — с самым
    /// коротким переводом: длинный отодвигал секцию «Интерфейс» за край экрана.
    private var sampleZikr: Zikr? {
        let azkar = azkarStore.sessions(in: .morning).map(\.zikr)
        let candidates = azkar.isEmpty ? (InMemoryContentRepository.preview.azkar[.morning] ?? []) : azkar
        return candidates
            .filter { $0.translation != nil }
            .min { ($0.translation?.text.count ?? 0) < ($1.translation?.text.count ?? 0) }
    }

    private var sampleHadith: Hadith? {
        hadithStore.hadiths(in: .nawawi).first
            ?? InMemoryContentRepository.preview.hadiths[.nawawi]?.first
    }
}

/// Название выбранного шага и ползунок с шагами между значками «меньше» / «больше».
private struct StepSlider<Step: Hashable>: View {
    let label: LocalizedStringResource
    let steps: [Step]
    let selection: Step
    let title: KeyPath<Step, LocalizedStringResource>
    let onSelect: (Step) -> Void

    @Environment(\.theme) private var theme

    private var index: Binding<Double> {
        Binding {
            Double(steps.firstIndex(of: selection) ?? 0)
        } set: { value in
            let position = Int(value.rounded()).clamped(to: 0...(steps.count - 1))
            onSelect(steps[position])
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(selection[keyPath: title])
                .font(.subheadline.weight(.semibold))
            Slider(value: index, in: 0...Double(steps.count - 1), step: 1) {
                Text(label)
            } minimumValueLabel: {
                Image(systemName: "textformat.size.smaller")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            } maximumValueLabel: {
                Image(systemName: "textformat.size.larger")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
            .accessibilityValue(Text(selection[keyPath: title]))
        }
        .padding(.vertical, Spacing.xxs)
    }
}

private extension ContentTextSize {
    var title: LocalizedStringResource {
        switch self {
        case .smaller: "textSize.content.smaller"
        case .standard: "textSize.content.standard"
        case .larger: "textSize.content.larger"
        case .large: "textSize.content.large"
        case .largest: "textSize.content.largest"
        }
    }
}

private extension InterfaceTextSize {
    var title: LocalizedStringResource {
        switch self {
        case .standard: "textSize.interface.standard"
        case .larger: "textSize.interface.larger"
        case .largest: "textSize.interface.largest"
        }
    }
}

#Preview {
    NavigationStack {
        TextSizeSettingsView()
    }
    .appEnvironment(.preview)
}
