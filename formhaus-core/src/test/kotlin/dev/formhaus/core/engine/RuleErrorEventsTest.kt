package dev.formhaus.core.engine

import dev.formhaus.core.support.text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuleErrorEventsTest {
    private val log = ArrayList<String>()

    private val record: FormEventFn = { event -> if (event is FormEvent.FieldError) log.add("${event.fieldKey}: ${event.error}") }

    private fun FormEngine.emailEntry(): String = "email: ${errors["email"]}"

    @Test
    fun `reports the rule errors before onValidate runs and each error once`(): Unit =
        runBlocking {
            val onValidate: ValidateFn = { _, _ ->
                log.add("onValidate")
                errorsOf("""{"email":"Taken","plan":"Pick a paid plan"}""")
            }
            val engine = League.engine(FormEngineOptions(onValidate = onValidate, onFormEvent = record, validateWithRules = true), "{}")
            assertFalse(engine.next())
            assertEquals(listOf(engine.emailEntry(), "onValidate", "plan: Pick a paid plan"), log)
        }

    @Test
    fun `reports the rule errors when onValidate throws`(): Unit =
        runBlocking {
            val onValidate: ValidateFn = { _, _ -> throw IllegalStateException("Service down") }
            val engine = League.engine(FormEngineOptions(onValidate = onValidate, onFormEvent = record, validateWithRules = true), "{}")
            assertThrows<IllegalStateException> { runBlocking { engine.next() } }
            assertEquals(listOf(engine.emailEntry()), log)
        }

    @Test
    fun `reports the rule errors once when the reply is stale`(): Unit =
        runBlocking {
            val check = pending()
            val options = FormEngineOptions(onValidate = { _, _ -> check.await() }, onFormEvent = record, validateWithRules = true)
            val engine = League.engine(options, "{}")
            val moving = started { engine.next() }
            assertEquals(listOf(engine.emailEntry()), log)
            engine.setValue("plan", text("pro"))
            check.complete(errorsOf("""{"plan":"Pick a paid plan"}"""))
            assertFalse(moving.await())
            assertEquals(listOf(engine.emailEntry()), log)
        }

    @Test
    fun `reports each error once when a second submit arrives while onValidate runs`(): Unit =
        runBlocking { concurrent { engine -> engine.submit { _, _ -> SubmitResult.Ok } } }

    @Test
    fun `reports each error once when a second next arrives while onValidate runs`(): Unit =
        runBlocking { concurrent { engine -> engine.next() } }

    private suspend fun concurrent(action: suspend (FormEngine) -> Boolean): Unit =
        coroutineScope {
            val check = pending()
            val options = FormEngineOptions(onValidate = { _, _ -> check.await() }, onFormEvent = record, validateWithRules = true)
            val engine = League.engine(options, "{}")
            val first = started { action(engine) }
            assertFalse(action(engine))
            check.complete(errorsOf("""{"plan":"Pick a paid plan"}"""))
            assertFalse(first.await())
            assertEquals(listOf(engine.emailEntry(), "plan: Pick a paid plan"), log)
        }
}
