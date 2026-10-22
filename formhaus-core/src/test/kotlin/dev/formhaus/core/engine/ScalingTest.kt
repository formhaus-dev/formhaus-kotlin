package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.server.validateSubmission
import dev.formhaus.core.support.Load
import dev.formhaus.core.support.definition
import dev.formhaus.core.support.engine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ScalingTest {
    private fun growth(
        runs: Int = 3,
        short: () -> Unit,
        long: () -> Unit,
    ): Double {
        var fastShort = Long.MAX_VALUE
        var fastLong = Long.MAX_VALUE
        repeat(runs) {
            fastShort = minOf(fastShort, measure(short))
            fastLong = minOf(fastLong, measure(long))
        }
        return fastLong / fastShort.toDouble()
    }

    private fun measure(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun array(count: Int): String = List(count) { "1" }.joinToString(",", "[", "]")

    private fun objectText(count: Int): String = (count - 1 downTo 0).joinToString(",", "{", "}") { "\"$it\":1,\"k$it\":1" }

    private class Form(
        val definition: String,
        val values: String,
    )

    private fun form(count: Int): Form {
        val fields =
            (0 until count).joinToString(",") { index ->
                val show = if (index > 0) ""","show":[{"field":"f${index - 1}","notEmpty":true}]""" else ""
                """{"key":"f$index","type":"text","label":"F"$show}"""
            }
        val values = (0 until count).joinToString(",", "{", "}") { "\"f$it\":\"v\"" }
        return Form("""{"id":"f","title":"F","submit":{"label":"S"},"fields":[$fields]}""", values)
    }

    @Test
    fun `a 200k-element array parses to every element`() {
        val items = requireNotNull(JsonValue.parse(array(200_000)).arrayValue)
        assertEquals(200_000, items.size)
        assertTrue(items.all { it == JsonValue.Number(1) })
    }

    @Test
    fun `80k descending numeric keys parse in ascending order before named keys`() {
        val parsed = requireNotNull(JsonValue.parse(objectText(80_000)).objectValue)
        assertEquals((0 until 80_000).map(Int::toString) + (79_999 downTo 0).map { "k$it" }, parsed.keys)
        assertEquals(JsonValue.Number(1), parsed["79999"])
    }

    @Test
    fun `a million array elements parse in linear time`() {
        assumeTrue(Load.heavy)
        val short = array(125_000)
        val long = array(1_000_000)
        assertTrue(growth(short = { JsonValue.parse(short) }, long = { JsonValue.parse(long) }) < 32)
    }

    @Test
    fun `a hundred thousand object keys parse in linear time`() {
        assumeTrue(Load.heavy)
        val short = objectText(6_250)
        val long = objectText(50_000)
        assertTrue(growth(short = { JsonValue.parse(short) }, long = { JsonValue.parse(long) }) < 32)
    }

    @Test
    fun `parsed objects keep JavaScript key order and the first position of duplicate keys`() {
        val parsed =
            requireNotNull(
                JsonValue.parse("""{"b":1,"2":1,"a":1,"10":1,"01":1,"2":3,"4294967295":1,"1":1}""").objectValue,
            )
        assertEquals(listOf("1", "2", "10", "b", "a", "01", "4294967295"), parsed.keys)
        assertEquals(JsonValue.Number(3), parsed["2"])
        val copy = parsed.toMutableStringMap()
        copy["0"] = JsonValue.Number(1)
        copy["c"] = JsonValue.Number(1)
        assertEquals(listOf("0", "1", "2", "10", "b", "a", "01", "4294967295", "c"), copy.keys)
    }

    @Test
    fun `2,000 chained fields validate without errors`() {
        val form = form(2_000)
        val result = validateSubmission(definition(form.definition), JsonValue.parse(form.values))
        assertTrue(result.errors.isEmpty())
        assertEquals(2_000, result.values.size)
    }

    @Test
    fun `validateSubmission scales linearly with the field count`() {
        assumeTrue(Load.heavy)
        val short = form(250)
        val long = form(2_000)
        val ratio =
            growth(
                short = { validateSubmission(definition(short.definition), JsonValue.parse(short.values)) },
                long = { validateSubmission(definition(long.definition), JsonValue.parse(long.values)) },
            )
        assertTrue(ratio < 32)
    }

    @ParameterizedTest
    @CsvSource("1e6,100", "2,2")
    fun `defaultItems without maxLength stops at 100 items`(
        count: String,
        expected: Int,
    ) {
        val engine =
            engine(
                """{"id":"f","title":"F","submit":{"label":"S"},"fields":[
                {"key":"g","type":"group","label":"G","defaultItems":$count,"fields":[{"key":"a","type":"text","label":"A"}]}]}""",
            )
        assertEquals(expected, engine.values["g"]?.arrayValue?.size)
    }
}
