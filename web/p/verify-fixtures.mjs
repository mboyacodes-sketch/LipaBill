#!/usr/bin/env node
/**
 * Asserts web/p/paylink.js decodes the same golden vectors as Kotlin PayLink.
 * Run: node web/p/verify-fixtures.mjs
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const fixtures = JSON.parse(readFileSync(join(dir, "fixtures.json"), "utf8"));
const code = readFileSync(join(dir, "paylink.js"), "utf8");

const sandbox = {
  module: { exports: {} },
  exports: {},
  console
};
sandbox.globalThis = sandbox;
vm.runInNewContext(code, sandbox, { filename: "paylink.js" });
const PayLinkWeb = sandbox.PayLinkWeb || sandbox.module.exports;

let failed = 0;
for (const row of fixtures.cases) {
  const parsed = PayLinkWeb.parse(row.token, fixtures.nowMillis);
  const ok =
    parsed &&
    parsed.kind === row.kind &&
    parsed.identifier === row.identifier &&
    (parsed.account || null) === (row.account || null) &&
    (parsed.amountShillings || null) === (row.amountShillings || null);
  if (!ok) {
    failed += 1;
    console.error("FAIL", row.token, "expected", row, "got", parsed);
  } else {
    console.log("ok", row.kind, row.identifier, row.amountShillings ?? "-");
  }
}

const bad = PayLinkWeb.parse("not-a-token", fixtures.nowMillis);
if (bad !== null) {
  failed += 1;
  console.error("FAIL expected null for garbage token");
}

if (failed) {
  console.error(`\n${failed} fixture(s) failed`);
  process.exit(1);
}
console.log(`\n${fixtures.cases.length} fixtures matched Kotlin vectors`);
