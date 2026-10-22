package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.support.Load
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class GroupEditTest {
    private fun groups(
        count: Int,
        items: Int,
    ): String {
        val fields =
            (0 until count).joinToString(",") { index ->
                """{"key":"g$index","type":"group","label":"G","defaultItems":$items,"fields":[
                {"key":"a","type":"text","label":"A"},
                {"key":"b","type":"text","label":"B","show":[{"field":"a","neq":"hide"}]},
                {"key":"c","type":"text","label":"C","show":[{"field":"mode","eq":"full"}]}]}"""
            }
        return """{"id":"p","title":"P","submit":{"label":"S"},"fields":[{"key":"mode","type":"text","label":"M"},$fields]}"""
    }

    private fun FormEngine.item(
        group: String,
        index: Int,
        field: String,
    ): JsonValue? =
        values[group]
            ?.arrayValue
            ?.get(index)
            ?.objectValue
            ?.get(field)

    private val full = """{"mode":"full"}"""

    @Test
    fun `an item condition hides and restores only its own item`() {
        val engine = engine(groups(2, 3), full)
        engine.setItemValue("g0", 1, "b", text("kept"))
        engine.setItemValue("g0", 1, "a", text("hide"))
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("a", "c"), listOf("a", "b", "c")),
            engine.items("g0").map { item ->
                item.fields.map { it.key }
            },
        )
        assertNull(engine.item("g0", 1, "b"))
        engine.setItemValue("g0", 1, "a", text("show"))
        assertEquals(text("kept"), engine.item("g0", 1, "b"))
        assertTrue(engine.items("g1").all { it.fields.size == 3 })
    }

    @Test
    fun `an outer condition reaches every item of every group`() {
        val engine = engine(groups(2, 2), full)
        engine.setItemValue("g1", 0, "c", text("x"))
        engine.setValue("mode", text("short"))
        assertTrue(engine.items("g0").all { item -> item.fields.map { it.key } == listOf("a", "b") })
        assertNull(engine.item("g1", 0, "c"))
        assertTrue(engine.getSubmitValues()["g1"]?.arrayValue?.all { it.objectValue?.get("c") == null } == true)
        engine.setValue("mode", text("full"))
        assertEquals(text("x"), engine.item("g1", 0, "c"))
    }

    @Test
    fun `hidden item errors clear on the edited item`() {
        val engine = engine(groups(1, 2), full)
        engine.setErrors(errorsOf("""{"g0.0.b":"taken","g0.1.b":"taken"}"""))
        engine.setItemValue("g0", 0, "a", text("hide"))
        assertEquals(errorsOf("""{"g0.1.b":"taken"}"""), engine.errors)
    }

    @Test
    fun `field events report only the edited item`() {
        val events = ArrayList<FormEvent>()
        val engine = engine(groups(3, 4), full, FormEngineOptions(onFormEvent = { events.add(it) }))
        engine.start()
        engine.setItemValue("g2", 3, "a", text("hide"))
        engine.setItemValue("g2", 3, "a", text("x"))
        engine.setValue("mode", text("short"))
        val hidden = (0 until 3).flatMap { group -> (0 until 4).map { FormEvent.FieldHidden("g$group.$it.c") } }
        assertEquals(listOf(FormEvent.FieldHidden("g2.3.b"), FormEvent.FieldShown("g2.3.b")) + hidden, events.drop(1))
    }

    private fun fastest(
        runs: Int,
        block: () -> Unit,
    ): Long =
        (0 until runs).minOf {
            val start = System.nanoTime()
            block()
            System.nanoTime() - start
        }

    @Test
    fun `one edit costs the same with 1 or 64 large groups`() {
        assumeTrue(Load.heavy)
        val small = engine(groups(1, 1000), full)
        val large = engine(groups(64, 1000), full)
        var round = 0
        val smallTime = fastest(5) { small.setItemValue("g0", round++, "a", text("v$round")) }
        round = 0
        val largeTime = fastest(5) { large.setItemValue("g0", round++, "a", text("v$round")) }
        assertTrue(largeTime / smallTime.toDouble() < 8)
    }

    @Test
    fun `building and validating grow linearly with the item count`() {
        assumeTrue(Load.heavy)
        val small = fastest(3) { engine(groups(4, 1000), full).validate() }
        val large = fastest(3) { engine(groups(32, 1000), full).validate() }
        assertTrue(large / small.toDouble() < 16)
    }
}
