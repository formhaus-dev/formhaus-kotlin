#!/usr/bin/env node
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const directory = join(root, 'formhaus-core/src/main/kotlin/dev/formhaus/core/cldr')
const fixtureDirectory = join(root, 'formhaus-core/src/test/resources/cldr')
const version = process.versions.cldr
const major = version.split('.')[0]
const output = join(directory, `Cldr${major}Tables.kt`)
const letters = 'abcdefghijklmnopqrstuvwxyz'
const privateSentinels = ['\ue000', '\ue001', '\ue002', '\ue003', '\ue004', '\ue005']
const hebrewSentinels = ['\u05d0', '\u05d1', '\u05d2', '\u05d3', '\u05d4', '\u05d6']
const entrySeparator = '\n'
const fieldSeparator = '|'
const chunk = 100

const supported = (tag) => Intl.DateTimeFormat.supportedLocalesOf(tag).length > 0
const canonical = (tag) => Intl.getCanonicalLocales(tag)[0]

function languageCodes() {
  const codes = []
  for (const a of letters) for (const b of letters) codes.push(a + b)
  for (const a of letters) for (const b of letters) for (const c of letters) codes.push(a + b + c)
  return codes.filter((code) => supported(code) && canonical(code) === code)
}

function regionCodes() {
  const names = new Intl.DisplayNames('en', { type: 'region', fallback: 'none' })
  const codes = ['001', '150', '419']
  for (const a of letters) for (const b of letters) codes.push((a + b).toUpperCase())
  return codes.filter((code) => names.of(code) !== undefined)
}

function separators(tag) {
  const list = new Intl.ListFormat(tag, { type: 'conjunction' })
  const sentinels = new Intl.Locale(tag).language === 'he' ? hebrewSentinels : privateSentinels
  const gaps = (count) => {
    const text = list.format(sentinels.slice(0, count))
    const parts = []
    let from = 0
    for (let index = 0; index < count; index++) {
      const at = text.indexOf(sentinels[index], from)
      if (at < 0 || text.indexOf(sentinels[index], at + 1) >= 0) throw new Error(`unexpected list in ${tag}: ${text}`)
      parts.push(text.slice(from, at))
      from = at + 1
    }
    parts.push(text.slice(from))
    if (parts[0] !== '') throw new Error(`list pattern of ${tag} has a prefix`)
    return parts.slice(1)
  }
  const pair = gaps(2)
  const four = gaps(4)
  const [start, middle, end, trailing] = four
  for (let count = 3; count <= sentinels.length; count++) {
    const expected = [start, ...Array(count - 3).fill(middle), end, trailing]
    if (gaps(count).join() !== expected.join()) throw new Error(`list pattern of ${tag} is not regular`)
  }
  const fields = [start, middle === start ? '' : middle, end, pair[0] === end ? '' : pair[0]]
  if (trailing !== '' || pair[1] !== '') fields.push(trailing, pair[1])
  for (const [index, field] of [...four, ...pair].entries()) {
    if (field.includes(entrySeparator) || field.includes(fieldSeparator)) throw new Error(`separator clash in ${tag}`)
    if (field === '' && index < 3) throw new Error(`empty separator in ${tag}`)
  }
  return fields.join(fieldSeparator)
}

function grouping(tag) {
  const format = (value) => new Intl.NumberFormat(`${tag}-u-nu-latn`).format(value)
  const groups = format(1234567890).split(/[^0-9]+/u).filter(Boolean)
  const primary = groups[groups.length - 1].length
  const secondary = groups.length > 2 ? groups[groups.length - 2].length : primary
  if (groups.slice(1, -1).some((group) => group.length !== secondary)) throw new Error(`irregular grouping in ${tag}`)
  const minimum = /^[0-9]+$/u.test(format(10 ** primary)) ? (/^[0-9]+$/u.test(format(10 ** (primary + 1))) ? 3 : 2) : 1
  return [primary, secondary, minimum].join(',')
}

const numbering = (tag) => new Intl.NumberFormat(tag).resolvedOptions().numberingSystem
const calendar = (tag) => new Intl.DateTimeFormat(tag).resolvedOptions().calendar

