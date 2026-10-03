#!/usr/bin/env node
// Переносит data/*.json в константы HADITH_DATA и AZKAR внутри inabah-prototype.html,
// чтобы правки данных можно было сразу посмотреть в прототипе.
//
//   node scripts/sync-prototype.mjs
//
// Данные правятся только в data/*.json — блок констант в прототипе перезаписывается целиком.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const PROTOTYPE = join(ROOT, 'inabah-prototype.html');
const read = name => JSON.parse(readFileSync(join(ROOT, 'data', name), 'utf8'));

const hadith = { nawawi: read('nawawi.json'), qudsi: read('qudsi.json'), ajurri: read('ajurri.json') };
const azkar = read('azkar.json');

const html = readFileSync(PROTOTYPE, 'utf8');
const start = html.indexOf('const HADITH_DATA');
const end = html.indexOf('</script>', html.indexOf('const AZKAR', start));
if (start < 0 || end < 0) throw new Error('Не найдены константы HADITH_DATA / AZKAR в прототипе');

const block =
  '// Сгенерировано scripts/sync-prototype.mjs из data/*.json — не править вручную.\n' +
  `const HADITH_DATA = ${JSON.stringify(hadith, null, 2)};\n\n` +
  `const AZKAR = ${JSON.stringify(azkar, null, 2)};\n`;

writeFileSync(PROTOTYPE, html.slice(0, start) + block + html.slice(end));
console.log('Прототип обновлён из data/*.json');
