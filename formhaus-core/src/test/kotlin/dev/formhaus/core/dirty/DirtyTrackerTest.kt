package dev.formhaus.core.dirty

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.jsonObject
import dev.formhaus.core.support.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirtyTrackerTest {
    private val form =
        """{"id":"f","title":"F","submit":{"label":"S"},"fields":[
          {"key":"a","type":"text","label":"A"},{"key":"z","type":"text","label":"Z"},
          {"key":"x","type":"custom","label":"X"}]}"""

    @Test
    fun `listeners run only when the dirty keys change`() {
        val engine = engine(form, """{"a":"1"}""")
        val tracker = engine.trackDirty()
        var calls = 0
        val unsubscribe = tracker.subscribe { calls += 1 }
        engine.setValue("a", text("2"))
        engine.setValue("a", text("3"))
        engine.setErrors(stringMapOf("a" to "bad"))
        assertEquals(1, calls)
        engine.reset(jsonObject("""{"a":"9"}"""))
        assertEquals(2, calls)
        assertFalse(tracker.dirty)
        unsubscribe()
        engine.setValue("a", text("4"))
        assertEquals(2, calls)
        assertTrue(tracker.dirty)
    }

    @Test
    fun `keys sort by code units`() {
        val engine = engine(form)
        val tracker = engine.trackDirty()
        engine.setValue("z", text("1"))
        engine.setValue("a", text("1"))
        assertEquals(listOf("a", "z"), tracker.dirtyKeys)
    }

    @Test
    fun `nested values compare structurally with numeric equality`() {
        val engine = engine(form, """{"x":{"b":[0,{"c":1}],"d":true}}""")
        val tracker = engine.trackDirty()
        engine.setValue("x", JsonValue.parse("""{"d":true,"b":[-0,{"c":1.0}]}"""))
        assertFalse(tracker.dirty)
        engine.setValue("x", JsonValue.parse("""{"d":true,"b":[0,{"c":2}]}"""))
        assertEquals(listOf("x"), tracker.dirtyKeys)
    }

    @Test
    fun `a stopped tracker keeps its last keys`() {
        val engine = engine(form)
        val tracker = engine.trackDirty()
        tracker.stop()
        engine.setValue("a", text("1"))
        assertFalse(tracker.dirty)
    }
}
