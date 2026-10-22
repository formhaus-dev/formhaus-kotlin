package dev.formhaus.core.scenario

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

class ScenarioTest {
    private fun deferNames(value: JsonValue?): List<String> = when (value) {
        is JsonValue.Array -> value.items.flatMap(::deferNames)
        is JsonValue.Object -> value.members.flatMap { (key, item) ->
            val name = item.stringValue
            if (key == "defer" && name != null) listOf(name) else deferNames(item)
        }
        else -> emptyList()
    }

    @TestFactory
    fun `scenario names are unique in every file`(): List<DynamicTest> = ScenarioFile.all.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val names = file.scenarios.map { it["name"]?.stringValue.orEmpty() }
            assertEquals(names.size, names.toSet().size)
        }
    }

    @TestFactory
    fun `uses each defer name once per scenario`(): List<DynamicTest> = ScenarioFile.all.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val repeated = file.scenarios.filter { scenario ->
                val names = deferNames(scenario["hooks"]) + deferNames(scenario["steps"])
                names.size != names.toSet().size
            }
            assertEquals(emptyList<String>(), repeated.map { it["name"]?.stringValue.orEmpty() })
        }
    }

    @TestFactory
    fun scenarios(): List<DynamicTest> = ScenarioFile.all.flatMap { file ->
        file.scenarios.map { scenario ->
            DynamicTest.dynamicTest("${file.name}: ${scenario["name"]?.stringValue.orEmpty()}") {
                val name = scenario["throws"]?.stringValue
                if (name != null) {
                    val values = scenario["values"]?.objectValue ?: MutableJsonObject()
                    val error = assertThrows<Throwable> { FormEngine(file.runtime(scenario), values) }
                    assertEquals(JsonValue.String(name), errorProjection(error, MutableJsonObject())["name"])
                    return@dynamicTest
                }
                val failures = runBlocking { ScenarioRun(file, scenario).run(this) }
                assertEquals("", failures.joinToString("\n"))
            }
        }
    }
}
