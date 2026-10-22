package dev.formhaus.core.engine

import dev.formhaus.core.support.engine
import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class ThreadContractTest {
    private val form =
        """{"id":"t","title":"T","submit":{"label":"Send"},"steps":[
        {"id":"one","title":"One","fields":[{"key":"name","type":"text","label":"Name"}]},
        {"id":"two","title":"Two","fields":[{"key":"age","type":"number","label":"Age"}]}]}"""

    private fun <Value> elsewhere(body: () -> Value): Result<Value> {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "intruder") }
        try {
            return executor.submit<Result<Value>> { runCatching(body) }.get()
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `with debug checks a mutation from another thread throws`() {
        val engine = engine(form, options = FormEngineOptions(onWarning = {}, debugChecks = true))
        val attempts =
            listOf(
                elsewhere { engine.setValue("name", text("Ada")) },
                elsewhere { engine.reset() },
                elsewhere { runBlocking { engine.next() } },
                elsewhere { engine.replaceDefinition(engine.definition) },
            )
        for (attempt in attempts) {
            val error = attempt.exceptionOrNull()
            assertTrue(error is IllegalStateException, "$error")
            assertTrue(error?.message.orEmpty().contains("thread \"intruder"), error?.message)
        }
        assertNull(engine.values["name"])
        engine.setValue("name", text("Ada"))
        assertEquals(text("Ada"), engine.values["name"])
    }

    @Test
    fun `without debug checks the engine does not watch threads`() {
        val engine = engine(form)
        assertTrue(elsewhere { engine.setValue("name", text("Ada")) }.isSuccess)
        assertEquals(text("Ada"), engine.values["name"])
    }
}
