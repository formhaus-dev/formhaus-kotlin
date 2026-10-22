package dev.formhaus.compose.containers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.LocalMeasuredFrames
import dev.formhaus.compose.components.MeasuredFrames
import dev.formhaus.compose.fields.Themed
import dev.formhaus.compose.form.BackPlacement
import dev.formhaus.compose.form.FormBehavior
import dev.formhaus.compose.form.FormhausForm
import dev.formhaus.compose.form.Provided
import dev.formhaus.compose.form.act
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.next
import dev.formhaus.compose.model.submit
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.compose.theme.FormhausTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

val flow =
    """
    {"id": "flow", "title": "Flow", "submit": {"label": "Book trip"},
     "steps": [
       {"id": "one", "title": "One", "fields": [{"key": "a", "type": "text", "label": "A"}]},
       {"id": "two", "title": "Two", "skip": {"label": "Skip for now"}, "fields": [{"key": "b", "type": "text", "label": "B"}]},
       {"id": "three", "title": "Three", "fields": [{"key": "c", "type": "text", "label": "C"}]}
     ]}
    """.trimIndent()

private fun invalidForm(): FormModel {
    val fields = (1..6).joinToString { """{"key": "f$it", "type": "text", "label": "Field $it", "validation": {"required": true}}""" }
    return model("""{"id": "s", "title": "S", "submit": {"label": "Send"}, "fields": [$fields]}""")
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h2400dp")
class ContainerLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val tokens = FormhausTokens(FormhausTheme.pointer, dark = false, fontScale = 1f, reduceMotion = false)

    private fun actionFrames(
        layout: ActionsLayout?,
        width: Dp,
        placement: BackPlacement = BackPlacement.AUTOMATIC,
    ): MeasuredFrames {
        val form = model(flow)
        val frames = MeasuredFrames()
        compose.setContent {
            CompositionLocalProvider(LocalMeasuredFrames provides frames, LocalActionsLayout provides layout) {
                Provided(behavior = FormBehavior(backPlacement = placement)) {
                    Themed(FormhausTheme.pointer, Modifier.width(width)) { FormActionsSlot(form) }
                }
            }
        }
        compose.act(form) { next() }
        return frames
    }

    private fun px(value: Dp): Float = with(compose.density) { value.toPx() }

    private fun assertStacked(placement: BackPlacement) {
        val frames = actionFrames(null, 390.dp, placement).frames
        val primary = requireNotNull(frames["actions/primary"])
        val back = requireNotNull(frames["actions/back"])
        assertEquals(px(tokens.spacing.s3), back.top - primary.bottom, 0.5f)
        assertEquals(px(390.dp), primary.width, 0.5f)
        assertEquals(primary.width, back.width, 0.5f)
    }

    @Test
    fun compactWidthStacksThePrimaryAboveAFullWidthBack() = assertStacked(BackPlacement.AUTOMATIC)

    @Test
    fun theBottomPlacementStacksTheSameWay() = assertStacked(BackPlacement.BOTTOM)

    @Test
    fun regularWidthPutsBackOnTheLeftAndThePrimaryAtItsOwnSizeOnTheRight() {
        val frames = actionFrames(ActionsLayout.ROW, 640.dp).frames
        val primary = requireNotNull(frames["actions/primary"])
        val back = requireNotNull(frames["actions/back"])
        assertEquals(back.center.y, primary.center.y, 0.5f)
        assertEquals(0f, back.left, 0.5f)
        assertEquals(px(640.dp), primary.right, 0.5f)
        assertTrue(primary.width < px(640.dp) / 2)
    }

    @Test
    fun theSummarySitsAfterTheFieldsAndBeforeTheActions() {
        val form = invalidForm()
        val frames = MeasuredFrames()
        compose.setContent { CompositionLocalProvider(LocalMeasuredFrames provides frames) { FormhausForm(form) } }
        compose.act(form) { submit() }
        val summary = requireNotNull(frames.frames["summary"])
        val first = requireNotNull(frames.frames["f1/field"])
        val last = requireNotNull(frames.frames["f6/field"])
        val primary = requireNotNull(frames.frames["actions/primary"])
        assertTrue(summary.top >= last.bottom)
        assertTrue(summary.bottom <= primary.top)
        assertTrue(first.top < last.top)
    }

    @Test
    fun focusingASummaryBelowTheFoldScrollsItFullyIntoView() {
        val form = invalidForm()
        val frames = MeasuredFrames()
        compose.setContent {
            CompositionLocalProvider(LocalMeasuredFrames provides frames) { Box(Modifier.height(500.dp)) { FormhausForm(form) } }
        }
        compose.act(form) { submit() }
        compose.waitForIdle()
        val summary = requireNotNull(frames.frames["summary"])
        assertTrue("$summary", summary.top >= 0f && summary.bottom <= px(500.dp) && summary.height > px(200.dp))
    }
}
