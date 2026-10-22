package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.warning
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonParseException
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.toJsonString
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.valueFields

public data class FormSnapshot(
    public val version: Int = CURRENT_VERSION,
    public val definitionId: String?,
    public val values: JsonObject,
    public val step: String?,
    public val skipped: List<String>,
    public val seed: String,
) {
    public fun toJson(): String {
        val json = MutableJsonObject()
        json["version"] = JsonValue.Number(version.toDouble())
        json["definitionId"] = definitionId?.let { JsonValue.String(it) } ?: JsonValue.Null
        json["values"] = JsonValue.Object(values)
        json["step"] = step?.let { JsonValue.String(it) } ?: JsonValue.Null
        json["skipped"] = JsonValue.Array(skipped.map { JsonValue.String(it) })
        json["seed"] = JsonValue.String(seed)
        return json.toJsonString()
    }

    public companion object {
        public const val CURRENT_VERSION: Int = 1

        public fun fromJson(text: String): FormSnapshot? {
            val json =
                try {
                    JsonValue.parse(text).objectValue
                } catch (_: JsonParseException) {
                    null
                } ?: return null
            val version = json["version"]?.numberValue?.takeIf { it % 1.0 == 0.0 } ?: return null
            val values = json["values"]?.objectValue ?: return null
            val skipped = json["skipped"]?.arrayValue ?: return null
            return FormSnapshot(
                version.toInt(),
                json["definitionId"]?.stringValue,
                values,
                json["step"]?.stringValue,
                skipped.map { it.stringValue ?: return null },
                json["seed"]?.stringValue ?: return null,
            )
        }
    }
}

public fun FormEngine.snapshot(): FormSnapshot =
    FormSnapshot(
        definitionId = definition.json["id"]?.stringValue,
        values = values,
        step = if (isMultiStep) currentStep?.id else null,
        skipped = core.skipped.map { it.id },
        seed = seed,
    )

internal fun FormSnapshot.matching(
    definition: FormDefinition,
    options: FormEngineOptions,
): FormSnapshot? {
    val id = definition.json["id"]?.stringValue
    val steps =
        definition.steps
            .orEmpty()
            .map { it.id }
            .toSet()
    val stepsKnown = (listOfNotNull(step) + skipped).all { it in steps }
    val sameForm = id == null || definitionId == null || id == definitionId
    if (version == FormSnapshot.CURRENT_VERSION && sameForm && stepsKnown) return this
    deliver(
        warning(
            IssueCode.SNAPSHOT_MISMATCH,
            "",
            "The saved progress does not match this form, so the form starts fresh",
            "Discard snapshots saved for another version of the form",
        ),
        options,
    )
    return null
}

internal fun EngineCore.restore(snapshot: FormSnapshot) {
    val steps = definition.steps.orEmpty()
    val cleared = values.copy()
    for (id in snapshot.skipped) {
        val keys = valueFields(steps.first { it.id == id }.fields).map { it.key }
        keys.forEach(cleared::remove)
        markSkipped(id, keys)
    }
    if (snapshot.skipped.isNotEmpty()) replaceValues(cleared)
    val target = snapshot.step ?: return visibility.markChanged(structureChanged = true, valuesChanged = true)
    val order = steps.indexOfFirst { it.id == target }
    val visible = visibleSteps
    val index = visible.indexOfFirst { it.id == target }
    currentStepIndex =
        if (index >= 0) index else maxOf(0, visible.count { step -> steps.indexOfFirst { it.id == step.id } < order } - 1)
    visibility.markChanged(structureChanged = true, valuesChanged = true)
}
