package dev.formhaus.benchmarks.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.formhaus.benchmarks.BenchmarkForms
import dev.formhaus.benchmarks.formatNanos
import dev.formhaus.benchmarks.median
import dev.formhaus.compose.form.FormhausForm
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.setValue
import dev.formhaus.core.json.JsonValue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val PICTURES = 3000

private fun setting(
    name: String,
    fallback: Int,
): Int = System.getenv(name)?.toIntOrNull() ?: fallback

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class RenderBenchmark {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val warmups = setting("FORMHAUS_RENDER_WARMUPS", 5)
    private val runs = setting("FORMHAUS_RENDER_RUNS", 20)
    private val pictures = BenchmarkForms.pictures(PICTURES)
    private lateinit var frame: Bitmap

    @Before
    fun enabled() {
        assumeTrue(System.getenv("FORMHAUS_RENDER_BENCHMARKS") == "1")
    }

    @Test
    fun render() {
        val rows =
            listOf(
                "render $PICTURES picture options" to sample { firstRender(pictures) },
                "select one of $PICTURES picture options" to sample(selection()),
                "render a 4-step form" to sample { firstRender(BenchmarkForms.inquiry) },
                "type one character into a text field" to sample(keystroke()),
            )
        val table =
            listOf(
                "Robolectric ${android.os.Build.VERSION.SDK_INT}, native graphics, 360 by 900 dp at xxhdpi, median of $runs runs after $warmups warmups",
                "",
                "| Benchmark | Compose |",
                "| --- | ---: |",
            ) + rows.map { (label, nanos) -> "| $label | ${formatNanos(nanos)} |" }
        val text = table.joinToString("\n")
        println(text)
        File("build/reports").apply { mkdirs() }.resolve("render-benchmarks.md").writeText(text + "\n")
    }

    private fun sample(measured: () -> Long): Double = median(List(warmups + runs) { measured().toDouble() }.drop(warmups))

    private fun show(model: FormModel?) {
        compose.runOnUiThread { compose.activity.setContent { if (model != null) FormhausForm(model) } }
        compose.waitForIdle()
    }

    private fun draw() {
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            if (!::frame.isInitialized) frame = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(frame))
        }
    }

    private fun timed(action: () -> Unit): Long {
        val start = System.nanoTime()
        action()
        compose.waitForIdle()
        draw()
        return System.nanoTime() - start
    }

    private fun firstRender(definition: String): Long {
        show(null)
        return timed { show(formModel(definition)) }
    }

    private fun selection(): () -> Long {
        val model = formModel(pictures)
        show(model)
        draw()
        var index = 0
        return {
            index += 1
            timed { compose.runOnUiThread { model.setValue("big", JsonValue.String("v${index % 2 + 5}")) } }
        }
    }

    private fun keystroke(): () -> Long {
        show(formModel(BenchmarkForms.inquiry, """{"projectType": "redesign"}"""))
        val field = compose.onAllNodes(hasSetTextAction())[0]
        field.performClick()
        compose.waitForIdle()
        draw()
        return { timed { field.performTextInput("a") } }
    }
}
