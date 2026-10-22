package dev.formhaus.compose.form

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.formhaus.compose.fields.definition
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.TestIds
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.next
import dev.formhaus.compose.model.submit
import dev.formhaus.compose.model.testId
import dev.formhaus.core.renderer.TestIdTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FIELDS = 30
private const val MISSING = 25
private const val PICTURES = 300
private val tagged = FormRendererOptions(testIds = TestIds.Automatic)
private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

private fun textField(index: Int): String =
    """{"key": "f$index", "type": "text", "label": "Field $index", "validation": {"required": true}}"""

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class LazyFormTest {
    @get:Rule
    val compose = createComposeRule()

    private fun longForm(vararg missing: Int): FormModel {
        val values = (0 until FIELDS).filter { it !in missing }.joinToString { "\"f$it\": \"x\"" }
        return model(definition(*Array(FIELDS, ::textField)), "{$values}", tagged)
    }

    private fun show(
        form: FormModel,
        behavior: FormBehavior = FormBehavior(),
    ) {
        compose.setContent { Provided(behavior = behavior) { FormhausForm(form) } }
        compose.waitForIdle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun aFailedSubmitScrollsToAndFocusesAnInvalidFieldThatIsNotComposed() {
        val form = longForm(MISSING)
        show(form, FormBehavior(errorSummary = false))
        compose.onNodeWithTag("f.f$MISSING").assertDoesNotExist()
        compose.act(form) { submit() }
        settle()
        compose.onNodeWithTag("f.f$MISSING").assertIsDisplayed()
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("f.f$MISSING")))
    }

    @Test
    fun theErrorSummaryScrollsIntoViewAfterAFailedSubmit() {
        val form = longForm(MISSING, MISSING + 2)
        show(form)
        compose.act(form) { submit() }
        settle()
        compose.onNodeWithTag(requireNotNull(form.testId(TestIdTarget.Kind.SUMMARY))).assertIsDisplayed()
    }

    @Test
    fun aBigChoiceFieldComposesOnlyTheRowsOnScreen() {
        val options =
            (0 until PICTURES).joinToString {
                """{"value": "v$it", "label": "Pick $it", "image": "https://example.invalid/$it.png"}"""
            }
        val form = model(definition("""{"key": "big", "type": "radio", "label": "Big", "options": [$options]}"""), options = tagged)
        show(form)
        val composed = compose.onAllNodes(isRadio).fetchSemanticsNodes().size
        assertTrue("$composed cards", composed in 1..PICTURES / 10)
        compose.onNodeWithTag("f.form").performScrollToNode(hasTestTag("f.big.v250"))
        compose.onNodeWithTag("f.big.v250").performClick()
        compose.waitForIdle()
        assertEquals("v250", form.engine.values["big"]?.stringValue)
    }

    private fun stepsBackToTheTop(mode: StepScroll) {
        val first = (0 until FIELDS).joinToString { textField(it).replace("true", "false") }
        val second = (0 until FIELDS).joinToString { textField(it).replace("\"f$it\"", "\"g$it\"") }
        val form =
            model(
                """{"id": "f", "title": "F", "submit": {"label": "Send"}, "steps": [
                {"id": "one", "title": "Step one", "fields": [$first]},
                {"id": "two", "title": "Step two", "fields": [$second]}]}""",
                options = tagged,
            )
        show(form, FormBehavior(stepScroll = mode))
        compose.onNodeWithTag("f.form").performScrollToNode(hasTestTag("f.f${FIELDS - 1}"))
        compose.act(form) { next() }
        settle()
        compose.onNodeWithTag("f.g0").assertIsDisplayed()
    }

    @Test
    fun aStepChangeScrollsALongFormBackToTheScreenTop() {
        stepsBackToTheTop(StepScroll.SCREEN_TOP)
    }

    @Test
    fun aStepChangeScrollsALongFormBackToTheFormTop() {
        stepsBackToTheTop(StepScroll.FORM_TOP)
    }
}
