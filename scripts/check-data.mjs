#!/usr/bin/env node
// Проверяет данные в data/*.json — единственном источнике данных приложения.
//
//   node scripts/check-data.mjs            — проверка, первые 5 предупреждений каждого типа
//   node scripts/check-data.mjs --verbose  — все предупреждения
//
// Код выхода 1 — есть ошибки (пустые обязательные поля, нет аудиофайла и т.п.).
// Предупреждения (подозрения на артефакты PDF) на код выхода не влияют.

import { readFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DATA_DIR = join(ROOT, 'data');
const AUDIO_DIR = join(ROOT, 'audio');

const VERBOSE = process.argv.includes('--verbose');

const EXPECTED = { morning: 16, evening: 16, nawawi: 50, qudsi: 40, ajurri: 40 };

function loadData() {
  const read = name => JSON.parse(readFileSync(join(DATA_DIR, name), 'utf8'));
  return {
    AZKAR: read('azkar.json'),
    HADITH_DATA: { nawawi: read('nawawi.json'), qudsi: read('qudsi.json'), ajurri: read('ajurri.json') },
  };
}

// ── Проверки ─────────────────────────────────────────────────

const errors = [];
const warnings = new Map(); // тип → [сообщения]

const error = (where, msg) => errors.push(`${where}: ${msg}`);
const warn = (kind, where, msg) => {
  if (!warnings.has(kind)) warnings.set(kind, []);
  warnings.get(kind).push(`${where}: ${msg}`);
};

const snippet = (text, index, radius = 30) =>
  JSON.stringify(text.slice(Math.max(0, index - radius), index + radius));

const ARABIC_MARKS = /[ً-ْٰ]/;
const CYRILLIC = /[А-Яа-яЁё]/;
const ARABIC = /[؀-ۿ]/;

function checkRussian(where, text) {
  // Разрыв абзаца посреди фразы: «…чтобы ты\n\nния, …» — потерянный кусок текста из PDF.
  for (const m of text.matchAll(/\n+\s*(?=[а-яё])/g)) {
    warn('Разрыв строки перед строчной буквой (возможна потеря текста из PDF)', where, snippet(text, m.index));
  }
  // Текст начинается с обрывка: «и предопределение его судьбы» …» — потеряно начало заголовка.
  if (/^[«"“]?[а-яё]/.test(text)) warn('Текст начинается со строчной буквы (потеряно начало)', where, snippet(text, 0));
  // «посланник Аллаха / сказал» — так в PDF отрисовался символ ﷺ.
  // «Хвала Аллаху» / Аль-хамду ли-Лляхи — допустимый разделитель перевода и транслитерации.
  for (const m of text.matchAll(/(?<!») \/ /g)) {
    warn('Косая черта вместо ﷺ', where, snippet(text, m.index));
  }
  if (/\n/.test(text)) warn('Переносы строк в русском тексте', where, `${text.split('\n').length - 1} шт.`);
  if (ARABIC.test(text.replace(/ﷺ/g, ''))) warn('Арабские символы в русском тексте', where, 'проверить');
  if (/ {2,}/.test(text)) warn('Двойные пробелы', where, 'в русском тексте');
}

// «Aбу Дауд» с латинской A — визуально не отличить, но ломает поиск и сортировку.
function checkMixedScript(where, field, text) {
  for (const m of text.matchAll(/[\p{L}]*(?:[A-Za-z][А-Яа-яЁё]|[А-Яа-яЁё][A-Za-z])[\p{L}]*/gu)) {
    warn('Латиница внутри русского слова', where, `${field}: «${m[0]}»`);
  }
}

function checkArabic(where, text) {
  // «رَضِيََ» — одна и та же огласовка дважды подряд: артефакт извлечения из PDF.
  for (const m of text.matchAll(/([ً-ْ])\1/g)) {
    warn('Повтор огласовки подряд (артефакт PDF)', where, snippet(text, m.index, 12));
  }
  // Огласовка в начале слова — оторвалась от буквы.
  for (const m of text.matchAll(/(?:^|\s)[ً-ْ]/g)) {
    warn('Огласовка без буквы', where, snippet(text, m.index, 12));
  }
  if (CYRILLIC.test(text) || /[A-Za-z]/.test(text)) error(where, 'латиница или кириллица в арабском тексте');
  if (!ARABIC_MARKS.test(text)) warn('Арабский текст без огласовок', where, 'проверить');
  if (/ {2,}/.test(text)) warn('Двойные пробелы', where, 'в арабском тексте');
}

function requireString(where, item, field) {
  if (typeof item[field] !== 'string' || !item[field].trim()) {
    error(where, `пустое или отсутствует поле «${field}»`);
    return false;
  }
  return true;
}

// id — номер хадиса в сборнике (для азкаров — номер внутри утренних/вечерних): 1, 2, 3… без пропусков.
function checkId(where, item, i) {
  if (item.id !== i + 1) error(where, `id должен быть ${i + 1} (порядковый номер), сейчас ${item.id}`);
}

function checkAzkar(AZKAR) {
  for (const type of ['morning', 'evening']) {
    const list = AZKAR[type];
    if (!Array.isArray(list)) { error(type, 'нет массива'); continue; }
    if (list.length !== EXPECTED[type]) error(type, `ожидалось ${EXPECTED[type]}, найдено ${list.length}`);

    const seenAudio = new Set();
    list.forEach((item, i) => {
      const where = `${type} id ${item.id ?? '?'}`;
      checkId(where, item, i);
      for (const f of ['arabic', 'translit', 'russian', 'source', 'audio']) requireString(where, item, f);
      if (!Number.isInteger(item.max) || item.max < 1) error(where, `некорректный max: ${item.max}`);
      if (item.arabic) checkArabic(where, item.arabic);
      if (item.russian) checkRussian(where, item.russian);
      for (const f of ['russian', 'translit', 'source']) if (item[f]) checkMixedScript(where, f, item[f]);

      const expectedAudio = `${type}_${String(i + 1).padStart(2, '0')}.mp3`;
      if (item.audio && item.audio !== expectedAudio) {
        warn('Имя аудио не совпадает с порядковым номером', where, `${item.audio}, ожидалось ${expectedAudio}`);
      }
      if (item.audio) {
        if (seenAudio.has(item.audio)) error(where, `аудио ${item.audio} уже используется другим зикром`);
        seenAudio.add(item.audio);
        if (!existsSync(join(AUDIO_DIR, type, item.audio))) error(where, `нет файла audio/${type}/${item.audio}`);
      }
    });
  }
  checkSharedAudio(AZKAR);
}

// Одинаковые MP3 у утреннего и вечернего зикра допустимы, только если и тексты совпадают.
function checkSharedAudio(AZKAR) {
  const byHash = new Map();
  for (const type of ['morning', 'evening']) {
    AZKAR[type].forEach((item, i) => {
      const path = join(AUDIO_DIR, type, item.audio ?? '');
      if (!item.audio || !existsSync(path)) return;
      const hash = createHash('md5').update(readFileSync(path)).digest('hex');
      if (!byHash.has(hash)) byHash.set(hash, []);
      byHash.get(hash).push({ type, i, item });
    });
  }
  const normalize = s => s.replace(/[ً-ْٰ\s،,.]/g, '');
  for (const group of byHash.values()) {
    if (group.length < 2) continue;
    const [a, ...rest] = group;
    for (const b of rest) {
      const where = `${a.type} id ${a.item.id} ↔ ${b.type} id ${b.item.id}`;
      if (normalize(a.item.arabic) !== normalize(b.item.arabic)) {
        error(where, `один и тот же аудиофайл (${a.item.audio} = ${b.item.audio}), но разный арабский текст`);
      }
    }
  }
}

function checkHadith(HADITH_DATA) {
  for (const type of ['nawawi', 'qudsi', 'ajurri']) {
    const list = HADITH_DATA[type];
    if (!Array.isArray(list)) { error(type, 'нет массива'); continue; }
    if (list.length !== EXPECTED[type]) error(type, `ожидалось ${EXPECTED[type]}, найдено ${list.length}`);

    const seenArabic = new Map();
    list.forEach((item, i) => {
      const where = `${type} id ${item.id ?? '?'}`;
      checkId(where, item, i);
      for (const f of ['rawi', 'arabic', 'russian', 'source']) requireString(where, item, f);
      if (item.arabic) {
        checkArabic(where, item.arabic);
        if (seenArabic.has(item.arabic)) error(where, `арабский текст совпадает с id ${seenArabic.get(item.arabic) + 1}`);
        seenArabic.set(item.arabic, i);
      }
      if (item.russian) checkRussian(where, item.russian);
      for (const f of ['russian', 'source', 'rawi']) if (item[f]) checkMixedScript(where, f, item[f]);
      // Арабский текст показывается только в карточке хадиса — передатчик пишется по-русски.
      if (item.rawi && ARABIC.test(item.rawi)) error(where, `rawi на арабском: ${item.rawi}`);
    });
  }
}

// ── Запуск ───────────────────────────────────────────────────

const { HADITH_DATA, AZKAR } = loadData();
checkAzkar(AZKAR);
checkHadith(HADITH_DATA);

const counts = Object.entries(EXPECTED)
  .map(([t]) => `${t}: ${(AZKAR[t] ?? HADITH_DATA[t])?.length ?? 0}`).join(', ');
console.log(`Записей — ${counts}\n`);

for (const [kind, list] of warnings) {
  console.log(`⚠ ${kind} — ${list.length}`);
  for (const line of VERBOSE ? list : list.slice(0, 5)) console.log(`    ${line}`);
  if (!VERBOSE && list.length > 5) console.log(`    … ещё ${list.length - 5} (--verbose)`);
}

if (errors.length) {
  console.log(`\n✖ Ошибок: ${errors.length}`);
  for (const e of errors) console.log(`    ${e}`);
  process.exit(1);
}
console.log(`\n✔ Ошибок нет. Предупреждений: ${[...warnings.values()].reduce((n, l) => n + l.length, 0)}`);