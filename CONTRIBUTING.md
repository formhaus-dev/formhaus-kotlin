# Contributing

## Setup

```sh
git clone https://github.com/formhaus-dev/formhaus-kotlin.git
cd formhaus-kotlin
./gradlew build
```

Requires JDK 17 and the Android SDK with platform 37 and build-tools 36. Set `sdk.dir` in `local.properties` or `ANDROID_HOME`. The duplicate check and the scripts that record data need Node 22.

## Modules

- `formhaus-core`: the engine, pure Kotlin for the JVM. Shared test helpers live in its `testFixtures` source set.
- `formhaus-research`: option order for `randomize` fields, pure Kotlin. Its tests read the conformance fixtures of `formhaus-core`.
- `formhaus-compose`: the Compose renderer, an Android library. UI tests run on the JVM with Robolectric, and screenshots with Roborazzi.
- `demo`: the sample app. It is not published.
- `benchmarks`: JMH benchmarks of the engine on the JVM, with kotlinx-benchmark. It is not published.
- `benchmarks-compose`: render benchmarks and the recomposition check of the Compose renderer, on Robolectric. It is not published.
- `build-logic`: the convention plugins every module applies: Kotlin, Android, ktlint and detekt, publishing, the public API check and coverage.

## Build and test

```sh
./gradlew check
./scripts/lint.sh
```

`./gradlew check` compiles with warnings as errors, runs the tests, ktlint, detekt, Android lint, the screenshot comparison, the Android API 26 signature check of the JVM modules and the public API check of the three libraries. `./gradlew :demo:assembleDebug` builds the demo app, and `./gradlew publishToMavenLocal` installs the three libraries in `~/.m2`. `./gradlew coverage` runs the tests with Kover, writes the merged HTML report of the three libraries to `build/reports/kover/htmlCoverage` and prints the line coverage. `./scripts/lint.sh` runs ktlint, detekt, the comment check and jscpd at the versions CI uses. `./scripts/lint.sh ktlint`, `detekt`, `comments`, `duplicates` or `tokens` runs one check.

## Screenshots

`./gradlew check` compares the screenshots in `formhaus-compose/src/test/screenshots` with the renderer. After an intended visual change, record them again and look at every changed image before you commit:

```sh
./gradlew :formhaus-compose:recordRoborazziDebug
```

The README images in `.github/assets` show the demo app in a device frame. `check` does not compare them, so font changes on a machine do not fail the build. Record them again after a visual change to the demo or the sets:

```sh
./gradlew :demo:recordReadmeImages
```

## Public API

The public API of `formhaus-core`, `formhaus-research` and `formhaus-compose` is recorded in each module's `api/` directory. `./gradlew check` fails when the compiled API differs from the dump. After an intended API change, update the dumps and commit them with the change:

```sh
./gradlew updateKotlinAbi
```

## Conformance

