# Changelog

## Unreleased

### formhaus-core

- New `validateWithRules` option on `FormEngineOptions` runs `onValidate` even when the definition rules fail; the definition error wins on the same field.
- **Breaking:** The `FormEngineOptions` constructor has a new parameter. Code compiled against 0.7.0 that constructs `FormEngineOptions` must be recompiled. Its source does not change.
- Fixed field errors from `onValidate` and `onStepValidate` staying on a step after its next Continue passed, and showing again after Back.
- Supports form definition format 1.7. `SUPPORTED_FORMAT_VERSION` is `1.7`.
- A `file` field with `"multiple": true` in a definition with `formatVersion` 1.7 or newer keeps a list of files. Below 1.7, `multiple` is reported as `unknown-property` and `outdated-format-version`, and the field keeps one file.
- New `FormField.isFileList`, `fileItems(value)` and `fileListLimit(field)`. A file list holds at most `maxLength` files, rounded down, from 0 to 1000, or 100 without `maxLength`.
- New issue code `IssueCode.MULTIPLE_NOT_APPLICABLE` for `multiple` on a field that is not a `file` field. `multiple` that is not a boolean is `invalid-type`.
- `validateSubmission` accepts a file list as an array of string references. A single string counts as a list of one. `""` and `null` entries are dropped. Any other entry fails the field with the `file` message.
- New renderer API `FormEngine.maxItemsMessage(field)`.
- New label `fileCount`, `Files: {count}`, in every bundled catalog.
- New renderer API `FormEngine.storedFileName`. Recall shows the name it returns for each string value of a `file` field, instead of the stored value.

### formhaus-compose

- A file list shows the chosen files below the picker, each with a Remove button. Picking adds files up to the list's limit and announces the files that were added.
- New `FormModel.pickedFiles(key)`.
- After a restore, a file list drops uploads whose file is gone.
- The `validateWithRules` engine option reaches `FormhausForm` and `FormModel`.
- Fixed `{{key}}` recall of a picked file showing its `file://` path instead of its name. `FormModel` keeps a `storedFileName` callback set by the app and asks it first.

## 0.7.0

### formhaus-core

- Passes the conformance suite of `@formhaus/core` 1.6.1.
- `validateSubmission` no longer throws `FormDefinitionError` for patterns that `checkDefinition` reports as `slow-pattern`. It runs every pattern on values of up to 10,000 characters.
- Patterns with one overlapping repeat now also run on values over 1,000 characters.
- `checkDefinition` no longer reports `slow-pattern` for a repeated group that starts or ends with a separator nothing else in the group matches, such as `[A-Za-z]+([ '-][A-Za-z]+)*` or `\w+(\s+\w+)*`.
- `checkDefinition` no longer reports `slow-pattern` for a group without unbounded repeats that repeats a fixed number of times or at most 10 times, such as `#(?:[0-9a-f]{3}){1,2}`.
- The `slow-pattern` warning also covers overlapping repeats of non-ASCII characters, such as `[À-ÿ]+[à-ÿ]+`.

### formhaus-compose

- New `conditionalReveal` option on `FormBehavior` draws a field revealed by a `radio` or `multiselect` option under that option in the built-in sets, indented with a line on its start side.
- With `conditionalReveal`, a choice field with revealed fields shows its error above its options.
- **Breaking:** The `FormBehavior` constructor has a new parameter. Code compiled against 0.6.0 that constructs `FormBehavior` must be recompiled. Its source does not change.

## 0.6.0

### formhaus-core

- Passes the conformance suite of `@formhaus/core` 1.6.0.
- New `revealTargets(nodes)` returns, for each node of a list, the choice field and option that reveal it, following the Revealed fields rule of the specification.
- New type `RevealTarget`.

### formhaus-compose

- `FieldContext` has `minLength` and `maxLength` of text fields, for character counters.
- Fixed focus landing on the first option of a radio group when another option is checked.

## 0.5.0

### formhaus-core

