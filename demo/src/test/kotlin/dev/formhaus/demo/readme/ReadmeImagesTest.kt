package dev.formhaus.demo.readme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import dev.formhaus.compose.form.FormhausForm
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.goToStep
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.demo.Look
import dev.formhaus.demo.SampleScaffold
import dev.formhaus.demo.resourceJson
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val FRAME = "device-frame"
private const val IMAGE_WIDTH = 1098
private const val IMAGE_HEIGHT = 2262
private const val VALUES =
    """{"projectType": "new-site", "budget": "medium", "services": ["design", "development"], "urgency": 4}"""

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w464dp-h956dp-xxhdpi")
class ReadmeImagesTest(
    private val name: String,
    private val look: Look,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun record() {
        val assets = File(checkNotNull(System.getProperty("formhaus.readme.assets")) { "Run ./gradlew :demo:recordReadmeImages" })
        val model =
            FormModel(
                FormDefinition(resourceJson("/readme-project.json")),
                checkNotNull(JsonValue.parse(VALUES).objectValue),
            ) { _, _ -> SubmitResult.Ok }
        compose.setContent {
            MaterialTheme(lightColorScheme()) {
                DeviceFrame(Modifier.testTag(FRAME)) {
                    SampleScaffold(model.definition.title.orEmpty(), look, {}, {}, systemBars) { FormhausForm(model) }
                }
            }
        }
        compose.runOnIdle { model.goToStep("scope") }
        compose.waitForIdle()
        val capture = File(System.getProperty("java.io.tmpdir"), "formhaus-readme-$name.png")
        compose.onNodeWithTag(FRAME).captureRoboImage(capture.path)
        ImageIO.write(downscaled(ImageIO.read(capture)), "png", File(assets, "set-$name.png"))
        capture.delete()
    }

    private fun downscaled(source: BufferedImage): BufferedImage {
        val target = BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        graphics.drawImage(source.getScaledInstance(IMAGE_WIDTH, IMAGE_HEIGHT, Image.SCALE_AREA_AVERAGING), 0, 0, null)
        graphics.dispose()
        return target
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> =
            listOf(
                arrayOf("formhaus", Look.formhaus),
                arrayOf("material", Look.material),
                arrayOf("kiln", Look.kiln),
            )
    }
}
