# Changelog

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
