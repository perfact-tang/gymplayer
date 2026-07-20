import test from "node:test";
import assert from "node:assert/strict";
import { normalizeRecords } from "../src/importTrainingMenu.js";

const options = {};

test("imports multilingual columns", () => {
  const [machine] = normalizeRecords([{ id: "m1", number: "1", name_ja: "プレス", bodyPart_ja: "胸", name_zh: "推胸", bodyPart_zh: "胸部", name_en: "Press", bodyPart_en: "Chest", name_ko: "프레스", bodyPart_ko: "가슴" }], options);
  assert.equal(machine.data.name, "プレス");
  assert.equal(machine.data.translations["zh-CN"].name, "推胸");
  assert.equal(machine.data.translations.en.bodyPart, "Chest");
});

test("accepts legacy columns and falls back to Japanese", () => {
  const [machine] = normalizeRecords([{ id: "m1", number: "11a", name: "プレス", bodyPart: "胸" }], options);
  assert.equal(machine.data.translations.en.name, "プレス");
  assert.equal(machine.data.translations.ko.bodyPart, "胸");
});

test("rejects an unknown column", () => {
  assert.throws(() => normalizeRecords([{ id: "m1", number: "1", name: "A", bodyPart: "B", typo: "x" }], options), /unknown column/);
});

test("rejects duplicate ids", () => {
  assert.throws(() => normalizeRecords([{ id: "m1", number: "1", name: "A", bodyPart: "B" }, { id: "m1", number: "2", name: "C", bodyPart: "D" }], options), /duplicate id/);
});
