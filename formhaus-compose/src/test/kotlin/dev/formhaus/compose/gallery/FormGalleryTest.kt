package dev.formhaus.compose.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.formhaus.compose.form.FormhausFormContent
import dev.formhaus.compose.form.Provided
import dev.formhaus.compose.form.act
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.ResolveFormhausTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FORM_SHEET = "form-sheet"

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1600dp-xhdpi")
class FormGalleryTest(
    private val entry: FormEntry,
) {
    @get:Rule
    val compose = createComposeRule()

    private val options by lazy { RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)) }

    @Test
    fun light() = capture("light")

    @Test
    @Config(qualifiers = "+night")
    fun dark() = capture("dark")

    private fun capture(appearance: String) {
        val form = entry.build()
        compose.setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(scheme) {
                Provided(entry.components, entry.behavior) {
                    ResolveFormhausTokens(entry.components.system) {
                        Box(Modifier.fillMaxWidth().testTag(FORM_SHEET).background(LocalFormhausTokens.current.colors.background)) {
                            FormhausFormContent(form, Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.act(form) { entry.prepare(this) }
        compose
            .onNodeWithTag(FORM_SHEET)
            .captureRoboImage("src/test/screenshots/${entry.name}-$appearance.png", roborazziOptions = options)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun entries(): List<Array<Any>> = formEntries.map { arrayOf(it) }
    }
}
