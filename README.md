<p align="center">
  <img src=".github/assets/logo.svg" alt="Formhaus" width="360">
</p>

<p align="center">
  <a href="https://github.com/formhaus-dev/formhaus-kotlin/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/formhaus-dev/formhaus-kotlin/ci.yml?branch=main&label=tests" alt="Tests"></a>
  <img src="https://img.shields.io/badge/Kotlin-2.4-blue" alt="Kotlin 2.4">
  <img src="https://img.shields.io/badge/JDK-17%2B-blue" alt="JDK 17+">
  <img src="https://img.shields.io/badge/Android-minSdk%2026-blue" alt="Android minSdk 26">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/formhaus-dev/formhaus-kotlin" alt="MIT license"></a>
</p>

<p align="center">
  Kotlin engine for <a href="https://github.com/formhaus-dev/formhaus">Formhaus</a> JSON form definitions.
</p>

<p align="center">
  <a href="https://formhaus.dev/guide/">Documentation</a> ·
  <a href="https://formhaus.dev/playground">Playground</a> ·
  <a href="https://github.com/formhaus-dev/formhaus">TypeScript core</a>
</p>

## What it is

`formhaus-core` is a port of `@formhaus/core` to pure Kotlin. It parses a definition, runs the form as a state machine and validates submitted values with the same rules as the TypeScript core. It has no UI, so you can drive any screen from it, or use it on a server.

- `FormEngine` holds values, errors, the current step and the visible fields, and moves between steps.
- `validateSubmission` checks a submitted body against the definition on a server.
- `checkDefinition` lists the issues of a definition.
- `localize` and `missingKeys` support translations.

The only dependency is `kotlinx-coroutines-core`. There is no JavaScript runtime.

## Requirements

- JDK 17 or later, or Android with `minSdk` 26 or later.

The library is built with Kotlin 2.4. It calls only APIs that exist on Android API 26, and `./gradlew check` verifies this.

## Install

Releases are served from JitPack. Add the repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("com.github.formhaus-dev:formhaus-kotlin:v0.1.0")
}
```

To work from a local clone instead, include it with `includeBuild("../formhaus-kotlin")` and depend on `dev.formhaus:formhaus-core`.

## Quick start

Parse a definition, create a `FormEngine` and set values. `next`, `back`, `skip` and `submit` are `suspend` functions, because hooks and the submit handler can wait on the network:

```kotlin
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.toJsonString

val definition = JsonValue.parse(
    """
    {
      "id": "signup",
      "submit": { "label": "Sign up" },
      "steps": [
        { "id": "account", "fields": [
          { "key": "email", "type": "email", "label": "Email", "validation": { "required": true } }
        ] },
        { "id": "profile", "fields": [
          { "key": "name", "type": "text", "label": "Name" }
        ] }
      ]
    }
    """,
)

val engine = FormEngine(definition)
engine.setValue("email", JsonValue.String("ada@example.com"))

suspend fun fill() {
    if (engine.next()) {
        engine.setValue("name", JsonValue.String("Ada"))
        engine.submit { values, _ ->
            send(values.toJsonString())
            SubmitResult.Ok
        }
    }
}
```

`next` returns `false` when validation fails, and the errors are in `engine.errors`. The submit handler receives the values of the active path and the ids of skipped steps. It returns `SubmitResult.Ok`, or `SubmitResult.Rejected(errors, formErrors)` to show server errors on the fields. `SubmitResult.of(json)` reads the `{ ok, errors, formErrors }` object that a Formhaus endpoint returns.

Observe the engine with `engine.subscribe { ... }`, which returns a function that removes the listener. Read `engine.currentStep`, `engine.visibleFields`, `engine.errors`, `engine.topLevelErrors`, `engine.progress` and `engine.canGoNext` after each change. The [documentation](https://formhaus.dev/guide/) covers the definition format.

## Validate on the server

`validateSubmission` checks the values against the same definition, with the visibility, steps and routes of the client:

```kotlin
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.server.SubmissionOptions
import dev.formhaus.core.server.validateSubmission

