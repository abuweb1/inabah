#!/usr/bin/env python3
"""Генерирует цвета вариантов «Фона арабского текста» (ParchmentStyle) для iOS.

    python3 scripts/generate-parchment-palettes.py          # записать ассеты
    python3 scripts/generate-parchment-palettes.py --check  # сверить ассеты с таблицей (код 1 — расходятся)

Вывод: Assets.xcassets/Palette/Parchment/<Вариант>/parchment<Вариант><Токен>.colorset.
Значения — один в один из Android `core/designsystem/ParchmentStyle.kt` (решение пользователя
2026-10-06: цвета как на Android). Вариант «Пергамент» (`classic`) использует нынешние ассеты
(parchmentLight, gold, parchmentMid, parchmentDeep, parchmentInk, successDeep) и здесь не создаётся.

HEX живут только в ассетах и в этой таблице (правило CLAUDE.md). Токены и порядок — как
в `ParchmentStyle.swift` (`ParchmentToken`).
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'Inabah', 'Resources', 'Assets.xcassets', 'Palette', 'Parchment')

TOKENS = ['Light', 'Highlight', 'Mid', 'Deep', 'Text', 'Accent']

# Вариант → light, highlight, mid, deep, текст, акцент (рамка, «✦», значок «N раз»).
VARIANTS = {
    # Тёплая старая бумага — к «Янтарной». Акцент темнее successDeep: на deep тот давал 4,35 < 4,5.
    'Sepia':    ['E9D2AE', 'DEC39A', 'D9BD94', 'C9A97C', '2B1D10', '1F4A2A'],
    # Приглушённый лиловый с золотой рамкой — к «Фиолетовой».
    'Amethyst': ['4A3A6E', '54427C', '46376A', '3B2E5C', 'F3EEFA', 'ECC87B'],
    # Глубокий зелёный с золотой рамкой — к «Изумрудной».
    'Jade':     ['1C4D40', '21584A', '1B4A3D', '153D32', 'EDF7F2', 'ECC87B'],
    # Приглушённый медово-коричневый с золотой рамкой — к «Янтарной».
    'Amber':    ['5A3818', '664020', '553415', '472B10', 'F8EEDF', 'ECC87B'],
    # Дымчатый серо-голубой — к «Графиту».
    'Smoky':    ['3A414F', '434B5B', '39404D', '2F3541', 'EEF1F6', 'B4CBF4'],
    # Тёмный — к любой палитре, для чтения в темноте.
    'Night':    ['2E2A40', '352F4C', '2B2640', '221E34', 'F0EAF8', '6DBF7E'],
}

INFO = {'author': 'xcode', 'version': 1}


def colorset(hex_value):
    r, g, b = hex_value[0:2], hex_value[2:4], hex_value[4:6]
    return {
        'colors': [{
            'color': {
                'color-space': 'srgb',
                'components': {'alpha': '1.000', 'red': f'0x{r}', 'green': f'0x{g}', 'blue': f'0x{b}'},
            },
            'idiom': 'universal',
        }],
        'info': INFO,
    }


def expected_files():
    files = {}
    for variant, values in VARIANTS.items():
        folder = os.path.join(OUT, variant)
        files[os.path.join(folder, 'Contents.json')] = {'info': INFO}
        for token, value in zip(TOKENS, values):
            name = f'parchment{variant}{token}'
            files[os.path.join(folder, f'{name}.colorset', 'Contents.json')] = colorset(value)
    return files


def dump(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + '\n'


def main():
    check = '--check' in sys.argv
    mismatched = []
    for path, data in expected_files().items():
        text = dump(data)
        if check:
            actual = open(path, encoding='utf-8').read() if os.path.exists(path) else None
            if actual is None or json.loads(actual) != data:
                mismatched.append(os.path.relpath(path, ROOT))
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(text)
    if check:
        if mismatched:
            print('Ассеты пергамента расходятся с таблицей:', *mismatched, sep='\n  ')
            sys.exit(1)
        print(f'Ассеты пергамента совпадают с таблицей ({len(VARIANTS)} вариантов × {len(TOKENS)} цветов).')
    else:
        print(f'Записано: {len(VARIANTS)} вариантов × {len(TOKENS)} цветов в {os.path.relpath(OUT, ROOT)}')


if __name__ == '__main__':
    main()
