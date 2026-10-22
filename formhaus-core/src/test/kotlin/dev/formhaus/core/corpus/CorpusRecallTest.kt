package dev.formhaus.core.corpus

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.HiddenValues
import dev.formhaus.core.engine.RecallOptions
import dev.formhaus.core.i18n.textSlots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class CorpusRecallTest {
    private fun check(fixture: CorpusFixture, case: RecallCase, hiddenValues: HiddenValues) {
        val definition = fixture.runtime()
        val slots = textSlots(definition)
        val engine = FormEngine(definition, case.values, FormEngineOptions(onWarning = {}, hiddenValues = hiddenValues))
        for (entry in case.texts) {
            val slot = requireNotNull(slots.firstOrNull { it.address == entry.address }) { entry.address }
            val text = engine.recallText(slot.text, RecallOptions(entry.item, format = { answer, _, _ -> answer.stored }))
            assertEquals(entry.text, text, "${entry.address} ${entry.item?.index ?: ""}")
        }
    }

    private fun recalls(hiddenValues: HiddenValues): List<DynamicTest> = CorpusFixture.all.flatMap { fixture ->
        fixture.recall.map { case -> DynamicTest.dynamicTest("${fixture.name}: ${case.name}") { check(fixture, case, hiddenValues) } }
    }

    @TestFactory
    fun `recall case names are unique in every fixture`(): List<DynamicTest> = CorpusFixture.all.map { fixture ->
        DynamicTest.dynamicTest(fixture.name) {
            val names = fixture.recall.map { it.name }
            assertEquals(names.size, names.toSet().size)
        }
    }

    @TestFactory
    fun recall(): List<DynamicTest> = recalls(HiddenValues.CLEAR)

    @TestFactory
    fun `recall reads kept hidden values as unanswered`(): List<DynamicTest> = recalls(HiddenValues.KEEP)
}
