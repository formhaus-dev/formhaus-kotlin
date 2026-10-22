package dev.formhaus.core.definitionvalidation

internal object Properties {
    val form = listOf(
        "\$schema", "formatVersion", "id", "lang", "title", "description", "messages", "labels", "i18n", "submit", "cancel", "fields", "steps",
    )
    val step = listOf("id", "title", "description", "fields", "show", "showAny", "routes", "next", "back", "skip", "i18n")
    val field = listOf(
        "key", "type", "label", "placeholder", "helperText", "autoAdvance", "defaultValue", "source", "show", "showAny", "validation",
        "options", "randomize", "layout", "columns", "optionsFrom", "optionsDependsOn", "accept", "rows", "mask", "inputMode",
        "autocomplete", "props", "i18n",
    )
    val section = listOf("key", "type", "title", "description", "layout", "show", "showAny", "fields", "i18n")
    val group = listOf(
        "key", "type", "label", "helperText", "itemLabel", "addLabel", "removeLabel", "defaultItems", "show", "showAny", "validation",
        "fields", "i18n",
    )
    val option = listOf("value", "label", "image", "exclusive", "pinned", "i18n")
    val action = listOf("label", "variant", "disabled", "i18n")
    val route = listOf("to", "show", "showAny")
    val validation = listOf(
        "required", "minLength", "minLengthMessage", "maxLength", "maxLengthMessage", "min", "minMessage", "max", "maxMessage", "pattern",
        "patternMessage", "matchField", "matchFieldMessage", "validator", "i18n",
    )
}
