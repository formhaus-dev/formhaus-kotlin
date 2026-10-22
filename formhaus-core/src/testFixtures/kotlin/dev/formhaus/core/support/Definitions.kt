package dev.formhaus.core.support

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField

fun jsonObject(text: String): JsonObject = requireNotNull(JsonValue.parse(text).objectValue)

fun definition(text: String): FormDefinition = FormDefinition(jsonObject(text))

fun field(text: String): FormField = definition("""{"id":"f","submit":{"label":"S"},"fields":[$text]}""").allFields.first()

fun engine(
    definition: String,
    values: String = "{}",
    options: FormEngineOptions =
        FormEngineOptions(onWarning = {
        }),
): FormEngine = FormEngine(definition(definition), jsonObject(values), options)

fun text(value: String): JsonValue = JsonValue.String(value)
