#!/usr/bin/env node
// Какие записи нужны для «Учимся читать» (раздел «Махрадж») и какие уже есть в audio/reading/.
//
//   node scripts/reading-recordings.mjs           список: ✓ есть, · нет (по разделам)
//   node scripts/reading-recordings.mjs --missing только недостающие
//   node scripts/reading-recordings.mjs --write   вписать найденные записи букв в data/reading.json (поле audio)
//
// Имена - docs/process/audio.md, раздел 5:
//   reading_<NN буквы>_name.m4a          название буквы (лист буквы, «Послушать алфавит»)
//   reading_<NN буквы>_closed_<знак>.m4a закрытый слог: алиф-хӏамза с фатхьой / касрой / доммой и буква с сукуном -
//                                        أَبْ «ъаб» (лист буквы, таблица шага «Сукун»; у алифа нет: согласная с сукуном
//                                        одна не произносится, решение пользователя 2026-10-19). У хӏамзы своё -
//                                        только название: остальное - записи алифа (audioLike)
//   reading_<NN буквы>_<знак>.m4a        слог с фатхьой / касрой / доммой (лист буквы, таблица слогов)
//   reading_s<NN шага>_<NN буквы>_<initial|medial|final>.m4a   слово-пример формы буквы (формы букв)

import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DATA = join(ROOT, 'data', 'reading.json');
const AUDIO = join(ROOT, 'audio', 'reading');
const args = new Set(process.argv.slice(2));

const raw = readFileSync(DATA, 'utf8');
const data = JSON.parse(raw);
const present = new Set(existsSync(AUDIO) ? readdirSync(AUDIO) : []);
const pad = n => String(n).padStart(2, '0');
const ru = x => x?.translations?.ru ?? {};
const VOWELS = ['fatha', 'kasra', 'damma'];

/** Разделы: заголовок и записи { file, what }. */
const sections = [];

sections.push({
  title: 'Названия букв (лист буквы, «Послушать алфавит»)',
  items: data.letters.map(l => ({ file: `reading_${pad(l.id)}_name.m4a`, what: `${l.letter} ${ru(l).name} - «${ru(l).nameTranslit}»` })),
});
const marks = Object.fromEntries(data.marks.map(m => [m.id, m]));
// Закрытый слог: алиф-хӏамза с огласовкой и буква с сукуном (у алифа и хӏамзы - хӏамза с сукуном на алифе,
// closedBase: أَأْ إِأْ أُأْ «ъаъ ъиъ ъуъ», решение пользователя 2026-10-19).
const alif = data.letters.find(l => l.letter === 'ا');
const CLOSED = { fatha: 'closedFatha', kasra: 'closedKasra', damma: 'closedDamma' };
const closedArabic = (l, v) => `${alif.syllableBases?.[v] ?? alif.letter}${marks[v].sign}${l.closedBase ?? l.letter}${marks.sukun.sign}`;
const closedFile = (l, v) => `reading_${pad(l.id)}_closed_${v}.m4a`;
// Хӏамза звучит как алиф (audioLike, решение пользователя 2026-10-19): своё у неё только название, слоги, танвин и
// закрытые слоги - записи алифа (reading_01_…). Свои записи слогов - только у букв без audioLike.
const letterOf = Object.fromEntries(data.letters.map(l => [l.letter, l]));
const owner = l => (l.audioLike ? letterOf[l.audioLike] : l);
const own = data.letters.filter(l => !l.audioLike);
// У و ي - только после фатхьи (closedVowels): أُوْ, إِيْ - мадд, إِوْ, أُيْ не бывает.
const closedVowels = l => l.closedVowels ?? VOWELS;
sections.push({
  title: 'Закрытые слоги (лист буквы, таблица шага «Сукун»)',
  items: own.flatMap(l => closedVowels(l).map(v => ({
    file: closedFile(l, v), what: `${closedArabic(l, v)} - «${ru(alif).syllables[v]}${ru(l).sound}»`,
  }))),
});
sections.push({
  title: 'Слоги с фатхьой, касрой, доммой (лист буквы, таблица слогов)',
  items: own.flatMap(l => VOWELS.filter(v => ru(l).syllables?.[v]).map(v => {
    const base = l.syllableBases?.[v] ?? l.letter;
    return { file: `reading_${pad(l.id)}_${v}.m4a`, what: `${base}${marks[v].sign} - «${ru(l).syllables[v]}»` };
  })),
});
// Танвин (шаг «Танвин»): после танвина фатхьи - алиф, который не читается (у алифа - без второго алифа).
const TANWIN = ['fathatan', 'kasratan', 'dammatan'];
sections.push({
  title: 'Слоги с танвином (лист буквы, таблица слогов шага «Танвин»)',
  items: own.flatMap(l => TANWIN.filter(v => ru(l).syllables?.[v]).map(v => {
    const base = l.syllableBases?.[v] ?? l.letter;
    const alif = v === 'fathatan' && base !== 'أ' ? 'ا' : '';
    return { file: `reading_${pad(l.id)}_${v}.m4a`, what: `${base}${marks[v].sign}${alif} - «${ru(l).syllables[v]}»` };
  })),
});
const byLetter = Object.fromEntries(data.letters.map(l => [l.letter, l]));
for (const step of data.steps) {
  for (const block of step.blocks ?? []) {
    if (block.type !== 'forms' || !block.examples) continue;
    sections.push({
      title: `Шаг ${step.id}: слова-примеры форм букв`,
      items: Object.entries(block.examples).flatMap(([char, words]) =>
        ['initial', 'medial', 'final'].map((form, i) => ({
          file: `reading_s${pad(step.id)}_${pad(byLetter[char].id)}_${form}.m4a`,
          what: `${words[i]} (${char}, ${{ initial: 'в начале', medial: 'в середине', final: 'в конце' }[form]})`,
        }))),
    });
  }
}

let total = 0;
let done = 0;
for (const { title, items } of sections) {
  const have = items.filter(i => present.has(i.file)).length;
  total += items.length;
  done += have;
  console.log(`\n${title}: ${have} из ${items.length}`);
  for (const { file, what } of items) {
    const ok = present.has(file);
    if (args.has('--missing') && ok) continue;
    console.log(`  ${ok ? '✓' : '·'} ${file}  ${what}`);
  }
}
console.log(`\nВсего: ${done} из ${total} записей.`);

// Записи букв - в data/reading.json (название, слоги, танвин, закрытые слоги): приложение берёт их оттуда -
// и в листе буквы, и в таблице закрытых слогов шага «Сукун».
if (args.has('--write')) {
  let changed = 0;
  const put = (l, key, file) => {
    if (present.has(file) && l.audio?.[key] !== file) {
      l.audio = { ...l.audio, [key]: file };
      changed++;
    }
  };
  for (const l of data.letters) {
    put(l, 'name', `reading_${pad(l.id)}_name.m4a`);
    for (const key of [...VOWELS, ...TANWIN]) put(l, key, `reading_${pad(owner(l).id)}_${key}.m4a`);
    for (const v of closedVowels(l)) put(l, CLOSED[v], closedFile(owner(l), v));
  }
  if (changed) writeFileSync(DATA, JSON.stringify(data, null, 2) + (raw.endsWith('\n') ? '\n' : ''));
  console.log(`--write: в reading.json вписано записей: ${changed}`);
}
