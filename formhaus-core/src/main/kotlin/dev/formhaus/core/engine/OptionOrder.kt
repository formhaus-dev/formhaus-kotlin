package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.warning
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.validation.randomizedTypes
import java.util.concurrent.atomic.AtomicBoolean

private val reportedUnordered = AtomicBoolean(false)

internal fun unorderedWarning(
    definition: FormDefinition,
    options: FormEngineOptions,
): List<DefinitionIssue> {
    if (options.research != null ||
        definition.allFields.none { field -> field.randomize || field.items.any { it.randomize } }
    ) {
        return emptyList()
    }
    if (reportedUnordered.getAndSet(true)) return emptyList()
    return listOf(
        warning(
            IssueCode.MISSING_RESEARCH,
            "",
            "\"randomize\" needs the research module, so the options show in written order",
            "Pass Research() from dev.formhaus.research",
        ),
    )
}

@RendererApi
public fun optionKey(
    group: String,
    field: String,
): String = "$group.$field"

@OptIn(RendererApi::class)
internal fun FormEngine.optionName(key: String): String = core.visibility.parse(key)?.let { optionKey(it.group, it.field) } ?: key

@OptIn(RendererApi::class)
internal fun FormEngine.orderedOptions(
    key: String,
    options: List<FieldOption>?,
): List<FieldOption> {
    val visibility = core.visibility
    val values = core.values.json
    val name = optionName(key)
    val field =
        visibility.fieldOf(name, values) ?: visibility.groups.firstNotNullOfOrNull { owner ->
            if (visibility.fieldOf(owner.key, values)?.ordinal != owner.ordinal) {
                null
            } else {
                owner.items.firstOrNull { optionKey(owner.key, it.key) == name }
            }
        }
    val source = options ?: field?.options.orEmpty()
    val research = this.options.research
    if (research == null || field == null || !field.randomize || field.type !in randomizedTypes) return source
    if (visibility.index.section(field)?.isMatrix == true) return source
    return research.order(seed, name, source)
}
