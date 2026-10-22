package dev.formhaus.core.engine

import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.model.FormStep

public class GoToStepResult(
    public val stepId: String?,
    public val reached: Boolean,
    public val reason: StepRedirectReason?,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, GoToStepResult::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("GoToStepResult")

    private fun parts(): ValueParts = listOf("stepId" to stepId, "reached" to reached, "reason" to reason)
}

private fun redirect(
    known: Boolean,
    busy: Boolean,
    index: Int?,
    target: Int,
): StepRedirectReason? =
    when {
        !known -> StepRedirectReason.UNKNOWN_STEP
        index == null -> if (busy) StepRedirectReason.BLOCKED else StepRedirectReason.NOT_ON_PATH
        busy || index != target -> StepRedirectReason.BLOCKED
        else -> null
    }

private fun EngineCore.blocks(step: FormStep): Boolean = !isStepSkipped(step.id) && !fieldsErrors(step.fields).isEmpty()

internal fun FormEngine.redirectTo(stepId: String): GoToStepResult {
    val core = core
    val steps = core.visibleSteps
    val current = core.currentStepIndex
    val order = HashMap<String, Int>()
    core.definition.steps
        .orEmpty()
        .forEachIndexed { position, step -> order.putIfAbsent(step.id, position) }
    val requested = if (core.isMultiStep) order[stepId] else null
    val index = steps.indexOfFirst { it.id == stepId }.takeIf { it >= 0 }
    val busy = core.stepValidating || core.submitting
    var target = current
    if (requested != null && !busy) target = index ?: (steps.count { (order[it.id] ?: 0) < requested } - 1)
    if (current < target) (current until target).firstOrNull { core.blocks(steps[it]) }?.let { target = it }
    target = maxOf(target, 0)
    if (target != current) moveTo(target)
    val id = steps.getOrNull(target)?.id
    val reason = redirect(requested != null, busy, index, target)
    if (reason != null) core.emit(FormEvent.StepRedirected(stepId, id, reason))
    return GoToStepResult(id, reason == null, reason)
}
