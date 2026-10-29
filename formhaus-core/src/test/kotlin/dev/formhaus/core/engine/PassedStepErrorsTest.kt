package dev.formhaus.core.engine

import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassedStepErrorsTest {
    private val taken = errorsOf("""{"email":"Taken"}""")
    private val itemTaken = errorsOf("""{"players.0.name":"Taken"}""")

    @Test
    fun `drops onValidate errors of the step on the next Continue and after Back`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions(onValidate = League.once(taken)))
            assertFalse(engine.next())
            assertEquals(taken, engine.errors)
            assertTrue(engine.next())
            assertTrue(engine.errors.isEmpty())
            assertTrue(engine.back())
            assertTrue(engine.errors.isEmpty())
        }

    @Test
    fun `drops onStepValidate errors of group items on the next Continue`(): Unit =
        runBlocking {
            val replies = ArrayDeque(listOf(null, itemTaken))
            val engine = League.engine(FormEngineOptions(onStepValidate = { _, _ -> replies.removeFirstOrNull() }))
            engine.next()
            assertFalse(engine.next())
            assertEquals(itemTaken, engine.errors)
            assertTrue(engine.next())
            assertTrue(engine.errors.isEmpty())
        }

    @Test
    fun `brings back the setErrors error a step error replaced`(): Unit =
        runBlocking {
            val server = errorsOf("""{"email":"Server says no"}""")
            val engine = League.engine(FormEngineOptions(onValidate = League.once(taken)))
            engine.setErrors(server)
            assertFalse(engine.next())
            assertEquals(taken, engine.errors)
            assertTrue(engine.next())
            assertEquals(server, engine.errors)
        }

    @Test
    fun `keeps errors of other steps`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions())
            engine.next()
            engine.setValue("email", text(""))
            engine.validateField("email")
            assertTrue(engine.next())
            assertEquals(errorsOf("""{"email":"This field is required"}"""), engine.errors)
        }

    @Test
    fun `keeps errors of the step on Back`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions(onValidate = { _, context -> itemTaken.takeIf { context.stepId == "roster" } }))
            engine.next()
            assertFalse(engine.next())
            assertTrue(engine.back())
            assertEquals(itemTaken, engine.errors)
        }
}
