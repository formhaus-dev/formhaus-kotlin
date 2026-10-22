package dev.formhaus.compose.model

import dev.formhaus.core.engine.RecallFormatFn
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.renderer.LabelSource

public typealias OptionsProvider = suspend (JsonObject) -> List<FieldOption>
public typealias FieldChangeFn = (String, JsonValue?, JsonObject) -> Unit
public typealias ValuesChangeFn = (JsonObject) -> Unit
public typealias ActionErrorFn = (Throwable) -> Unit

@Suppress("LongParameterList")
public class FormRendererOptions(
    public val labels: LabelSource? = null,
    public val optionsProviders: Map<String, OptionsProvider> = emptyMap(),
    public val onCancel: (() -> Unit)? = null,
    public val onFieldChange: FieldChangeFn? = null,
    public val onValuesChange: ValuesChangeFn? = null,
    public val onError: ActionErrorFn? = null,
    public val lang: String? = null,
    public val formatRecall: RecallFormatFn? = null,
    public val testIds: TestIds = TestIds.Off,
) {
    public fun copy(
        labels: LabelSource? = this.labels,
        optionsProviders: Map<String, OptionsProvider> = this.optionsProviders,
        onCancel: (() -> Unit)? = this.onCancel,
        onFieldChange: FieldChangeFn? = this.onFieldChange,
        onValuesChange: ValuesChangeFn? = this.onValuesChange,
        onError: ActionErrorFn? = this.onError,
        lang: String? = this.lang,
        formatRecall: RecallFormatFn? = this.formatRecall,
        testIds: TestIds = this.testIds,
    ): FormRendererOptions =
        FormRendererOptions(labels, optionsProviders, onCancel, onFieldChange, onValuesChange, onError, lang, formatRecall, testIds)

    override fun equals(other: Any?): Boolean = equalParts(other, FormRendererOptions::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("FormRendererOptions")

    private fun parts(): ValueParts =
        listOf(
            "labels" to labels,
            "optionsProviders" to optionsProviders,
            "onCancel" to onCancel,
            "onFieldChange" to onFieldChange,
            "onValuesChange" to onValuesChange,
            "onError" to onError,
            "lang" to lang,
            "formatRecall" to formatRecall,
            "testIds" to testIds,
        )
}
