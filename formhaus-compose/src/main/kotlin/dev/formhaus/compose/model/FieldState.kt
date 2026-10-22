package dev.formhaus.compose.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.formhaus.core.engine.GroupItem
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FieldOption

@Stable
public class FieldState internal constructor(
    public val key: String,
) {
    public var value: JsonValue? by mutableStateOf(null)
        private set
    public var error: String? by mutableStateOf(null)
        private set
    public var loading: Boolean by mutableStateOf(false)
        private set
    public var version: Int by mutableIntStateOf(0)
        private set
    public var focusToken: Int by mutableIntStateOf(0)
        private set
    internal var hasFocus: Boolean by mutableStateOf(false)
        private set
    private var focusPending: Int? = null
    private var orderSource: List<FieldOption>? = null
    private var ordered: List<FieldOption> = emptyList()

    internal fun ordered(
        source: List<FieldOption>,
        order: () -> List<FieldOption>,
    ): List<FieldOption> {
        if (orderSource !== source) {
            ordered = order()
            orderSource = source
        }
        return ordered
    }

    internal fun requestFocus(id: Int) {
        focusPending = id
        focusToken += 1
    }

    internal fun noteFocus(focused: Boolean) {
        hasFocus = focused
    }

    internal fun dropFocusRequest() {
        focusPending = null
    }

    internal fun takeFocusRequest(): Int? = focusPending.also { focusPending = null }

    internal fun update(
        value: JsonValue?,
        error: String?,
        loading: Boolean,
    ) {
        if (this.value == value && this.error == error && this.loading == loading) return
        this.value = value
        this.error = error
        this.loading = loading
        version += 1
    }
}

@Stable
public class GroupState internal constructor(
    public val key: String,
) {
    public var items: List<GroupItem> by mutableStateOf(emptyList())
        private set
    public var hasContent: List<Boolean> by mutableStateOf(emptyList())
        private set

    internal fun update(
        items: List<GroupItem>,
        hasContent: List<Boolean>,
    ) {
        this.items = items
        this.hasContent = hasContent
    }
}

internal class OptionsState {
    var options: List<FieldOption>? by mutableStateOf(null)
        private set
    var request = 0
    var source: OptionsSource? = null

    fun update(options: List<FieldOption>?) {
        this.options = options
    }

    fun cancel() {
        request += 1
        source = null
        update(null)
    }
}

internal data class OptionsSource(
    val provider: String,
    val revision: Int,
    val inputs: List<JsonValue?>,
)

public data class FocusRequest(
    val key: String?,
    val id: Int,
    val summary: Boolean,
)
