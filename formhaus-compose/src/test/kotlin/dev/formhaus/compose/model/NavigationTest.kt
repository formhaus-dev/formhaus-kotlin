package dev.formhaus.compose.model

import dev.formhaus.core.i18n.localize
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.renderer.LabelSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun commitOnAnAutoAdvanceFieldMovesToTheNextStep() {
        val form = model(stepsDefinition, """{"name": "Ada"}""")
        form.commitValue("seat", JsonValue.String("w"))
        assertEquals("when", form.structure.currentStep?.id)
        assertEquals(2, form.structure.progress.current)
        assertTrue(form.actions.showBack)
        assertEquals("Book", form.actions.primaryLabel)
    }

    @Test
    fun nextWithErrorsStaysAndFocusesTheFirstError() =
        runTest {
            val form = model(stepsDefinition)
            form.next()
            assertEquals("who", form.structure.currentStep?.id)
            assertEquals("name", form.focusRequest?.key)
            assertEquals(mapOf("who" to 1), form.stepErrorCounts)
        }

    @Test
    fun nextAndBackMoveBetweenStepsAndAskForFocus() =
        runTest {
            val form = model(stepsDefinition, """{"name": "Ada"}""")
            assertEquals("Continue", form.actions.primaryLabel)
            form.next()
            assertEquals("when", form.structure.currentStep?.id)
            assertNotNull(form.focusRequest)
            form.back()
            assertEquals("who", form.structure.currentStep?.id)
        }

    @Test
    fun focusOpensTheStepOfAFieldAndFocusesIt() =
        runTest {
            val form = model(stepsDefinition, """{"name": "Ada"}""")
            form.next()
            form.focus("name")
            assertEquals("who", form.structure.currentStep?.id)
            assertEquals("name", form.focusRequest?.key)
        }

    @Test
    fun loadingBlocksEditsAndActions() =
        runTest {
            val form = model(stepsDefinition, """{"name": "Ada"}""")
            form.loading = true
            assertTrue(form.disabled)
            form.setValue("name", JsonValue.String("Bo"))
            form.next()
            assertEquals(JsonValue.String("Ada"), form.field("name").value)
            assertEquals("who", form.structure.currentStep?.id)
        }

    @Test
    fun labelsComeFromTheRendererSourceThenTheDefinitionThenDefaults() {
        val definition = stepsDefinition.replaceFirst("{", """{"labels": {"back": "Zurück"}, """)
        val source = LabelSource.Catalog(json("""{"continue": "Weiter"}"""))
        val form = model(definition, options = FormRendererOptions(labels = source))
        assertEquals("Weiter", form.label("continue"))
        assertEquals("Zurück", form.label("back"))
        assertEquals("Step 1 of 2", form.label("stepOf", stringMapOf("current" to JsonValue.Number(1), "total" to JsonValue.Number(2))))
        assertEquals("Weiter", form.actions.primaryLabel)
    }

    @Test
    fun anOverlayTranslatesTextsMessagesAndLabels() =
        runTest {
            val overlay =
                json(
                    """{"fields": {"email": {"label": "E-Mail"}}, "messages": {"required": "Pflichtfeld"},
                    "labels": {"errorSummary": "Bitte prüfen"}}""",
                )
            val localized = localize(json(signupDefinition), overlay, "de")
            val form = FormModel(localized, values("""{"agree": true}""")) { _, _ -> null }
            assertEquals("Bitte prüfen", form.label("errorSummary"))
            assertEquals(
                "E-Mail",
                form.structure.visibleFields
                    .first()
                    .label,
            )
            form.submit()
            assertEquals("Pflichtfeld", form.field("email").error)
        }
}
