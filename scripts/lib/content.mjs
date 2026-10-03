// Общие функции для data/*.json.
//
// Формат записи: языконезависимые поля (id, arabic, max, audio) — на верхнем уровне,
// всё, что зависит от языка перевода (text, translit, rawi, source), — в translations.<код языка>.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
export const DATA_DIR = join(ROOT, 'data');

// Язык, на котором написан прототип и который обязан быть у каждой записи.
export const BASE_LANGUAGE = 'ru';

export const readData = name => JSON.parse(readFileSync(join(DATA_DIR, name), 'utf8'));

export function loadAll() {
  return {
    azkar: readData('azkar.json'),
    hadith: { nawawi: readData('nawawi.json'), qudsi: readData('qudsi.json'), ajurri: readData('ajurri.json') },
  };
}

// Плоская запись в форме, которую понимает прототип (порядок ключей важен — от него зависит дифф).
export function legacyZikr(item, lang = BASE_LANGUAGE) {
  const t = item.translations?.[lang] ?? {};
  return {
    id: item.id, arabic: item.arabic, translit: t.translit, russian: t.text,
    source: t.source, max: item.max, audio: item.audio,
  };
}

export function legacyHadith(item, lang = BASE_LANGUAGE) {
  const t = item.translations?.[lang] ?? {};
  return { id: item.id, rawi: t.rawi, arabic: item.arabic, russian: t.text, source: t.source };
}
