package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FILE_LISTS_MINOR
import dev.formhaus.core.model.OPTION_RULES_MINOR
import dev.formhaus.core.model.formatAtLeast

internal object Properties {
    val since =
        mapOf(
            "multiple" to FILE_LISTS_MINOR,
            "readOnly" to OPTION_RULES_MINOR,
            "reorderable" to OPTION_RULES_MINOR,
            "description" to OPTION_RULES_MINOR,
            "disabled" to OPTION_RULES_MINOR,
        )

    fun known(
        list: List<String>,
        version: JsonValue?,
    ): List<String> = list.filter { key -> since[key]?.let { formatAtLeast(version, it) } != false }

    fun newer(
        list: List<String>,
        version: JsonValue?,
        key: String,
    ): String? {
        val needed = since[key]?.takeIf { key in list && !formatAtLeast(version, it) } ?: return null
        return "Set \"formatVersion\": \"1.$needed\" to use it"
    }

    val form =
        listOf(
            "\$schema",
            "formatVersion",
            "id",
            "lang",
            "title",
            "description",
            "messages",
            "labels",
            "i18n",
            "submit",
            "cancel",
            "fields",
            "steps",
        )
    val step =
        listOf("id", "title", "description", "fields", "show", "showAny", "routes", "next", "back", "skip", "i18n")
    val field =
        listOf(
            "key",
            "type",
            "label",
            "placeholder",
            "helperText",
            "autoAdvance",
            "defaultValue",
            "source",
            "show",
            "showAny",
            "validation",
            "options",
            "randomize",
            "layout",
            "columns",
            "optionsFrom",
            "optionsDependsOn",
            "accept",
            "multiple",
            "rows",
            "mask",
            "inputMode",
            "autocomplete",
            "readOnly",
            "props",
            "i18n",
        )
    val section = listOf("key", "type", "title", "description", "layout", "show", "showAny", "fields", "i18n")
    val group =
        listOf(
            "key",
            "type",
            "label",
            "helperText",
            "itemLabel",
            "addLabel",
            "removeLabel",
            "defaultItems",
            "reorderable",
            "show",
            "showAny",
            "validation",
            "fields",
            "i18n",
        )
    val option = listOf("value", "label", "image", "exclusive", "pinned", "description", "disabled", "i18n")
    val action = listOf("label", "variant", "disabled", "i18n")
    val route = listOf("to", "show", "showAny")
    val validation =
        listOf(
            "required",
            "minLength",
            "minLengthMessage",
            "maxLength",
            "maxLengthMessage",
            "min",
            "minMessage",
            "max",
            "maxMessage",
            "pattern",
            "patternMessage",
            "matchField",
            "matchFieldMessage",
            "validator",
            "i18n",
        )
}
