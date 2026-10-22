package dev.formhaus.core.engine

import dev.formhaus.core.support.engine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class CancellationTest {
    @Test
    fun `next returns false when a hook throws CancellationException`(): Unit = runBlocking {
        val engine = engine(Forms.STEPS, options = FormEngineOptions(onBeforeStepChange = { throw CancellationException() }))
        assertFalse(engine.next())
        assertEquals("one", engine.currentStep?.id)
        assertFalse(engine.stepValidating)
    }

    @Test
    fun `back returns false when onBeforeStepChange throws CancellationException`(): Unit = runBlocking {
        val engine = engine(
            Forms.STEPS,
            options = FormEngineOptions(onBeforeStepChange = { context ->
                if (context.reason == StepChangeReason.BACK) throw CancellationException()
                true
            }),
        )
        assertTrue(engine.next())
        assertFalse(engine.back())
        assertEquals("two", engine.currentStep?.id)
        assertFalse(engine.stepValidating)
    }

    @Test
    fun `a cancelled caller gets false from next instead of an error`(): Unit = runBlocking {
        withTimeout(60_000) {
            val started = Channel<Unit>(Channel.UNLIMITED)
            val engine = engine(
                Forms.STEPS,
                options = FormEngineOptions(onStepValidate = { _, _ ->
                    started.send(Unit)
                    delay(3_600_000)
                    null
                }),
            )
            var result: Boolean? = null
            val job = launch { result = engine.next() }
            started.receive()
            job.cancel()
            job.join()
            assertEquals(false, result)
            assertFalse(engine.stepValidating)
        }
    }

    @Test
    fun `submit returns false when the handler throws CancellationException`(): Unit = runBlocking {
        val events = ArrayList<FormEvent>()
        val engine = engine(Forms.FLAT, options = FormEngineOptions(onFormEvent = { events.add(it) }))
        assertFalse(engine.submit { _, _ -> throw CancellationException() })
        assertFalse(engine.submitting)
        assertEquals(FormEvent.FormSubmitFailed(SubmitFailureReason.Error(null)), events.last())
    }

    @Test
    fun `submit returns true when onAfterSubmit throws CancellationException after the submit`(): Unit = runBlocking {
        val engine = engine(Forms.FLAT, options = FormEngineOptions(onAfterSubmit = { _, _ -> throw CancellationException() }))
        assertTrue(engine.submit { _, _ -> SubmitResult.Ok })
        assertFalse(engine.submitting)
    }

    @Test
    fun `next returns true when onAfterStepChange throws CancellationException after the move`(): Unit = runBlocking {
        val engine = engine(Forms.STEPS, options = FormEngineOptions(onAfterStepChange = { throw CancellationException() }))
        assertTrue(engine.next())
        assertEquals("two", engine.currentStep?.id)
        assertFalse(engine.stepValidating)
    }
}
