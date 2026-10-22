package dev.formhaus.core.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyOrderTest {
    @Test
    fun `array-index keys come first in numeric order, then insertion order`() {
        val map = MutableStringMap<Int>()
        for (key in listOf("a", "10", "2", "01", "4294967295", "4294967294", "-1", "0")) map[key] = 1
        assertEquals(listOf("0", "2", "10", "4294967294", "a", "01", "4294967295", "-1"), map.keys)
        map.remove("2")
        map["3"] = 1
        assertEquals(listOf("0", "3", "10", "4294967294", "a", "01", "4294967295", "-1"), map.keys)
    }

    @Test
    fun `insertion-order maps keep index keys where they were added`() {
        val map = MutableStringMap<Int>(insertionOrder = true)
        for (key in listOf("b", "2", "a", "1")) map[key] = 1
        assertEquals(listOf("b", "2", "a", "1"), map.keys)
    }

    @Test
    fun `copies are independent of the original`() {
        val original = mutableStringMapOf("a" to 1, "1" to 2)
        val copy = original.toMutableStringMap()
        copy["b"] = 3
        copy.remove("a")
        assertEquals(listOf("1", "a"), original.keys)
        assertEquals(listOf("1", "b"), copy.keys)
        assertEquals(stringMapOf("a" to 1, "1" to 2), original)
        assertEquals(stringMapOf("1" to 2, "a" to 1), original)
    }
}
