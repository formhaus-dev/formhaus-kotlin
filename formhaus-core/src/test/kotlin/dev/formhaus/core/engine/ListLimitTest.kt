package dev.formhaus.core.engine

import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.renderer.actionsState
import dev.formhaus.core.support.engine
import dev.formhaus.core.visibility.isActionDisabled
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@OptIn(RendererApi::class)
class ListLimitTest {
    private fun options(
        prefix: String,
        count: Int,
    ): String = (0 until count).joinToString(",", "[", "]") { """{"value":"$prefix$it","label":"$prefix$it"}""" }

    private fun picks(
        prefix: String,
        count: Int,
    ): String = (0 until count).joinToString(",", "[", "]") { "\"$prefix$it\"" }

    private val blocked = """[{"field":"choices","notContains":"o5"}]"""

    private fun branch(
        id: String,
        count: Int,
    ): String =
        """{"id":"$id","routes":[{"to":null}],"fields":[
        {"key":"tags","type":"multiselect","label":"Tags","options":${options("t", count)}},
        {"key":"note$id","type":"text","label":"Note","show":[{"field":"tags","contains":"t1"}]}]}"""

    private fun branchForm(): String =
        """{"id":"f","submit":{"label":"S"},"steps":[
        {"id":"pick","routes":[{"to":"a","show":[{"field":"path","eq":"a"}]},{"to":"b"}],
         "fields":[{"key":"path","type":"radio","label":"P","options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]}]},
        ${branch("a", 1500)},${branch("b", 1200)}]}"""

    @Test
    fun `next and submit guards use the static option count of the definition`(): Unit =
        runBlocking {
            val engine =
                engine(
                    """{"id":"f","submit":{"label":"S","disabled":$blocked},"steps":[
                {"id":"one","next":{"label":"Go","disabled":$blocked},
                 "fields":[{"key":"choices","type":"multiselect","label":"C","options":${options("o", 1001)}}]},
                {"id":"two","fields":[{"key":"b","type":"text","label":"B"}]}]}""",
                    """{"choices":${picks("o", 1001)}}""",
                )
            val next = engine.currentStep?.next?.action
            assertFalse(engine.isActionDisabled(next))
            assertFalse(engine.actionsState(null).primaryDisabled)
            assertTrue(isActionDisabled(next, engine.getSubmitValues()))
            assertTrue(engine.next())
            assertFalse(engine.isActionDisabled(engine.definition.submit))
            assertFalse(engine.actionsState(null).primaryDisabled)
        }

    @ParameterizedTest
    @CsvSource("a,true", "b,false")
    fun `a shared key takes the list limit of the branch on the path`(
        path: String,
        shown: Boolean,
    ): Unit =
        runBlocking {
            val engine = engine(branchForm(), """{"path":"$path","tags":${picks("t", 1300)}}""")
            assertTrue(engine.next())
            assertEquals(
                listOf("tags") + if (shown) listOf("note$path") else emptyList(),
                engine.visibleFields.map { it.key },
            )
        }
}
