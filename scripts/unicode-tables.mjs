#!/usr/bin/env node
import { readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const directory = join(root, 'formhaus-core/src/main/kotlin/dev/formhaus/core/unicode')
const version = process.versions.unicode
const major = version.split('.')[0]
const output = join(directory, `Unicode${major}Tables.kt`)
const alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_'
const maxPoint = 0x10ffff
const maxUnit = 0xffff
const chunk = 100

const char = (point) => String.fromCodePoint(point)
const isSurrogate = (point) => point >= 0xd800 && point <= 0xdfff

function encode(numbers) {
  let text = ''
  for (let value of numbers) {
    if (!Number.isInteger(value) || value < 0) throw new Error(`cannot encode ${value}`)
    while (value >= 32) {
      text += alphabet[32 + (value & 31)]
      value = Math.floor(value / 32)
    }
    text += alphabet[value]
  }
  return text
}

const zigzag = (value) => (value < 0 ? -2 * value - 1 : 2 * value)

function rangesOf(test, last = maxPoint) {
  const ranges = []
  for (let point = 0; point <= last; point++) {
    if (!test(point)) continue
    const previous = ranges[ranges.length - 1]
    if (previous && previous[1] === point - 1) previous[1] = point
    else ranges.push([point, point])
  }
  return ranges
}

function encodeRanges(ranges) {
  const numbers = []
  let next = 0
  for (const [lower, upper] of ranges) {
    numbers.push(lower - next, upper - lower)
    next = upper + 1
  }
  return encode(numbers)
}

function runsOf(map, last) {
  const runs = []
  for (let point = 0; point <= last; point++) {
    const target = map(point)
    if (target === point) continue
    const delta = target - point
    const run = runs[runs.length - 1]
    if (run && run.delta === delta && (run.count === 1 || point === run.start + run.count * run.step)) {
      if (run.count === 1) run.step = point - run.start
      run.count++
    } else {
      runs.push({ start: point, count: 1, step: 1, delta })
    }
  }
  return runs
}

function encodeRuns(runs) {
  const numbers = []
  let next = 0
  for (const { start, count, step, delta } of runs) {
    numbers.push(start - next, count - 1, step - 1, zigzag(delta))
    next = start + (count - 1) * step + 1
  }
  return encode(numbers)
}

function property(name, last = maxPoint) {
  const pattern = new RegExp(`^\\p{${name}}$`, 'u')
  return rangesOf((point) => pattern.test(char(point)), last)
}

function canonicalize(unit) {
  if (isSurrogate(unit)) return unit
  const upper = String.fromCharCode(unit).toUpperCase()
  if (upper.length !== 1) return unit
  const target = upper.charCodeAt(0)
  return unit >= 128 && target < 128 ? unit : target
}

function lowercase(point) {
  if (isSurrogate(point)) return point
  const lower = char(point).toLowerCase()
  const points = [...lower].map((it) => it.codePointAt(0))
  if (points.length === 1) return points[0]
  if (point === 0x130) return point
  throw new Error(`unexpected full lowercase of U+${point.toString(16)}`)
}

function checkCanonical() {
  const orbits = new Map()
  for (let unit = 0; unit <= maxUnit; unit++) {
    const key = canonicalize(unit)
    if (!orbits.has(key)) orbits.set(key, [])
    orbits.get(key).push(unit)
  }
  for (const members of orbits.values()) {
    if (members.length < 2) continue
    for (const unit of members) {
      const pattern = new RegExp(`^\\u${unit.toString(16).padStart(4, '0')}$`, 'i')
      for (const other of members) {
        if (!pattern.test(String.fromCharCode(other))) throw new Error(`V8 does not fold U+${unit.toString(16)}`)
      }
    }
  }
}

function checkGroupNames(ranges, prefix) {
  const inside = new Set()
  for (const [lower, upper] of ranges) for (let point = lower; point <= upper; point++) inside.add(point)
  for (let point = 0; point <= maxPoint; point++) {
    if (isSurrogate(point) || point === 0x24 || point === 0x5f || point === 0x200c || point === 0x200d) continue
    const name = prefix + char(point)
    let valid = false
    try {
      valid = new RegExp(`(?<${name}>a)`).exec('a')?.groups?.[name] === 'a'
    } catch {
      valid = false
    }
    if (valid !== inside.has(point)) throw new Error(`group name check differs at U+${point.toString(16)}`)
  }
}

function closing(point) {
  return !isSurrogate(point) && /[?!:.。]$/.test(char(point).normalize('NFKD'))
}

function checkClosing(ranges) {
  const inside = new Set()
  for (const [lower, upper] of ranges) for (let point = lower; point <= upper; point++) inside.add(point)
  for (let point = 0; point <= maxPoint; point++) {
    if (isSurrogate(point)) continue
    for (const prefix of ['', 'a', 'e', 'ᄀ', '?']) {
      if (/[?!:.。]$/.test((prefix + char(point)).normalize('NFKC')) !== inside.has(point)) {
        throw new Error(`closing check differs at U+${point.toString(16)}`)
      }
    }
  }
}

const idStart = property('ID_Start')
const idContinue = property('ID_Continue')
const closingRanges = rangesOf(closing)
checkCanonical()
checkGroupNames(idStart, '')
checkGroupNames(idContinue, 'a')
checkClosing(closingRanges)

const tables = {
  VERSION: null,
  CANONICAL_RUNS: encodeRuns(runsOf(canonicalize, maxUnit)),
  LOWERCASE_RUNS: encodeRuns(runsOf(lowercase, maxPoint)),
  CASED_RANGES: encodeRanges(property('Cased')),
  CASE_IGNORABLE_RANGES: encodeRanges(property('Case_Ignorable')),
  ID_START_RANGES: encodeRanges(idStart),
  ID_CONTINUE_RANGES: encodeRanges(idContinue),
  LETTER_MARK_NUMBER_RANGES: encodeRanges(rangesOf((point) => /^[\p{L}\p{M}\p{N}]$/u.test(char(point)))),
  WHITE_SPACE_RANGES: encodeRanges(rangesOf((point) => /^\s$/.test(String.fromCharCode(point)), maxUnit)),
  CLOSING_PUNCTUATION_RANGES: encodeRanges(closingRanges),
  HEBREW_SCRIPT_RANGES: encodeRanges(property('Script=Hebrew')),
}

function constant(name, value) {
  if (value === null) return `internal const val UNICODE_VERSION: String = "${version}"\n`
  const pieces = []
  for (let index = 0; index < value.length; index += chunk) pieces.push(value.slice(index, index + chunk))
  if (pieces.length === 1) return `internal const val ${name}: String =\n    "${pieces[0]}"\n`
  const body = pieces.map((piece, index) => (index === 0 ? `    "${piece}" +` : `        "${piece}"${index < pieces.length - 1 ? ' +' : ''}`))
  return `internal const val ${name}: String =\n${body.join('\n')}\n`
}

for (const file of readdirSync(directory)) {
  if (/^Unicode\d+Tables\.kt$/.test(file)) rmSync(join(directory, file))
}
const source = Object.entries(tables).map(([name, value]) => constant(name, value)).join('\n')
writeFileSync(output, `package dev.formhaus.core.unicode\n\n${source}`)
console.log(`wrote ${output} for Unicode ${version}`)
