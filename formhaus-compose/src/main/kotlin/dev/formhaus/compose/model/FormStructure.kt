package dev.formhaus.compose.model

import androidx.compose.runtime.Immutable
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.StepProgress
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.renderer.definitionRevision
import dev.formhaus.core.renderer.nodeId

@Immutable
public class FormStructure internal constructor(
    engine: FormEngine,
) {
    public val nodes: List<FormNode> = engine.visibleNodes
    public val steps: List<FormStep> = engine.visibleSteps
    public val currentStep: FormStep? = engine.currentStep
    public val stepIndex: Int = engine.currentStepIndex
    public val progress: StepProgress = engine.progress
    public val isMultiStep: Boolean = engine.isMultiStep
    public val isFirstStep: Boolean = engine.isFirstStep
    public val isLastStep: Boolean = engine.isLastStep
    internal val revision: Int = engine.definitionRevision
    private val signature: List<Int> = nodeSignature(nodes) + SECTION_BREAK + steps.map { it.nodeId }

    public val visibleFields: List<FormField>
        get() =
            nodes.flatMap { node ->
                when (node) {
                    is FormField -> listOf(node)
                    is FormSection -> node.fields.filterIsInstance<FormField>()
                }
            }

    override fun equals(other: Any?): Boolean =
        other is FormStructure &&
            revision == other.revision &&
            signature == other.signature &&
            stepIndex == other.stepIndex &&
            progress == other.progress &&
            currentStep?.nodeId == other.currentStep?.nodeId &&
            isLastStep == other.isLastStep

    override fun hashCode(): Int = 31 * revision + signature.hashCode()
}

private const val SECTION_END = -1
private const val SECTION_BREAK = -2

private fun nodeSignature(nodes: List<FormNode>): List<Int> =
    nodes.flatMap { node ->
        if (node is FormSection) listOf(node.nodeId) + nodeSignature(node.fields) + SECTION_END else listOf(node.nodeId)
    }
