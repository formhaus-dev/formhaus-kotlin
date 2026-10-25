package dev.formhaus.compose.form

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.formhaus.compose.fields.RadioField
import dev.formhaus.compose.fields.definition
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.TestIds
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.submit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val tagged = FormRendererOptions(testIds = TestIds.Automatic)
private val revealing = FormBehavior(conditionalReveal = true)
private val traversalGroup = SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true)
private const val OPTIONS =
    """[{"value": "email", "label": "Email"}, {"value": "phone", "label": "Phone"}, {"value": "post", "label": "Post"}]"""
private const val MANY = 60
private const val LATE = 50

private val contact =
    definition(
        """{"key": "contact", "type": "radio", "label": "Contact", "validation": {"required": true}, "options": $OPTIONS}""",
        """{"key": "email", "type": "text", "label": "Email address", "show": [{"field": "contact", "eq": "email"}]}""",
        """{"key": "phone", "type": "text", "label": "Phone number", "show": [{"field": "contact", "eq": "phone"}]}""",
        """{"key": "note", "type": "text", "label": "Note"}""",
    )

private val channels =
    definition(
        """{"key": "channels", "type": "multiselect", "label": "Channels", "options": $OPTIONS}""",
        """{"key": "email", "type": "text", "label": "Email address", "show": [{"field": "channels", "contains": "email"}]}""",
        """{"key": "post", "type": "text", "label": "Postal address", "show": [{"field": "channels", "contains": "post"}]}""",
    )

private val nested =
    definition(
        """{"key": "contact", "type": "radio", "label": "Contact", "options": $OPTIONS}""",
        """{"key": "when", "type": "radio", "label": "When", "show": [{"field": "contact", "eq": "phone"}],
           "options": [{"value": "day", "label": "Day"}, {"value": "evening", "label": "Evening"}]}""",
        """{"key": "hour", "type": "text", "label": "Hour", "show": [{"field": "when", "eq": "evening"}]}""",
    )

private const val CONTACT_RADIO = """{"key": "contact", "type": "radio", "label": "Contact", "options": $OPTIONS}"""
private const val EMAIL_FIELD =
    """{"key": "email", "type": "text", "label": "Email address", "show": [{"field": "contact", "eq": "email"}]}"""

private val sectioned =
    """{"id": "f", "title": "F", "submit": {"label": "Send"}, "fields": [
       {"key": "about", "type": "section", "title": "About", "fields": [$CONTACT_RADIO, $EMAIL_FIELD]}]}"""

private val stepped =
    """{"id": "f", "title": "F", "submit": {"label": "Send"}, "steps": [
       {"id": "one", "title": "One", "fields": [$CONTACT_RADIO, $EMAIL_FIELD]},
       {"id": "two", "title": "Two", "fields": [{"key": "note", "type": "text", "label": "Note"}]}]}"""

private val manyOptions = (0 until MANY).joinToString { """{"value": "v$it", "label": "Pick $it"}""" }

