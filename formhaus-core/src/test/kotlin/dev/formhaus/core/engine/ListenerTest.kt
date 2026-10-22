package dev.formhaus.core.engine

import dev.formhaus.core.support.engine
import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ListenerTest {
    private val form = """{"id":"f","title":"F","submit":{"label":"S"},"fields":[{"key":"b","type":"text","label":"B"}]}"""

    @Test
    fun `a listener removed during notify is skipped and one added is called`() {
        val engine = engine(form)
        var calls = ""
        var added = false
        var cancelB: (() -> Unit)? = null
        val cancelA =
            engine.subscribe {
                calls += "A"
                cancelB?.invoke()
                if (!added) {
                    added = true
                    engine.subscribe { calls += "C" }
                }
            }
        cancelB = engine.subscribe { calls += "B" }
        engine.setValue("b", text("x"))
        assertEquals("AC", calls)
        calls = ""
        engine.setValue("b", text("y"))
        assertEquals("AC", calls)
        cancelA()
    }

    @Test
    fun `FormLifecycleError carries the cause message`() {
        val engine =
            engine(
                form,
                options =
                    FormEngineOptions(onAfterSubmit = {
                        _,
                        _,
                        ->
                        throw IllegalStateException("boom")
                    }),
            )
        val error = assertThrows<FormLifecycleError> { runBlocking { engine.submit { _, _ -> null } } }
        assertEquals("The action completed, but afterSubmit failed: boom", error.message)
    }

    @Test
    fun `group items are identifiable and hashable`() {
        val engine =
            engine(
                """{"id":"f","title":"F","submit":{"label":"S"},"formatVersion":"1.1",
               "fields":[{"key":"g","type":"group","label":"G","fields":[{"key":"n","type":"text","label":"N"}]}]}""",
                """{"g":[{"n":"a"},{"n":"b"}]}""",
            )
        val items = engine.items("g")
        assertEquals(2, items.toSet().size)
        assertEquals(2, items.map { it.id }.toSet().size)
        assertEquals(items, engine.items("g"))
    }
}
