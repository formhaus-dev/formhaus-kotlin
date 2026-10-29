package dev.formhaus.compose.gallery

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.formhaus.compose.fields.FieldList
import dev.formhaus.compose.fields.Themed
import dev.formhaus.compose.fields.UploadStore
import dev.formhaus.compose.fields.addFiles
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.model
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val SHEET = "sheet"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FileListGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f))

    @Test
    fun light() = sheet("light")

    @Test
    @Config(qualifiers = "+night")
    fun dark() = sheet("dark")

    private fun sheet(appearance: String) {
        val form =
            model(
                """{"id": "f", "title": "F", "formatVersion": "1.7", "submit": {"label": "Send"}, "fields": [
                   {"key": "docs", "type": "file", "label": "Receipts", "accept": ".pdf", "multiple": true,
                    "helperText": "Up to 3 files", "validation": {"required": true, "maxLength": 3}}]}""",
                """{"docs": ["https://cdn.example.com/files/w9-2025.pdf"]}""",
            )
        val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
        val files =
            listOf("taxi-receipt-october.pdf", "hotel-invoice-2026-10-12-berlin-mitte-conference-stay.pdf").map { name ->
                val source = File.createTempFile("formhaus-", ".pdf").apply { writeText(name) }
                requireNotNull(runBlocking { UploadStore.copy(cache, form.uploadOwner, { name }) { source.inputStream() } })
            }
        compose.setContent {
            Themed(modifier = Modifier.testTag(SHEET)) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) { FieldList(form) }
            }
        }
        compose.runOnIdle { FieldContext(form.structure.visibleFields.first(), form).addFiles(files, 0) }
        compose.waitForIdle()
        compose
            .onNodeWithTag(SHEET)
            .captureRoboImage("src/test/screenshots/file-list-$appearance.png", roborazziOptions = options)
        form.close()
    }
}
