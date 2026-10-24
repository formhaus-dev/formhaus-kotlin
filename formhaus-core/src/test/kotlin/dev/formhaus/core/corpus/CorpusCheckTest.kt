package dev.formhaus.core.corpus

import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.server.validateSubmission
import dev.formhaus.core.support.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

class CorpusCheckTest {
    private val audited =
        listOf(
            "check-autocomplete",
            "check-autocomplete-type",
            "check-autocomplete-version",
            "check-clean",
            "check-columns",
            "check-columns-not-applicable",
            "check-conditions",
            "check-container-conditions",
            "check-container-keys",
            "check-container-nesting",
            "check-container-scope",
            "check-container-shape",
            "check-contains-not-list",
            "check-contains-options",
            "check-custom-type-names",
            "check-default-items",
            "check-disabled-not-applicable",
            "check-escaped-property",
            "check-exclusive",
            "check-field-rules",
            "check-group-item-ceiling",
            "check-group-scope",
            "check-image-urls",
            "check-matrix",
            "check-matrix-options",
            "check-missing-required",
            "check-null-lists",
            "check-numeric-id",
            "check-outdated-version-1-0",
            "check-outdated-version-contains",
            "check-outdated-version-exclusive",
            "check-outdated-version-missing",
            "check-outdated-version-newer",
            "check-outdated-version-randomize",
            "check-randomize",
            "check-randomize-type",
            "check-scale",
            "check-scale-range",
            "check-section-forward-reference",
            "check-slow-pattern",
            "contains-disabled",
            "contains-limit",
            "disabled-actions",
            "duplicate-options",
            "exclusive-options",
            "file-app-reference",
            "flat-api-steps",
            "format-version",
            "group-list-answers",
            "group-over-limit",
            "hidden-values",
            "i18n-empty-overlay",
            "i18n-exclusive-branches",
            "i18n-own-properties",
            "i18n-renderer-labels",
            "i18n-signup-steps",
            "impossible-dates",
            "invalid-labels",
            "invalid-messages",
            "localized-catalog",
            "localized-overlay",
            "option-order",
            "options-from",
            "proto-keys",
            "recall-branches",
            "recall-groups",
            "recall-inactive",
            "recall-limit",
            "recall-no-reexpansion",
            "recall-sensitive",
            "recall-steps",
            "recall-syntax",
            "recall-translations",
            "recall-values",
            "recall-version",
            "shared-key-branches",
            "slow-pattern-quadratic",
            "times-and-hidden",
            "trimmed-conditions",
            "trimmed-text",
            "unsupported-format-version",
        )

    private fun entries(
        issues: List<DefinitionIssue>,
        severity: DefinitionIssue.Severity,
    ): List<IssueEntry> = issues.filter { it.severity == severity }.map { IssueEntry(it.code.rawValue, it.path) }.sorted()

    private fun fixtures(
        include: (CorpusFixture) -> Boolean,
        test: (CorpusFixture) -> Unit,
    ): List<DynamicTest> = CorpusFixture.all.filter(include).map { DynamicTest.dynamicTest(it.name) { test(it) } }

    @Test
    fun `covers every audited case`() {
        assertEquals(audited, Fixtures.conformanceNames)
    }

    @TestFactory
    fun checkDefinition(): List<DynamicTest> =
        fixtures({ true }) { fixture ->
            assertEquals(
                fixture.issues.sorted(),
                entries(checkDefinition(fixture.definition), DefinitionIssue.Severity.ERROR),
            )
        }

    @TestFactory
    fun warnings(): List<DynamicTest> =
        fixtures({ it.warnings != null }) { fixture ->
            assertEquals(
                fixture.warnings.orEmpty().sorted(),
                entries(checkDefinition(fixture.definition), DefinitionIssue.Severity.WARNING),
            )
        }

    @TestFactory
    fun `rejected by the engine and the server`(): List<DynamicTest> =
        fixtures({ it.issues.isNotEmpty() }) { fixture ->
            assertThrows<FormDefinitionError> { FormEngine(fixture.definition) }
            assertThrows<FormDefinitionError> {
                validateSubmission(
                    fixture.definition,
                    JsonValue.Object(MutableJsonObject()),
                )
            }
        }

    @Test
    fun `lists the patterns the server refuses wherever slow patterns are reported`() {
        val slow = CorpusFixture.all.filter { fixture -> fixture.warnings.orEmpty().any { it.code == "slow-pattern" } }
        assertEquals(emptyList<String>(), slow.filter { it.refused == null }.map { it.name })
        assertTrue(CorpusFixture.all.any { it.refused != null })
    }

    @TestFactory
    fun `slow patterns refused by the server`(): List<DynamicTest> =
        fixtures({ it.refused != null }) { fixture ->
            val issues =
                try {
                    validateSubmission(fixture.definition, JsonValue.Object(MutableJsonObject()))
                    emptyList()
                } catch (error: FormDefinitionError) {
                    error.issues.map { IssueEntry(it.code.rawValue, it.path) }
                }
            assertEquals(fixture.refused.orEmpty().sorted(), issues.sorted())
        }
}
