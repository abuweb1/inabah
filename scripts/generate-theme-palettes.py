#!/usr/bin/env python3
"""Генерирует цвета единых стилей оформления (ThemeStyle) в Assets.xcassets/Palette/Themes.

    python3 scripts/generate-theme-palettes.py

Опорные цвета берутся из нынешних ассетов разделов:
  violet   — азкары (фон, шапка, поверхности, акцент, карточки утро/вечер);
  emerald  — хадисы (фон, шапка, карточки трёх сборников);
  amber    — «Махрадж» (фон);
  graphite — настройки (фон).
Недостающие цвета стиля выводятся из опорного стиля поворотом тона в OKLCH при той же
светлоте; насыщенность масштабируется по отношению насыщенностей фонов. Цвета вне sRGB
приводятся в охват уменьшением насыщенности.

HEX живут только в ассетах (правило CLAUDE.md); этот скрипт хранит, как они получены.
Имя ассета — <стиль><Токен>, токены — ThemeColorToken в Core/DesignSystem/ThemeStyle.swift.
"""
import json
import math
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PALETTE = os.path.join(ROOT, 'Inabah', 'Resources', 'Assets.xcassets', 'Palette')
OUT = os.path.join(PALETTE, 'Themes')

# --- цветовые пространства -------------------------------------------------

def _lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _unlin(c):
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def hex_to_oklch(h):
    r, g, b = (_lin(int(h[i:i + 2], 16) / 255) for i in (0, 2, 4))
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return L, math.hypot(a, bb), math.degrees(math.atan2(bb, a)) % 360


def _oklch_to_linear(L, C, H):
    a, b = C * math.cos(math.radians(H)), C * math.sin(math.radians(H))
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076127119 * s)


def oklch_to_hex(L, C, H):
    """Вне охвата sRGB — уменьшаем насыщенность, тон и светлота сохраняются."""
    lo, hi = 0.0, C
    rgb = _oklch_to_linear(L, C, H)
    if any(v < -1e-4 or v > 1 + 1e-4 for v in rgb):
        for _ in range(30):
            mid = (lo + hi) / 2
            if any(v < -1e-4 or v > 1 + 1e-4 for v in _oklch_to_linear(L, mid, H)):
                hi = mid
            else:
                lo = mid
        rgb = _oklch_to_linear(L, lo, H)
    return ''.join(f"{round(_unlin(min(max(v, 0), 1)) * 255):02X}" for v in rgb)

# --- исходные ассеты -------------------------------------------------------

def read_asset(name):
    for dirpath, dirnames, _ in os.walk(PALETTE):
        if os.path.basename(dirpath) == 'Themes':
            dirnames.clear()
            continue
        if f'{name}.colorset' in dirnames:
            comp = json.load(open(os.path.join(dirpath, f'{name}.colorset', 'Contents.json')))['colors'][0]['color']['components']
            return ''.join(comp[k][2:] for k in ('red', 'green', 'blue'))
    raise SystemExit(f'нет ассета {name}')


CARDS = ['morning', 'evening', 'nawawi', 'qudsi', 'ajurri']
STOPS = ['Start', 'Mid', 'End']

# Опорные цвета стиля «violet» — нынешние азкары.
VIOLET = {
    'backgroundTop': 'azkarBackgroundTop', 'backgroundMid': 'azkarBackgroundMid', 'backgroundBottom': 'azkarBackgroundBottom',
    'header': 'headerBackground', 'eveningHeader': 'eveningCardStart',
    'surface': 'appBackground', 'card': 'cardBackground', 'action': 'actionBackground',
    'accent': 'accentPurple', 'accentLight': 'accentPurpleLight', 'accentDim': 'accentPurpleDim', 'accentShadow': 'shadowPurple',
    'textSecondary': 'textSecondary', 'textTertiary': 'textTertiary', 'tab': 'tabAzkar', 'cardShadow': 'shadowPurple',
    **{f'{c}Card{s}': f'{c}Card{s}' for c in ('morning', 'evening') for s in STOPS},
}
# Карточки сборников — нынешние хадисы: из них выводятся карточки сборников других стилей.
EMERALD_CARDS = {f'{c}Card{s}': f'{c}Card{s}' for c in ('nawawi', 'qudsi', 'ajurri') for s in STOPS}

