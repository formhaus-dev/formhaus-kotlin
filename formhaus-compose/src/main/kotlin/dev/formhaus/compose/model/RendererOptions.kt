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

public data class FormRendererOptions(
    val labels: LabelSource? = null,
    val optionsProviders: Map<String, OptionsProvider> = emptyMap(),
    val onCancel: (() -> Unit)? = null,
    val onFieldChange: FieldChangeFn? = null,
    val onValuesChange: ValuesChangeFn? = null,
    val onError: ActionErrorFn? = null,
    val lang: String? = null,
    val formatRecall: RecallFormatFn? = null,
    val testIds: TestIds = TestIds.Off,
)
