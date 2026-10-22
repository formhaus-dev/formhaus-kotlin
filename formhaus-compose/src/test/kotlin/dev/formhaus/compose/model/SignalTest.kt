package dev.formhaus.compose.model

import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.renderer.version
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignalTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun aKeystrokeChangesOnlyTheEditedField() {
        val form = model(signupDefinition)
        val email = form.field("email")
        val name = form.field("name")
        val others = {
            name.value
            name.error
            name.loading
            form.structure
            form.actions
            form.errorGroups
            Unit
        }
        assertFalse(fires(others) { form.setValue("email", JsonValue.String("a")) })
        assertTrue(fires({ email.value }) { form.setValue("email", JsonValue.String("ab")) })
        assertFalse(
            fires({
                email.error
                email.loading
            }) { form.setValue("email", JsonValue.String("abc")) },
        )
        assertEquals(JsonValue.String("abc"), email.value)
        assertEquals(0, name.version)
    }

    @Test
    fun aConditionChangeUpdatesTheStructureNotUnrelatedFields() {
        val form = model(signupDefinition)
        val name = form.field("name")
        val before = form.structureVersion
        assertTrue(fires({ form.structure }) { form.setValue("work", JsonValue.Bool(true)) })
        assertEquals(before + 1, form.structureVersion)
        assertTrue("company" in form.structure.visibleFields.map { it.key })
        assertFalse(fires({ name.value }) { form.setValue("work", JsonValue.Bool(false)) })
    }

    @Test
    fun actionStateChangesOnlyWhenItsOutputChanges() {
        val form = model(signupDefinition)
        assertTrue(form.actions.primaryDisabled)
        assertFalse(fires({ form.actions }) { form.setValue("name", JsonValue.String("Ada")) })
        assertTrue(fires({ form.actions }) { form.setValue("agree", JsonValue.Bool(true)) })
        assertFalse(form.actions.primaryDisabled)
        assertEquals("Create", form.actions.primaryLabel)
    }

    @Test
    fun valuesSignalAndOnValuesChangeFireOncePerChange() {
        val emitted = mutableListOf<JsonObject>()
        val form = model(signupDefinition, options = FormRendererOptions(onValuesChange = { emitted += it }))
        form.start()
        val start = form.valuesVersion
        form.setValue("name", JsonValue.String("A"))
        form.setValue("name", JsonValue.String("A"))
        assertEquals(start + 1, form.valuesVersion)
        assertEquals(2, emitted.size)
        assertEquals(JsonValue.String("A"), emitted.last()["name"])
    }

    @Test
    fun snapshotCountersFollowTheEngine() {
        val form = model(signupDefinition)
        form.setValue("name", JsonValue.String("A"))
        assertEquals(form.engine.version, form.version)
        assertEquals(1, form.field("name").version)
    }

    @Test
    fun aFailedSubmitCountsAnInvalidAttemptAndAsksForFocusOnTheFirstError() =
        runTest {
            val form = model(signupDefinition, """{"agree": true}""")
            form.submit()
            assertEquals(1, form.invalidAttempts)
            assertEquals("email", form.focusRequest?.key)
            assertEquals("This field is required", form.field("email").error)
            assertEquals(listOf("Email"), form.errorGroups.flatMap { group -> group.entries.map { it.label } })
            form.submit()
            assertEquals(2, form.invalidAttempts)
        }

    @Test
    fun aDisabledSubmitDoesNotRun() =
        runTest {
            var sent = false
            val form =
                model(signupDefinition, """{"email": "a@b.co"}""") { _, _ ->
                    sent = true
                    null
                }
            form.submit()
            assertFalse(sent)
            assertEquals(0, form.invalidAttempts)
        }

    @Test
    fun serverErrorsMapToFieldsAndFormLevelErrors() =
        runTest {
            val form =
                model(signupDefinition, """{"email": "a@b.co", "agree": true}""") { _, _ ->
                    SubmitResult.Rejected(stringMapOf("email" to "Taken", "nope" to "Unknown"), listOf("Try later"))
                }
            form.submit()
            assertEquals("Taken", form.field("email").error)
            assertEquals(listOf("Try later", "Unknown"), form.topLevelErrors)
            assertEquals("email", form.focusRequest?.key)
        }

    @Test
    fun aThrownSubmitShowsAnActionError() =
        runTest {
            val form = model(signupDefinition, """{"email": "a@b.co", "agree": true}""") { _, _ -> throw Down() }
            assertNull(form.actionError)
            form.submit()
            assertEquals("Form action failed", form.actionError)
        }

    private class Down : Exception()
}
