#!/usr/bin/env node
// Переносит заполненные записи из data/pending/missing-texts.json в основные data/*.json.
//
//   node scripts/apply-pending.mjs
//
// Применённые записи удаляются из pending-файла, незаполненные остаются.
// После — node scripts/check-data.mjs и node scripts/sync-prototype.mjs.

import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { ROOT, BASE_LANGUAGE } from './lib/content.mjs';

const PENDING = join(ROOT, 'data', 'pending', 'missing-texts.json');

const cache = new Map();
const load = file => {
  if (!cache.has(file)) cache.set(file, JSON.parse(readFileSync(join(ROOT, 'data', file), 'utf8')));
  return cache.get(file);
};
const itemOf = e => {
  const data = load(e.file);
  const list = e.section ? data[e.section] : data;
  const item = list?.find(x => x.id === e.id);
  if (!item) throw new Error(`${e.file} ${e.section ?? ''} id ${e.id}: запись не найдена`);
  // Пропуски найдены в русском переводе — правки идут в translations.ru.
  return item.translations[BASE_LANGUAGE];
};

const pending = JSON.parse(readFileSync(PENDING, 'utf8'));
const applied = [];

pending.texts = pending.texts.filter(e => {
  if (!e.text.trim()) return true;
  const item = itemOf(e);
  const anchor = e.before + e.gap + e.after;
  if (item.text.split(anchor).length !== 2) {
    throw new Error(`${e.file} id ${e.id}: текст вокруг пропуска изменился — якорь не найден`);
  }
  const joined = [e.before.trimEnd(), e.text.trim(), e.after.trimStart()].filter(Boolean).join(' ');
  item.text = item.text.replace(anchor, joined);
  applied.push(`${e.file} id ${e.id}: текст вставлен`);
  return false;
});

pending.sources = pending.sources.filter(e => {
  if (!e.source.trim()) return true;
  const item = itemOf(e);
  if (item.source) throw new Error(`${e.file} id ${e.id}: source уже заполнен («${item.source}»)`);
  item.source = e.source.trim();
  applied.push(`${e.file}${e.section ? ' ' + e.section : ''} id ${e.id}: source = ${item.source}`);
  return false;
});

for (const [file, data] of cache) writeFileSync(join(ROOT, 'data', file), JSON.stringify(data, null, 2) + '\n');
writeFileSync(PENDING, JSON.stringify(pending, null, 2) + '\n');

console.log(applied.length ? applied.join('\n') : 'Нечего применять — заполненных записей нет');
console.log(`Осталось: текстов ${pending.texts.length}, источников ${pending.sources.length}`);
