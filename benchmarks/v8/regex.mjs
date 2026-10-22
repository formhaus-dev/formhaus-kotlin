import { readFileSync, writeFileSync } from 'node:fs';
import { Worker, isMainThread, parentPort, workerData } from 'node:worker_threads';

const COMPILE_VARIANTS = 300;
const casesUrl = new URL('../src/main/resources/regex-cases.json', import.meta.url);

function option(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

const settings = {
  warmups: Number(option('warmups', 5)),
  runs: Number(option('runs', 15)),
  batchMs: Number(option('batch-ms', 50)),
  cap: Number(option('cap', 5)),
  json: option('json', null),
};

function randomText(count, seed) {
  const mask = (1n << 64n) - 1n;
  let state = BigInt(seed);
  let text = '';
  for (let index = 0; index < count; index += 1) {
    state = (state * 6364136223846793005n + 1442695040888963407n) & mask;
    text += state >> 63n === 0n ? 'a' : 'b';
  }
  return text;
}

function text(spec) {
  if (typeof spec === 'string') return spec;
  return (spec ?? [])
    .map((part) => {
      if (typeof part === 'string') return part;
      if (part.random !== undefined) return randomText(part.random, part.seed);
      return part.repeat.repeat(part.count);
    })
    .join('');
}

function loadCases(group) {
  const root = JSON.parse(readFileSync(casesUrl, 'utf8'));
  return root[group].map((item) => ({ ...item, pattern: text(item.pattern), short: text(item.short), long: text(item.long) }));
}

function median(samples) {
  const sorted = [...samples].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

function now() {
  return Number(process.hrtime.bigint());
}

function batchSize(call) {
  let calls = 1;
  for (;;) {
    const start = now();
    for (let index = 0; index < calls; index += 1) call(index);
    if (now() - start >= settings.batchMs * 1e6 || calls >= 1 << 30) return calls;
    calls *= 2;
  }
}

function measure(call) {
  const calls = batchSize(call);
  const samples = [];
  for (let run = 0; run < settings.warmups + settings.runs; run += 1) {
    const start = now();
    for (let index = 0; index < calls; index += 1) call(index);
    if (run >= settings.warmups) samples.push((now() - start) / calls);
  }
  return median(samples);
}

function formatNumber(value) {
  if (value < 1) return value.toFixed(2);
  if (value < 10) return value.toFixed(1);
  return Math.round(value).toLocaleString('en-US');
}

function formatNanos(nanos) {
  if (typeof nanos === 'string') return nanos;
  if (nanos >= 1e10) return `${formatNumber(nanos / 1e9)} s`;
  if (nanos >= 1e6) return `${formatNumber(nanos / 1e6)} ms`;
  if (nanos >= 100) return `${formatNumber(nanos / 1e3)} µs`;
  return `${formatNumber(nanos)} ns`;
}

function typicalRows() {
  const rows = [];
  for (const item of loadCases('typical')) {
    const variants = Array.from({ length: COMPILE_VARIANTS }, (_, index) => `${item.pattern}(?:z${index}){0}`);
    const cached = new RegExp(item.pattern);
    rows.push([`match ${item.label}, short`, `matchShort:${item.id}`, measure(() => cached.test(item.short))]);
    rows.push([`match ${item.label}, long`, `matchLong:${item.id}`, measure(() => cached.test(item.long))]);
    rows.push([`compile ${item.label}`, `compile:${item.id}`, measure((index) => new RegExp(variants[index % COMPILE_VARIANTS]).test(item.short))]);
  }
  return rows;
}

function pathologicalRow(item) {
  return new Promise((resolve) => {
    const worker = new Worker(new URL(import.meta.url), { workerData: { id: item.id, settings }, argv: [] });
    let timer = setTimeout(() => {
      worker.terminate();
      resolve(`> ${settings.cap} s`);
    }, settings.cap * 1000);
    worker.on('message', (message) => {
      if (message.probed) {
        clearTimeout(timer);
        timer = null;
        return;
      }
      resolve(message.result);
      worker.terminate();
    });
    worker.on('error', (error) => {
      if (timer) clearTimeout(timer);
      resolve(error instanceof RangeError ? 'stack overflow' : `error: ${error.message}`);
    });
  });
}

function runWorker() {
  Object.assign(settings, workerData.settings);
  const item = loadCases('pathological').find((candidate) => candidate.id === workerData.id);
  const cached = new RegExp(item.pattern);
  cached.test(item.long);
  parentPort.postMessage({ probed: true });
  parentPort.postMessage({ result: measure(() => cached.test(item.long)) });
}

async function main() {
  const rows = typicalRows();
  for (const item of loadCases('pathological')) {
    rows.push([item.label, `match:${item.id}`, await pathologicalRow(item)]);
  }
  const order = (key) => (key.startsWith('compile:') ? 2 : key.startsWith('match:') ? 1 : 0);
  rows.sort((a, b) => order(a[1]) - order(b[1]));
  console.log(`V8 ${process.versions.v8}, Node ${process.version}: new RegExp(p).test(s), median of ${settings.runs} batches after ${settings.warmups} warmups. V8 caches compiled patterns by source, so compile rows can hit that cache.\n`);
  console.log('| Benchmark | V8 |\n| --- | ---: |');
  for (const [label, , value] of rows) console.log(`| ${label.replaceAll('|', '\\|')} | ${formatNanos(value)} |`);
  if (settings.json) writeFileSync(settings.json, JSON.stringify(Object.fromEntries(rows.map(([, key, value]) => [key, value])), null, 2));
}

if (isMainThread) await main();
else runWorker();
