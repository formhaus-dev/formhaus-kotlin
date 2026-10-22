package dev.formhaus.demo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SampleRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the sample definitions have no issues`() {
        listOf(SampleDefinitions.contact, SampleDefinitions.trip).forEach { text ->
            assertTrue(checkDefinition(JsonValue.parse(text)).isEmpty())
        }
    }

    @Test
    fun `each sample opens from the list and renders its form`() {
        compose.setContent { DemoApp() }
        samples.forEach { sample ->
            compose.onNodeWithTag("sample.${sample.id}").performClick()
            compose.onNodeWithTag("${sample.definition.id}.form").assertExists()
            compose.onNodeWithText(firstLabels.getValue(sample.id), substring = true).assertExists()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
    }

    private val firstLabels =
        mapOf(
            "contact" to "Name",
            "trip" to "Destination",
            "translated" to "E-Mail",
            "system" to "Destination",
            "custom" to "Name",
        )
}
