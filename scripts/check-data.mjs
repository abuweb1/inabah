#!/usr/bin/env node
// Проверяет данные в data/*.json — единственном источнике данных приложения.
//
// Сначала проверяется структура записи (translations.<язык>), затем содержимое:
// тексты базового языка (ru) разворачиваются в плоскую форму и проверяются по полям
// russian / translit / rawi / source — так в сообщениях называются translations.ru.text и др.
//
//   node scripts/check-data.mjs            — проверка, первые 5 предупреждений каждого типа
//   node scripts/check-data.mjs --verbose  — все предупреждения
//
// Плюс data/collections.json — сведения о сборниках для экрана «О сборнике»,
// и data/reminders.json — тексты напоминаний (общие для iOS и Android).
//
// Код выхода 1 — есть ошибки (пустые обязательные поля, нет аудиофайла и т.п.).
// Предупреждения (подозрения на артефакты PDF) на код выхода не влияют.

import { readFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
import { ROOT, BASE_LANGUAGE, loadAll, readData, legacyZikr, legacyHadith } from './lib/content.mjs';

const AUDIO_DIR = join(ROOT, 'audio');

const VERBOSE = process.argv.includes('--verbose');

const EXPECTED = { morning: 16, evening: 16, nawawi: 50, qudsi: 40, ajurri: 40 };

const AZKAR_KEYS = ['id', 'arabic', 'max', 'audio', 'translations'];
const HADITH_KEYS = ['id', 'arabic', 'translations'];
const AZKAR_TRANSLATION_KEYS = ['translit', 'text', 'source'];
const HADITH_TRANSLATION_KEYS = ['rawi', 'text', 'source', 'notes'];
const NOTE_KEYS = ['term', 'text'];
const COLLECTION_KEYS = ['translations'];
const COLLECTION_TRANSLATION_KEYS = ['authors', 'about'];
const AUTHOR_KEYS = ['name', 'years'];

// reminders.json: translations.<язык>.<вид>[.<раздел>] = { title, body }; в каждом виде — свои подстановки.
const AZKAR_SECTIONS = ['morning', 'evening'];
const REMINDER_KINDS = {
  azkarStart: { sections: AZKAR_SECTIONS, placeholders: [] },
  azkarUnfinished: { sections: AZKAR_SECTIONS, placeholders: [] },
  hadithOfDay: { sections: null, placeholders: ['collection', 'number', 'text'] },
};
const REMINDER_TEXT_KEYS = ['title', 'body'];

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
      // `audio` необязательно: записей пока нет (с 2026-10-06). Если поле есть — файл должен лежать в audio/.
      for (const f of ['arabic', 'translit', 'russian', 'source']) requireString(where, item, f);
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

// collections.json: { <сборник>: { translations: { <язык>: { authors?: [{ name, years? }], about: [абзац…] } } } }.
// Названия сборников — в приложении, число хадисов — длина data/<сборник>.json: сюда не дублируются.
function checkCollections(collections, hadithTypes) {
  if (!collections || typeof collections !== 'object' || Array.isArray(collections)) {
    error('collections', 'ожидается объект { <сборник>: … }');
    return;
  }
  for (const type of hadithTypes) if (!(type in collections)) error('collections', `нет сведений о сборнике «${type}»`);
  for (const [type, entry] of Object.entries(collections)) {
    const where = `collections ${type}`;
    if (!hadithTypes.includes(type)) error(where, `нет такого сборника (data/${type}.json)`);
    for (const k of Object.keys(entry ?? {})) if (!COLLECTION_KEYS.includes(k)) error(where, `неизвестное поле «${k}»`);
    const translations = entry?.translations;
    if (!translations || typeof translations !== 'object' || Array.isArray(translations)) {
      error(where, 'нет объекта translations');
      continue;
    }
    if (!translations[BASE_LANGUAGE]) error(where, `нет перевода translations.${BASE_LANGUAGE}`);
    for (const [lang, t] of Object.entries(translations)) {
      const at = `${where} ${lang}`;
      if (!/^[a-z]{2}$/.test(lang)) error(where, `код языка «${lang}» — ожидается ISO 639-1 (ru, en…)`);
      for (const k of Object.keys(t ?? {})) {
        if (!COLLECTION_TRANSLATION_KEYS.includes(k)) error(where, `неизвестное поле translations.${lang}.${k}`);
      }
      // authors — необязательно (у подборки может не быть известного составителя), но не пустой массив.
      if (t?.authors !== undefined) {
        if (!Array.isArray(t.authors) || !t.authors.length) error(at, 'authors — непустой массив или поля нет');
        else t.authors.forEach((a, i) => {
          for (const k of Object.keys(a ?? {})) if (!AUTHOR_KEYS.includes(k)) error(at, `неизвестное поле authors[${i}].${k}`);
          // { name: "", years: "" } — заготовка «составитель не указан»: приложение её не показывает.
          if (a?.name === '' && !a.years) return;
          if (requireString(at, a ?? {}, 'name')) checkMixedScript(at, 'authors.name', a.name);
          if (a?.years !== undefined) requireString(at, a, 'years');
        });
      }
      if (!Array.isArray(t?.about) || !t.about.length) {
        error(at, 'about — непустой массив абзацев');
        continue;
      }
      t.about.forEach((paragraph, i) => {
        if (typeof paragraph !== 'string' || !paragraph.trim()) { error(at, `about[${i}] — пустой абзац`); return; }
        if (lang === BASE_LANGUAGE) checkRussian(`${at} about[${i}]`, paragraph);
        checkMixedScript(at, `about[${i}]`, paragraph);
      });
    }
  }
}

// Сноски к словам перевода: notes — массив { term, text } (пустой — сносок пока нет).
// term — слово или выражение из text перевода; пока способ показа не решён, несовпадение — предупреждение.
function checkNotes(collections) {
  const fold = s => s.toLowerCase().replace(/ё/g, 'е').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
  for (const [type, list] of Object.entries(collections)) {
    for (const item of list ?? []) {
      for (const [lang, t] of Object.entries(item.translations ?? {})) {
        if (t?.notes === undefined) continue;
        const where = `${type} id ${item.id ?? '?'}`;
        if (!Array.isArray(t.notes)) { error(where, `translations.${lang}.notes — ожидается массив`); continue; }
        t.notes.forEach((note, i) => {
          const at = `${where} notes[${i}]`;
          for (const k of Object.keys(note ?? {})) if (!NOTE_KEYS.includes(k)) error(at, `неизвестное поле «${k}»`);
          const hasTerm = requireString(at, note ?? {}, 'term');
          // Пояснение может приводить арабский термин и продолжать term со строчной — проверки checkRussian не для него.
          if (requireString(at, note ?? {}, 'text')) checkMixedScript(at, 'notes.text', note.text);
          if (hasTerm && typeof t.text === 'string' && !fold(t.text).includes(fold(note.term))) {
            warn('Слово сноски не найдено в тексте перевода', at, `«${note.term}»`);
          }
        });
      }
    }
  }
}

// reminders.json: у каждого языка все виды (и разделы азкаров), непустые title/body,
// подстановки {…} — только свои для вида (неизвестная — ошибка: приложение её не заменит).
function checkReminders(reminders) {
  const translations = reminders?.translations;
  if (!translations || typeof translations !== 'object' || Array.isArray(translations)) {
    error('reminders', 'нет объекта translations');
    return;
  }
  for (const k of Object.keys(reminders)) if (k !== 'translations') error('reminders', `неизвестное поле «${k}»`);
  if (!translations[BASE_LANGUAGE]) error('reminders', `нет перевода translations.${BASE_LANGUAGE}`);
  const checkText = (where, text, placeholders) => {
    for (const k of Object.keys(text ?? {})) if (!REMINDER_TEXT_KEYS.includes(k)) error(where, `неизвестное поле «${k}»`);
    for (const field of REMINDER_TEXT_KEYS) {
      if (!requireString(where, text ?? {}, field)) continue;
      for (const [, name] of text[field].matchAll(/\{(\w*)\}/g)) {
        if (!placeholders.includes(name)) error(where, `${field}: неизвестная подстановка {${name}} (можно: ${placeholders.map(p => `{${p}}`).join(', ') || 'никаких'})`);
      }
      checkMixedScript(where, field, text[field]);
    }
  };
  for (const [lang, t] of Object.entries(translations)) {
    const at = `reminders ${lang}`;
    if (!/^[a-z]{2}$/.test(lang)) error('reminders', `код языка «${lang}» — ожидается ISO 639-1 (ru, en…)`);
    for (const k of Object.keys(t ?? {})) if (!(k in REMINDER_KINDS)) error(at, `неизвестный вид «${k}»`);
    for (const [kind, spec] of Object.entries(REMINDER_KINDS)) {
      const entry = t?.[kind];
      if (!entry) { error(at, `нет вида «${kind}»`); continue; }
      if (!spec.sections) { checkText(`${at} ${kind}`, entry, spec.placeholders); continue; }
      for (const k of Object.keys(entry)) if (!spec.sections.includes(k)) error(`${at} ${kind}`, `неизвестный раздел «${k}»`);
      for (const section of spec.sections) {
        if (!entry[section]) error(`${at} ${kind}`, `нет раздела «${section}»`);
        else checkText(`${at} ${kind}.${section}`, entry[section], spec.placeholders);
      }
    }
  }
}

// Структура: известные ключи, id первым, translations с базовым языком и кодами ISO 639-1.
function checkStructure(where, item, keys, translationKeys) {
  const actual = Object.keys(item);
  if (actual[0] !== 'id') error(where, 'id должен быть первым полем');
  for (const k of actual) if (!keys.includes(k)) error(where, `неизвестное поле «${k}»`);
  const translations = item.translations;
  if (!translations || typeof translations !== 'object' || Array.isArray(translations)) {
    error(where, 'нет объекта translations');
    return;
  }
  if (!translations[BASE_LANGUAGE]) error(where, `нет перевода translations.${BASE_LANGUAGE}`);
  for (const [lang, t] of Object.entries(translations)) {
    if (!/^[a-z]{2}$/.test(lang)) error(where, `код языка «${lang}» — ожидается ISO 639-1 (ru, en…)`);
    for (const k of Object.keys(t ?? {})) {
      if (!translationKeys.includes(k)) error(where, `неизвестное поле translations.${lang}.${k}`);
    }
  }
}

// ── Запуск ───────────────────────────────────────────────────

const data = loadAll();
for (const type of ['morning', 'evening']) {
  for (const item of data.azkar[type] ?? []) {
    checkStructure(`${type} id ${item.id ?? '?'}`, item, AZKAR_KEYS, AZKAR_TRANSLATION_KEYS);
  }
}
for (const [type, list] of Object.entries(data.hadith)) {
  for (const item of list ?? []) {
    checkStructure(`${type} id ${item.id ?? '?'}`, item, HADITH_KEYS, HADITH_TRANSLATION_KEYS);
  }
}

const AZKAR = {
  morning: data.azkar.morning?.map(z => legacyZikr(z)),
  evening: data.azkar.evening?.map(z => legacyZikr(z)),
};
const HADITH_DATA = Object.fromEntries(
  Object.entries(data.hadith).map(([type, list]) => [type, list?.map(h => legacyHadith(h))]));
checkAzkar(AZKAR);
checkHadith(HADITH_DATA);
checkNotes(data.hadith);
checkCollections(readData('collections.json'), Object.keys(data.hadith));
checkReminders(readData('reminders.json'));

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