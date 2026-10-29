package dev.formhaus.core.engine

import dev.formhaus.core.support.engine
import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateWithRulesTest {
    private val emails =
        """{"id":"emails","title":"Emails","submit":{"label":"Send"},"fields":[
        {"key":"email","type":"email","label":"Email","validation":{"required":true}},
        {"key":"confirm","type":"email","label":"Confirm"}]}"""

    private val mismatchErrors = errorsOf("""{"email":"Use a work email","confirm":"Emails must match","":"Check the form"}""")

    private val mismatch: ValidateFn = { _, _ -> mismatchErrors }

    private val pass: ValidateFn = { _, _ -> null }

    private val failures = FormEvent.FormSubmitFailed(SubmitFailureReason.Validation)

    private fun emailsEngine(
        options: FormEngineOptions,
        values: String = "{}",
    ): FormEngine = engine(emails, values, options)

    @Test
    fun `runs onValidate on submit when the definition rules fail and merges both errors`(): Unit =
        runBlocking {
            val events = ArrayList<FormEvent>()
            val seen = ArrayList<ValidateContext>()
            var sent = 0
            val options =
                FormEngineOptions(
                    onValidate = { values, context -> mismatch(values, context.also(seen::add)) },
                    onFormEvent = events::add,
                    validateWithRules = true,
                )
            val engine = emailsEngine(options, """{"confirm":"b@example.com"}""")
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok.also { sent += 1 } })
            assertEquals(0, sent)
            assertEquals(listOf(ValidateContext(null, listOf("email", "confirm"), true)), seen)
            assertEquals(listOf("email", "confirm"), engine.errors.keys)
            assertNotEquals("Use a work email", engine.errors["email"])
            assertEquals("Emails must match", engine.errors["confirm"])
            assertEquals(listOf("Check the form"), engine.topLevelErrors)
            assertEquals(1, events.count { it == failures })
            assertFalse(engine.submitting)
        }

    @Test
    fun `keeps the definition errors when onValidate passes on submit`(): Unit =
        runBlocking {
            val events = ArrayList<FormEvent>()
            val engine = emailsEngine(FormEngineOptions(onValidate = pass, onFormEvent = events::add, validateWithRules = true))
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(listOf("email"), engine.errors.keys)
            assertEquals(1, events.count { it == failures })
        }

    @Test
    fun `does not run onBeforeSubmit or the handler when the definition rules fail`(): Unit =
        runBlocking {
            var before = 0
            var sent = 0
            val engine =
                emailsEngine(
                    FormEngineOptions(onValidate = mismatch, onBeforeSubmit = { true.also { before += 1 } }, validateWithRules = true),
                )
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok.also { sent += 1 } })
            assertEquals(0, before)
            assertEquals(0, sent)
        }

    @Test
    fun `submits when the rules and onValidate pass`(): Unit =
        runBlocking {
            var sent = 0
            val engine = emailsEngine(FormEngineOptions(onValidate = pass, validateWithRules = true), """{"email":"a@example.com"}""")
            assertTrue(engine.submit { _, _ -> SubmitResult.Ok.also { sent += 1 } })
            assertEquals(1, sent)
        }

    @Test
    fun `opens the first step with an error of either source on submit`(): Unit =
        runBlocking {
            val options =
                FormEngineOptions(
                    onValidate = { _, context -> errorsOf("""{"players.0.name":"Duplicate"}""").takeIf { context.submit } },
                    validateWithRules = true,
                )
            val engine = League.onNotes(options)
            engine.setValue("email", text(""))
            assertFalse(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals("account", engine.currentStep?.id)
            assertEquals(setOf("email", "players.0.name"), engine.errors.keys.toSet())
            assertEquals("Duplicate", engine.errors["players.0.name"])
        }

    @Test
    fun `ignores a submit result when a checked value changes while it runs`(): Unit =
        runBlocking {
            val check = pending()
            val events = ArrayList<FormEvent>()
            val engine =
                emailsEngine(
                    FormEngineOptions(onValidate = { _, _ -> check.await() }, onFormEvent = events::add, validateWithRules = true),
                )
            val submitting = started { engine.submit { _, _ -> SubmitResult.Ok } }
            assertTrue(engine.submitting)
            engine.setValue("confirm", text("c@example.com"))
            check.complete(errorsOf("""{"confirm":"Emails must match"}"""))
            assertFalse(submitting.await())
            assertNull(engine.errors["confirm"])
            assertFalse(failures in events)
        }

    @Test
    fun `runs onValidate on Continue when the step rules fail and merges both errors`(): Unit =
        runBlocking {
            val seen = ArrayList<ValidateContext>()
            val options =
                FormEngineOptions(
                    onValidate = { _, context ->
                        seen.add(context)
                        errorsOf("""{"email":"Taken","plan":"Pick a paid plan","":"Service down"}""")
                    },
                    validateWithRules = true,
                )
            val engine = League.engine(options, """{"plan":"solo"}""")
            assertFalse(engine.next())
            assertEquals(listOf(ValidateContext("account", listOf("email", "plan"), false)), seen)
            assertEquals(listOf("email", "plan"), engine.errors.keys)
            assertNotEquals("Taken", engine.errors["email"])
            assertEquals("Pick a paid plan", engine.errors["plan"])
            assertEquals(listOf("Service down"), engine.topLevelErrors)
            assertEquals("account", engine.currentStep?.id)
            assertFalse(engine.stepValidating)
        }

    @Test
    fun `skips onStepValidate and onBeforeStepChange when the step rules fail`(): Unit =
        runBlocking {
            val calls = ArrayList<String>()
            val options =
                FormEngineOptions(
                    onValidate = { _, context -> null.also { calls.add(context.stepId.orEmpty()) } },
                    onStepValidate = { step, _ -> null.also { calls.add("step $step") } },
                    onBeforeStepChange = { context -> false.also { calls.add("before ${context.fromStepId}") } },
                    validateWithRules = true,
                )
            val engine = League.engine(options, "{}")
            assertFalse(engine.next())
            assertNotNull(engine.errors["email"])
            assertEquals(listOf("account"), calls)
        }

    @Test
    fun `moves on when the step rules and onValidate pass`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions(onValidate = pass, validateWithRules = true))
            assertTrue(engine.next())
            assertEquals("roster", engine.currentStep?.id)
        }

    @Test
    fun `ignores a step result when a checked value changes while it runs`(): Unit =
        runBlocking {
            val check = pending()
            val engine = League.engine(FormEngineOptions(onValidate = { _, _ -> check.await() }, validateWithRules = true), "{}")
            val moving = started { engine.next() }
            assertTrue(engine.stepValidating)
            engine.setValue("plan", text("pro"))
            check.complete(errorsOf("""{"plan":"Pick a paid plan"}"""))
            assertFalse(moving.await())
            assertNull(engine.errors["plan"])
        }

    @Test
    fun `has no effect without onValidate`(): Unit =
        runBlocking {
            val engine = League.engine(FormEngineOptions(validateWithRules = true), "{}")
            assertFalse(engine.next())
            assertFalse(engine.stepValidating)
            assertEquals(listOf("email"), engine.errors.keys)
        }
}
