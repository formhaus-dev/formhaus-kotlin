package dev.formhaus.core.corpus

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.scenario.json
import dev.formhaus.core.server.SubmissionOptions
import dev.formhaus.core.server.validateSubmission
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class CorpusCaseTest {
    private fun engine(
        definition: FormDefinition,
        case: CorpusCase,
    ): FormEngine =
        FormEngine(
            definition,
            case.values.objectValue ?: MutableJsonObject(),
            FormEngineOptions(hiddenValues = case.hiddenValues),
        )

    private suspend fun browserCanSubmit(
        definition: FormDefinition,
        case: CorpusCase,
    ): Boolean {
        val engine = engine(definition, case)
        while (engine.isMultiStep && !engine.isLastStep) {
            if (engine.isActionDisabled(engine.currentStep?.next?.action)) return false
            if (!engine.next()) return false
        }
        return engine.validate().isEmpty() && !engine.isActionDisabled(definition.submit)
    }

    private fun options(case: CorpusCase): SubmissionOptions = SubmissionOptions(case.skippedSteps, hiddenValues = case.hiddenValues)

    private fun cases(test: (CorpusFixture, CorpusCase) -> Unit): List<DynamicTest> =
        CorpusFixture.valid.flatMap { fixture ->
            fixture.cases.map { case ->
                DynamicTest.dynamicTest("${fixture.name}: ${case.name}") { test(fixture, case) }
            }
        }

    @TestFactory
    fun `case names are unique in every fixture`(): List<DynamicTest> =
        CorpusFixture.all.map { fixture ->
            DynamicTest.dynamicTest(fixture.name) {
                val names = fixture.cases.map { it.name }
                assertEquals(names.size, names.toSet().size)
            }
        }

    @TestFactory
    fun server(): List<DynamicTest> =
        cases { fixture, case ->
            val result = validateSubmission(fixture.runtime(), case.values, options(case))
            assertEquals(case.serverValues ?: case.expectedValues, JsonValue.Object(result.values))
            assertEquals(case.expectedErrors, json(result.errors))
        }

    @TestFactory
    fun `browser engine`(): List<DynamicTest> =
        cases { fixture, case ->
            val definition = fixture.runtime()
            val engine = engine(definition, case)
            assertEquals(case.expectedErrors, json(engine.validate()))
            assertEquals(case.expectedValues, JsonValue.Object(engine.getSubmitValues()))
            assertEquals(case.canSubmit, runBlocking { browserCanSubmit(definition, case) })
        }

    @TestFactory
    fun `a payload the browser accepts passes the server`(): List<DynamicTest> =
        cases { fixture, case ->
            val definition = fixture.runtime()
            if (!runBlocking { browserCanSubmit(definition, case) }) return@cases
            assertTrue(validateSubmission(definition, case.values, options(case)).errors.isEmpty())
        }
}