function likelyRegion(tag) {
  const { language, region } = new Intl.Locale(tag).maximize()
  return region ? `${language}-${region}` : ''
}

function candidates() {
  const languages = languageCodes()
  const regions = regionCodes()
  const scripted = []
  const regional = []
  const full = []
  for (const language of languages) {
    const scripts = new Set([new Intl.Locale(language).maximize().script])
    for (const region of regions) scripts.add(new Intl.Locale(`${language}-${region}`).maximize().script)
    scripts.delete(undefined)
    for (const script of scripts) scripted.push([`${language}-${script}`, [language]])
    for (const region of regions) regional.push([`${language}-${region}`, [language]])
    for (const script of scripts) {
      for (const region of regions) {
        full.push([`${language}-${script}-${region}`, [`${language}-${script}`, `${language}-${region}`, language]])
      }
    }
  }
  return [...languages.map((language) => [language, []]), ...scripted, ...regional, ...full]
}

function table(tags, value, fallback) {
  const stored = new Map()
  const fallbackValue = value(fallback)
  for (const [tag, parents] of tags) {
    if (canonical(tag) !== tag || !supported(tag)) continue
    const inherited = parents.map((parent) => stored.get(parent)).find((it) => it !== undefined) ?? fallbackValue
    const own = value(tag)
    if (own !== inherited) stored.set(tag, own)
  }
  const tagsByValue = new Map()
  for (const [tag, own] of stored) tagsByValue.set(own, [...(tagsByValue.get(own) ?? []), tag])
  return [...tagsByValue].map(([own, owners]) => owners.join(' ') + fieldSeparator + own).join(entrySeparator)
}

function literal(text) {
  let escaped = ''
  for (const char of text) {
    const code = char.codePointAt(0)
    if (char === '"' || char === '\\' || char === '$') escaped += `\\${char}`
    else if (char === '\n') escaped += '\\n'
    else if (code >= 0x20 && code < 0x7f) escaped += char
    else {
      for (let index = 0; index < char.length; index++) {
        escaped += `\\u${char.charCodeAt(index).toString(16).padStart(4, '0')}`
      }
    }
  }
  return escaped
}

function constant(name, value) {
  const pieces = []
  let piece = ''
  for (const char of value) {
    const escaped = literal(char)
    if (piece.length + escaped.length > chunk) {
      pieces.push(piece)
      piece = ''
    }
    piece += escaped
    if (char === entrySeparator) {
      pieces.push(piece)
      piece = ''
    }
  }
  if (piece !== '') pieces.push(piece)
  const lines = pieces.map((text, index) => (index === 0 ? `    "${text}"` : `        "${text}"`))
  return `internal const val ${name}: String =\n${lines.join(' +\n')}\n`
}

