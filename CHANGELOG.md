# Changelog

## Unreleased

### formhaus-core

- **Breaking:** `FormEngineOptions.debugChecks` is a `Boolean?`. `null`, the default, keeps the checks off.
- New renderer API `Locale.withPreferredCalendar()` adds the calendar CLDR prefers for the locale, such as Buddhist for `th-TH` and Persian for `fa-IR`.

## 0.1.0

### formhaus-core

- Initial Kotlin port of `@formhaus/core` for the JVM and Android API 26 and later. It passes the conformance suite of `@formhaus/core` 1.2.1 and the group item fix after it.
- `FormEngine` runs a form without a UI: values, steps and routes, conditions, validation, answer recall, repeating groups, `next`, `back`, `skip` and `submit`.
- `FormEngineOptions` takes lifecycle hooks, custom validators, `messages`, `lang`, `seed`, `research`, `hiddenValues`, `debugChecks`, `formatRecall` and `recallFormatter`.
- `validateSubmission` checks submitted values against a definition on a server, with the visibility, steps and routes of the client.
- `checkDefinition` lists the errors and warnings of a definition.
- `localize` applies a translation overlay to a definition, and `missingKeys` lists the built-in message and label keys a catalog lacks.
- `dev.formhaus.research.Research` orders the options of `randomize` fields per seed.
- Patterns run on a built-in ECMAScript regex engine with `new RegExp(p).test(s)` semantics. Patterns without backreferences match in time linear in the input.
- Unicode 17 and CLDR 48 data is embedded, so results do not depend on the platform's character data or locale data.
- With `debugChecks`, a call from a thread other than the one that created the engine throws.
