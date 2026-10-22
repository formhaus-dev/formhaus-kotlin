package dev.formhaus.compose.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.ownedFormModel
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.concurrent.thread

private val definition =
    FormDefinition(
        JsonValue.parse(
            """{"id": "d", "title": "D", "submit": {"label": "Send"}, "fields": [{"key": "x", "type": "text", "label": "X"}]}""",
        ),
    )

@RunWith(RobolectricTestRunner::class)
class AppDebuggableTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun offThreadWriteFails(
        debuggable: Boolean,
        debugChecks: Boolean? = null,
    ): Boolean {
        context.applicationInfo.flags =
            if (debuggable) {
                context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
            } else {
                context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            }
        val form =
            ownedFormModel(
                context,
                definition,
                MutableJsonObject(),
                FormEngineOptions(debugChecks = debugChecks),
                FormRendererOptions(),
            ) { _, _ -> null }
        var failed = false
        thread { failed = runCatching { form.engine.setValue("x", JsonValue.String("a")) }.isFailure }.join()
        form.close()
        return failed
    }

    @Test
    fun aDebuggableAppTurnsOnTheEngineThreadChecks() {
        assertTrue(offThreadWriteFails(debuggable = true))
    }

    @Test
    fun aReleaseAppLeavesThemOff() {
        assertFalse(offThreadWriteFails(debuggable = false))
    }

    @Test
    fun anExplicitChoiceOfTheAppWinsOverTheDebuggableFlag() {
        assertFalse(offThreadWriteFails(debuggable = true, debugChecks = false))
        assertTrue(offThreadWriteFails(debuggable = false, debugChecks = true))
    }
}
