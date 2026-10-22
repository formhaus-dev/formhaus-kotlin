package dev.formhaus.core.engine

import dev.formhaus.core.support.engine
import dev.formhaus.core.support.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AwaitOrderTest {
    private fun CoroutineScope.deferOnce(
        engine: FormEngine,
        busy: (FormEngine) -> Boolean,
        action: () -> Unit,
    ) {
        var once = false
        engine.subscribe {
            if (!busy(engine) || once) return@subscribe
            once = true
            launch { action() }
        }
    }

    @Test
    fun `a write queued while onBeforeStepChange replies synchronously makes next stale`(): Unit =
        runBlocking {
            val engine = engine(Forms.STEPS, """{"a":"x"}""", FormEngineOptions(onBeforeStepChange = { true }))
            deferOnce(engine, FormEngine::stepValidating) { engine.setValue("a", text("y")) }
            assertFalse(engine.next())
            assertEquals("one", engine.currentStep?.id)
            assertEquals(text("y"), engine.values["a"])
            assertFalse(engine.stepValidating)
        }

    @Test
    fun `a write queued while onStepValidate replies synchronously makes next stale`(): Unit =
        runBlocking {
            val engine = engine(Forms.STEPS, """{"a":"x"}""", FormEngineOptions(onStepValidate = { _, _ -> null }))
            deferOnce(engine, FormEngine::stepValidating) { engine.setValue("a", text("y")) }
            assertFalse(engine.next())
            assertEquals("one", engine.currentStep?.id)
            assertFalse(engine.stepValidating)
        }

    @Test
    fun `a write queued while onBeforeSubmit replies synchronously stops the submission`(): Unit =
        runBlocking {
            val engine = engine(Forms.FLAT, """{"a":"x"}""", FormEngineOptions(onBeforeSubmit = { true }))
            deferOnce(engine, FormEngine::submitting) { engine.setValue("a", text("y")) }
            var calls = 0
            assertFalse(
                engine.submit { _, _ ->
                    calls += 1
                    null
                },
            )
            assertEquals(0, calls)
            assertEquals(text("y"), engine.values["a"])
            assertFalse(engine.submitting)
        }

    @Test
    fun `cancelPendingActions queued at form_submitted skips onAfterSubmit`(): Unit =
        runBlocking {
            var calls = 0
            lateinit var engine: FormEngine
            val options =
                FormEngineOptions(
                    onAfterSubmit = { _, _ -> calls += 1 },
                    onFormEvent = { event ->
                        if (event ==
                            FormEvent.FormSubmitted(1)
                        ) {
                            launch { engine.cancelPendingActions() }
                        }
                    },
                )
            engine = engine(Forms.FLAT, """{"a":"x"}""", options)
            assertTrue(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(0, calls)
            assertFalse(engine.submitting)
        }

    @Test
    fun `server errors queued at form_submitted survive a successful submit`(): Unit =
        runBlocking {
            lateinit var engine: FormEngine
            val options =
                FormEngineOptions(
                    onFormEvent = { event ->
                        if (event ==
                            FormEvent.FormSubmitted(0)
                        ) {
                            launch { engine.setErrors(errorsOf("""{"a":"taken"}""")) }
                        }
                    },
                )
            engine = engine(Forms.FLAT, options = options)
            assertTrue(engine.submit { _, _ -> SubmitResult.Ok })
            assertEquals(errorsOf("""{"a":"taken"}"""), engine.errors)
        }
}
