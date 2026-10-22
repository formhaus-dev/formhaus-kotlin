package dev.formhaus.core.scenario

import dev.formhaus.core.json.JsonValue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RunnerTest {
    private val file = file("""{"id":"f","title":"F","submit":{"label":"S"},"steps":[
        {"id":"one","title":"One","fields":[{"key":"a","type":"text","label":"A"}]},
        {"id":"two","title":"Two","fields":[{"key":"b","type":"text","label":"B"}]}]}""")

    private fun file(definition: String): ScenarioFile = ScenarioFile("runner", JsonValue.parse(definition), null, emptyList())

    private fun failures(scenario: String, on: ScenarioFile = file): List<String> = runBlocking {
        ScenarioRun(on, requireNotNull(JsonValue.parse(scenario).objectValue)).run(this)
    }

    @Test
    fun `passes a matching scenario`() {
        assertEquals(emptyList<String>(), failures("""{"name":"ok","steps":[{"do":"next","returns":true},{"expect":{"step":"two"}}]}"""))
    }

    @Test
    fun `fails on a wrong expectation`() {
        assertEquals(1, failures("""{"name":"x","steps":[{"expect":{"step":"two"}}]}""").size)
    }

    @Test
    fun `fails on unknown action, hook and expectation names`() {
        val result = failures("""{"name":"x","hooks":{"onSomething":[]},"steps":[{"do":"jump"},{"expect":{"color":"red"}}]}""")
        assertTrue("unknown hook onSomething" in result)
        assertTrue(result.any { "unknown action jump" in it })
        assertTrue("unknown expectation color" in result)
    }

    @Test
    fun `fails on unsettled actions and unresolved deferred replies`() {
        val result = failures("""{"name":"x","hooks":{"onBeforeStepChange":[{"defer":"gate"}]},"steps":[{"do":"next","await":false}]}""")
        assertEquals(listOf("unsettled action(s): step 0 next", "unresolved deferred reply(s): gate"), result)
    }

    @Test
    fun `fails when settle waits on a deferred reply`() {
        val result = failures(
            """{"name":"x","hooks":{"onBeforeStepChange":[{"defer":"gate"}]},"steps":[{"do":"next","await":false},{"do":"settle"}]}""",
        )
        assertEquals(listOf("step 0 next never settled"), result)
    }

    @Test
    fun `rejects resolving an unknown deferred reply`() {
        assertThrows<ScriptError> { failures("""{"name":"x","steps":[{"do":"resolve","deferred":"nope"}]}""") }
    }

    @Test
    fun `runs a deferred reply to completion after resolve`() {
        val result = failures(
            """{"name":"x","hooks":{"onBeforeStepChange":[{"defer":"gate","return":false}]},"steps":[
                {"do":"next","await":false,"returns":false},{"expect":{"stepValidating":true}},
                {"do":"resolve","deferred":"gate"},{"expect":{"stepValidating":false,"step":"one"}},{"do":"settle"}]}""",
        )
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `treats a null skip handler as no handler`() {
        val skippable = file("""{"id":"f","title":"F","submit":{"label":"S"},"steps":[
            {"id":"one","title":"One","skip":true,"fields":[{"key":"a","type":"text","label":"A"}]}]}""")
        assertEquals(emptyList<String>(), failures("""{"name":"x","steps":[{"do":"skip","handler":null,"returns":false}]}""", skippable))
    }

    @Test
    fun `coerces non-string onStepValidate errors like JavaScript String`() {
        val result = failures(
            """{"name":"x","hooks":{"onStepValidate":[{"return":{"a":5,"z":[1,2]}}]},
               "steps":[{"do":"next","returns":false},{"expect":{"errors":{"a":"5"},"topLevelErrors":["1,2"]}}]}""",
        )
        assertEquals(emptyList<String>(), result)
    }
}
