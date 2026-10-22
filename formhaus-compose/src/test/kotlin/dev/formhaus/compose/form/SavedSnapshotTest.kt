package dev.formhaus.compose.form

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import dev.formhaus.compose.model.FormModel
import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.renderer.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SAVED =
    """{"version":1,"definitionId":"trip","values":{"name":"Ada"},"step":"seat","skipped":[],"seed":""}"""

@RunWith(RobolectricTestRunner::class)
class SavedSnapshotTest {
    private val issues = mutableListOf<DefinitionIssue>()
    private val options = FormEngineOptions(onWarning = { if (it.code == IssueCode.SNAPSHOT_MISMATCH) issues += it })

    private fun restored(json: String): FormModel {
        val handle = SavedStateHandle(mapOf(SNAPSHOT_KEY to Bundle().apply { putString(SNAPSHOT_JSON, json) }))
        return FormHolder(handle).model(trip.source, null, options) { start ->
            FormModel(trip, engineOptions = options, restoring = start) { _, _ -> null }
        }
    }

    @Test
    fun aSavedSnapshotRestores() {
        assertEquals("Ada", restored(SAVED).engine.values["name"]?.stringValue)
        assertEquals(emptyList<IssueCode>(), issues.map { it.code })
    }

    @Test
    fun malformedOrMismatchedSavedJsonStartsFreshWithAWarning() {
        for (json in listOf("{", SAVED.replace("\"step\":\"seat\"", "\"step\":4"), SAVED.replace("\"definitionId\":\"trip\",", ""))) {
            issues.clear()
            assertNull(json, restored(json).engine.values["name"])
            assertEquals(json, listOf(IssueCode.SNAPSHOT_MISMATCH), issues.map { it.code })
        }
    }
}
