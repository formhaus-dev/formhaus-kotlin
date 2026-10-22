import { CONTROLS, MIXES, PALETTE, RADII, SHADOW, SPACING, STEPS, TYPOGRAPHY } from './mapping.mjs';

const meta = (token) => token.original.$extensions?.['dev.formhaus'] ?? {};

const number = (value) => String(value);

const float = (value) => `${number(value)}f`;

export function color(value) {
  if (value?.colorSpace !== 'srgb' || !/^#[0-9a-f]{6}$/i.test(value.hex)) throw new Error(`unsupported color ${JSON.stringify(value)}`);
  const opaque = `Color(0xFF${value.hex.slice(1).toUpperCase()})`;
  return value.alpha === undefined || value.alpha === 1 ? opaque : `${opaque}.copy(alpha = ${float(value.alpha)})`;
}

export function dimension(value) {
  if (value?.unit !== 'px' || typeof value.value !== 'number') throw new Error(`unsupported dimension ${JSON.stringify(value)}`);
  return number(value.value);
}

function lookup(context, name) {
  const token = context.get(name);
  if (!token) throw new Error(`missing token ${name}`);
  return token;
}

const literal = (unit) => (token) => (token.$type === 'color' ? color(token.$value) : `${dimension(token.$value)}.${unit}`);
const values = (context, fields, format) => fields.map(([field, name]) => [field, format(lookup(context, name))]);
const constant = (field) => field.replace(/[A-Z]/g, (letter) => `_${letter}`).toUpperCase();
const valName = (context, type) => `${context}${type}`;

const call = (callee, fields) =>
  fields.length === 0 ? callee : [`${callee}(`, ...fields.map(([field, value]) => `        ${field} = ${value},`), '    )'].join('\n');

const declaration = (name, type, body) => `internal val ${name}: ${type} =\n    ${body}`;

function family(type, contexts, fields, [first, ...rest], unit) {
  const format = literal(unit);
  const base = values(contexts[first], fields, format);
  return [
    declaration(valName(first, type), type, call(type, base)),
    ...rest.map((name) => {
      const changed = values(contexts[name], fields, format).filter(([field, value]) => base.find(([other]) => other === field)[1] !== value);
      return declaration(valName(name, type), type, changed.length ? call(`${valName(first, type)}.copy`, changed) : valName(first, type));
    }),
  ];
}

function mixes(name, context) {
  const fields = MIXES.flatMap(([field, token, from, onto]) => {
    const { mix } = meta(lookup(context, token));
    if (!mix || mix.color !== from || (onto && mix.with !== onto)) throw new Error(`${token} is not a supported mix`);
    if (onto) return [[field, float(mix.amount)]];
    const partner = context.paths.get(mix.with.slice(1, -1));
    if (!partner) throw new Error(`missing token ${mix.with}`);
    return [
      [field, float(mix.amount)],
      [`${field}With`, color(partner.$value)],
    ];
  });
  return declaration(valName(name, 'ColorMixes'), 'ColorMixes', call('ColorMixes', fields));
}

function steps(contexts) {
  return STEPS.map(([field, name]) => {
    const amounts = ['fine', 'coarse'].map((key) => {
      const { add } = meta(lookup(contexts[key], name));
      if (add?.base !== '{font-size.default}') throw new Error(`${name} is not a supported add`);
      return dimension(add.amount);
    });
    if (amounts[0] !== amounts[1]) throw new Error(`${name} adds a different amount per pointer`);
    return `internal const val ${constant(field)}: Float = ${float(amounts[0])}`;
  });
}

function shadow(context) {
  const token = lookup(context, SHADOW);
  const { offsetX, offsetY, blur, spread } = token.$value;
  if (token.original.$value.color !== '{color.shadow}' || dimension(spread) !== '0') throw new Error(`${SHADOW} is not supported`);
  const fields = [
    ['x', offsetX],
    ['y', offsetY],
    ['blur', blur],
  ];
  return ['internal object RaisedShadow {', ...fields.map(([field, value]) => `    val ${field}: Dp = ${dimension(value)}.dp`), '}'].join('\n');
}

export function kotlinSource(contexts) {
  return (
    [
      'package dev.formhaus.compose.theme',
      [
        'import androidx.compose.ui.graphics.Color',
        'import androidx.compose.ui.unit.Dp',
        'import androidx.compose.ui.unit.dp',
        'import androidx.compose.ui.unit.sp',
      ].join('\n'),
      ...family('Palette', contexts, PALETTE, ['light', 'lightIncreased']),
      ...family('Palette', contexts, PALETTE, ['dark', 'darkIncreased']),
      mixes('light', contexts.light),
      mixes('dark', contexts.dark),
      ...steps(contexts),
      ...family('Typography', contexts, TYPOGRAPHY, ['fine', 'coarse'], 'sp'),
      ...family('Spacing', contexts, SPACING, ['regular', 'compact'], 'dp'),
      ...family('Radii', contexts, RADII, ['standard'], 'dp'),
      ...family('Controls', contexts, CONTROLS, ['fine', 'coarse'], 'dp'),
      shadow(contexts.light),
    ].join('\n\n') + '\n'
  );
}
