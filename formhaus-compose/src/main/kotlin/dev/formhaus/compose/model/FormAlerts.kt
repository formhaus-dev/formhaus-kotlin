package dev.formhaus.compose.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.renderer.ErrorGroup
import dev.formhaus.core.renderer.ErrorSummaryOptions
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.renderer.errorSummary
import dev.formhaus.core.renderer.errorsVersion

@Stable
internal class FormAlerts(
    private val engine: FormEngine,
) {
    var topLevelErrors: List<String> by mutableStateOf(emptyList())
        private set
    var errorGroups: List<ErrorGroup> by mutableStateOf(emptyList())
        private set
    var stepErrorCounts: Map<String, Int> by mutableStateOf(emptyMap())
        private set
    var actionError: String? by mutableStateOf(null)
        private set
    var alertVersion: Int by mutableIntStateOf(0)
        private set
    var announcement: Announcement? by mutableStateOf(null)
        private set

    private var lastErrors: FieldErrors = engine.errors
    private var seenErrors = -1
    private var lastStructureVersion = -1
    private var lastValuesVersion = -1

    fun refresh(
        labels: LabelSource?,
        structureVersion: Int,
        valuesVersion: Int,
    ) {
        topLevelErrors = engine.topLevelErrors
        if (engine.errorsVersion != seenErrors) {
            seenErrors = engine.errorsVersion
            if (topLevelErrors.isNotEmpty()) alertVersion += 1
        }
        val errors = engine.errors
        val recallChanged = !errors.isEmpty() && valuesVersion != lastValuesVersion
        val errorsChanged = errors != lastErrors || errorGroups.isEmpty() != errors.isEmpty()
        if (errorsChanged || structureVersion != lastStructureVersion || recallChanged) {
            lastErrors = errors
            lastStructureVersion = structureVersion
            lastValuesVersion = valuesVersion
            errorGroups = engine.errorSummary(ErrorSummaryOptions(labels = labels)).groups
            val stepErrors = engine.stepErrors
            stepErrorCounts = stepErrors.keys.associateWith { stepErrors[it].orEmpty().size }
        }
    }

    fun reportActionError(message: String?) {
        actionError = message
        if (message != null) alertVersion += 1
    }

    fun announce(text: String) {
        announcement = Announcement(text, (announcement?.id ?: 0) + 1)
    }
}

internal data class Announcement(
    val text: String,
    val id: Int,
)