function fixture() {
  const locales = [
    'en', 'en-GB', 'en-IN', 'ru', 'de', 'de-CH', 'es', 'es-MX', 'pl', 'pt-PT', 'pt-BR', 'fr', 'fr-CA', 'it', 'nl',
    'sv', 'fi', 'tr', 'uk', 'cs', 'el', 'hu', 'ro', 'kk', 'hi-IN', 'bn', 'mr', 'ne', 'ar', 'ar-SA', 'ar-EG',
    'ar-u-nu-arab', 'fa-IR', 'he', 'th-TH', 'ja-JP', 'ja-JP-u-ca-japanese', 'zh', 'zh-TW', 'zh-Hant-HK', 'ko',
    'vi', 'id', 'sr', 'sr-Latn', 'my', 'km', 'sw', 'am', 'ur', 'ta',
  ]
  const lists = [['Red', 'Green'], ['Red', 'Green', 'Blue'], ['Red', 'Green', 'Blue', 'Black'], ['agua', 'isla'],
    ['agua', 'hielo'], ['agua', 'hijo'], ['אבא', 'אמא'], ['אבא', 'Bob']]
  const numbers = [0, 7, 1234, 12345, 1234567.5, -1234.25, 0.1, -0.000123, 1 / 3, 1e20]
  const stamps = [['date', '2024-06-14'], ['date', '0999-12-31'], ['time', '13:05'], ['time', '00:00'],
    ['datetime', '2024-06-14T13:05']]
  const cases = []
  for (const locale of locales) {
    const list = new Intl.ListFormat(locale, { type: 'conjunction' })
    for (const items of lists) cases.push({ locale, type: 'multiselect', value: items, expected: list.format(items) })
    const number = new Intl.NumberFormat(locale, { maximumSignificantDigits: 21 })
    for (const value of numbers) cases.push({ locale, type: 'number', value, expected: number.format(value + 0) })
    for (const [type, value] of stamps) {
      const options = { dateStyle: type === 'time' ? undefined : 'medium', timeStyle: type === 'date' ? undefined : 'short', timeZone: 'UTC' }
      const parts = value.match(/\d+/gu).map(Number)
      const [year, month, day, hours = 0, minutes = 0] = type === 'time' ? [1970, 1, 1, ...parts] : parts
      const date = new Date(0)
      date.setUTCFullYear(year, month - 1, day)
      date.setUTCHours(hours, minutes)
      cases.push({ locale, type, value, expected: new Intl.DateTimeFormat(locale, options).format(date) })
    }
  }
  return cases
}

function pluralFixture() {
  const locales = ['en', 'de', 'es', 'fr', 'fr-CA', 'it', 'pt', 'pt-BR', 'ja', 'zh-CN', 'ru', 'uk', 'not a tag']
  const values = [
    ...Array.from({ length: 32 }, (_, index) => index), 100, 101, 102, 104, 105, 111, 112, 114, 121, 122, 125, 1000, 1001,
    999999, 1000000, 1000001, 2000000, 21000000, 1e15, 0.5, 1.5, 2.25, 11.5, 1.0001, 1.0005, 0.0004, 1000000.5, -1, -2, -5,
  ]
  const tag = (locale) => {
    try {
      return supported(locale) ? locale : 'en'
    } catch {
      return 'en'
    }
  }
  return locales.flatMap((locale) => {
    const rules = new Intl.PluralRules(tag(locale))
    return values.map((value) => ({ locale, value, expected: rules.select(value) }))
  })
}

function fixtureJson(cases) {
  const lines = cases.map((item) => `  ${JSON.stringify(item)}`)
  return `{"cldr":"${version}","icu":"${process.versions.icu}","cases":[\n${lines.join(',\n')}\n]}\n`
}

const tags = candidates()
const tables = {
  LIST_PATTERNS: separators,
  NUMBER_GROUPINGS: grouping,
  NUMBERING_SYSTEMS: numbering,
  CALENDARS: calendar,
}
const fallback = 'en'
const encoded = Object.entries(tables).map(([name, value]) => [name, value(fallback), table(tags, value, fallback)])
const unregioned = tags.filter(([tag]) => !new Intl.Locale(tag).region)
encoded.push(['LIKELY_REGIONS', likelyRegion('xx'), table(unregioned, likelyRegion, 'xx')])

for (const file of readdirSync(directory)) {
  if (/^Cldr\d+Tables\.kt$/u.test(file)) rmSync(join(directory, file))
}
const header = `package dev.formhaus.core.cldr\n\ninternal const val CLDR_VERSION: String = "${version}"\n`
const body = encoded.map(
  ([name, own, value]) => `internal const val FALLBACK_${name}: String = "${literal(own)}"\n\n${constant(name, value)}`,
)
writeFileSync(output, [header, ...body].join('\n'))
mkdirSync(fixtureDirectory, { recursive: true })
writeFileSync(join(fixtureDirectory, 'recall-format.json'), fixtureJson(fixture()))
writeFileSync(join(fixtureDirectory, 'plural-rules.json'), fixtureJson(pluralFixture()))
const sizes = encoded.map(([name, , value]) => `${name} ${Buffer.byteLength(value)} bytes`)
console.log(`wrote ${output} for CLDR ${version}: ${sizes.join(', ')}`)
