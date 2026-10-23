package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateHookTest {
    private class Calls(
        private val result: (ValidateContext) -> FieldErrors? = { null },
    ) {
        val seen = ArrayList<Pair<JsonObject, ValidateContext>>()
        val hook: ValidateFn = { values, context ->
            seen.add(values to context)
            result(context)
        }
    }

    private fun context(
        stepId: String?,
        fields: String,
        submit: Boolean,
    ) = ValidateContext(stepId, fields.split(' ').filter(String::isNotEmpty), submit)

    @Test
    fun `gets the values and the visible field paths of the step, with group items`(): Unit =
        runBlocking {
            val calls = Calls()
            val values = League.VALUES.replace("\"solo\"", "\"team\",\"company\":\"Acme\"")
            val engine = League.engine(FormEngineOptions(onValidate = calls.hook), values)
            assertTrue(engine.next())
            assertTrue(engine.next())
            assertEquals(text("Acme"), calls.seen[0].first["company"])
            assertEquals(context("account", "email plan company", false), calls.seen[0].second)
            assertEquals(context("roster", "players players.0.name players.0.role players.1.name", false), calls.seen[1].second)
        }

    @Test
    fun `runs only after the built-in rules pass`(): Unit =
        runBlocking {
            val calls = Calls()
            val engine = League.engine(FormEngineOptions(onValidate = calls.hook), "{}")
            assertFalse(engine.next())
            assertNotNull(engine.errors["email"])
            assertEquals(0, calls.seen.size)
        }

    @Test
    fun `runs after onStepValidate and before onBeforeStepChange`(): Unit =
        runBlocking {
            val order = ArrayList<String>()
            val options =
                FormEngineOptions(
                    onStepValidate = { _, _ -> null.also { order.add("onStepValidate") } },
                    onValidate = { _, _ -> null.also { order.add("onValidate") } },
                    onBeforeStepChange = { true.also { order.add("onBeforeStepChange") } },
                )
            assertTrue(League.engine(options).next())
            assertEquals(listOf("onStepValidate", "onValidate", "onBeforeStepChange"), order)
        }

    @Test
    fun `does not run when onStepValidate reports errors`(): Unit =
        runBlocking {
            val calls = Calls()
            val engine =
                League.engine(
                    FormEngineOptions(onStepValidate = {
                        _,
                        _,
                        ->
                        errorsOf("""{"email":"Taken"}""")
                    }, onValidate = calls.hook),
                )
            assertFalse(engine.next())
            assertEquals(errorsOf("""{"email":"Taken"}"""), engine.errors)
            assertEquals(0, calls.seen.size)
        }

    @Test
    fun `keeps the step and shows field errors`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions(onValidate = { _, _ -> errorsOf("""{"email":"Taken"}""") }))
            assertFalse(engine.next())
            assertEquals("account", engine.currentStep?.id)
            assertEquals(errorsOf("""{"email":"Taken"}"""), engine.errors)
            assertFalse(engine.stepValidating)
        }

    @Test
    fun `shows errors on group item paths`(): Unit =
        runBlocking {
            val errors = errorsOf("""{"players.1.name":"Name the player"}""")
            val engine = League.engine(FormEngineOptions(onValidate = { _, context -> errors.takeIf { context.stepId == "roster" } }))
            engine.next()
            assertFalse(engine.next())
            assertEquals(errors, engine.errors)
        }

    @Test
    fun `turns empty, unknown, hidden and other-step keys into top-level errors`(): Unit =
        runBlocking {
            val errors = errorsOf("""{"":"Service down","ghost":"Ghost","company":"Hidden","notes":"Later","players.0.name":"Item"}""")
            val engine = League.engine(FormEngineOptions(onValidate = { _, _ -> errors }))
            assertFalse(engine.next())
            assertTrue(engine.errors.isEmpty())
            assertEquals(listOf("Service down", "Ghost", "Hidden", "Later", "Item"), engine.topLevelErrors)
        }

    @Test
    fun `passes on null and an empty map`(): Unit =
        runBlocking {
            for (result in listOf(null, stringMapOf<String>())) {
                assertTrue(League.engine(FormEngineOptions(onValidate = { _, _ -> result })).next())
            }
        }

    @Test
    fun `ignores a result when a checked value changes while it runs`(): Unit =
        runBlocking {
            val check = pending()
            val engine = League.engine(FormEngineOptions(onValidate = { _, _ -> check.await() }))
            val moving = started { engine.next() }
            assertTrue(engine.stepValidating)
            engine.setValue("plan", text("pro"))
            check.complete(errorsOf("""{"email":"Taken"}"""))
            assertFalse(moving.await())
            assertTrue(engine.errors.isEmpty())
            assertEquals("account", engine.currentStep?.id)
        }

    @Test
    fun `ignores a result when the step changes while it runs`(): Unit =
        runBlocking {
            val check = pending()
            val engine =
                League.engine(
                    FormEngineOptions(onValidate = { _, context ->
                        if (context.stepId ==
                            "roster"
                        ) {
                            check.await()
                        } else {
                            null
                        }
                    }),
                )
            engine.next()
            val moving = started { engine.next() }
            engine.goToStepWithField("email")
            check.complete(errorsOf("""{"players.0.name":"Taken"}"""))
            assertFalse(moving.await())
            assertEquals("account", engine.currentStep?.id)
            assertTrue(engine.errors.isEmpty())
        }

    @Test
    fun `does not run on Back or Skip`(): Unit =
        runBlocking {
            val calls = Calls()
            val engine = League.engine(FormEngineOptions(onValidate = calls.hook))
            engine.next()
            engine.skip()
            engine.back()
            assertEquals(1, calls.seen.size)
        }

    @Test
    fun `gets every visible field path on submit and runs before onBeforeSubmit`(): Unit =
        runBlocking {
            val order = ArrayList<String>()
            val calls = Calls { null.also { order.add("onValidate") } }
            val engine =
                League.onNotes(
                    FormEngineOptions(onValidate = calls.hook, onBeforeSubmit = { true.also { order.add("onBeforeSubmit") } }),
                )
            order.clear()
            assertTrue(engine.submit { _, _ -> SubmitResult.Ok.also { order.add("submit") } })
            assertEquals(listOf("onValidate", "onBeforeSubmit", "submit"), order)
            assertEquals(text("Hi"), calls.seen.last().first["notes"])
            val fields = "email plan players players.0.name players.0.role players.1.name notes"
            assertEquals(context("notes", fields, true), calls.seen.last().second)
        }

    @Test
    fun `leaves skipped steps out`(): Unit =
        runBlocking {
            val calls = Calls()
            val engine = League.engine(FormEngineOptions(onValidate = calls.hook))
            engine.next()
            engine.skip()
            engine.submit { _, _ -> SubmitResult.Ok }
            assertEquals(
                listOf("email", "plan", "notes"),
                calls.seen
                    .last()
                    .second.fields,
            )
        }

    @Test
    fun `blocks the submit and opens the step of the first error`(): Unit =
        runBlocking {
            val events = ArrayList<FormEvent>()
            var before = 0
            var sent = 0
            val errors = errorsOf("""{"players.0.name":"Duplicate","":"Check the roster"}""")
            val options =
                FormEngineOptions(
                    onValidate = { _, context -> errors.takeIf { context.submit } },
                    onBeforeSubmit = { true.also { before += 1 } },
                    onFormEvent = events::add,
                )
            val engine = League.onNotes(options)
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok.also { sent += 1 } })
            assertEquals(0, sent)
            assertEquals(0, before)
            assertEquals("roster", engine.currentStep?.id)
            assertEquals(errorsOf("""{"players.0.name":"Duplicate"}"""), engine.errors)
            assertEquals(listOf("Check the roster"), engine.topLevelErrors)
            assertFalse(engine.submitting)
            assertTrue(FormEvent.FormSubmitFailed(SubmitFailureReason.Validation) in events)
        }

    @Test
    fun `runs on a single-step form without a step id`(): Unit =
        runBlocking {
            val calls = Calls { errorsOf("""{"name":"Taken"}""") }
            val definition =
                """{"id":"single","title":"Single","submit":{"label":"Send"},
                "fields":[{"key":"name","type":"text","label":"Name"}]}"""
            val engine = engine(definition, """{"name":"Ada"}""", FormEngineOptions(onValidate = calls.hook))
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(context(null, "name", true), calls.seen.last().second)
            assertEquals(errorsOf("""{"name":"Taken"}"""), engine.errors)
        }

    @Test
    fun `ignores a submit result when a checked value changes while it runs`(): Unit =
        runBlocking {
            val check = pending()
            var sent = 0
            val engine = League.onNotes(FormEngineOptions(onValidate = { _, context -> if (context.submit) check.await() else null }))
            val submitting = started { engine.submit { _, _ -> SubmitResult.Ok.also { sent += 1 } } }
            assertTrue(engine.submitting)
            engine.setValue("notes", text("Changed"))
            check.complete(errorsOf("""{"notes":"Too short"}"""))
            assertFalse(submitting.await())
            assertEquals(0, sent)
            assertTrue(engine.errors.isEmpty())
        }

    @Test
    fun `keeps server errors from an earlier submit next to its errors`(): Unit =
        runBlocking {
            var round = 0
            val engine =
                League.onNotes(
                    FormEngineOptions(onValidate = { _, context ->
                        if (context.submit && ++round == 2) errorsOf("""{"email":"Taken"}""") else null
                    }),
                )
            engine.submit { _, _ -> SubmitResult.Rejected(errorsOf("""{"notes":"Rejected"}""")) }
            assertEquals(errorsOf("""{"notes":"Rejected"}"""), engine.errors)
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(errorsOf("""{"notes":"Rejected","email":"Taken"}"""), engine.errors)
            assertEquals("account", engine.currentStep?.id)
        }
}