# Свои опорные цвета каждого стиля (что совпадает с разделом), остальное — выводится.
STYLES = {
    'violet': {'own': VIOLET},
    'emerald': {'own': {
        'backgroundTop': 'hadithBackgroundTop', 'backgroundMid': 'hadithBackgroundMid', 'backgroundBottom': 'hadithBackgroundBottom',
        'header': 'hadithHeaderBackground', 'tab': 'tabHadith', 'cardShadow': 'successDeep', **EMERALD_CARDS}},
    'amber': {'own': {
        'backgroundTop': 'makharijBackgroundTop', 'backgroundMid': 'makharijBackgroundMid', 'backgroundBottom': 'makharijBackgroundBottom',
        'tab': 'tabMakharij'},
        # Ан-Навави в янтаре выходил ярко-оранжевым: тот же тон и насыщенность, что у
        # аль-Аджурри, только светлее на пару ступеней (решение пользователя).
        'from_card': {'nawawiCard': ('ajurriCard', 0.09)}},
    'graphite': {'own': {
        'backgroundTop': 'settingsBackgroundTop', 'backgroundMid': 'settingsBackgroundMid', 'backgroundBottom': 'settingsBackgroundBottom',
        'tab': 'tabSettings'},
        'lightness': {'nawawiCard': -0.05}},
}
# Акцент, вкладка и шапки — не тусклее этой доли насыщенности опорного стиля
# (иначе у графита акцент становится неотличимым от серого текста).
ACCENT_TOKENS = {'accent', 'accentLight', 'accentDim', 'accentShadow', 'cardShadow', 'header', 'eveningHeader'}
ACCENT_MIN_CHROMA = 0.55
# Карточки держатся в тоне стиля: отклонение тона от основного — не больше этого (градусы).
# Различие карточек между собой — в первую очередь светлотой (утро светлее вечера и т. д.).
CARD_MAX_HUE_OFFSET = 15
# Насыщенность выведенных карточек не усиливается больше чем в столько раз: фон фиолетового
# и янтарного стилей насыщеннее изумрудного, и без ограничения карточки «кислотные».
CARD_MAX_CHROMA_SCALE = 1.25

TOKENS = list(VIOLET) + list(EMERALD_CARDS)


def _hue_delta(a, b):
    return (a - b + 180) % 360 - 180


def transform(src_hex, src_style_mid, dst_style_mid, token, lightness_shift=0.0):
    L, C, H = hex_to_oklch(src_hex)
    L += lightness_shift
    _, src_c, src_h = hex_to_oklch(src_style_mid)
    _, dst_c, dst_h = hex_to_oklch(dst_style_mid)
    k = dst_c / src_c if src_c else 1
    if token in ACCENT_TOKENS:
        k = max(k, ACCENT_MIN_CHROMA)
    offset = _hue_delta(H, src_h)
    if 'Card' in token and token != 'cardShadow':
        offset = max(-CARD_MAX_HUE_OFFSET, min(CARD_MAX_HUE_OFFSET, offset))
        k = min(k, CARD_MAX_CHROMA_SCALE)
    return oklch_to_hex(L, C * k, (dst_h + offset) % 360)


def main():
    violet_mid = read_asset('azkarBackgroundMid')
    emerald_mid = read_asset('hadithBackgroundMid')
    result = {}
    for style, spec in STYLES.items():
        own = spec['own']
        mid = read_asset(own['backgroundMid'])
        # Точечная поправка светлоты карточек стиля (OKLCH L): {'nawawiCard': -0.1}.
        shifts = spec.get('lightness', {})
        colors = {}
        for token in TOKENS:
            shift = next((d for prefix, d in shifts.items() if token.startswith(prefix)), 0.0)
            if token in own:
                colors[token] = read_asset(own[token])
            elif token in EMERALD_CARDS:
                colors[token] = transform(read_asset(EMERALD_CARDS[token]), emerald_mid, mid, token, shift)
            else:
                colors[token] = transform(read_asset(VIOLET[token]), violet_mid, mid, token, shift)
        # Карточка по образцу другой карточки стиля: её тон и насыщенность, светлота + delta.
        for target, (source, delta) in spec.get('from_card', {}).items():
            for stop in STOPS:
                L, C, H = hex_to_oklch(colors[source + stop])
                colors[target + stop] = oklch_to_hex(L + delta, C, H)
        result[style] = colors

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    info = {'info': {'author': 'xcode', 'version': 1}}
    json.dump(info, open(os.path.join(OUT, 'Contents.json'), 'w'), indent=2)
    for style, colors in result.items():
        folder = os.path.join(OUT, style.capitalize())
        os.makedirs(folder)
        json.dump(info, open(os.path.join(folder, 'Contents.json'), 'w'), indent=2)
        for token, h in colors.items():
            name = style + token[0].upper() + token[1:]
            d = os.path.join(folder, f'{name}.colorset')
            os.makedirs(d)
            json.dump({'colors': [{'color': {'color-space': 'srgb', 'components': {
                'alpha': '1.000', 'red': f'0x{h[0:2]}', 'green': f'0x{h[2:4]}', 'blue': f'0x{h[4:6]}'}},
                'idiom': 'universal'}], **info}, open(os.path.join(d, 'Contents.json'), 'w'), indent=2)
        print(style, ' '.join(f'{t}=#{h}' for t, h in colors.items()))
    print(f'Токенов на стиль: {len(TOKENS)}, ассеты — {os.path.relpath(OUT, ROOT)}')


if __name__ == '__main__':
    main()
