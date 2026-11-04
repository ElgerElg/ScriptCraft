#!/usr/bin/env node
/*
 * Проверка данных сайта документации: node docs/check.js
 * Загружает data/syntax.js и data/examples.js (как это делает браузер) и проверяет:
 * уникальные id, обязательные поля, известные категории и ссылки related.
 */
"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const sandbox = { window: {} };
vm.createContext(sandbox);
for (const f of ["data/syntax.js", "data/examples.js"]) {
  vm.runInContext(fs.readFileSync(path.join(__dirname, f), "utf8"), sandbox, { filename: f });
}
const docs = sandbox.window.SC_DOCS;
const errors = [];
const err = (m) => errors.push(m);
const isText = (v) => typeof v === "string" && v.trim().length > 0;
const isI18n = (v) => v && isText(v.ru) && isText(v.en);

const cats = new Set();
for (const c of docs.syntax.categories) {
  if (!isText(c.id) || !isText(c.ru) || !isText(c.en)) err("категория без id/ru/en: " + JSON.stringify(c));
  if (cats.has(c.id)) err("повтор категории " + c.id);
  cats.add(c.id);
}

const ids = new Set();
const counts = {};
for (const e of docs.syntax.entries) {
  const where = e.id || JSON.stringify(e).slice(0, 60);
  if (!isText(e.id) || !/^[A-Za-z0-9_-]+$/.test(e.id)) err(where + ": плохой id");
  if (ids.has(e.id)) err(where + ": повтор id");
  ids.add(e.id);
  if (!cats.has(e.category)) err(where + ": неизвестная категория " + e.category);
  if (!isI18n(e.name)) err(where + ": нет name.ru/name.en");
  if (!isI18n(e.desc)) err(where + ": нет desc.ru/desc.en");
  if (!Array.isArray(e.syntax) || !e.syntax.length || !e.syntax.every(isText)) err(where + ": нет syntax[]");
  if (!isText(e.example)) err(where + ": нет example");
  if (!/^\d+\.\d+\.\d+$/.test(e.since || "")) err(where + ": плохое since");
  if (!Array.isArray(e.related)) err(where + ": related должен быть массивом");
  counts[e.category] = (counts[e.category] || 0) + 1;
}
for (const e of docs.syntax.entries) {
  for (const r of e.related || []) {
    if (!ids.has(r)) err(e.id + ": related ссылается на несуществующий " + r);
    if (r === e.id) err(e.id + ": related на самого себя");
  }
}

const exIds = new Set();
for (const x of docs.examples) {
  if (!isText(x.id) || exIds.has(x.id)) err("пример: плохой/повторный id " + x.id);
  exIds.add(x.id);
  if (!isI18n(x.title) || !isI18n(x.intro)) err(x.id + ": нет title/intro ru+en");
  if (!Array.isArray(x.blocks) || !x.blocks.length) err(x.id + ": нет blocks");
  for (const b of x.blocks || []) {
    if (!isText(b.code) || !isI18n(b.title) || !isText(b.lang)) err(x.id + ": плохой блок кода");
  }
  for (const r of x.related || []) if (!ids.has(r)) err(x.id + ": related " + r + " не найден");
}

for (const c of docs.syntax.categories) console.log(`${c.id.padEnd(12)} ${String(counts[c.id] || 0).padStart(3)}  ${c.ru}`);
console.log(`${"всего".padEnd(12)} ${String(docs.syntax.entries.length).padStart(3)}  записей, примеров: ${docs.examples.length}`);
if (errors.length) {
  console.error("\nОШИБКИ (" + errors.length + "):\n  " + errors.join("\n  "));
  process.exit(1);
}
console.log("OK");
