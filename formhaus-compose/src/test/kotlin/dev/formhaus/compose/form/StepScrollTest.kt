package dev.formhaus.compose.form

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.model.FocusRequest
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.TestIds
import dev.formhaus.compose.model.back
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.next
import dev.formhaus.compose.model.setValue
import dev.formhaus.compose.model.submit
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StepScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private fun stepsForm(options: FormRendererOptions = FormRendererOptions()): FormModel = model(textSteps, """{"a": "x"}""", options)

    @Test
    fun aStepChangeTargetsTheScreenTopByDefaultAndTheFormTopOnRequest() {
        val form = stepsForm()
        compose.setContent { FormFields(form) }
        val before = ScrollTrigger(form)
        compose.act(form) { next() }
        val after = ScrollTrigger(form)
        assertEquals("two", form.structure.currentStep?.id)
        assertEquals(StepScroll.SCREEN_TOP, FormBehavior().stepScroll)
        assertEquals(ScrollTarget.ScreenTop, ScrollTarget.resolve(before, after, StepScroll.SCREEN_TOP))
        assertEquals(ScrollTarget.FormTop, ScrollTarget.resolve(before, after, StepScroll.FORM_TOP))
        assertNull(ScrollTarget.resolve(after, after, StepScroll.SCREEN_TOP))
    }

    @Test
    fun focusOnAnInvalidFieldWinsOverTheTopOnAStepChange() {
        val form = stepsForm()
        compose.setContent { FormFields(form) }
        compose.act(form) { next() }
        compose.runOnIdle { form.setValue("a", JsonValue.String("")) }
        val before = ScrollTrigger(form)
        compose.act(form) { submit() }
        val after = ScrollTrigger(form)
        assertEquals("one", form.structure.currentStep?.id)
        assertEquals(ScrollTarget.Field("a"), ScrollTarget.resolve(before, after, StepScroll.SCREEN_TOP))
        assertEquals(ScrollTarget.Field("a"), ScrollTarget.resolve(before, after, StepScroll.FORM_TOP))
    }

    @Test
    fun aSummaryFocusTargetsTheSummaryOnlyWhileItIsShown() {
        val request = FocusRequest("a", 1, summary = true)
        assertEquals(ScrollTarget.Summary, ScrollTarget.focus(request, summary = true))
        assertEquals(ScrollTarget.Field("a"), ScrollTarget.focus(request, summary = false))
        assertNull(ScrollTarget.focus(FocusRequest(null, 2, summary = false), summary = true))
    }

    private fun requests(behavior: FormBehavior = FormBehavior()): Pair<FormModel, TopRequests> {
        val form = stepsForm()
        val top = TopRequests()
        compose.setContent {
            Provided(behavior = behavior) { CompositionLocalProvider(LocalTopRequests provides top) { FormhausFormContent(form) } }
        }
        compose.waitForIdle()
        return form to top
    }

    @Test
    fun formhausFormContentAsksTheOwningScrollViewForItsTopOnNextAndBack() {
        val (form, top) = requests()
        compose.act(form) { next() }
        assertEquals(1, top.issued)
        compose.act(form) { back() }
        assertEquals(2, top.issued)
    }

    @Test
    fun anErrorFocusOnAStepChangeAsksForNoTop() {
        val (form, top) = requests()
        compose.act(form) { next() }
        compose.runOnIdle { form.setValue("a", JsonValue.String("")) }
        compose.act(form) { submit() }
        assertEquals("one", form.structure.currentStep?.id)
        assertEquals(1, top.issued)
    }

    @Test
    fun theFormTopOptionKeepsTheScrollInsideTheForm() {
        val (form, top) = requests(FormBehavior(stepScroll = StepScroll.FORM_TOP))
        compose.act(form) { next() }
        assertEquals(0, top.issued)
    }

    private fun hostedOffset(mode: StepScroll): Float {
        val form = stepsForm()
        val scroll = ScrollState(0)
        compose.setContent {
            Provided(behavior = FormBehavior(stepScroll = mode)) {
                Box(Modifier.height(400.dp)) {
                    Column(Modifier.verticalScroll(scroll)) {
                        Spacer(Modifier.height(900.dp))
                        FormhausFormContent(form)
                        Spacer(Modifier.height(900.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val start = with(compose.density) { 1200.dp.toPx() }
        compose.runOnIdle { scroll.dispatchRawDelta(start) }
        compose.waitForIdle()
        assertEquals(start, scroll.value.toFloat(), 1f)
        compose.act(form) { next() }
        return with(compose.density) { scroll.value.toDp().value }
    }

    @Test
    fun aHostedFormScrollsTheHostScrollViewBackToItsVeryTop() {
        assertEquals(0f, hostedOffset(StepScroll.SCREEN_TOP), 1f)
    }

    @Test
    fun theFormTopOptionScrollsAHostedFormOnlyToItsOwnTop() {
        val offset = hostedOffset(StepScroll.FORM_TOP)
        assertTrue("offset $offset", offset > 850 && offset < 950)
    }

    @Test
    fun formhausFormScrollsItsOwnScrollViewBackToItsVeryTop() {
        val form = stepsForm(FormRendererOptions(testIds = TestIds.Automatic))
        compose.setContent { Box(Modifier.height(120.dp)) { FormhausForm(form) } }
        compose.waitForIdle()
        val node = compose.onNodeWithTag("s.form")
        val range = { node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange] }
        compose.runOnIdle {
            node
                .fetchSemanticsNode()
                .config[SemanticsActions.ScrollBy]
                .action
                ?.invoke(0f, 60f)
        }
        compose.waitForIdle()
        assertTrue(range().value() > 0f)
        compose.act(form) { next() }
        assertEquals(0f, range().value(), 0.5f)
    }
}
