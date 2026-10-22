package dev.formhaus.compose.containers

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
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
private const val CURRENT = 7
private val viewport = 240.dp
private val steps = (1..STEPS).map { StepStatus("s$it", "Step number $it", 0, null) }

@RunWith(RobolectricTestRunner::class)
class StepMarksTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stepMarksWrapInsideTheWidth() {
        show(fontScale = 1f)
        val bounds = steps.map { compose.onNode(hasText(it.title)).getUnclippedBoundsInRoot() }
        assertTrue(bounds.all { it.left >= 0.dp && it.right <= viewport })
        assertTrue(bounds.last().top > bounds.first().top)
        compose.onNode(isSelected() and hasText("Step number $CURRENT")).assertExists()
    }

    @Test
    fun largeTextShowsOnlyTheCurrentMark() {
        show(fontScale = 1.5f)
        compose.onAllNodesWithText("Step number", substring = true).assertCountEquals(1)
        compose.onNode(isSelected() and hasText("Step number $CURRENT")).assertExists()
    }

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Themed(modifier = Modifier.width(viewport)) {
                    StepProgressView(ProgressContext(CURRENT, STEPS, "Step $CURRENT of $STEPS", null, null, steps))
                }
            }
        }
    }
}
