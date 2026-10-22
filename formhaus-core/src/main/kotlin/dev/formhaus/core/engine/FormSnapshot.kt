package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.warning
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonParseException
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.toJsonString
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.valueFields

public class FormSnapshot internal constructor(
    public val version: Int,
    public val definitionId: String?,
    public val values: JsonObject,
    public val step: String?,
    public val skipped: List<String>,
    public val seed: String,
) {
    override fun equals(other: Any?): Boolean =
        other is FormSnapshot &&
            version == other.version &&
            definitionId == other.definitionId &&
            values == other.values &&
            step == other.step &&
            skipped == other.skipped &&
            seed == other.seed

    override fun hashCode(): Int = listOf(version, definitionId, values, step, skipped, seed).hashCode()

    override fun toString(): String = "FormSnapshot(${toJson()})"

    public fun withValues(values: JsonObject): FormSnapshot = FormSnapshot(version, definitionId, values, step, skipped, seed)

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
            val definitionId = json.optionalString("definitionId") ?: return null
            val step = json.optionalString("step") ?: return null
            return FormSnapshot(
                version.toInt(),
                definitionId.value,
                values,
                step.value,
                skipped.map { it.stringValue ?: return null },
                json["seed"]?.stringValue ?: return null,
            )
        }

        public fun fromJson(
            text: String,
            options: FormEngineOptions,
        ): FormSnapshot? = fromJson(text) ?: null.also { deliver(mismatchWarning(), options) }
    }
}

private fun JsonObject.optionalString(key: String): Box<String?>? =
    when (val value = this[key]) {
        null, JsonValue.Null -> Box(null)
        is JsonValue.String -> Box(value.value)
        else -> null
    }

public fun FormEngine.snapshot(): FormSnapshot =
    FormSnapshot(
        version = FormSnapshot.CURRENT_VERSION,
        definitionId = definition.json["id"]?.stringValue,
        values = withoutPasswords(values, definition.allFields),
        step = if (isMultiStep) currentStep?.id else null,
        skipped = core.skipped.map { it.id },
        seed = seed,
    )

public fun FormEngine.snapshotJson(maxBytes: Int): String? {
    val json = snapshot().toJson()
    if (json.length * UTF16_BYTES <= maxBytes) return json
    deliver(
        warning(
            IssueCode.SNAPSHOT_TOO_LARGE,
            "",
            "The saved progress is larger than $maxBytes bytes, so it is not saved",
            "Keep long texts and many group items out of forms that save their progress",
        ),
        options,
    )
    return null
}

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
    val skippable =
        definition.steps
            .orEmpty()
            .filter { it.canSkip }
            .map { it.id }
            .toSet()
    val stepsKnown = (listOfNotNull(step) + skipped).all { it in steps } && skipped.all { it in skippable }
    val sameForm = id == definitionId
    if (version == FormSnapshot.CURRENT_VERSION && sameForm && stepsKnown) {
        return withValues(withoutPasswords(values, definition.allFields))
    }
    deliver(mismatchWarning(), options)
    return null
}

private fun mismatchWarning() =
    warning(
        IssueCode.SNAPSHOT_MISMATCH,
        "",
        "The saved progress does not match this form, so the form starts fresh",
        "Discard snapshots saved for another version of the form",
    )

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

private fun withoutPasswords(
    values: JsonObject,
    fields: List<FormField>,
): JsonObject {
    val result = MutableJsonObject()
    for ((key, value) in values) {
        val owners = fields.filter { it.key == key }
        if (owners.any { it.type == PASSWORD }) continue
        val group = owners.firstOrNull { it.isGroup }
        result[key] = if (group == null) value else itemsWithoutPasswords(value, group.items)
    }
    return result
}

private fun itemsWithoutPasswords(
    value: JsonValue,
    items: List<FormField>,
): JsonValue {
    val entries = value.arrayValue ?: return value
    return JsonValue.Array(entries.map { entry -> entry.objectValue?.let { JsonValue.Object(withoutPasswords(it, items)) } ?: entry })
}

private const val PASSWORD = "password"
private const val UTF16_BYTES = 2
