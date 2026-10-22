package dev.formhaus.compose.platform

import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.HookBox
import dev.formhaus.compose.model.texts
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val definition =
    """
    {"formatVersion": "1.6", "id": "r", "title": "R", "lang": "de", "submit": {"label": "Send"},
     "fields": [
       {"key": "tools", "type": "multiselect", "label": "Tools",
        "options": [{"value": "a", "label": "Figma"}, {"value": "b", "label": "Sketch"}, {"value": "c", "label": "Penpot"}]},
       {"key": "why", "type": "text", "label": "Why {{tools}}?"}
     ]}
    """.trimIndent()

@RunWith(RobolectricTestRunner::class)
class DefaultRecallTest {
    @Test
    fun recallFormatsWithAndroidIcuByDefault() {
        val form =
            FormModel(
                FormDefinition(JsonValue.parse(definition)),
                requireNotNull(JsonValue.parse("""{"tools": ["a", "b", "c"]}""").objectValue),
            ) { _, _ -> null }
        val why = form.structure.visibleFields.first { it.key == "why" }
        assertEquals("Why Figma, Sketch und Penpot?", form.texts(why).label)
        form.close()
    }

    @Test
    fun theRendererPassesTheIcuFormatterUnlessTheAppSetsOne() {
        assertSame(IcuRecallFormatter, HookBox(FormEngineOptions()).forwarding(FormRendererOptions(), null, false).recallFormatter)
        val own = HookBox(FormEngineOptions(recallFormatter = IcuRecallFormatter)).forwarding(FormRendererOptions(lang = "de"), null, false)
        assertEquals("de", own.lang)
    }
}
