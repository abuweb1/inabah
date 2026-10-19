#!/usr/bin/env node
// Какие записи нужны для «Учимся читать» (раздел «Махрадж») и какие уже есть в audio/reading/.
//
//   node scripts/reading-recordings.mjs           список: ✓ есть, · нет (по разделам)
//   node scripts/reading-recordings.mjs --missing только недостающие
//   node scripts/reading-recordings.mjs --write   вписать найденные записи букв в data/reading.json (поле audio)
//
// Имена - docs/process/audio.md, раздел 5:
//   reading_<NN буквы>_name.m4a          название буквы (лист буквы, «Послушать алфавит»)
//   reading_<NN буквы>_sound.m4a         звук буквы с сукуном (лист буквы)
//   reading_<NN буквы>_<знак>.m4a        слог с фатхьой / касрой / доммой (лист буквы, таблица слогов)
//   reading_s<NN шага>_<NN буквы>_<initial|medial|final>.m4a   слово-пример формы буквы (формы букв)

import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DATA = join(ROOT, 'data', 'reading.json');
const AUDIO = join(ROOT, 'audio', 'reading');
const args = new Set(process.argv.slice(2));

const data = JSON.parse(readFileSync(DATA, 'utf8'));
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
sections.push({
  title: 'Звук буквы с сукуном (лист буквы)',
  items: data.letters.map(l => ({ file: `reading_${pad(l.id)}_sound.m4a`, what: `${l.letter}ْ - «${ru(l).sound}»` })),
});
const marks = Object.fromEntries(data.marks.map(m => [m.id, m]));
sections.push({
  title: 'Слоги с фатхьой, касрой, доммой (лист буквы, таблица слогов)',
  items: data.letters.flatMap(l => VOWELS.filter(v => ru(l).syllables?.[v]).map(v => {
    const base = l.syllableBases?.[v] ?? l.letter;
    return { file: `reading_${pad(l.id)}_${v}.m4a`, what: `${base}${marks[v].sign} - «${ru(l).syllables[v]}»` };
  })),
});
// Танвин (шаг «Танвин»): после танвина фатхьи - алиф, который не читается (у алифа - без второго алифа).
const TANWIN = ['fathatan', 'kasratan', 'dammatan'];
sections.push({
  title: 'Слоги с танвином (лист буквы, таблица слогов шага «Танвин»)',
  items: data.letters.flatMap(l => TANWIN.filter(v => ru(l).syllables?.[v]).map(v => {
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

// Записи букв - в data/reading.json (название, звук, слоги, танвин): приложение берёт их оттуда.
if (args.has('--write')) {
  let changed = 0;
  for (const l of data.letters) {
    for (const key of ['name', 'sound', ...VOWELS, ...TANWIN]) {
      const file = `reading_${pad(l.id)}_${key}.m4a`;
      if (present.has(file) && l.audio?.[key] !== file) {
        l.audio = { ...l.audio, [key]: file };
        changed++;
      }
    }
  }
  if (changed) writeFileSync(DATA, JSON.stringify(data, null, 2) + '\n');
  console.log(`--write: в reading.json вписано записей: ${changed}`);
}
