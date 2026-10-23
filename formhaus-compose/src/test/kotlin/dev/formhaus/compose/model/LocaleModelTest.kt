package dev.formhaus.compose.model

import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.i18n.LocaleCatalog
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormDefinition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocaleModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun stepsModel(
        engineOptions: FormEngineOptions,
        values: String = "{}",
    ): FormModel = FormModel(FormDefinition(json(stepsDefinition)), values(values), engineOptions = engineOptions) { _, _ -> null }

    @Test
    fun aLocaleCatalogTranslatesMessagesAndLabels() =
        runTest {
            val form = stepsModel(FormEngineOptions(locale = listOfNotNull(LocaleCatalog.bundled("de"))))
            assertEquals("Weiter", form.actions.primaryLabel)
            assertEquals("Bitte diese Antworten prüfen", form.label("errorSummary"))
            form.next()
            assertEquals("Dieses Feld ist erforderlich", form.field("name").error)
            assertEquals(listOf("fh-name"), form.errorGroups.flatMap { group -> group.entries.map { it.id } })
        }

    @Test
    fun onValidateRunsThroughTheModelAndItsFormErrorsClearOnContinue() =
        runTest {
            var down = true
            val form = stepsModel(FormEngineOptions(), """{"name": "Ada"}""")
            form.updateHandlers(
                engineOptions =
                    FormEngineOptions(onValidate = { _, context ->
                        stringMapOf("" to "Service down", "name" to "Taken").takeIf { down && context.stepId == "who" }
                    }),
            )
            form.next()
            assertEquals("who", form.structure.currentStep?.id)
            assertEquals("Taken", form.field("name").error)
            assertEquals(listOf("Service down"), form.topLevelErrors)
            down = false
            form.next()
            assertEquals("when", form.structure.currentStep?.id)
            assertEquals(emptyList<String>(), form.topLevelErrors)
        }
}
