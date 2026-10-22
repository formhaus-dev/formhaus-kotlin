package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.jsString
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField

private const val INVALID_VALUE_MESSAGE = "Invalid value"

internal val defaultMessages: Map<MessageKey, String> =
    linkedMapOf(
        MessageKey.REQUIRED to "This field is required",
        MessageKey.MIN_LENGTH to "Minimum length is {min}",
        MessageKey.MAX_LENGTH to "Maximum length is {max}",
        MessageKey.MIN_ITEMS to "At least {min} required",
        MessageKey.MAX_ITEMS to "At most {max} allowed",
        MessageKey.MIN to "Must be at least {min}",
        MessageKey.MAX to "Must be at most {max}",
        MessageKey.PATTERN to "Invalid format",
        MessageKey.MATCH_FIELD to "Fields must match",
        MessageKey.EMAIL to "Enter a valid email",
        MessageKey.NUMBER to "Enter a number",
        MessageKey.DATE to "Enter a valid date",
        MessageKey.DATETIME to "Enter a valid date and time",
        MessageKey.TIME to "Enter a valid time",
        MessageKey.OPTION to "Select one of the available options",
        MessageKey.OPTIONS to "Must be a list of available options",
        MessageKey.EXCLUSIVE to "Select \"{option}\" or other options, not both",
        MessageKey.BOOLEAN to "Must be true or false",
        MessageKey.STRING to "Must be text",
        MessageKey.FILE to "Must be a file reference",
        MessageKey.VALUE to INVALID_VALUE_MESSAGE,
        MessageKey.HIDDEN to "{label}: {message}",
    )

internal val defaultMessageKeys: List<MessageKey> = defaultMessages.keys.toList()

public typealias MessageParams = JsonObject
public typealias MessageFn = (MessageKey, MessageParams, FormField) -> String?
internal typealias ResolveMessage = (MessageKey, MessageParams, FormField) -> String

public sealed interface MessageSource {
    public data class Catalog(
        public val catalog: JsonValue,
    ) : MessageSource

    public class Function(
        public val resolve: MessageFn,
    ) : MessageSource
}

internal val noParams: JsonObject = stringMapOf()

private fun isWordChar(char: Char): Boolean = char in '0'..'9' || char in 'A'..'Z' || char in 'a'..'z' || char == '_'

internal fun replacePlaceholders(
    template: String,
    replacement: (String) -> String?,
): String {
    val output = StringBuilder(template.length)
    var index = 0
    while (index < template.length) {
        if (template[index] == '{') {
            var end = index + 1
            while (end < template.length && isWordChar(template[end])) end++
            if (end > index + 1 && end < template.length && template[end] == '}') {
                val value = replacement(template.substring(index + 1, end))
                if (value != null) output.append(value) else output.append(template, index, end + 1)
                index = end + 1
                continue
            }
        }
        output.append(template[index])
        index++
    }
    return output.toString()
}

internal fun formatText(
    template: String,
    params: JsonObject,
): String = cut(replacePlaceholders(template) { name -> params[name]?.let(::jsString) })

internal fun resolveText(
    key: MessageKey,
    params: JsonObject,
    sources: List<MessageSource?>,
    field: FormField?,
): String {
    for (source in sources) {
        when (source) {
            is MessageSource.Function -> {
                if (field != null) {
                    source.resolve(key, params, field)?.takeIf { it.isNotEmpty() }?.let { return it }
                }
            }

            is MessageSource.Catalog -> {
                source.catalog[key.rawValue]?.stringValue?.takeIf { it.isNotEmpty() }?.let {
                    return formatText(
                        it,
                        params,
                    )
                }
            }

            null -> {
                continue
            }
        }
    }
    return formatText(defaultMessages[key].orEmpty(), params)
}

internal fun messageResolver(vararg sources: MessageSource?): ResolveMessage {
    val list = sources.toList()
    return { key, params, field -> resolveText(key, params, list, field) }
}

internal fun namedResolver(resolve: ResolveMessage): ResolveMessage =
    { key, params, field ->
        val message = resolve(key, params, field)
        if (field.type != "hidden") {
            message
        } else {
            val label = field.label?.takeIf { it.isNotEmpty() } ?: field.key
            resolve(
                MessageKey.HIDDEN,
                stringMapOf(
                    "label" to JsonValue.String(label),
                    "message" to JsonValue.String(message),
                ),
                field,
            )
        }
    }

public fun getDefaultMessage(
    rule: MessageKey,
    params: JsonObject = noParams,
): String {
    val template = defaultMessages[rule] ?: INVALID_VALUE_MESSAGE
    val custom = "unit" in params && (rule == MessageKey.MIN_LENGTH || rule == MessageKey.MAX_LENGTH)
    val unitTemplate = if (rule == MessageKey.MIN_LENGTH) "At least {min} {unit}" else "At most {max} {unit}"
    return formatText(if (custom) unitTemplate else template, params)
}
