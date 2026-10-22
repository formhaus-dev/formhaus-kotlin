package dev.formhaus.benchmarks.compose

import dev.formhaus.benchmarks.BenchmarkForms
import dev.formhaus.benchmarks.parseJson
import dev.formhaus.core.definitionvalidation.checkDefinition
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkFormsTest {
    @Test
    fun `the benchmark definitions have no issues`() {
        listOf(BenchmarkForms.inquiry, BenchmarkForms.recall, BenchmarkForms.pictures(3), BenchmarkForms.textFields(3)).forEach { text ->
            assertEquals(emptyList<Any>(), checkDefinition(parseJson(text)))
        }
    }
}