Behaviour is defined by the conformance suite of the [TypeScript core](https://github.com/formhaus-dev/formhaus/tree/main/packages/core/fixtures/conformance). A behaviour change starts as a fixture in the formhaus repository. When the Kotlin engine disagrees with a fixture, fix the engine.

The copy in `formhaus-core/src/test/resources/fixtures/conformance` is synced, never edited:

```sh
./scripts/sync-fixtures.sh <git-ref> [path-to-formhaus-repo]
```

`fixtures/VERSION` records the ref and the resolved sha. The same script copies the locale catalogs of `@formhaus/core` to `formhaus-core/src/main/resources/dev/formhaus/core/locales`, with their own `VERSION`. The definition corpus runs `checkDefinition`, overlays, text slots, value cases and recall cases. Every scenario in `fixtures/conformance/scenarios` runs through `FormEngine` as its own test.

## Generated data

The Unicode and CLDR tables in `formhaus-core/src/main/kotlin/dev/formhaus/core` come from the data of the Node release that runs the scripts. The committed tables are Unicode 17 and CLDR 48, which Node 24 ships. Run the scripts with Node 24 and commit the result:

```sh
node scripts/unicode-tables.mjs
node scripts/cldr-tables.mjs
```

`unicode-tables.mjs` writes the `unicode/Unicode<N>Tables.kt` file. `cldr-tables.mjs` writes `cldr/Cldr<N>Tables.kt` and the recall format and plural rule fixtures in `src/test/resources/cldr`.

`formhaus-core/src/test/resources/fixtures/regex/cases.json` holds the expected results of the regex engine, recorded with Node. To record them again:

```sh
node scripts/regex-cases.mjs
```

Node 24.15 folds case wrongly in some modifier groups, where the native engine follows the specification. The script leaves these cases out and checks every recorded case again in a new node process.

## Benchmarks and heavy tests

CI runs fast checks that count work and never measures time. `benchmarks-compose` counts the recompositions of a keystroke in a 20-field form in `./gradlew check`: typing into one field must not recompose the others. The large cases run only when `FORMHAUS_HEAVY_TESTS=1` is set:

```sh
FORMHAUS_HEAVY_TESTS=1 ./gradlew :formhaus-core:test
```

`scripts/reveal-fuzz.sh` compares `revealTargets` with the TypeScript one on 50,000 generated definitions. It takes the built entry of `@formhaus/core` at the pinned tag, by default `../formhaus/packages/core/dist/index.js`, and an optional count:

```sh
scripts/reveal-fuzz.sh ../formhaus/packages/core/dist/index.js 50000
```

`scripts/benchmarks.sh` runs the timing benchmarks and prints the tables:

```sh
scripts/benchmarks.sh
scripts/benchmarks.sh jvm
scripts/benchmarks.sh v8
scripts/benchmarks.sh render
scripts/benchmarks.sh --smoke
```

- `jvm` runs the JMH benchmarks of the regex engine, answer recall and `FormEngine`. Each regex row is one `validateFieldValue` call with a `pattern` rule, next to `java.util.regex` on the same patterns and inputs. The cases match the Swift package and live in `benchmarks/src/main/resources/regex-cases.json`. `java.util.regex` runs the pathological patterns outside JMH, with each call capped at 5 seconds.
- `v8` runs `benchmarks/v8/regex.mjs` with Node and no dependencies. It measures `new RegExp(p).test(s)` on the same cases, and runs each pathological pattern in a worker it stops after 5 seconds.
- `render` runs the Compose render benchmarks on Robolectric with native graphics in a 360 by 900 dp window: the first render of 3,000 picture options and of a 4-step form, a selection and a keystroke, each through layout and draw. `FORMHAUS_RENDER_WARMUPS` and `FORMHAUS_RENDER_RUNS` set the counts.
- `--smoke` runs every part with minimal iterations to check that it works. Its numbers mean nothing.

Run the heavy tests and the benchmarks before a release and after changes to the regex engine, the engine core or the renderer. Run them on a quiet machine; the scripts already use `nice -n 15`.

## Pull requests

1. Fork the repo and create a branch from `main`.
2. Keep the code free of comments.
3. Make sure `./gradlew check` and `./scripts/lint.sh` pass. Run the heavy tests when the change needs them.
4. Commit using [conventional commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `chore:`).
5. Open a pull request against `main`. The `test` and `lint` checks must pass.

## Releases

1. Sync the fixtures to a tagged `@formhaus/core` release with `./scripts/sync-fixtures.sh @formhaus/core@X.Y.Z`.
2. Run the heavy tests.
3. Set the version in `build-logic/src/main/kotlin/formhaus.publishing.gradle.kts`, add the release to `CHANGELOG.md` and update the install snippets in `README.md`.
4. Tag `main` with `v` and the version, such as `v0.3.0`, and push the tag. The release workflow publishes the three libraries to Maven Central, and JitPack builds them from `jitpack.yml`.
