package dev.formhaus.core.corpus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class CorpusRevealTest {
    private val fixtures = CorpusFixture.all.filter { it.reveals != null }

    @Test
    fun `the corpus pins revealed fields`() {
        assertTrue(fixtures.isNotEmpty())
    }

    @TestFactory
    fun reveals(): List<DynamicTest> =
        fixtures.map { fixture ->
            DynamicTest.dynamicTest(fixture.name) { assertEquals(fixture.reveals, revealPointers(fixture.definition)) }
        }
}
