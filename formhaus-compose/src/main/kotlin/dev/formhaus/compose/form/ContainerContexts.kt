package dev.formhaus.compose.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.formhaus.compose.model.ActionTestIds
import dev.formhaus.compose.model.FieldState
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.actionTestIds
import dev.formhaus.compose.model.back
import dev.formhaus.compose.model.cancel
import dev.formhaus.compose.model.performPrimaryAction
import dev.formhaus.compose.model.skip
import dev.formhaus.compose.model.testId
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.renderer.FormActionsState
import dev.formhaus.core.renderer.TestIdTarget
import kotlinx.coroutines.launch

@Stable
public class SectionContext internal constructor(
    public val section: FormSection,
    public val title: String,
    public val description: String?,
    public val content: @Composable () -> Unit,
)

@Stable
public class GroupItemContext internal constructor(
    public val id: String,
    public val index: Int,
    public val title: String,
    public val canRemove: Boolean,
    public val hasContent: Boolean,
    public val removeLabel: String,
    public val removeName: String,
    public val disabled: Boolean,
    public val content: @Composable () -> Unit,
    public val requestRemove: () -> Unit,
    public val remove: () -> Unit,
    internal val removeFocus: FieldState,
    internal val model: FormModel,
    internal val fields: List<FormField>,
)

@Stable
public class ConfirmContext internal constructor(
    public val title: String,
    public val message: String,
    public val confirmLabel: String,
    public val cancelLabel: String,
    public val confirm: () -> Unit,
    public val cancel: () -> Unit,
)

@Stable
public class GroupContext internal constructor(
    public val group: FormField,
    public val label: String,
    public val helperText: String?,
    public val error: String?,
    public val items: List<GroupItemContext>,
    public val canAdd: Boolean,
    public val addLabel: String,
    public val disabled: Boolean,
    public val add: () -> Unit,
    internal val model: FormModel,
)

@Immutable
public data class StepStatus(
    val id: String,
    val title: String,
    val errorCount: Int,
    val errorLabel: String?,
)

@Immutable
public data class ProgressContext(
    val current: Int,
    val total: Int,
    val counterLabel: String,
    val stepTitle: String?,
    val stepDescription: String?,
    val steps: List<StepStatus>?,
    val errorCount: Int = 0,
    val errorLabel: String? = null,
)

@Stable
public class ActionsContext internal constructor(
    public val state: FormActionsState,
    public val busy: Boolean,
    public val model: FormModel,
) {
    internal constructor(model: FormModel) : this(model.actions, model.disabled, model)

    public val testIds: ActionTestIds get() = model.actionTestIds

    internal val primaryTestId: String?
        get() = model.testId(if (state.isLastStep) TestIdTarget.Kind.SUBMIT else TestIdTarget.Kind.NEXT)

    public fun performPrimaryAction() {
        model.scope.launch { model.performPrimaryAction() }
    }

    public fun back() {
        model.scope.launch { model.back() }
    }

    public fun skip() {
        model.scope.launch { model.skip() }
    }

    public fun cancel() {
        model.cancel()
    }
}
