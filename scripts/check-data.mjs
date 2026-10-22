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
// data/reminders.json — тексты напоминаний, data/versions.json — история версий («Что нового»)
// и data/reading.json - «Учимся читать» в разделе «Махрадж», общие для iOS и Android.
//
// Код выхода 1 — есть ошибки (пустые обязательные поля, нет аудиофайла и т.п.).
// Предупреждения (подозрения на артефакты PDF) на код выхода не влияют.

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, extname } from 'node:path';
import { ROOT, BASE_LANGUAGE, loadAll, readData, legacyZikr, legacyHadith } from './lib/content.mjs';

const AUDIO_DIR = join(ROOT, 'audio');

const VERBOSE = process.argv.includes('--verbose');

const EXPECTED = { morning: 16, evening: 16, nawawi: 50, qudsi: 42, ajurri: 40 };

const AZKAR_KEYS = ['id', 'arabic', 'max', 'audio', 'translations'];
const HADITH_KEYS = ['id', 'arabic', 'translations'];
const AZKAR_TRANSLATION_KEYS = ['translit', 'text', 'source', 'rawi'];
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
  // «Серия под угрозой» (2026-10-11): {streak} — «12 дней подряд» (склонение — приложение).
  streakAtRisk: { sections: AZKAR_SECTIONS, placeholders: ['streak'] },
  // «Пора на урок» «Учимся читать» (2026-10-20): {step} — «Шаг 7 · Сукун» (название шага — приложение).
  readingLesson: { sections: null, placeholders: ['step'] },
};
const REMINDER_TEXT_KEYS = ['title', 'body'];

// versions.json: versions[] сверху вниз = { version, date, translations.<язык> = { items, android?, ios? } }.
const VERSION_KEYS = ['version', 'date', 'translations'];
const VERSION_ITEM_LISTS = ['items', 'android', 'ios'];

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
      // `audio` необязательно; свои записи с 2026-10-12 (`.m4a`, docs/process/audio.md). Файл — в audio/<раздел>/.
      for (const f of ['arabic', 'translit', 'russian', 'source']) requireString(where, item, f);
      if (!Number.isInteger(item.max) || item.max < 1) error(where, `некорректный max: ${item.max}`);
      if (item.arabic) checkArabic(where, item.arabic);
      if (item.russian) checkRussian(where, item.russian);
      for (const f of ['russian', 'translit', 'source']) if (item[f]) checkMixedScript(where, f, item[f]);

      // Запись другого раздела (вечерний зикр с тем же арабским, что утренний) — норма: текст сверяет checkSharedAudio.
      if (item.audio) {
        const ownSection = audioSection(item.audio) === type;
        const expected = `${type}_${String(i + 1).padStart(2, '0')}`;
        if (!AUDIO_EXTENSIONS.includes(extname(item.audio))) {
          error(where, `аудио ${item.audio}: формат не ${AUDIO_EXTENSIONS.join(' / ')}`);
        }
        if (ownSection && item.audio.slice(0, -extname(item.audio).length) !== expected) {
          warn('Имя аудио не совпадает с порядковым номером', where, `${item.audio}, ожидалось ${expected}.m4a`);
        }
        if (seenAudio.has(item.audio)) error(where, `аудио ${item.audio} уже используется другим зикром`);
        seenAudio.add(item.audio);
        if (!existsSync(audioPath(item.audio, type))) error(where, `нет файла audio/${audioSection(item.audio) ?? type}/${item.audio}`);
      }
    });
  }
  checkSharedAudio(AZKAR);
  checkUniqueAudioNames();
}

// Форматы записей: свои записи — AAC `.m4a` (2026-10-12); `.mp3` — по-прежнему допустим.
const AUDIO_EXTENSIONS = ['.m4a', '.mp3'];

/** Раздел файла по имени (`morning_03.m4a` → `morning`); не по разделу — `null`. */
function audioSection(name) {
  const prefix = name.split('_')[0];
  return ['morning', 'evening'].includes(prefix) ? prefix : null;
}

/** Путь к файлу: по разделу в имени, иначе — в папке раздела зикра. */
function audioPath(name, type) {
  return join(AUDIO_DIR, audioSection(name) ?? type, name);
}

// В приложении (iOS Bundle, Android assets/audio) файлы лежат в одной папке — имена уникальны во всём audio/.
function checkUniqueAudioNames() {
  if (!existsSync(AUDIO_DIR)) return;
  const seen = new Map();
  for (const dir of readdirSync(AUDIO_DIR)) {
    const path = join(AUDIO_DIR, dir);
    if (!statSync(path).isDirectory()) continue;
    for (const name of readdirSync(path)) {
      if (seen.has(name)) error('audio', `${name} — и в ${seen.get(name)}/, и в ${dir}/`);
      seen.set(name, dir);
    }
  }
}

