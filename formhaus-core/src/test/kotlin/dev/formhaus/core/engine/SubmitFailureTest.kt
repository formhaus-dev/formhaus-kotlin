package dev.formhaus.core.engine

import dev.formhaus.core.support.engine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class SubmitFailureTest {
    private class ServerError(override val status: Int?) : Exception(), SubmitStatusError

    private fun failure(error: Throwable): FormEvent? = runBlocking {
        val events = ArrayList<FormEvent>()
        val engine = engine(Forms.FLAT, options = FormEngineOptions(onFormEvent = { events.add(it) }))
        engine.start()
        assertTrue(runCatching { engine.submit { _, _ -> throw error } }.isFailure)
        events.lastOrNull()
    }

    private fun outcome(result: GoToStepResult): String = "${result.stepId} ${result.reached} ${result.reason?.rawValue}"

    private fun failed(status: Int?): FormEvent = FormEvent.FormSubmitFailed(SubmitFailureReason.Error(status))

    @Test
    fun `a thrown error reports its status, 0 for IOException, the host status, or none`() {
        assertEquals(failed(0), failure(IOException("offline")))
        assertEquals(failed(503), failure(ServerError(503)))
        assertEquals(failed(null), failure(ServerError(null)))
        assertEquals(failed(null), failure(IllegalStateException()))
    }

    @Test
    fun `goToStep in a form without steps stays put and reports an unknown step`() {
        val events = ArrayList<FormEvent>()
        val engine = engine(Forms.FLAT, options = FormEngineOptions(onFormEvent = { events.add(it) }))
        engine.start()
        events.clear()
        assertEquals("null false unknown-step", outcome(engine.goToStep("one")))
        assertEquals(listOf(FormEvent.StepRedirected("one", null, StepRedirectReason.UNKNOWN_STEP)), events)
    }

    @Test
    fun `goToStep while a step change is pending stays on the current step`(): Unit = runBlocking {
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
            val job = launch { engine.next() }
            started.receive()
            assertEquals("one false blocked", outcome(engine.goToStep("two")))
            assertEquals("one false blocked", outcome(engine.goToStep("one")))
            job.cancel()
            job.join()
        }
    }
}
