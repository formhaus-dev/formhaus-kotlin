package dev.formhaus.compose.form

import androidx.compose.runtime.Stable
import dev.formhaus.compose.model.FieldState
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.commitValue
import dev.formhaus.compose.model.options
import dev.formhaus.compose.model.performPrimaryAction
import dev.formhaus.compose.model.setValue
import dev.formhaus.compose.model.testId
import dev.formhaus.compose.model.texts
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.toJsonString
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.TestIdTarget
import dev.formhaus.core.renderer.advancesOnCommit
import dev.formhaus.core.renderer.isRequired
import kotlinx.coroutines.launch

@Stable
public class FieldContext internal constructor(
    public val field: FormField,
    public val key: String,
    internal val state: FieldState,
    public val model: FormModel,
) {
    public constructor(field: FormField, model: FormModel, key: String = field.key) : this(field, key, model.field(key), model)

    private val texts = model.texts(field, key)
    public val label: String = texts.label.orEmpty()
    public val placeholder: String? = texts.placeholder
    public val helperText: String? = texts.helperText?.takeIf { it.isNotEmpty() }
    public val value: JsonValue? = state.value
    public val error: String? = state.error
    public val loading: Boolean = state.loading
    public val disabled: Boolean = model.disabled
    internal val focused: Boolean = state.hasFocus
    public val options: List<FieldOption> =
        model.options(key).let { loaded ->
            if (field.randomize) model.engine.orderOptions(key, loaded) else loaded ?: field.options.orEmpty()
        }

    public val isItem: Boolean get() = key != this.field.key
    public val isInvalid: Boolean get() = !error.isNullOrEmpty()
    public val isLocked: Boolean get() = loading || (disabled && !focused)
    public val isRequired: Boolean get() = this.field.isRequired
    public val advancesOnCommit: Boolean get() = !isItem && this.field.advancesOnCommit
    public val accessibilityName: String get() = if (isRequired) "$label, ${text("required")}" else label
    public val testId: String? get() = model.testId(TestIdTarget.Kind.FIELD, key)

    public val text: String
        get() =
            when (val current = value) {
                is JsonValue.String -> current.value
                is JsonValue.Number -> numberText(current.value)
                is JsonValue.Bool -> current.value.toString()
                else -> ""
            }

    public fun optionTestId(value: String): String? = model.testId(TestIdTarget.Kind.OPTION, key, value)

    public fun setValue(value: JsonValue?) {
        model.setValue(key, value)
    }

    public fun commit(value: JsonValue?) {
        if (advancesOnCommit) model.commitValue(key, value) else model.setValue(key, value)
    }

    public fun performPrimaryAction() {
        model.scope.launch { model.performPrimaryAction() }
    }

    public fun text(
        label: String,
        params: JsonObject = MutableJsonObject(),
    ): String = model.label(label, params)
}

internal fun numberText(number: Double): String = JsonValue.Number(number).toJsonString()
