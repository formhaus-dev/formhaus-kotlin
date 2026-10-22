package dev.formhaus.demo

import dev.formhaus.compose.form.FormComponents
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.i18n.localize
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.research.Research

class Sample(
    val id: String,
    val title: String,
    val summary: String,
    val definition: FormDefinition,
    val components: FormComponents = FormComponents.standard,
    val engineOptions: FormEngineOptions = FormEngineOptions(),
    val labels: LabelSource? = null,
)

private val translated = localize(JsonValue.parse(SampleDefinitions.contact), JsonValue.parse(SampleDefinitions.german), "de")

val samples: List<Sample> =
    listOf(
        Sample("contact", "Contact", "One step with validation", FormDefinition(JsonValue.parse(SampleDefinitions.contact))),
        Sample(
            "trip",
            "Trip",
            "Three steps, a traveller group and randomized options",
            FormDefinition(JsonValue.parse(SampleDefinitions.trip)),
            engineOptions = FormEngineOptions(research = Research()),
        ),
        Sample(
            "translated",
            "Translated",
            "The contact form in German",
            translated.definition,
            engineOptions = FormEngineOptions(messages = MessageSource.Catalog(translated.messages)),
            labels = LabelSource.Catalog(translated.labels),
        ),
        Sample(
            "system",
            "System set",
            "Material 3 components and colors",
            FormDefinition(JsonValue.parse(SampleDefinitions.trip)),
            components = FormComponents.system,
        ),
        Sample(
            "custom",
            "Custom slot",
            "Text fields drawn by the app",
            FormDefinition(JsonValue.parse(SampleDefinitions.contact)),
            components = FormComponents.standard.field("text") { UnderlineField(it) }.field("email") { UnderlineField(it) },
        ),
    )