// Один аудиофайл у разных зикров (вечерний ссылается на утренний или файлы совпадают по содержимому)
// допустим, только если совпадает и арабский текст.
function checkSharedAudio(AZKAR) {
  const byHash = new Map();
  for (const type of ['morning', 'evening']) {
    AZKAR[type].forEach((item, i) => {
      const path = audioPath(item.audio ?? '', type);
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

// versions.json: номера X.Y.Z без повторов и строго по убыванию (как числа: 1.10.0 > 1.9.0), даты ГГГГ-ММ-ДД,
// у каждой версии — базовый язык и хотя бы один непустой пункт. Нет записи для текущей версии приложения —
// предупреждение: окно «Что нового» тогда не покажется.
function checkVersions(file) {
  const versions = file?.versions;
  if (!Array.isArray(versions) || versions.length === 0) {
    error('versions', 'нет непустого массива versions');
    return;
  }
  for (const k of Object.keys(file)) if (k !== 'versions') error('versions', `неизвестное поле «${k}»`);
  const parse = v => (/^\d+\.\d+\.\d+$/.test(v ?? '') ? v.split('.').map(Number) : null);
  const compare = (a, b) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
  const isDate = d => /^\d{4}-\d{2}-\d{2}$/.test(d) && !Number.isNaN(Date.parse(`${d}T00:00:00Z`)) &&
    new Date(`${d}T00:00:00Z`).toISOString().slice(0, 10) === d;
  let previous = null;
  for (const [index, entry] of versions.entries()) {
    const at = `versions ${entry?.version ?? `#${index + 1}`}`;
    for (const k of Object.keys(entry ?? {})) if (!VERSION_KEYS.includes(k)) error(at, `неизвестное поле «${k}»`);
    const number = parse(entry?.version);
    if (!number) error(at, `номер «${entry?.version ?? ''}» — ожидается X.Y.Z`);
    else if (previous && compare(previous, number) <= 0) error(at, 'номера должны идти сверху вниз по убыванию, без повторов');
    if (number) previous = number;
    if (!isDate(entry?.date ?? '')) error(at, `дата «${entry?.date ?? ''}» — ожидается ГГГГ-ММ-ДД`);
    const translations = entry?.translations;
    if (!translations || typeof translations !== 'object' || Array.isArray(translations)) {
      error(at, 'нет объекта translations');
      continue;
    }
    if (!translations[BASE_LANGUAGE]) error(at, `нет перевода translations.${BASE_LANGUAGE}`);
    for (const [lang, t] of Object.entries(translations)) {
      const where = `${at} ${lang}`;
      if (!/^[a-z]{2}$/.test(lang)) error(at, `код языка «${lang}» — ожидается ISO 639-1 (ru, en…)`);
      for (const k of Object.keys(t ?? {})) if (!VERSION_ITEM_LISTS.includes(k)) error(where, `неизвестный список «${k}»`);
      let count = 0;
      for (const list of VERSION_ITEM_LISTS) {
        const items = t?.[list];
        if (items === undefined) continue;
        if (!Array.isArray(items)) { error(where, `${list} — ожидается массив строк`); continue; }
        items.forEach((item, i) => {
          if (typeof item !== 'string' || !item.trim()) error(where, `${list}[${i}] — пустой пункт`);
          else { count++; checkMixedScript(where, `${list}[${i}]`, item); }
        });
      }
      if (count === 0) error(where, 'нет ни одного пункта');
    }
  }
  const known = new Set(versions.map(v => v?.version));
  const appVersions = [
    ['Android', 'android/app/build.gradle.kts', /versionName\s*=\s*"([^"]+)"/],
    ['iOS', 'Inabah.xcodeproj/project.pbxproj', /MARKETING_VERSION = ([^;]+);/],
  ];
  for (const [platform, path, pattern] of appVersions) {
    const full = join(ROOT, path);
    const current = existsSync(full) ? readFileSync(full, 'utf8').match(pattern)?.[1] : null;
    if (current && !known.has(current)) warn('versions', platform, `нет записи для текущей версии ${current} — «Что нового» не покажется`);
  }
}

// reading.json - «Учимся читать» в разделе «Махрадж» (docs/06-data-structure.md, «Учимся читать»):
// знаки огласовок, 28 букв и хамза (29-я) и шаги. Слоги и формы букв не хранятся - их строят приложения из буквы и знака;
// хранится транскрипция слогов (нерегулярна: «ли», а не «льи»). В текстах - только «-», без длинного тире (решение пользователя).
const READING_KEYS = ['marks', 'letters', 'steps', 'guides'];
const READING_MARK_KEYS = ['id', 'sign', 'translations'];
const READING_MARK_TEXT_KEYS = ['name', 'where', 'sound'];
const READING_LETTER_KEYS = ['id', 'letter', 'name', 'joinsNext', 'joinsPrevious', 'syllableBases', 'closedBase', 'closedVowels', 'audioLike', 'audio', 'translations'];
// Записи закрытых слогов буквы (أَبْ «ъаб», إِبْ, أُبْ; просьба пользователя 2026-10-19 - согласная с сукуном
// одна не произносится): ключ audio → огласовка алифа перед буквой.
const READING_CLOSED_AUDIO = { closedFatha: 'fatha', closedKasra: 'kasra', closedDamma: 'damma' };
const READING_LETTER_TEXT_KEYS = ['name', 'nameTranslit', 'sound', 'syllables', 'note'];
const READING_STEP_KEYS = ['id', 'marks', 'example', 'translations', 'blocks', 'quiz', 'quizCount', 'words', 'final', 'extra'];
// Вопросов в одном заходе в «Проверить себя» (просьба пользователя 2026-10-19): берутся случайно из запаса quiz;
// у шага - quizCount (экзамен - 20), иначе столько.
const READING_QUIZ_ROUND = 16;
const READING_STEP_TEXT_KEYS = ['title', 'subtitle'];
// Только типы, которые приложения рисуют (аудит 2026-10-17: «groups», «compare», «join» описывались в
// формате, но экранов у них нет - блок молча пропал бы).
const READING_BLOCKS = {
  text: ['type', 'translations'], marks: ['type', 'marks'], letters: ['type', 'letters'],
  syllables: ['type', 'letters', 'marks', 'sample'],
  // Зикр итога «Читаем зикр» (2026-10-17): ссылка на зикр «Азкаров», слова, места слитного чтения.
  zikr: ['type', 'section', 'id', 'audio', 'arabic', 'words', 'joins', 'translations'],
  rule: ['type', 'arabic', 'letters', 'translations'],
  // variants (хӏамза, 2026-10-21): буква не из groups - плитка во всю строку, 4 строки написаний (отдельно, в начале,
  // в середине, в конце); notes в переводе - текст на карточке буквы.
  forms: ['type', 'groups', 'variants', 'examples', 'exampleCard', 'translations'],
  formulas: ['type', 'columns', 'items'],
  // Закрытые слоги шага «Сукун» (2026-10-19): строит приложение из букв (closedVowels, closedBase) - ряд на букву;
  // на уроке - sample случайных, весь алфавит - отдельным экраном.
  closedSyllables: ['type', 'letters', 'sample'],
  // Тема урока (шаг «Особые случаи», 2026-10-17): карточка - название, описание, пример; блоки - её экран.
  topic: ['type', 'arabic', 'blocks', 'translations'],
};
// Буква в слове-примере - и на подставке (подсветка - letterRange в Android): алиф несёт хӏамзу (أ إ), хӏамза стоит
// на алифе, вав и йа (أ إ ؤ ئ).
const LETTER_SEATS = { 'ا': ['أ', 'إ'], 'ء': ['أ', 'إ', 'ؤ', 'ئ'] };
// Справка (extra) и экран темы рисуют только текст, правила и формулы (ReadingExtraScreen).
const READING_EXTRA_BLOCKS = ['text', 'rule', 'formulas'];
// Мадд (2026-10-15): буква мадда без знака тянет огласовку перед ней - ا после фатхьи, ي после касры,
// و после доммы; маленькие буквы мадда (ٰ ۥ ۦ) - сразу после той же огласовки (алиф - на черте ـٰ).
const MADD_LETTERS = { 'ا': 'fatha', 'ي': 'kasra', 'و': 'damma' };
const SMALL_MADD = { 'ٰ': 'fatha', 'ۦ': 'kasra', 'ۥ': 'damma' };
// Хамза на подставке вав / йа - как буква хамза.
const HAMZA_SEATS = ['ؤ', 'ئ'];
const TATWEEL = 'ـ';
// Неразрывный пробел - основа маленького алифа после несоединяемой буквы (صِرَ ٰطُ).
const NBSP = '\u00A0';
// Хӏамзату-львасли (алиф васла) артикля.
const WASL = 'ٱ';
// Кружок над немым алифом (маленький круглый ноль, как в мусхафе): алиф пишется, но не читается.
const SILENT_ALIF_MARK = '۟';

/**
 * Слово «Читаем слова»: слова через один пробел; в слове - буква (подставка хамзы) и пройденный знак,
 * после знака - необязательная маленькая буква мадда того же звука; буква мадда без знака - только после
 * своей огласовки. Ошибка - текст, всё верно - `null`.
 */
function readingWordProblem(text, { byLetter, bases, signs, passed }) {
  for (const word of text.split(' ')) {
    if (!word) return 'лишний пробел';
    const chars = [...word];
    let previous = null;
    for (let c = 0; c < chars.length;) {
      const char = chars[c];
      // Артикль (шаг «Артикль», 2026-10-16): хӏамзату-львасли ٱ - без знака; лям после неё - с сукуном
      // (лунная буква) или без знака, и тогда следующая буква - с шаддой (солнечная).
      if (char === WASL) {
        if (signs.has(chars[c + 1])) return 'у хӏамзату-львасли ٱ знака нет';
        previous = 'wasl'; c += 1; continue;
      }
      if (char === 'ل' && previous === 'wasl' && !signs.has(chars[c + 1])) {
        const next = chars.slice(c + 2, c + 4);
        if (!next.includes('ّ')) return 'лям артикля без знака - только перед буквой с шаддой (солнечной)';
        previous = null; c += 1; continue;
      }
      // Та марбута (шаг «Мадд перед артиклем», 2026-10-16) - пока как буква та, с огласовкой.
      const known = byLetter.has(char) || bases.has(char) || HAMZA_SEATS.includes(char) || char === 'ة';
      if (!known) return `неизвестная буква «${char}»`;
      if (chars[c + 1] === 'ٰ') return 'маленький алиф - после фатхьи, не вместо неё';
      // Шадда (шаг «Шадда», 2026-10-16) - вместе с огласовкой в любом порядке: بَّ или بَّ (Unicode
      // ставит огласовку перед шаддой); после шадды обязательна огласовка или танвин.
      let mark = signs.get(chars[c + 1]);
      let width = 2;
      const doubled = mark === 'shadda' || signs.get(chars[c + 2]) === 'shadda';
      if (doubled) {
        if (mark === 'shadda') mark = signs.get(chars[c + 2]);
        width = 3;
        if (!passed.has('shadda')) return 'знак shadda ещё не пройден';
        if (!mark || mark === 'sukun' || mark === 'shadda') return `после шадды у «${char}» нужна огласовка`;
      }
      if (!mark) {
        if (MADD_LETTERS[char] && previous === MADD_LETTERS[char]) { previous = null; c += 1; continue; }
        // Алиф после танвина фатхьи не читается (أَبَدًا, решение по книге 2026-10-16).
        if (char === 'ا' && previous === 'fathatan') { previous = null; c += 1; continue; }
        // Немой алиф - с кружком сверху (ٱسْجُدُوا۟, решение пользователя 2026-10-17): не читается.
        if (char === 'ا' && chars[c + 1] === SILENT_ALIF_MARK) { previous = null; c += 2; continue; }
        return `после «${char}» нет знака`;
      }
      if (!passed.has(mark)) return `знак ${mark} ещё не пройден`;
      c += width;
      // Маленький алиф - после фатхьи на соединительной черте (هَـٰذَا, как в Къуране, решение
      // пользователя 2026-10-16); после несоединяемой буквы - на неразрывном пробеле: صِرَ ٰطُ - алиф стоит
      // отдельно после ر, а не над её фатхьой (как в книге, вариант E - решение пользователя 2026-10-17).
      const joins = byLetter.get(char)?.joinsNext ?? true;
      if (chars[c] === TATWEEL || chars[c] === NBSP) {
        if (chars[c + 1] !== 'ٰ') return 'соединительная черта или неразрывный пробел - только перед маленьким алифом';
        if (chars[c] === TATWEEL && !joins) return `после «${char}» маленький алиф - на неразрывном пробеле, не на черте`;
        if (chars[c] === NBSP && joins) return `после «${char}» маленький алиф - на соединительной черте (ـٰ)`;
        c += 1;
      } else if (chars[c] === 'ٰ') {
        return joins ? 'маленький алиф - на соединительной черте (ـٰ)' : 'маленький алиф после несоединяемой буквы - на неразрывном пробеле';
      }
      if (SMALL_MADD[chars[c]]) {
        if (SMALL_MADD[chars[c]] !== mark) return 'маленькая буква мадда не к своей огласовке';
        c += 1;
        previous = null;
      } else previous = mark;
    }
  }
  return null;
}
const READING_QUIZ = { syllable: ['type', 'letter', 'mark'], closedSyllable: ['type', 'letter', 'mark'], letterName: ['type', 'letter'], letterForm: ['type', 'letter', 'form'], word: ['type', 'arabic', 'answer', 'translations'] };
// Формы буквы: отдельная, в начале, в середине, в конце слова.
const READING_FORMS = ['isolated', 'initial', 'medial', 'final'];
const READING_ALPHABET = 29;
// Слоги с этими знаками читаются по-своему у каждой буквы - их транскрипция нужна в letters[].syllables.
const VOWEL_MARKS = ['fatha', 'kasra', 'damma', 'fathatan', 'kasratan', 'dammatan'];
const READING_AUDIO_DIR = join(AUDIO_DIR, 'reading');
// Рисунки руководств (2026-10-21): images/reading/<image>.webp, вырезает scripts/cut-reading-images.py.
const READING_IMAGES_DIR = join(ROOT, 'images', 'reading');
// Руководство по произношению: свой экран для букв letters; блоки - текст с заголовком и рисунки.
const READING_GUIDE_KEYS = ['id', 'letters', 'translations', 'blocks'];
const READING_GUIDE_BLOCKS = { text: ['type', 'translations'], image: ['type', 'image', 'legend', 'translations'] };

/**
 * Зикр итога «Читаем зикр» (2026-10-17): зикр есть в azkar.json, запись - его же; `arabic` - слова через
 * пробел; слово - как в «Читаем слова» (пройденные знаки); место слитного чтения - слова зикра подряд,
 * с транскрипцией и подписью; у зикра - краткое описание (`text`) и транскрипция строки целиком.
 */
function checkReadingZikr(where, block, { byLetter, marks, letters, passed, checkTexts }) {
  if (!['morning', 'evening'].includes(block.section)) error(where, 'section - morning / evening');
  const source = (data.azkar[block.section] ?? []).find(z => z.id === block.id);
  if (!source) error(where, `нет зикра ${block.section} ${block.id} в azkar.json`);
  else if ((block.audio ?? null) !== (source.audio ?? null)) error(where, `audio - как у зикра в azkar.json (${source.audio ?? 'null'})`);
  const signs = new Map(marks.map(m => [m.sign, m.id]));
  const bases = new Set(letters.flatMap(l => Object.values(l?.syllableBases ?? {})));
  const words = Array.isArray(block.words) && block.words.length ? block.words : (error(where, 'words - непустой массив'), []);
  for (const [n, item] of words.entries()) {
    const wat = `${where} word ${n + 1}`;
    for (const k of Object.keys(item ?? {})) if (!['arabic', 'translations'].includes(k)) error(wat, `неизвестное поле «${k}»`);
    if (!requireString(wat, item ?? {}, 'arabic')) continue;
    if (item.arabic.includes(' ')) error(wat, 'одно слово, без пробелов');
    const problem = readingWordProblem(item.arabic, { byLetter, bases, signs, passed });
    if (problem) error(wat, `«${item.arabic}»: ${problem}`);
    checkTexts(wat, item.translations, ['translit'], ['translit']);
  }
  if (requireString(where, block, 'arabic') && block.arabic !== words.map(w => w?.arabic).join(' ')) error(where, 'arabic - слова words через пробел');
  for (const [n, item] of (Array.isArray(block.joins) ? block.joins : (error(where, 'joins - массив'), [])).entries()) {
    const jat = `${where} join ${n + 1}`;
    for (const k of Object.keys(item ?? {})) if (!['arabic', 'translations'].includes(k)) error(jat, `неизвестное поле «${k}»`);
    if (requireString(jat, item ?? {}, 'arabic') && !` ${block.arabic} `.includes(` ${item.arabic} `)) error(jat, 'arabic - слова зикра подряд');
    const ru = checkTexts(jat, item?.translations, ['translit', 'note'], ['translit', 'note']);
    if (ru?.note) checkRussian(jat, ru.note);
  }
  const ru = checkTexts(where, block.translations, ['text', 'translit'], ['text', 'translit']);
  if (ru?.text) checkRussian(where, ru.text);
}

function checkReading(file) {
  if (!file || typeof file !== 'object') { error('reading', 'нет файла или он не объект'); return; }
  for (const k of Object.keys(file)) if (!READING_KEYS.includes(k)) error('reading', `неизвестное поле «${k}»`);

  // Тексты одного языка: известные поля, без «-», без латиницы внутри русских слов.
  const checkTexts = (at, translations, keys, required) => {
    if (!translations || typeof translations !== 'object' || Array.isArray(translations)) {
      error(at, 'нет объекта translations');
      return null;
    }
    if (!translations[BASE_LANGUAGE]) error(at, `нет перевода translations.${BASE_LANGUAGE}`);
    for (const [lang, t] of Object.entries(translations)) {
      if (!/^[a-z]{2}$/.test(lang)) error(at, `код языка «${lang}» - ожидается ISO 639-1 (ru, en…)`);
      for (const k of Object.keys(t ?? {})) if (!keys.includes(k)) error(at, `неизвестное поле translations.${lang}.${k}`);
      for (const k of required) requireString(`${at} ${lang}`, t ?? {}, k);
      const strings = JSON.stringify(t ?? {});
      if (strings.includes('—')) error(`${at} ${lang}`, '«—» в тексте - только «-» (решение пользователя)');
      for (const [k, v] of Object.entries(t ?? {})) if (typeof v === 'string') checkMixedScript(`${at} ${lang}`, k, v);
    }
    return translations[BASE_LANGUAGE] ?? null;
  };
  const checkAudio = (at, name) => {
    if (name === null) return;
    if (typeof name !== 'string' || !AUDIO_EXTENSIONS.includes(extname(name))) {
      error(at, `аудио «${name}» - ожидается имя файла .m4a / .mp3 или null`);
    } else if (!existsSync(join(READING_AUDIO_DIR, name))) {
      error(at, `нет файла audio/reading/${name}`);
    }
  };

  const marks = Array.isArray(file.marks) ? file.marks : [];
  if (!marks.length) error('reading marks', 'нет непустого массива marks');
  const markIds = new Set();
  for (const [i, mark] of marks.entries()) {
    const at = `reading mark ${mark?.id ?? `#${i + 1}`}`;
    if (Object.keys(mark ?? {})[0] !== 'id') error(at, 'id должен быть первым полем');
    for (const k of Object.keys(mark ?? {})) if (!READING_MARK_KEYS.includes(k)) error(at, `неизвестное поле «${k}»`);
    if (!/^[a-z][A-Za-z]*$/.test(mark?.id ?? '')) error(at, 'id - слово латиницей (fatha, kasra…)');
    else if (markIds.has(mark.id)) error(at, 'повтор id');
    else markIds.add(mark.id);
    if (!/^[ً-ْ]$/.test(mark?.sign ?? '')) error(at, `sign «${mark?.sign ?? ''}» - ожидается один знак огласовки`);
    checkTexts(at, mark?.translations, READING_MARK_TEXT_KEYS, ['name', 'where', 'sound']);
  }
  const knownMark = (at, id) => { if (!markIds.has(id)) error(at, `неизвестный знак «${id}»`); };

  const letters = Array.isArray(file.letters) ? file.letters : [];
  if (letters.length !== READING_ALPHABET) error('reading letters', `букв ${letters.length}, ожидается ${READING_ALPHABET}`);
  const byLetter = new Map();
  for (const [i, letter] of letters.entries()) {
    const at = `reading letter ${letter?.letter ?? `#${i + 1}`}`;
    if (Object.keys(letter ?? {})[0] !== 'id') error(at, 'id должен быть первым полем');
    checkId(at, letter ?? {}, i);
    for (const k of Object.keys(letter ?? {})) if (!READING_LETTER_KEYS.includes(k)) error(at, `неизвестное поле «${k}»`);
    if (!/^[ء-ي]$/.test(letter?.letter ?? '')) error(at, 'letter - одна арабская буква без огласовки');
    else if (byLetter.has(letter.letter)) error(at, 'повтор буквы');
    else byLetter.set(letter.letter, letter);
    if (requireString(at, letter ?? {}, 'name')) checkArabic(at, letter.name);
    if ('joinsNext' in (letter ?? {}) && letter.joinsNext !== false) error(at, 'joinsNext - только false (по умолчанию соединяется)');
    if ('joinsPrevious' in (letter ?? {}) && letter.joinsPrevious !== false) error(at, 'joinsPrevious - только false (по умолчанию соединяется)');
    // Подставка слога по знакам (у алифа - хамза на алифе: أ, إ); нет знака - сама буква.
    const bases = letter?.syllableBases;
    if (bases !== undefined && (typeof bases !== 'object' || Array.isArray(bases))) error(at, 'syllableBases - объект { <знак>: буква }');
    else for (const [k, v] of Object.entries(bases ?? {})) {
      knownMark(`${at} syllableBases`, k);
      if (!/^[ء-ي]$/.test(v ?? '')) error(at, `syllableBases.${k} - одна арабская буква`);
    }
    // Буква с сукуном в закрытом слоге, если не сама буква (алиф и хӏамза - хӏамза на алифе: أَأْ إِأْ أُأْ).
    if (letter?.closedBase !== undefined && !/^[ء-ي]$/.test(letter.closedBase)) error(at, 'closedBase - одна арабская буква');
    // После каких огласовок у буквы бывает закрытый слог (у و ي - только после фатхьи: أُوْ, إِيْ - мадд); нет - после всех трёх.
    const vowels = letter?.closedVowels;
    if (vowels !== undefined && (!Array.isArray(vowels) || !vowels.length || vowels.some(v => !Object.values(READING_CLOSED_AUDIO).includes(v)))) {
      error(at, 'closedVowels - непустой массив из fatha, kasra, damma');
    }
    const audio = letter?.audio;
    if (!audio || typeof audio !== 'object' || Array.isArray(audio)) error(at, 'audio - объект { name, <знак>, closedFatha…: файл | null }');
    else for (const [k, v] of Object.entries(audio)) {
      if (k === 'sound') error(`${at} audio`, 'sound - больше нет: вместо звука с сукуном - закрытые слоги closedFatha, closedKasra, closedDamma');
      else if (k !== 'name' && !(k in READING_CLOSED_AUDIO)) knownMark(`${at} audio`, k);
      if (k in READING_CLOSED_AUDIO && Array.isArray(vowels) && !vowels.includes(READING_CLOSED_AUDIO[k])) error(`${at} audio`, `${k} - нет в closedVowels`);
      checkAudio(`${at} audio.${k}`, v);
    }
    const ru = checkTexts(at, letter?.translations, READING_LETTER_TEXT_KEYS, ['name', 'nameTranslit', 'sound']);
    const syllables = ru?.syllables;
    if (syllables !== undefined && (typeof syllables !== 'object' || Array.isArray(syllables))) error(at, 'syllables - объект { <знак>: транскрипция }');
    else for (const [k, v] of Object.entries(syllables ?? {})) {
      knownMark(`${at} syllables`, k);
      if (typeof v !== 'string' || !v.trim()) error(at, `syllables.${k} - пустая транскрипция`);
    }
  }
  const knownLetter = (at, l) => { if (!byLetter.has(l)) error(at, `неизвестная буква «${l}»`); };
  // Буква звучит как другая (хӏамза - как алиф, решение пользователя 2026-10-19): своя запись - только название,
  // остальные записи - той буквы (reading_<NN той буквы>_…).
  for (const letter of letters) {
    if (letter?.audioLike === undefined) continue;
    const at = `reading letter ${letter.id}`;
    const like = byLetter.get(letter.audioLike);
    if (!like || like === letter || like.audioLike !== undefined) { error(at, `audioLike - другая буква курса без своего audioLike`); continue; }
    for (const [k, v] of Object.entries(letter.audio ?? {})) {
      if (k !== 'name' && v !== null && v !== like.audio?.[k]) error(`${at} audio.${k}`, `${v} - запись буквы ${like.letter} (${like.audio?.[k] ?? 'у неё нет'})`);
    }
  }

  const steps = Array.isArray(file.steps) ? file.steps : [];
  // Транскрипция слов «Читаем слова» - с ней сверяются слова-примеры форм (одно слово - одна транскрипция).
  const wordTranslit = new Map();
  for (const s of steps) for (const w of s?.words?.items ?? []) {
    const t = w?.translations?.[BASE_LANGUAGE]?.translit;
    if (typeof w?.arabic === 'string' && typeof t === 'string') wordTranslit.set(w.arabic, t);
  }
  if (!steps.length) error('reading steps', 'нет непустого массива steps');
  const passed = new Set();
  // Карточки форм этого и прошлых шагов - варианты вопроса letterForm (экзамен шага 12, 2026-10-17).
  const passedFormGroups = [];
  for (const [i, step] of steps.entries()) {
    passedFormGroups.push(...(step?.blocks ?? []).filter(b => b?.type === 'forms').flatMap(b => b.groups ?? []));
    const at = `reading step ${step?.id ?? `#${i + 1}`}`;
    if (Object.keys(step ?? {})[0] !== 'id') error(at, 'id должен быть первым полем');
    checkId(at, step ?? {}, i);
    for (const k of Object.keys(step ?? {})) if (!READING_STEP_KEYS.includes(k)) error(at, `неизвестное поле «${k}»`);
    checkTexts(at, step?.translations, READING_STEP_TEXT_KEYS, ['title', 'subtitle']);
    // Итог курса («Читаем зикр») - только `true` и только у последнего шага, без номера на экране.
    if (step?.final !== undefined && (step.final !== true || i !== steps.length - 1)) error(at, 'final - только true и только у последнего шага');
    // Итог пройден, когда прочитаны все его зикры (кнопки «Готово» нет): без зикров его не пройти.
    if (step?.final === true && !(step.blocks ?? []).some(b => b?.type === 'zikr')) error(at, 'итог (final) без блоков zikr - его не пройти');
    // Пример справа в списке шагов - необязательный, только арабский.
    if (step?.example !== undefined && requireString(at, step, 'example')) checkArabic(at, step.example);
    for (const m of step?.marks ?? []) { knownMark(at, m); passed.add(m); }
    // Знак пройден к этому шагу - у каждой буквы нужен его слог (для листа буквы и проверок).
    for (const m of [...passed].filter(m => VOWEL_MARKS.includes(m))) {
      const missing = letters.filter(l => !l?.translations?.[BASE_LANGUAGE]?.syllables?.[m]).map(l => l.letter);
      if (missing.length && (step?.marks ?? []).includes(m)) {
        warn('reading: нет транскрипции слогов', at, `знак ${m} - у ${missing.length} букв (${missing.slice(0, 5).join(' ')}…)`);
      }
    }
    // «Читаем слова»: группы по groupSize, каждое слово - repeat раз; слово - буквы (или подставка хамзы)
    // и после каждой - пройденный к шагу знак; транскрипция - необязательная (у первой группы).
    if (step?.words !== undefined) {
      const w = step.words;
      const where = `${at} words`;
      for (const k of Object.keys(w ?? {})) if (!['groupSize', 'repeat', 'items', 'pairs'].includes(k)) error(where, `неизвестное поле «${k}»`);
      for (const k of ['groupSize', 'repeat']) if (!Number.isInteger(w?.[k]) || w[k] < 1) error(where, `${k} - целое ≥ 1`);
      const items = Array.isArray(w?.items) ? w.items : (error(where, 'items - массив'), []);
      const signs = new Map(marks.map(m => [m.sign, m.id]));
      const bases = new Set(letters.flatMap(l => Object.values(l?.syllableBases ?? {})));
      const seen = new Set();
      for (const [j, item] of items.entries()) {
        const wat = `${where} ${j + 1}`;
        for (const k of Object.keys(item ?? {})) if (!['arabic', 'translations'].includes(k)) error(wat, `неизвестное поле «${k}»`);
        if (!requireString(wat, item ?? {}, 'arabic')) continue;
        if (seen.has(item.arabic)) warn('reading: повтор слова', wat, item.arabic);
        seen.add(item.arabic);
        const problem = readingWordProblem(item.arabic, { byLetter, bases, signs, passed });
        if (problem) error(wat, `«${item.arabic}»: ${problem}`);
        if (item.translations !== undefined) checkTexts(wat, item.translations, ['translit'], ['translit']);
      }
    }
    const blocks = Array.isArray(step?.blocks) ? step.blocks : (error(at, 'blocks - массив'), []);
    if (!blocks.length) warn('reading: шаг без содержания', at, step?.translations?.[BASE_LANGUAGE]?.title ?? '');
    // Справка (extra, 2026-10-16): заголовок и блоки - те же правила, что у блоков урока.
    let extraBlocks = [];
    if (step?.extra !== undefined) {
      for (const k of Object.keys(step.extra ?? {})) if (!['translations', 'blocks'].includes(k)) error(`${at} extra`, `неизвестное поле «${k}»`);
      const ru = checkTexts(`${at} extra`, step.extra?.translations, ['title'], ['title']);
      if (ru?.title) checkRussian(`${at} extra`, ru.title);
      extraBlocks = Array.isArray(step.extra?.blocks) && step.extra.blocks.length ? step.extra.blocks : (error(`${at} extra`, 'blocks - непустой массив'), []);
    }
    const entries = [
      ...blocks.map((block, j) => ({ block, where: `${at} block ${j + 1}` })),
      ...extraBlocks.map((block, j) => ({ block, where: `${at} extra block ${j + 1}`, inExtra: true })),
    ];
    // Блоки темы проверяются как блоки урока; тема в теме - нет.
    for (const [j, block] of blocks.entries()) {
      if (block?.type !== 'topic') continue;
      const tat = `${at} block ${j + 1}`;
      const ru = checkTexts(tat, block.translations, ['title', 'text'], ['title', 'text']);
      if (ru?.title) checkRussian(tat, ru.title);
      if (ru?.text) checkRussian(tat, ru.text);
      if (block.arabic !== undefined && requireString(tat, block, 'arabic')) checkArabic(tat, block.arabic);
      const inner = Array.isArray(block.blocks) && block.blocks.length ? block.blocks : (error(tat, 'blocks - непустой массив'), []);
      for (const [k, b] of inner.entries()) {
        if (b?.type === 'topic') error(`${tat} topic block ${k + 1}`, 'тема внутри темы');
        else entries.push({ block: b, where: `${tat} topic block ${k + 1}`, inExtra: true });
      }
    }
    for (const { block, where, inExtra } of entries) {
      if (inExtra && !READING_EXTRA_BLOCKS.includes(block?.type)) {
        error(where, `«${block?.type}» - справка и тема показывают только ${READING_EXTRA_BLOCKS.join(', ')}`);
        continue;
      }
      if (block?.type === 'topic') continue;
      const keys = READING_BLOCKS[block?.type];
      if (!keys) { error(where, `неизвестный тип «${block?.type}»`); continue; }
      for (const k of Object.keys(block)) if (!keys.includes(k)) error(where, `неизвестное поле «${k}»`);
      if (block.type === 'text') {
        // info - полный текст во всплывающем окне по ⓘ.
        const ru = checkTexts(where, block.translations, ['text', 'info', 'title'], ['text']);
        if (ru?.text) checkRussian(where, ru.text);
        if (ru?.info) checkRussian(where, ru.info);
      }
      // syllables.marks - столбцы таблицы (танвин), без поля - пройденные огласовки.
      if (block.type === 'marks' || block.type === 'syllables') for (const m of block.marks ?? []) knownMark(where, m);
      // Без letters - весь алфавит.
      if (['letters', 'syllables', 'closedSyllables'].includes(block.type)) for (const l of block.letters ?? []) knownLetter(where, l);
      if (block.type === 'closedSyllables' && !passed.has('sukun')) error(where, 'closedSyllables: сукун ещё не пройден к этому шагу');
      // sample - на уроке столько случайных слогов, весь алфавит - отдельным экраном.
      if ((block.type === 'syllables' || block.type === 'closedSyllables') && block.sample !== undefined && (!Number.isInteger(block.sample) || block.sample < 1)) error(where, 'sample - целое ≥ 1');
      if (block.type === 'rule') {
        if (requireString(where, block, 'arabic')) checkArabic(where, block.arabic);
        for (const l of block.letters ?? []) knownLetter(where, l);
        const ru = checkTexts(where, block.translations, ['translit', 'text'], ['translit', 'text']);
        if (ru?.text) checkRussian(where, ru.text);
      }
      if (block.type === 'forms') for (const g of block.groups ?? []) for (const l of g ?? []) knownLetter(where, l);
      // Примеры форм: буква → 3 слова (буква в начале, в середине, в конце); exampleCard - буквы третьей карточки.
      if (block.type === 'forms') {
        const grouped = new Set((block.groups ?? []).flat());
        for (const [l, rows] of Object.entries(block.variants ?? {})) {
          knownLetter(where, l);
          if (grouped.has(l)) error(where, `variants.${l}: буква уже в groups`);
          if (!Array.isArray(rows) || rows.length !== 4 || rows.some(r => typeof r !== 'string' || !r.trim())) error(where, `variants.${l} - 4 строки: отдельно, в начале, в середине, в конце`);
        }
        for (const [l, words] of Object.entries(block.examples ?? {})) {
          knownLetter(where, l);
          if (!grouped.has(l) && !block.variants?.[l]) error(where, `examples.${l}: буквы нет ни в groups, ни в variants`);
          if (!Array.isArray(words) || words.length !== 3) { error(where, `examples.${l} - 3 слова: начало, середина, конец`); continue; }
          for (const w of words) {
            if (typeof w !== 'string' || ![l, ...(LETTER_SEATS[l] ?? [])].some(c => w.includes(c))) error(where, `examples.${l}: в слове «${w}» нет буквы ${l}`);
            else checkArabic(where, w);
          }
        }
        for (const l of block.exampleCard ?? []) if (!block.examples?.[l]) error(where, `exampleCard: у буквы ${l} нет примеров`);
        // Транскрипция примеров (карточка буквы, «подсмотреть»): у каждой буквы - 3, по словам examples.
        const ru = checkTexts(where, block.translations, ['examples', 'notes'], []);
        for (const [l, note] of Object.entries(ru?.notes ?? {})) {
          if (!block.examples?.[l] && !block.variants?.[l] && !grouped.has(l)) error(where, `notes.${l}: буквы нет в блоке`);
          if (typeof note?.text !== 'string' || !note.text.trim()) error(where, `notes.${l}: нужен text`);
          else checkRussian(where, note.text);
          if (note?.info !== undefined && (typeof note.info !== 'string' || !note.info.trim())) error(where, `notes.${l}.info - непустая строка`);
          for (const k of Object.keys(note ?? {})) if (!['text', 'info'].includes(k)) error(where, `notes.${l}: неизвестное поле «${k}»`);
        }
        const translit = ru?.examples ?? {};
        for (const [l, words] of Object.entries(block.examples ?? {})) {
          const t = translit[l];
          if (!Array.isArray(t) || t.length !== words.length || t.some(x => typeof x !== 'string' || !x.trim())) {
            error(where, `translations.${BASE_LANGUAGE}.examples.${l} - ${words.length} транскрипции, как слов`);
            continue;
          }
          t.forEach((x, n) => {
            const other = wordTranslit.get(words[n]);
            if (other !== undefined && other !== x) error(where, `«${words[n]}»: «${x}», а в «Читаем слова» - «${other}»`);
          });
        }
        for (const l of Object.keys(translit)) if (!block.examples?.[l]) error(where, `translations.${BASE_LANGUAGE}.examples.${l}: у буквы нет примеров`);
      }
      // Формулы (мадд: بَ + ا = بَا; маленькие буквы мадда: تَشَٰبَهَ = تَشَابَهَ): части, итог, транскрипция итога.
      if (block.type === 'formulas') {
        if (block.columns !== undefined && (!Number.isInteger(block.columns) || block.columns < 1 || block.columns > 3)) error(where, 'columns - 1…3');
        const items = Array.isArray(block.items) && block.items.length ? block.items : (error(where, 'items - непустой массив'), []);
        for (const [n, item] of items.entries()) {
          const iat = `${where} item ${n + 1}`;
          for (const k of Object.keys(item ?? {})) if (!['parts', 'result', 'translations', 'audio'].includes(k)) error(iat, `неизвестное поле «${k}»`);
          // Запись итога (примеры шадды, 2026-10-20): `null` - ещё не записана, имя - заполняет reading-recordings --write.
          if (item && 'audio' in item) checkAudio(iat, item.audio);
          // Пустые части - просто слово с транскрипцией (льам твёрдый / мягкий).
          if (!Array.isArray(item?.parts)) error(iat, 'parts - массив');
          for (const p of item?.parts ?? []) if (typeof p !== 'string' || !p) error(iat, 'parts - строки'); else checkArabic(iat, p);
          if (requireString(iat, item ?? {}, 'result')) checkArabic(iat, item.result);
          if (item?.translations !== undefined) checkTexts(iat, item.translations, ['translit'], ['translit']);
        }
      }
      if (block.type === 'zikr') checkReadingZikr(where, block, { byLetter, marks, letters, passed, checkTexts });
    }
    const quizCount = step?.quizCount ?? READING_QUIZ_ROUND;
    if (step?.quizCount !== undefined && (!Number.isInteger(step.quizCount) || step.quizCount < 1)) error(at, 'quizCount - целое больше 0');
    // Запас меньше захода - вопросы каждый раз одни и те же, только в другом порядке.
    else if (step?.quiz?.length && step.quiz.length < quizCount) error(at, `в quiz ${step.quiz.length} вопросов - меньше, чем в заходе (${quizCount})`);
    for (const [j, q] of (step?.quiz ?? []).entries()) {
      const where = `${at} quiz ${j + 1}`;
      const keys = READING_QUIZ[q?.type];
      if (!keys) { error(where, `неизвестный тип «${q?.type}»`); continue; }
      for (const k of Object.keys(q)) if (!keys.includes(k)) error(where, `неизвестное поле «${k}»`);
      // Слово (шаг «Шадда»): слово - как в «Читаем слова», 3 варианта транскрипции, answer - номер верного.
      if (q.type === 'word') {
        const signs = new Map(marks.map(m => [m.sign, m.id]));
        const bases = new Set(letters.flatMap(l => Object.values(l?.syllableBases ?? {})));
        if (requireString(where, q, 'arabic')) {
          const problem = readingWordProblem(q.arabic, { byLetter, bases, signs, passed });
          if (problem) error(where, `«${q.arabic}»: ${problem}`);
        }
        // question - свой вопрос («Как читается на паузе?»).
        const ru = checkTexts(where, q.translations, ['options', 'explain', 'question'], []);
        if (typeof ru?.question === 'string') checkRussian(where, ru.question);
        const options = ru?.options;
        if (!Array.isArray(options) || options.length !== 3 || options.some(o => typeof o !== 'string' || !o.trim())) error(where, 'options - 3 непустые строки');
        else if (new Set(options).size !== 3) error(where, 'options - без повторов');
        if (!Number.isInteger(q.answer) || q.answer < 0 || q.answer > 2) error(where, 'answer - номер варианта 0…2');
        if (typeof ru?.explain === 'string') checkRussian(where, ru.explain);
        continue;
      }
      knownLetter(where, q.letter);
      // Форма - из списка; буква - в одной из групп forms этого или прошлого шага (варианты - буквы её группы).
      if (q.type === 'letterForm') {
        if (!READING_FORMS.includes(q.form)) error(where, `form «${q.form}» - одно из ${READING_FORMS.join(', ')}`);
        if (!passedFormGroups.some(g => g.includes(q.letter))) error(where, `буквы «${q.letter}» нет в таблицах форм шага и прошлых шагов`);
      }
      if (q.type === 'syllable') {
        if (!passed.has(q.mark)) error(where, `знак «${q.mark}» ещё не пройден к этому шагу`);
        // С сукуном ответ - звук буквы (sound), как в приложении.
        const ru = byLetter.get(q.letter)?.translations?.[BASE_LANGUAGE];
        if (!(q.mark === 'sukun' ? ru?.sound : ru?.syllables?.[q.mark])) {
          error(where, `нет транскрипции слога ${q.letter} + ${q.mark} - нечего показать в ответе`);
        }
      }
      // Закрытый слог (أِبْ): огласовка алифа - фатхьа, касра или домма; сукун пройден; у буквы есть звук.
      if (q.type === 'closedSyllable') {
        if (!['fatha', 'kasra', 'damma'].includes(q.mark)) error(where, 'closedSyllable: mark - fatha, kasra или damma');
        if (!passed.has('sukun')) error(where, 'closedSyllable: сукун ещё не пройден к этому шагу');
        if (!byLetter.get(q.letter)?.translations?.[BASE_LANGUAGE]?.sound) error(where, `нет звука буквы ${q.letter}`);
        // После этой огласовки буква слог не закрывает (أُوْ, إِيْ - мадд): closedVowels буквы.
        const vowels = byLetter.get(q.letter)?.closedVowels;
        if (vowels && !vowels.includes(q.mark)) error(where, `closedSyllable: у ${q.letter} после ${q.mark} закрытого слога нет (closedVowels)`);
      }
    }
  }

  // Руководства: id уникален, буквы известны и не повторяются между руководствами (у буквы - одно).
  if (file.guides !== undefined && !Array.isArray(file.guides)) error('reading guides', 'guides - массив');
  const guideIds = new Set();
  const guideLetters = new Set();
  for (const [i, guide] of (Array.isArray(file.guides) ? file.guides : []).entries()) {
    const at = `reading guide ${guide?.id ?? `#${i + 1}`}`;
    for (const k of Object.keys(guide ?? {})) if (!READING_GUIDE_KEYS.includes(k)) error(at, `неизвестное поле «${k}»`);
    if (requireString(at, guide ?? {}, 'id')) {
      if (guideIds.has(guide.id)) error(at, 'id повторяется');
      guideIds.add(guide.id);
    }
    if (!Array.isArray(guide?.letters) || !guide.letters.length) error(at, 'нет непустого массива letters');
    for (const l of guide?.letters ?? []) {
      knownLetter(at, l);
      if (guideLetters.has(l)) error(at, `буква ${l} уже в другом руководстве`);
      guideLetters.add(l);
    }
    checkTexts(at, guide?.translations, ['title'], ['title']);
    if (!Array.isArray(guide?.blocks) || !guide.blocks.length) error(at, 'нет непустого массива blocks');
    for (const [k, block] of (guide?.blocks ?? []).entries()) {
      const where = `${at} block ${k + 1}`;
      const keys = READING_GUIDE_BLOCKS[block?.type];
      if (!keys) { error(where, `«${block?.type}» - руководство показывает только ${Object.keys(READING_GUIDE_BLOCKS).join(', ')}`); continue; }
      for (const f of Object.keys(block)) if (!keys.includes(f)) error(where, `неизвестное поле «${f}»`);
      if (block.type === 'text') {
        const ru = checkTexts(where, block.translations, ['title', 'text'], ['text']);
        if (ru?.text) checkRussian(where, ru.text);
        continue;
      }
      if (requireString(where, block, 'image') && !existsSync(join(READING_IMAGES_DIR, `${block.image}.webp`))) {
        error(where, `нет файла images/reading/${block.image}.webp`);
      }
      // Подпись необязательна: рисунок без текста - тоже рисунок.
      if (block.translations !== undefined) {
        const ru = checkTexts(where, block.translations, ['title', 'text'], []);
        if (ru?.text) checkRussian(where, ru.text);
      }
      if (block.legend !== undefined && !Array.isArray(block.legend)) error(where, 'legend - массив');
      for (const [n, item] of (Array.isArray(block.legend) ? block.legend : []).entries()) {
        const lat = `${where} legend ${n + 1}`;
        for (const f of Object.keys(item ?? {})) if (!['color', 'translations'].includes(f)) error(lat, `неизвестное поле «${f}»`);
        if (!/^#[0-9A-Fa-f]{6}$/.test(item?.color ?? '')) error(lat, `color «${item?.color}» - ожидается #RRGGBB`);
        checkTexts(lat, item?.translations, ['text'], ['text']);
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
checkVersions(readData('versions.json'));
checkReading(readData('reading.json'));

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