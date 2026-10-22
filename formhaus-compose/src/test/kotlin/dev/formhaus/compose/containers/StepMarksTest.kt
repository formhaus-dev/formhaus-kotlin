package dev.formhaus.compose.containers

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.fields.Themed
import dev.formhaus.compose.form.ProgressContext
import dev.formhaus.compose.form.StepStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val STEPS = 10
private val viewport = 240.dp

@RunWith(RobolectricTestRunner::class)
class StepMarksTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theCurrentStepMarkScrollsIntoView() {
        val steps = (1..STEPS).map { StepStatus("s$it", "Step number $it", 0, null) }
        val current = mutableIntStateOf(1)
        compose.setContent {
            Themed(modifier = Modifier.width(viewport)) {
                StepProgressView(ProgressContext(current.intValue, STEPS, "Step ${current.intValue}", null, null, steps))
            }
        }
        for (step in listOf(STEPS, STEPS / 2, 1)) {
            compose.runOnIdle { current.intValue = step }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            val bounds = compose.onNode(isSelected() and hasText("Step number $step")).getUnclippedBoundsInRoot()
            assertTrue("step $step at $bounds", bounds.left >= 0.dp && bounds.right <= viewport)
        }
    }
}
