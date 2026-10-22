package dev.formhaus.benchmarks.compose

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.formhaus.benchmarks.BenchmarkForms
import dev.formhaus.compose.form.FormhausForm
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val FIELDS = 20
private const val KEYSTROKES = 3
private const val FIELD_COMPOSABLE = "dev.formhaus.compose.form.FormhausField"
private const val INPUT_COMPOSABLE = "dev.formhaus.compose.fields.TextInputField"
private const val RENDERER_PACKAGE = "dev.formhaus.compose."
private const val RENDERER_CALLS_PER_KEYSTROKE = 40
private const val SETTLE_MILLIS = 1_000L

@RunWith(RobolectricTestRunner::class)
class RecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a keystroke recomposes only the field it types into`() {
        val model = formModel(BenchmarkForms.textFields(FIELDS))
        compose.setContent { FormhausForm(model) }
        val field = compose.onAllNodes(hasSetTextAction())[FIELDS / 2]
        field.performClick()
        field.performTextInput("a")
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        val counts =
            CompositionCounter().record {
                repeat(KEYSTROKES) {
                    field.performTextInput("a")
                    compose.waitForIdle()
                }
            }
        val fields = counts[FIELD_COMPOSABLE] ?: 0
        val inputs = counts[INPUT_COMPOSABLE] ?: 0
        val renderer = counts.filterKeys { it.startsWith(RENDERER_PACKAGE) }.values.sum()
        assertTrue("$fields field recompositions", fields in 1..KEYSTROKES)
        assertTrue("$inputs input recompositions", inputs in 1..KEYSTROKES)
        assertTrue("$renderer renderer calls", renderer <= RENDERER_CALLS_PER_KEYSTROKE * KEYSTROKES)
    }
}