- Passes the conformance suite of `@formhaus/core` 1.4.2.
- **Breaking:** `validateSubmission` throws `FormDefinitionError` for a definition with a `slow-pattern` pattern and reports each one as a `slow-pattern` error. A pattern with one overlapping repeat after `^`, such as `^\d+\.?\d*$`, still runs, on values of up to 1,000 characters. A longer value gets the pattern error without matching.
- The `slow-pattern` warning also covers backreferences, named backreferences and patterns longer than 1,000 characters.
- Fixed `slow-pattern` warnings for repeated groups split by a separator, such as `^([\w-]+\.)+[a-z]{2,}$`.

## 0.4.0

### formhaus-core

- New `locale` option on `FormEngineOptions` takes a list of `LocaleCatalog`. Per key, `messages` wins over `definition.messages`, which wins over the catalogs in list order, which win over the built-in English. Labels follow the same order.
- A catalog text can be `PluralForms`, picked by the CLDR plural rules of the catalog locale on `{count}`, `{min}` or `{max}`. That number is formatted for the same locale.
- Plural rules cover `en`, `de`, `es`, `fr`, `it`, `pt`, `pt-PT`, `ja`, `zh`, `ru` and `uk`. Other languages use the `other` form.
- New `FormEngine.locale` replaces the catalogs of a running engine. Labels and the next validation use the new catalogs.
- Message and label catalogs for `es`, `fr`, `fr-CA`, `de`, `pt-BR`, `it`, `ja`, `zh-CN`, `ru` and `uk`, plus `en`, through `LocaleCatalog.bundled(tag)`. `LocaleCatalog.bundledTags` lists them.
- New `LocaleCatalog.fromJson(json)` reads a catalog in the format of `@formhaus/core/locales/<tag>.json`.
- New `localeChain(tag)` and `checkCatalog(json)`. `localeChain` adds the likely region, so `zh-Hans-CN` also tries `zh-CN`. It drops `-u-` and `-x-` extensions and replaces deprecated language codes, so `iw` tries `he`.
- New types `LocaleCatalog`, `LocaleText`, `PluralForms` and `CatalogIssues`.
- A multi-step overlay also accepts `fields.<key>` at the top level. `steps.<id>.fields.<key>` wins when both are set.
- New `onValidate` engine option runs after the definition rules pass, on Continue after `onStepValidate` and on Submit before `onBeforeSubmit`. It gets the values and a `ValidateContext` with `stepId`, `fields` and `submit`, where `fields` lists the visible field paths in scope, and returns errors keyed by field path. Keys outside `fields` show as top-level errors.
- An `onValidate` error on another step opens that step on Submit.
- New types `ValidateFn` and `ValidateContext`.
- Top-level errors from `onStepValidate` and `onValidate` clear on the next Continue, Back, Skip or Submit. Top-level errors from the submit handler and from `setErrors` stay.
- New `FormEngine.errorSummary(ErrorSummaryOptions(idPrefix, labels))` returns the field errors grouped by step, the top-level errors and the field error count.
- New types `ErrorSummary` and `ErrorSummaryOptions`.
- **Breaking:** `FormEngine.errorGroups(labels)` is removed. Use `errorSummary(ErrorSummaryOptions(labels = labels)).groups`.
- **Breaking:** `ErrorEntry.id` is the element id of the web renderers, such as `fh-email` or `fh-guests--1--name`, instead of the field key.

### formhaus-compose

- The `locale` and `onValidate` engine options reach `FormhausForm` and `FormModel`, and the built-in labels and error summary use the locale catalogs.
- A new `locale` in `engineOptions` applies to a retained form, such as after a system language change.

## 0.3.0

### formhaus-core

- New `matrix-options-mismatch` warning for a row of a `layout: "matrix"` section whose option values differ from those of the first field. Rows that use `optionsFrom` are not checked.
- Conforms to `@formhaus/core` 1.3.0.

## 0.2.1

### Packaging

