package dev.formhaus.compose.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.formhaus.compose.components.OptionImages
import dev.formhaus.compose.fields.BuiltInField
import dev.formhaus.compose.fields.Themed
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.focus
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SHEET = "sheet"

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class FieldGalleryTest(
    private val entry: GalleryEntry,
) {
    @get:Rule
    val compose = createComposeRule()

    private val options by lazy { RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)) }

    @Before
    fun loadPictures() {
        runBlocking { OptionImages.image(SWATCH) }
    }

    @Test
    fun light() = sheet("light")

    @Test
    @Config(qualifiers = "+night")
    fun dark() = sheet("dark")

    @Test
    fun focused() = focusedSheet("focused")

    @Test
    @Config(qualifiers = "+night")
    fun focusedDark() = focusedSheet("focused-dark")

    private fun focusedSheet(name: String) {
        val form = stateModel(entry, FieldStateKind.FILLED)
        render { Row("focused", form) }
        compose.runOnIdle {
            form.focus(
                form.structure.visibleFields
                    .first()
                    .key,
            )
        }
        compose.waitForIdle()
        capture(name)
    }

    private fun sheet(appearance: String) {
        val rows = entry.states.map { it.name.lowercase() to stateModel(entry, it) }
        render { rows.forEach { (name, form) -> Row(name, form) } }
        capture(appearance)
    }

    private fun render(rows: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            Themed(modifier = Modifier.testTag(SHEET)) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) { rows() }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(appearance: String) {
        compose
            .onNodeWithTag(SHEET)
            .captureRoboImage("src/test/screenshots/${entry.name}-$appearance.png", roborazziOptions = options)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun entries(): List<Array<Any>> = galleryEntries.map { arrayOf(it) }
    }
}

@androidx.compose.runtime.Composable
private fun Row(
    name: String,
    form: FormModel,
) {
    val tokens = LocalFormhausTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = tokens.textStyle(TextRole.caption).copy(fontSize = 11.sp))
        BuiltInField(FieldContext(form.structure.visibleFields.first(), form))
    }
}