private val many =
    definition(
        """{"key": "big", "type": "radio", "label": "Big", "options": [$manyOptions]}""",
        """{"key": "detail", "type": "text", "label": "Detail", "validation": {"required": true}, "show": [{"field": "big", "eq": "v$LATE"}]}""",
        """{"key": "after", "type": "text", "label": "After"}""",
    )

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class RevealTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        definition: String,
        values: String = "{}",
        behavior: FormBehavior = revealing,
        components: FormComponents = FormComponents.standard,
    ): FormModel {
        val form = model(definition, values, tagged)
        compose.setContent { Provided(components, behavior) { FormhausForm(form) } }
        compose.waitForIdle()
        return form
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun top(tag: String): Float = bounds(tag).top

    private fun bottom(tag: String): Float = bounds(tag).bottom

    private fun assertBetween(
        tag: String,
        above: String,
        below: String,
    ) {
        assertTrue("$tag after $above", top(tag) >= bottom(above))
        assertTrue("$tag before $below", bottom(tag) <= top(below))
    }

    private fun assertNestedIn(
        tag: String,
        parent: String,
    ) {
        compose.onNodeWithTag(tag).assert(hasAnyAncestor(hasTestTag(parent))).assert(hasAnyAncestor(traversalGroup))
    }

    private fun assertErrorBeforeOptions(components: FormComponents) {
        val form = show(contact, components = components)
        compose.act(form) { submit() }
        compose.waitForIdle()
        val error = compose.onNodeWithText("This field is required").fetchSemanticsNode()
        assertTrue(error.boundsInRoot.bottom <= top("f.contact.email"))
    }

    @Test
    fun revealedFieldsStayInPlaceByDefault() {
        show(contact, """{"contact": "email"}""", FormBehavior())
        compose.onNodeWithTag("f.email").assert(!hasAnyAncestor(hasTestTag("f.contact")))
        assertTrue(top("f.email") >= bottom("f.contact"))
    }

    @Test
    fun aRadioDrawsTheFieldOfTheCheckedOptionUnderIt() {
        val form = show(contact)
        compose.onNodeWithTag("f.email").assertDoesNotExist()
        compose.onNodeWithTag("f.contact.email").performClick()
        compose.waitForIdle()
        assertNestedIn("f.email", "f.contact")
        assertBetween("f.email", "f.contact.email", "f.contact.phone")
        compose.onNodeWithTag("f.note").assert(!hasAnyAncestor(hasTestTag("f.contact")))
        compose.onNodeWithTag("f.contact.phone").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("f.email").assertDoesNotExist()
        assertBetween("f.phone", "f.contact.phone", "f.contact.post")
        assertTrue(form.engine.values["phone"] == null)
    }

    @Test
    fun aMultiselectDrawsTheFieldsOfEveryCheckedOption() {
        show(channels, """{"channels": ["email", "post"]}""")
        assertNestedIn("f.email", "f.channels")
        assertNestedIn("f.post", "f.channels")
        assertBetween("f.email", "f.channels.email", "f.channels.phone")
        assertTrue(top("f.post") >= bottom("f.channels.post"))
    }

    @Test
    fun revealsNest() {
        show(nested, """{"contact": "phone", "when": "evening"}""")
        assertNestedIn("f.when", "f.contact")
        assertNestedIn("f.hour", "f.when")
        assertBetween("f.hour", "f.when.evening", "f.contact.post")
    }

    @Test
    fun theErrorOfAChoiceWithRevealsComesBeforeItsOptions() {
        assertErrorBeforeOptions(FormComponents.standard)
    }

    @Test
    fun aSectionDrawsTheRevealsOfItsFieldsUnderTheirOption() {
        show(sectioned, """{"contact": "email"}""")
        assertNestedIn("f.email", "f.contact")
        assertBetween("f.email", "f.contact.email", "f.contact.phone")
    }

    @Test
    fun aStepDrawsTheRevealsOfItsFieldsUnderTheirOption() {
        show(stepped, """{"contact": "email"}""")
        assertNestedIn("f.email", "f.contact")
        assertBetween("f.email", "f.contact.email", "f.contact.phone")
    }

    @Test
    fun theSystemSetShowsTheErrorOfAChoiceWithRevealsBeforeItsOptions() {
        assertErrorBeforeOptions(FormComponents.system)
    }

    @Test
    fun aCustomChoiceComponentKeepsTheFieldsInPlace() {
        show(contact, """{"contact": "email"}""", components = FormComponents.standard.field("radio") { RadioField(it) })
        compose.onNodeWithTag("f.email").assert(!hasAnyAncestor(hasTestTag("f.contact")))
    }

    @Test
    fun theSystemSetDrawsRevealsUnderItsOptions() {
        show(channels, """{"channels": ["email"]}""", components = FormComponents.system)
        assertNestedIn("f.email", "f.channels")
        assertBetween("f.email", "f.channels.email", "f.channels.phone")
    }

    @Test
    fun theSystemSetKeepsTheFieldsOfARadioDrawnAsAMenuInPlace() {
        show(contact, """{"contact": "email"}""", revealing.copy(inlineChoiceLimit = 2), FormComponents.system)
        compose.onNodeWithTag("f.email").assert(!hasAnyAncestor(hasTestTag("f.contact")))
    }

    @Test
    fun aLazyChoicePlacesTheRevealAfterItsOptionRowAndScrollsToItsErrors() {
        val form = show(many, """{"big": "v$LATE"}""", revealing.copy(errorSummary = false))
        compose.onNodeWithTag("f.detail").assertDoesNotExist()
        compose.act(form) { submit() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag("f.detail").assertIsDisplayed()
        compose.onNode(isFocused()).assert(hasAnyAncestor(hasTestTag("f.detail")))
        compose.onNodeWithTag("f.detail").assert(hasAnyAncestor(traversalGroup))
        compose.onNodeWithTag("f.form").performScrollToNode(hasTestTag("f.big.v${LATE + 1}"))
        assertBetween("f.detail", "f.big.v$LATE", "f.big.v${LATE + 1}")
    }
}
