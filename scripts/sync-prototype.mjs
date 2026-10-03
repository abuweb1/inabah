#!/usr/bin/env node
// Переносит data/*.json в константы HADITH_DATA и AZKAR внутри inabah-prototype.html,
// чтобы правки данных можно было сразу посмотреть в прототипе.
//
//   node scripts/sync-prototype.mjs
//
// Данные правятся только в data/*.json — блок констант в прототипе перезаписывается целиком.
// Прототип знает только русский перевод в плоской форме (russian, rawi, translit, source) —
// записи разворачиваются из translations.ru.

import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { ROOT, loadAll, legacyZikr, legacyHadith } from './lib/content.mjs';

const PROTOTYPE = join(ROOT, 'inabah-prototype.html');
const data = loadAll();

const hadith = Object.fromEntries(
  Object.entries(data.hadith).map(([type, list]) => [type, list.map(h => legacyHadith(h))]));
const azkar = { morning: data.azkar.morning.map(z => legacyZikr(z)), evening: data.azkar.evening.map(z => legacyZikr(z)) };

const HEADER = '// Сгенерировано scripts/sync-prototype.mjs из data/*.json — не править вручную.\n';

const html = readFileSync(PROTOTYPE, 'utf8');
let start = html.indexOf('const HADITH_DATA');
const end = html.indexOf('</script>', html.indexOf('const AZKAR', start));
if (start < 0 || end < 0) throw new Error('Не найдены константы HADITH_DATA / AZKAR в прототипе');
// Заголовок — часть генерируемого блока: заменяется вместе с ним, а не дописывается заново.
while (html.slice(0, start).endsWith(HEADER)) start -= HEADER.length;

const block =
  HEADER +
  `const HADITH_DATA = ${JSON.stringify(hadith, null, 2)};\n\n` +
  `const AZKAR = ${JSON.stringify(azkar, null, 2)};\n`;

writeFileSync(PROTOTYPE, html.slice(0, start) + block + html.slice(end));
console.log('Прототип обновлён из data/*.json');