- Published to Maven Central as `dev.formhaus:formhaus-core`, `dev.formhaus:formhaus-compose` and `dev.formhaus:formhaus-research`.

## 0.2.0

### formhaus-core

- **Breaking:** `dev.formhaus.research.Research` moved to the new `formhaus-research` package. `FormEngineOptions.research` and the `FormResearch` interface stay in core.
- **Breaking:** `FormEngineOptions.debugChecks` is a `Boolean?`. `null`, the default, keeps the checks off.
- **Breaking:** The `FormEvent` and `SubmitFailureReason.Error` cases, `SubmitResult.Rejected`, `StepChangeContext`, `StepProgress`, `GoToStepResult`, `DefinitionIssue`, `SubmissionResult`, `TextSlot`, `MissingKeys`, `TestIdTarget`, `ErrorEntry`, `ErrorGroup`, `FormActionsState`, `RecallDependency` and `RecalledTexts` are no longer data classes and lose `componentN`. `TestIdTarget` keeps `copy`.
- New renderer API `Locale.withPreferredCalendar()` adds the calendar CLDR prefers for the locale, such as Buddhist for `th-TH` and Persian for `fa-IR`.
- New `FormSnapshot` and `FormEngine.snapshot()`. The `restoring` parameter of the `FormEngine` constructors restores the values, the current step, the skipped steps and the seed without validating.
- A snapshot of another snapshot version, with a step the definition no longer has, or that skips a step without a `skip` action starts the form fresh and reports the `snapshot-mismatch` warning. So does a snapshot whose `definitionId` is not the definition's `id`, where a missing `id` must match `null`.
- `FormSnapshot.fromJson(text)` returns `null` for malformed JSON and for fields of the wrong type. `FormSnapshot.fromJson(text, options)` also reports the `snapshot-mismatch` warning.
- A snapshot leaves out the values of `password` fields, also inside group items.
- New `FormEngine.snapshotJson(maxBytes)` returns `null` and reports the `snapshot-too-large` warning for a snapshot over `maxBytes` in UTF-16.
- New renderer APIs `FormEngine.restarts` and `FormEngine.sending`.

### formhaus-compose

- New Jetpack Compose renderer for Android, `minSdk` 26.
- `FormhausForm` renders a definition or a `FormModel`, with `initialValues`, `engineOptions` and `FormRendererOptions`.
- `FormComponents.standard` matches the web renderers, `FormComponents.system` uses Material 3 and the colors of `MaterialTheme`, and `FormComponents.headless` has no built-in fields.
- Custom composables replace any field type, and the section, group, group item, confirm dialog, progress and actions containers.
- `FormhausTheme` holds the Formhaus design tokens for light, dark and raised contrast.
- `TestIds.Automatic` and `TestIds.Custom` add test tags with the scheme of the web `testIds`.
- `FormhausForm(definition)` keeps its values, step and picked files across configuration changes and restores them after process death. A submit that is already sending finishes when the form goes away. Progress over 96 KB in UTF-16 is not saved for process death.
- `restoring` and `onSnapshotChange` on `FormhausForm` and `FormModel` save and restore progress with a `FormSnapshot`. `restoring` replaces `initialValues`.
- `FormhausForm(definition)` keeps its state while it leaves the composition and comes back within the same `ViewModelStoreOwner`, and releases it when the owner is cleared.
- An options provider that throws reports the error to `onError`, or shows it as an action error, and the field falls back to its static options.
- `FormModel.close()` stops a model you created and deletes the files picked in it.
- `FormhausForm(definition)` turns `debugChecks` on in a debuggable app when it is `null`, so an engine call from another thread throws there.
- Options with an `image` need the `android.permission.INTERNET` permission.

### formhaus-research

- New package with `Research`, the option order for `randomize` fields.

### Packaging

- **Breaking:** Each package has its own coordinates: `com.github.formhaus-dev.formhaus-kotlin:formhaus-core`, `formhaus-compose` and `formhaus-research`. `com.github.formhaus-dev:formhaus-kotlin` is no longer published.

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
