# -*- coding: utf-8 -*-
"""
Рисунки «Учимся читать» (руководство «Межзубные буквы и дод», 2026-10-21): из исходных PNG пользователя
вырезает рисунок без рамки, кремового фона и впечатанного текста, чтобы приложение клало его на пергамент
любой темы. Фон панели убирается заливкой от краёв (только кремовые пиксели, связанные с краем), край -
мягкий; углы скругляются по форме панели. Результат - WebP с прозрачностью в images/reading/.

Запуск (нужен Pillow и numpy):  python3 scripts/cut-reading-images.py <папка с исходными PNG>
Цвета квадратов на нёбе (легенда в data/reading.json) - печатаются в конце.
"""
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'images' / 'reading'

# Файл, исходник, рамка панели (x0, y0, x1, y1) - только рисунок, без подписей и легенды.
CUTS = [
    ('interdental-sa-za', 'за_са_зо.PNG', (52, 156, 740, 752)),
    ('interdental-zo', 'за_са_зо.PNG', (795, 156, 1483, 752)),
    ('dod-place', 'дод.PNG', (35, 170, 811, 961)),
    ('dod-air', 'дод_.PNG', (27, 170, 851, 967)),
    ('dod-palate', 'дод_.PNG', (876, 170, 1509, 750)),
]
# Отступ внутрь панели (золотая кайма) и радиус скругления.
INSET = 10
RADIUS = 26
# Насколько пиксель может отличаться от фона панели и всё ещё быть фоном; от него до SOFT - полупрозрачный край.
HARD = 30
SOFT = 60
MAX_WIDTH = 900
# Стереть с рисунка (x0, y0, x1, y1 в пикселях результата, с обводкой, не задевая соседей) - решение пользователя
# 2026-10-21: в нижнем ряду по 2 квадрата с каждой стороны - у зубов; внутренние (ближе к середине нёба) убраны.
ERASE = {'dod-palate': [(166, 415, 194, 446), (400, 415, 428, 446)]}


def background_color(a):
    """Фон панели - самый частый светлый цвет у её краёв."""
    edge = np.concatenate([a[:4].reshape(-1, 3), a[-4:].reshape(-1, 3), a[:, :4].reshape(-1, 3), a[:, -4:].reshape(-1, 3)])
    light = edge[edge.sum(1) > 600]
    return np.median(light, axis=0)


def cut(src, box):
    x0, y0, x1, y1 = box
    a = np.asarray(src.convert('RGB').crop((x0 + INSET, y0 + INSET, x1 - INSET, y1 - INSET))).astype(float)
    h, w, _ = a.shape
    dist = np.sqrt(((a - background_color(a)) ** 2).sum(2))
    candidate = dist < SOFT
    region = np.zeros((h, w), bool)
    queue = deque()
    for y in range(h):
        for x in (0, w - 1):
            if dist[y, x] < HARD:
                region[y, x] = True
                queue.append((y, x))
    for x in range(w):
        for y in (0, h - 1):
            if dist[y, x] < HARD and not region[y, x]:
                region[y, x] = True
                queue.append((y, x))
    # Заливка идёт по твёрдому фону; полупрозрачный край - только сосед твёрдого.
    while queue:
        y, x = queue.popleft()
        if dist[y, x] >= HARD:
            continue
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < h and 0 <= nx < w and not region[ny, nx] and candidate[ny, nx]:
                region[ny, nx] = True
                queue.append((ny, nx))
    alpha = np.ones((h, w))
    alpha[region] = np.clip((dist[region] - HARD) / (SOFT - HARD), 0, 1)
    # Полупрозрачный край без кремовой каймы: цвет тянем от фона к рисунку.
    bg = background_color(a)
    edge = region & (alpha > 0)
    a[edge] = np.clip(bg + (a[edge] - bg) / alpha[edge][:, None], 0, 255)
    alpha *= rounded_mask(w, h, RADIUS)
    rgba = np.dstack([a, alpha * 255]).astype(np.uint8)
    img = Image.fromarray(rgba)
    if img.width > MAX_WIDTH:
        img = img.resize((MAX_WIDTH, round(img.height * MAX_WIDTH / img.width)), Image.LANCZOS)
    return img


def erase(img, points):
    """Закрашивает квадраты цветом соседнего нёба: заливка дыры усреднением соседей, края смягчены."""
    a = np.asarray(img).astype(float)
    h, w = a.shape[:2]
    hole = np.zeros((h, w), bool)
    for x0, y0, x1, y1 in points:
        hole[y0:y1 + 1, x0:x1 + 1] = True
    rgb = a[..., :3].copy()
    known = (~hole).astype(float)
    while (known[hole] == 0).any():
        total = np.zeros_like(rgb)
        count = np.zeros((h, w))
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            total += np.roll(rgb * known[..., None], (dy, dx), (0, 1))
            count += np.roll(known, (dy, dx), (0, 1))
        fill = hole & (known == 0) & (count > 0)
        rgb[fill] = total[fill] / count[fill][:, None]
        known[fill] = 1
    a[..., :3] = rgb
    smooth = Image.fromarray(a.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    out = np.asarray(img).copy()
    out[hole] = np.asarray(smooth)[hole]
    return Image.fromarray(out)


def rounded_mask(w, h, r):
    yy, xx = np.mgrid[0:h, 0:w]
    cx = np.clip(xx, r, w - 1 - r)
    cy = np.clip(yy, r, h - 1 - r)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    return np.clip(r - d + 0.5, 0, 1)


def palate_colors(img):
    """Цвета квадратов на нёбе: светлый (касание) и тёмный (давление) - насыщенные фиолетовые пиксели."""
    a = np.asarray(img.convert('RGBA')).astype(int)
    rgb, al = a[..., :3], a[..., 3]
    purple = (al == 255) & (rgb[..., 2] > rgb[..., 1] + 40) & (rgb[..., 0] > rgb[..., 1] + 10)
    px = rgb[purple]
    light = px[px.sum(1) > 380]
    dark = rgb[(al == 255) & (rgb.sum(2) < 120) & (rgb[..., 2] > rgb[..., 1] + 15)]
    return ['#%02X%02X%02X' % tuple(np.median(p, axis=0).astype(int)) for p in (light, dark)]


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'android' / 'docs'
    OUT.mkdir(parents=True, exist_ok=True)
    for name, file, box in CUTS:
        img = cut(Image.open(source / file), box)
        if name in ERASE:
            img = erase(img, ERASE[name])
        img.save(OUT / f'{name}.webp', 'WEBP', quality=88, method=6)
        print(f'{name}.webp  {img.width}x{img.height}')
        if name == 'dod-palate':
            print('  легенда:', ', '.join(palate_colors(img)))


if __name__ == '__main__':
    main()
