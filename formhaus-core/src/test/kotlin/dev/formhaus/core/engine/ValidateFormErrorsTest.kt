package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateFormErrorsTest {
    private val serviceDown = errorsOf("""{"":"Service down"}""")

    @Test
    fun `clears them on the next Continue`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions(onValidate = League.once(serviceDown)))
            assertFalse(engine.next())
            assertEquals(listOf("Service down"), engine.topLevelErrors)
            assertTrue(engine.next())
            assertEquals(emptyList<String>(), engine.topLevelErrors)
        }

    @Test
    fun `clears them on Back`(): Unit =
        runBlocking {
            val closed = errorsOf("""{"":"Roster closed"}""")
            val engine = League.engine(FormEngineOptions(onValidate = { _, context -> closed.takeIf { context.stepId == "roster" } }))
            engine.next()
            assertFalse(engine.next())
            assertEquals(listOf("Roster closed"), engine.topLevelErrors)
            assertTrue(engine.back())
            assertEquals(emptyList<String>(), engine.topLevelErrors)
        }

    @Test
    fun `clears them on a Submit that fails the definition rules`(): Unit =
        runBlocking {
            val engine = League.onNotes(FormEngineOptions(onValidate = League.once(serviceDown) { it }))
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(listOf("Service down"), engine.topLevelErrors)
            engine.setValue("email", text(""))
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertNotNull(engine.errors["email"])
            assertEquals(emptyList<String>(), engine.topLevelErrors)
        }

    @Test
    fun `keeps form errors of the submit handler and of setErrors`(): Unit =
        runBlocking {
            var down = false
            val engine = League.onNotes(FormEngineOptions(onValidate = { _, context -> serviceDown.takeIf { context.submit && down } }))
            engine.submit { _, _ -> SubmitResult.Rejected(formErrors = listOf("Server says no")) }
            assertEquals(listOf("Server says no"), engine.topLevelErrors)
            down = true
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(listOf("Server says no", "Service down"), engine.topLevelErrors)
            down = false
            engine.setErrors(errorsOf("""{"":"External"}"""))
            engine.setValue("email", text(""))
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(listOf("External"), engine.topLevelErrors)
        }

    @Test
    fun `rejects Continue when it throws`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions(onValidate = { _, _ -> error("offline") }))
            assertEquals("offline", runCatching { engine.next() }.exceptionOrNull()?.message)
            assertFalse(engine.stepValidating)
            assertEquals("account", engine.currentStep?.id)
        }

    @Test
    fun `rejects Submit when it throws, without sending`(): Unit =
        runBlocking {
            var sent = 0
            val engine = League.onNotes(FormEngineOptions(onValidate = { _, context -> if (context.submit) error("offline") else null }))
            val thrown = runCatching { engine.submit { _, _ -> SubmitResult.Ok.also { sent += 1 } } }.exceptionOrNull()
            assertEquals("offline", thrown?.message)
            assertEquals(0, sent)
            assertFalse(engine.submitting)
        }

    @Test
    fun `ignores a result after reset`(): Unit =
        runBlocking {
            val check = pending()
            val engine = League.engine(FormEngineOptions(onValidate = { _, _ -> check.await() }))
            val moving = started { engine.next() }
            engine.reset(requireNotNull(JsonValue.parse(League.VALUES).objectValue))
            check.complete(errorsOf("""{"email":"Taken","":"Service down"}"""))
            assertFalse(moving.await())
            assertTrue(engine.errors.isEmpty())
            assertEquals(emptyList<String>(), engine.topLevelErrors)
            assertFalse(engine.stepValidating)
        }

    @Test
    fun `ignores a submit result after replaceDefinition`(): Unit =
        runBlocking {
            val check = pending()
            var sent = 0
            val engine = League.onNotes(FormEngineOptions(onValidate = { _, context -> if (context.submit) check.await() else null }))
            val submitting = started { engine.submit { _, _ -> SubmitResult.Ok.also { sent += 1 } } }
            engine.replaceDefinition(JsonValue.parse(League.DEFINITION.replace("\"League\"", "\"League 2\"")))
            check.complete(errorsOf("""{"notes":"Too short"}"""))
            assertFalse(submitting.await())
            assertEquals(0, sent)
            assertTrue(engine.errors.isEmpty())
            assertFalse(engine.submitting)
        }
}