val result = validateSubmission(
    definition,
    JsonValue.parse(requestBody),
    SubmissionOptions(skippedSteps = listOf("profile")),
)
if (result.errors.isEmpty()) {
    save(result.values)
} else {
    reject(result.errors)
}
```

The body is the values object. `result.values` holds the values of the active path, and `result.errors` maps keys to messages. `SubmissionOptions` also takes custom `validators`, `messages` and `hiddenValues`. `checkDefinition(definition)` lists the issues of a definition before you use it:

```kotlin
import dev.formhaus.core.definitionvalidation.checkDefinition

val problems = checkDefinition(definition).filter { it.isError }
```

## Translations

`localize` applies an overlay of translated texts and sets the language. `missingKeys` lists the built-in message and label keys that a catalog lacks, for a unit test of your translations:

```kotlin
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.i18n.localize
import dev.formhaus.core.i18n.missingKeys
import dev.formhaus.core.validation.MessageSource

val overlay = JsonValue.parse("""{ "fields": { "email": { "label": "E-Mail" } }, "submit": { "label": "Anmelden" } }""")
val localized = localize(definition, overlay, "de")

val engine = FormEngine(
    localized.definition,
    options = FormEngineOptions(messages = MessageSource.Catalog(localized.messages)),
)
val missing = missingKeys(messages = MessageSource.Catalog(localized.messages))
println(missing.messages)
```

`Localized` holds the translated `definition` and the `messages` and `labels` catalogs. With `debugChecks` on and a `messages` catalog in `FormEngineOptions`, the engine reports `missing-message-key` through `onWarning` when a shown message is in neither the catalog nor the definition.

## FormEngineOptions

| Option | Effect |
|---|---|
| `seed` | Fixes the random option order of `randomize` fields. The same seed gives the same order. |
| `research` | A `FormResearch` that creates seeds and orders options. `dev.formhaus.research.Research()` is the built-in one. Without it, options show in written order. |
| `hiddenValues` | `HiddenValues.CLEAR` (default) drops the value of a field that a condition hides. `HiddenValues.KEEP` keeps it in `engine.values` and in submitted values. |
| `debugChecks` | Makes calls from a thread other than the creating one throw, and reports missing message keys. |
| `recallFormatter` | A `RecallFormatter` that formats recalled answers: lists, numbers, dates and times. The default follows CLDR 48. |

The options class also takes `onBeforeStepChange`, `onAfterStepChange`, `onBeforeSubmit`, `onAfterSubmit`, `onStepValidate`, `onFormEvent`, `onWarning`, `validators`, `messages`, `lang` and `formatRecall`.

```kotlin
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.HiddenValues
import dev.formhaus.research.Research

val engine = FormEngine(
    definition,
    options = FormEngineOptions(
        research = Research(),
        seed = "respondent-42",
        hiddenValues = HiddenValues.KEEP,
        debugChecks = true,
    ),
)
val order = engine.orderOptions("plan").map { it.value }
```

With `HiddenValues.KEEP`, conditions, validation and recall still treat hidden fields as unanswered, so they never block `next` or `submit`. Pass `SubmissionOptions(hiddenValues = HiddenValues.KEEP)` to `validateSubmission` as well, or the server drops them.

## Threading

`FormEngine` is single-threaded. Create it and call it from one thread, such as the main thread, and do not share it. With `debugChecks = true`, a call from another thread throws `IllegalStateException` with both thread names. `validateSubmission`, `checkDefinition`, `localize` and the JSON parser can run on any thread, also at the same time.

## Conformance

Behaviour follows the [conformance suite](https://github.com/formhaus-dev/formhaus/tree/main/packages/core/fixtures/conformance) of `@formhaus/core`. The pinned core ref is recorded in `formhaus-core/src/test/resources/fixtures/VERSION`. Definition files, value cases, recall cases, option order vectors and every engine scenario run as tests.

To move to another release:

```sh
./scripts/sync-fixtures.sh @formhaus/core@X.Y.Z
```

The fixtures are copied, never edited here. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Unicode and CLDR data

Case folding, Unicode properties and the formats for recalled lists, numbers and dates come from embedded tables of Unicode 17 and CLDR 48, not from the JDK or Android. Results are the same on every JVM, and on every Android version from API 26. The tables are generated by `scripts/unicode-tables.mjs` and `scripts/cldr-tables.mjs`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Behaviour changes start in the [Formhaus](https://github.com/formhaus-dev/formhaus) repository.

## Security

Report vulnerabilities privately. See [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE)
