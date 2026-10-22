package dev.formhaus.compose.form

import android.os.Bundle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import dev.formhaus.compose.fields.TextInputField
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.setValue
import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import kotlinx.coroutines.isActive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class RetainedOwnerTest {
    private val seen = LinkedHashSet<FormModel>()
    private val issues = mutableListOf<DefinitionIssue>()
    private val options = FormEngineOptions(onWarning = { issues += it })
    private val shown = mutableStateOf(true)
    private val ticks = mutableIntStateOf(0)

    private val capturing: FormComponents =
        FormComponents.standard.field("text") { context ->
            seen += context.model
            TextInputField(context)
        }

    @After
    fun clear() {
        HostActivity.content = {}
    }

    private fun launch(
        definitions: List<FormDefinition> = listOf(trip),
        saved: Bundle? = null,
    ): ActivityController<HostActivity> {
        HostActivity.content = {
            ticks.intValue
            Provided(capturing) {
                if (shown.value) definitions.forEach { FormhausForm(it, engineOptions = options) { _, _ -> null } }
            }
        }
        val controller = Robolectric.buildActivity(HostActivity::class.java)
        if (saved == null) controller.setup() else controller.setup(saved)
        settle()
        return controller
    }

    @Test
    fun removingAndAddingTheFormWithinTheSameOwnerKeepsItsState() {
        launch()
        val model = seen.last()
        model.setValue("name", JsonValue.String("Ada"))
        shown.value = false
        settle()
        shown.value = true
        settle()
        assertSame(model, seen.last())
        assertEquals("Ada", model.engine.values["name"]?.stringValue)
        assertTrue(model.scope.isActive)
    }

    @Test
    fun clearingTheOwnerReleasesTheModel() {
        val controller = launch()
        val model = seen.last()
        controller.pause().stop().destroy()
        assertFalse(model.scope.isActive)
    }

    @Test
    fun twoFormsInALoopGetTheirOwnModels() {
        val other = FormDefinition(json(tripJson.replace("\"id\": \"trip\"", "\"id\": \"other\"")))
        for (definitions in listOf(listOf(trip, trip), listOf(trip, other))) {
            seen.clear()
            val controller = launch(definitions)
            val first = seen.toList()
            assertEquals(2, first.size)
            assertNotSame(first[0], first[1])
            repeat(3) {
                ticks.intValue += 1
                settle()
            }
            assertEquals(first, seen.toList())
            assertTrue(first.all { it.scope.isActive })
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun savedStateHoldsTheLatestEditWithoutWaitingForAFrame() {
        val controller = launch()
        seen.last().setValue("name", JsonValue.String("Ada"))
        seen.last().setValue("name", JsonValue.String("Ada Lovelace"))
        val saved = Bundle()
        controller.saveInstanceState(saved)
        seen.clear()
        launch(saved = saved)
        assertEquals(
            "Ada Lovelace",
            seen
                .last()
                .engine.values["name"]
                ?.stringValue,
        )
    }

    @Test
    fun aSnapshotOverTheLimitIsNotSavedAndReportsAWarning() {
        val controller = launch()
        seen.last().setValue("name", JsonValue.String("x".repeat(MAX_SAVED_SNAPSHOT_BYTES)))
        val saved = Bundle()
        controller.saveInstanceState(saved)
        assertEquals(1, issues.count { it.code == IssueCode.SNAPSHOT_TOO_LARGE })
        seen.clear()
        launch(saved = saved)
        assertNull(seen.last().engine.values["name"])
    }
}
